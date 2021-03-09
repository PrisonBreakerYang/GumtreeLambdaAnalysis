import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import org.eclipse.jetty.util.ArrayUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import opennlp.tools.stemmer.PorterStemmer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import com.github.gumtreediff.tree.Tree;

public class BadLambdaFinder {
    private final Repository repo;
    private final Git git;
    private final String url;
    private final Stemmer stemmer;
    private final int threshold;
    int context;
    List<ModifiedLambda> modifiedLambdas;
    String[] keywords;
    public BadLambdaFinder()
    {
        this.repo = null;
        this.git = null;
        this.url = null;
        this.stemmer = null;
        this.threshold = 0;
        this.keywords = null;
    }

    public BadLambdaFinder(List<ModifiedLambda> modifiedLambdas, String url, String repoPath, int editThreshold, String[] keywords, int context) throws IOException, GitAPIException {
        assert url.endsWith(".git");
        //"/repos" is the path that stores all downloaded GitHub projects(by git clone)
        if (!Files.exists(Paths.get("../repos"))) {
            new File("../repos").mkdirs();
        }

        String repoName = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf(".git"));
        //String ownerName = url.split("[/ : . # %]")[5];
        if (repoPath == null) {
            repoPath = "../repos/" + repoName;
        }
        else repoPath = repoPath + "/" + repoName;
        if (!Files.exists(Paths.get(repoPath))) {
            System.err.printf("Cloning into %s...\n", repoPath);
            Git.cloneRepository().setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.err))).setURI(url).setDirectory(new File(repoPath)).call();
        }
        repo = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build();
        git = new Git(repo);
        this.url = url;
        this.modifiedLambdas = modifiedLambdas;
        stemmer = new Stemmer();
        threshold = editThreshold;
        this.keywords = keywords;
        this.context = context;

        //walk all commits from the head commit
        walkAllCommits(repo, url, repoPath);
    }

    /*
    This method can merge edits in a list of edits. For example,
    if Edit A removed line (3 - 9) and Edit B removed line (10 -
     13), then they can be merged to a larger edit. This is
     designed as a patch since the diff algorithm is not so
     perfect. Removed lambda in a large edit will not be
     included.
     */
    static List<Edit> getMergedEdits(List<Edit> editList)
    {
        int seq = 1;
        List<Edit> mergedEdits = new ArrayList<>();
        mergedEdits.add(editList.get(0));
        while (seq < editList.size())
        {
            assert editList.get(seq).getType() == Edit.Type.DELETE || editList.get(seq).getType() == Edit.Type.REPLACE;
            assert editList.get(seq).getBeginA() > editList.get(seq - 1).getEndA();
            if (editList.get(seq).getBeginA() == mergedEdits.get(mergedEdits.size() - 1).getEndA() + 1)
            {
                int as = mergedEdits.get(mergedEdits.size() - 1).getBeginA();
                int ae = editList.get(seq).getEndA();
                int bs = mergedEdits.get(mergedEdits.size() - 1).getBeginB();
                int be = editList.get(seq).getEndB();
                Edit mergedEdit = new Edit(as, ae, bs, be);
                mergedEdits.remove(mergedEdits.size() - 1);
                mergedEdits.add(mergedEdit);
            }
            else
            {
                mergedEdits.add(editList.get(seq));
            }
            seq += 1;
        }

        return mergedEdits;
    }

    /*
    This method is also a patch to tackle of the problem of diff
    algorithm.
     */
    boolean isLambdaReallyRemoved(PositionTuple positionTuple, List<Edit> mergedEditList, RevCommit currentCommit, String currentPath) throws IOException {
        RevTree newTree = currentCommit.getTree();
        TreeWalk newTreeWalk = TreeWalk.forPath(repo, currentPath, newTree);
        ObjectLoader fileContentAfterCommit = null;
        if (newTreeWalk != null)
        {
            fileContentAfterCommit = repo.open(newTreeWalk.getObjectId(0));
        }
        assert fileContentAfterCommit != null;
        String newFileContent = new String(fileContentAfterCommit.getBytes());
        //newFileContentLines records every line of the file content after this commit
        String[] newFileContentLines = newFileContent.split("\n");
        for (Edit mergedEdit : mergedEditList)
        {
            if (positionTuple.beginLine >= mergedEdit.getBeginA() + 1 && positionTuple.beginLine <= mergedEdit.getEndA())
            //A represents the information of the edit in the old file while B represents that in the new file
            //If the lambda is in the code snippet before the edit, we check whether it still exists in the snippet after the edit
            {
                for (int i = mergedEdit.getBeginB() + 1; i <= mergedEdit.getEndB(); i++)
                {
                    if (newFileContentLines[i - 1].contains("->"))
                    //If there are still lambdas in the code snippet after the edit
                    {
                        return false;
                    }
                }
                break;
            }
        }
        return true;
    }

    static boolean lambdaInEdits(PositionTuple positionTuple, List<Edit> mergedEditList, String AorB)
    {
        if (AorB.equals("A"))
        {
            for (Edit mergedEdit : mergedEditList) {
                if (positionTuple.beginLine >= mergedEdit.getBeginA() + 1 && positionTuple.beginLine <= mergedEdit.getEndA()) {
                    return true;
                }
            }
        }
        if (AorB.equals("B"))
        {
            for (Edit mergedEdit : mergedEditList) {
                if (positionTuple.beginLine >= mergedEdit.getBeginB() + 1 && positionTuple.beginLine <= mergedEdit.getEndB()) {
                    return true;
                }
            }
        }
        return false;
    }
    //This function counts how many java files are modified in a single commit
    int numOfJavaFiles(List<DiffEntry> diffs) throws IOException {
        int count = 0;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(repo);
        //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));
        for (DiffEntry diff : diffs)
        {
            if (!(diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.ADD
                    || ((diff.getChangeType() == DiffEntry.ChangeType.RENAME) && (!diff.getOldPath().equals(diff.getNewPath()))))) {
                if (formatter.toFileHeader(diff).getOldPath().endsWith(".java"))
                {
                    count += 1;
                }
            }
        }
        return count;
    }

    //This function is based on the Gumtree diff tool
    //https://github.com/GumTreeDiff/gumtree
    //A probably useful blog: https://hoblovski.github.io/2018/05/31/GumTree.html
    void gumTreeDiffWithParent(RevCommit currentCommit, RevCommit parentCommit, String url) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(repo);
        //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));
        List<DiffEntry> diffs = formatter.scan(parentCommit, currentCommit);
        int fileModified = diffs.size();

        if (!commitRelatedToKeywords(currentCommit, this.keywords))
        {
            return;
        }
        diffs.forEach(diff ->
        {
            //We only focus on modified java files
            if (diff.getChangeType() == DiffEntry.ChangeType.DELETE
             || diff.getChangeType() == DiffEntry.ChangeType.ADD) return;

            try {
                boolean isJavaFile = false;
                FileHeader fileHeader = formatter.toFileHeader(diff);

                if (fileHeader.getOldPath().endsWith(".java")) {
                    isJavaFile = true;
                }

                if (!isJavaFile) {
                    return;
                }

                formatter.setContext(0);    //set the context lines to be 0
                formatter.format(diff);
                String content = outputStream.toString();
                //If the diff file even doesn't have a lambda expression, pass
                if (!content.contains("->")) {
                    return;
                }

                outputStream.reset();
                RevTree oldTree = parentCommit.getTree();
                RevTree newTree = currentCommit.getTree();
                TreeWalk oldTreeWalk = TreeWalk.forPath(repo, diff.getOldPath(), oldTree);
                TreeWalk newTreeWalk = TreeWalk.forPath(repo, diff.getNewPath(), newTree);
                ObjectLoader fileContentBeforeCommit = null;
                ObjectLoader fileContentAfterCommit = null;
                if (oldTreeWalk != null && newTreeWalk != null) {
                    fileContentBeforeCommit = repo.open(oldTreeWalk.getObjectId(0));
                    fileContentAfterCommit = repo.open(newTreeWalk.getObjectId(0));

                    List<PositionTuple> positionTupleList = new ArrayList<>();
                    //List<PositionTuple> positionTupleList_new = new ArrayList<>();
                    GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(new String(fileContentBeforeCommit.getBytes()), positionTupleList, true);
                    //GumtreeJDTDriver gumtreeJDTDriver_new = new GumtreeJDTDriver(new String(fileContentAfterCommit.getBytes()), positionTupleList_new, true);
                    List<Edit> editsLongerThanThreshold = new ArrayList<>();
                    List<Edit> editsDeletedOrReplaced = new ArrayList<>();
                    List<Edit> mergedEdits;
                    for (HunkHeader hunk : fileHeader.getHunks())
                    {
                        for (Edit edit : hunk.toEditList())
                        {
//                            if (edit.getType() == Edit.Type.DELETE || ((edit.getType() == Edit.Type.REPLACE) && (edit.getEndA() - edit.getBeginA() > threshold)))
//                            //if ((edit.getType() == Edit.Type.DELETE || edit.getType() == Edit.Type.REPLACE) && (edit.getEndA() - edit.getBeginA() > threshold))
//                            {
//                                editsLongerThanThresholdOrDeleted.add(edit);
//                            }
                            if (edit.getType() == Edit.Type.DELETE || edit.getType() == Edit.Type.REPLACE)
                            {
                                editsDeletedOrReplaced.add(edit);
                            }
                        }
                    }
                    if (editsDeletedOrReplaced.isEmpty())
                    {
                        return;
                    }
                    mergedEdits = getMergedEdits(editsDeletedOrReplaced);
                    for (Edit mergedEdit : mergedEdits)
                    {
                        //If the removed lambda is in a big edit (with edit lines more than threshold, e.g. 5)
                        if (mergedEdit.getEndA() - mergedEdit.getBeginA() > threshold)
                        {
                            editsLongerThanThreshold.add(mergedEdit);
                        }
                    }

                    //Maybe the following for loop can be deleted......
//                    for (HunkHeader hunk : fileHeader.getHunks())
//                    {
//                        for (Edit edit : hunk.toEditList())
//                        {
//                            if (edit.getType() == Edit.Type.DELETE)
//                            {
//                                editsLongerThanThresholdOrDeleted.add(edit);
//                            }
//                        }
//                    }

                    FileWriter oldFile, newFile;
                    try {
                        //Cache target file in local file directory, maybe it's not so necessary......
                        oldFile = new FileWriter("old-new-file\\oldfile.java");
                        oldFile.write("");
                        String oldFileContent = new String(fileContentBeforeCommit.getBytes());
                        oldFile.write(oldFileContent);
                        oldFile.flush();

                        newFile = new FileWriter("old-new-file\\newfile.java");
                        newFile.write("");
                        newFile.write(new String(fileContentAfterCommit.getBytes()));
                        newFile.flush();

                        Run.initGenerators();
                        //Build tree for both old and new files by Gumtree tool

                        Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file\\oldfile.java").getRoot();
                        Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file\\newfile.java").getRoot();

                        //Diff information is stored in the variable "mappings"
                        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
                        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);
                        //System.out.println(mappings);
                        //EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator();
                        //EditScript actions = editScriptGenerator.computeActions(mappings);

                        boolean diffRelatedToLambda = false;
                        boolean actionTraveled = false;

                        //List<PositionTupleMap> positionTupleMapList = new ArrayList<>();

                        //遍历当前diff所有positionTuple
                        for (PositionTuple positionTuple : positionTupleList)
                        {
                            boolean lambdaInLongDeletedCode = false;
                            for (Edit edit : editsLongerThanThreshold)
                            {
                                //Not sure about the second item. endline or beginline??
                                if (positionTuple.beginLine >= edit.getBeginA() + 1 && positionTuple.beginLine <= edit.getEndA())
                                {
                                    lambdaInLongDeletedCode = true;
                                    break;
                                }
                            }

                            if (lambdaInLongDeletedCode)
                            {
                                continue;
                            }

                            if (mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0)) == null
                            && isLambdaReallyRemoved(positionTuple, mergedEdits, currentCommit, diff.getNewPath())
                            && lambdaInEdits(positionTuple, mergedEdits, "A"))
                            {
                                //表示commit前文件的lambda在commit后文件中找不到

                                int nodesNum = oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0).getMetrics().size;
                                assert url.endsWith(".git");
                                //Record the length of edit which hold this removed lambda
                                int editBeginLine = 0, editEndLine = 0;
                                for (Edit edit : mergedEdits)
                                {
                                    if (positionTuple.beginLine >= edit.getBeginA() + 1 && positionTuple.beginLine <= edit.getEndA())
                                    {
                                        editBeginLine = edit.getBeginA() + 1;
                                        editEndLine = edit.getEndA();
                                    }
                                }
                                ModifiedLambda newLambda = new ModifiedLambda(repo, currentCommit, parentCommit, diff, gumtreeJDTDriver.cu, positionTuple,
                                        nodesNum, url.substring(0, url.lastIndexOf(".git")), fileModified, numOfJavaFiles(diffs), editBeginLine, editEndLine,
                                        lambda_context(oldFileContent, positionTuple.beginLine, positionTuple.endLine, this.context));

                                this.modifiedLambdas.add(newLambda);
                            }
                        }

                        oldFile.close();
                        newFile.close();
                    } catch (SyntaxException e) {
                        //e.printStackTrace();
                    }
                }
//                if (fileContentBeforeCommit == null) {
//                    return;
//                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
    static String lambda_context(String fileContent, int lambdaBeginLine, int lambdaEndLine, int context)
    {
        String[] lines = fileContent.split("\n");
        int beginLine = Math.max(1, lambdaBeginLine - context);
        int endLine = Math.min(lines.length, lambdaEndLine + context);
        StringBuilder lambdaWithContext = new StringBuilder();
        for (int i = beginLine; i <= endLine; i++)
        {
            lambdaWithContext.append(lines[i - 1]);
            lambdaWithContext.append("\n");
        }
        return lambdaWithContext.toString();
    }

    void walkAllCommits(Repository repo, String url, String repoPath) throws GitAPIException, IOException {
//        String repoName = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf(".git"));
//        File branchRoot = new File(repoPath +"/.git/refs/heads");
//        //The default branch name of some projects are not "master", we can get the proper name in the "heads" directory
//        //To improve: https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/porcelain/ListBranches.java
//        File[] branch = branchRoot.listFiles();
//        assert branch != null;
//        System.out.println(branch[0].toString());
//        assert Objects.requireNonNull(branch).length == 1;
        //String branch = "repos/" + repoName
        //Ref head = repo.exactRef("refs/heads/" + branch[0].toString().split("\\\\")[7]);
        List<Ref> call = git.branchList().call();
        Ref head= call.get(0);
        System.out.println(head);
        assert call.size() == 1;
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        System.out.println("Start commit: " + commit);
        System.out.println("Walking all commits starting at HEAD");
        walk.markStart(commit);
        walk.setRevFilter(RevFilter.NO_MERGES);
        int count = 0;
        for (RevCommit currentCommit : walk) {
            if (currentCommit.getParentCount() == 0) {
                // this is the initial commit of the repo
                break;
            } else {
                //if (currentCommit.getParents().length > 1) System.out.println(currentCommit.getParents().length + "\n" + currentCommit.getName());
                for (RevCommit parentCommit : currentCommit.getParents()) {
                    gumTreeDiffWithParent(currentCommit, parentCommit, url);
                }
            }

            count++;
        }
        walk.dispose();
    }
    //This function is not used currently
    public void statisticsOfModifiedLambdas(List<ModifiedLambda> modifiedLambdas)
    {
        //how many actions in a single lambda
        //how much proportion of lambdas are revised (count by position and node number, "insert" and move type not included)
        //some problems: many actions' area overlap. so the proportion can be larger than 1
        //the distribution of action type [action, node]    *
        //average actions in one-line lambda and multi-line lambda
        //average and max action node depth (how far the node from the root)
        //average and max action node height (how far the farthest leaf node away from the node)
        //average and max action node size (the number of the node and its descendents)
        for (ModifiedLambda modifiedLambda : modifiedLambdas)
        {
            modifiedLambda.actionNum = modifiedLambda.actionList.size();
            modifiedLambda.lineLength = modifiedLambda.pos.endLine - modifiedLambda.pos.beginLine + 1;
            modifiedLambda.posLength = modifiedLambda.pos.endPos - modifiedLambda.pos.beginPos;
            modifiedLambda.oneLineLambda = modifiedLambda.lineLength == 1;
            modifiedLambda.action_avg = modifiedLambda.actionNum / modifiedLambda.lineLength;
            int depthSum = 0;
            int heightSum = 0;
            int sizeSum = 0;
            modifiedLambda.actionDepth_max = 0;
            modifiedLambda.actionHeight_max = 0;
            modifiedLambda.actionSize_max = 0;
            float lengthOfActions = 0;
            float numOfModNodes = 0;
            modifiedLambda.proportionOfModPos = 0;
            modifiedLambda.proportionOfModNodes = 0;
            //遍历action
            for (Action action : modifiedLambda.actionList)
            {
                depthSum += action.getNode().getMetrics().depth;
                heightSum += action.getNode().getMetrics().height;
                sizeSum += action.getNode().getMetrics().size;
                if (!action.getName().contains("insert") && !action.getName().contains("move"))
                {
                    lengthOfActions += action.getNode().getLength();
                    numOfModNodes += action.getNode().getMetrics().size;
                }
                modifiedLambda.actionTypeBag.add(new String("<" + action.getName().split("-")[0] + " " + action.getNode().getType() + ">"));
                modifiedLambda.actionDepth_max = Math.max(action.getNode().getMetrics().depth, modifiedLambda.actionDepth_max);
                modifiedLambda.actionHeight_max = Math.max(action.getNode().getMetrics().height, modifiedLambda.actionHeight_max);
                modifiedLambda.actionSize_max = Math.max(action.getNode().getMetrics().size, modifiedLambda.actionSize_max);
            }
            modifiedLambda.actionDepth_avg = depthSum / modifiedLambda.actionNum;
            modifiedLambda.actionHeight_avg = heightSum / modifiedLambda.actionNum;
            modifiedLambda.actionSize_avg = sizeSum / modifiedLambda.actionNum;
            modifiedLambda.proportionOfModPos = lengthOfActions / modifiedLambda.posLength;
            modifiedLambda.proportionOfModNodes = numOfModNodes / modifiedLambda.nodesNum;
            //遍历action type，形如<update SimpleName>
            modifiedLambda.actionTypeSet = new HashSet<>(modifiedLambda.actionTypeBag);
            for (String actionType : modifiedLambda.actionTypeBag)
            {
                if (!modifiedLambda.actionTypeMap.containsKey(actionType))
                {
                    modifiedLambda.actionTypeMap.put(actionType, 1);
                }
                else if (modifiedLambda.actionTypeMap.containsKey(actionType))
                {
                    Integer value = modifiedLambda.actionTypeMap.get(actionType) + 1;
                    modifiedLambda.actionTypeMap.put(actionType, value);
                }
            }
        }

    }
    //This function is not used currently, because we are not filtering commit message now
    @Deprecated
    public boolean commitRelatedToKeywords(ModifiedLambda lambda, String[] keywords)
    {
        RevCommit commit = lambda.currentCommit;
        String message = commit.getFullMessage().toLowerCase();
        stemmer.add(message.toCharArray(), message.length());
        stemmer.stem();
        String stemMsg = stemmer.toString();
        String[] tokens = stemMsg.split("\\W+");

        for (String keyword : keywords)
        {
            for (String token : tokens)
            {
                if (token.equals(keyword))
                {
                    return true;
                }
            }
        }
        return false;
    }
    //This function is not used currently, because we are not filtering commit message now
    @Deprecated
    public boolean commitRelatedToKeywords(RevCommit commit, String[] keywords)
    {
        if (keywords == null)
        {
            return true;
        }
        String message = commit.getFullMessage().toLowerCase().replaceAll("\\W+", " ");

        StringTokenizer st = new StringTokenizer(message);
        PorterStemmer porterStemmer = new PorterStemmer();
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            porterStemmer.reset();
            String stemmedToken = porterStemmer.stem(token);
            for (String keyword : keywords) {
                porterStemmer.reset();
                if (stemmedToken.equals(porterStemmer.stem(keyword))) {
                    return true;
                }
            }
        }
        return false;
    }
    public void statisticsToExcel(List <ModifiedLambda> modifiedLambdas)
    {

    }

    //This function tackles the problem of repeated lambda. Some commits have different commit
    // hash but their content is exactly the same, so a removed lambda may appear multiple times
    //and should be reduced to one commit
    @Deprecated
    public void reduceRepeatedLambdas(List<ModifiedLambda> modifiedLambdas)
    {
        List<ModifiedLambda> reducedModifiedLambdaList = new ArrayList<>();
        Set<String> filePaths = new HashSet<>();
        Map<String, List<ModifiedLambda>> map = new HashMap<>();
        for (ModifiedLambda modifiedLambda : modifiedLambdas)
        {
            //count set to true for every lambda pos
            modifiedLambda.pos.count = true;
            filePaths.add(modifiedLambda.diffEntry.getNewPath());
        }
        //System.err.println("number of file path: " + filePaths.size());
        for (String filePath : filePaths)
        {
            //map.put(filePath, modifiedLambdas.stream().filter(lambda -> lambda.diffEntry.getNewPath() == filePath).collect(Collectors.toList()));
            for (ModifiedLambda lambda : modifiedLambdas)
            {
                if (lambda.diffEntry.getNewPath().equals(filePath))
                {
                    map.computeIfAbsent(filePath, k -> new ArrayList<>());
                    map.get(filePath).add(lambda);
                }
            }
            //System.err.println(filePath + ":" + map.get(filePath).size());
        }

        for (String filePath : map.keySet())
        {
            List<ModifiedLambda> lambdasThisFile = map.get(filePath);
            Set<String> uniquePositionTuples = new HashSet<>();
            for (ModifiedLambda lambda : lambdasThisFile)
            {
//                for (PositionTuple positionTuple : uniquePositionTuples)
//                {
//                    if (lambda.pos.beginPos == positionTuple.beginPos && lambda.pos.endPos == positionTuple.endPos
//                     && lambda.pos.beginLine == positionTuple.beginLine && lambda.pos.endLine == positionTuple.endLine)
//                    {
//                        modifiedLambdas.remove(lambda);
//                    }
//                    else uniquePositionTuples.add(lambda.pos);
//                }
                //System.err.println("file path: " + lambda.diffEntry.getNewPath() + "  lambda line: L" + lambda.pos.beginLine + "-" + lambda.pos.endLine);
                //System.err.println("position set at present: " + uniquePositionTuples.toString());
                if (!uniquePositionTuples.contains(lambda.pos.toString()))
                {
                    uniquePositionTuples.add(lambda.pos.toString());
                }
                else
                {
                    //this lambda is repeated!
                    modifiedLambdas.remove(lambda);
                }
            }
            //System.err.println(uniquePositionTuples);
        }
    }
    @Deprecated
    public static void main1(String[] args) throws IOException, GitAPIException {

        try {
            //String[] keywords = {"bug", "fix", "issue", "error", "crash"};

            //String[] projectList = { "google/guava", "jenkinsci/jenkins", "bazelbuild/bazel", "apache/skywalking", "apache/flink"};

            //String[] projectList = {"ACRA/acra"};
            //String[] projectList = {"apache/camel", "flyway/flyway", "apache/storm", "elastic/elasticsearch", "TooTallNate/Java-WebSocket"};
            //String[] projectList = {"adyliu/jafka", "rabbitmq/rabbitmq-jms-client", "opentracing-contrib/java-spring-cloud", "opentracing-contrib/java-specialagent" };
            String[] projectList = {"rabbitmq/rabbitmq-jms-client"};
            String[] keywords = null;
            //String[] keywords = {"base", "engine", "optimize", "efficiency", "performance", "lazy", "eager", "evaluation", "outdated", "lambda", "thread", "safe", "JMS"};
            //String[] projectList = {"apache/camel"};
            PrintStream out = System.out;
            Date date = new Date();
            SimpleDateFormat ft = new SimpleDateFormat("MM-dd-HH-mm");
            PrintStream ps = new PrintStream("log-file/log-" + ft.format(date) + ".txt");
            System.setOut(ps);
            System.out.println("This is the filter result of projects: " + Arrays.toString(projectList));
            System.out.println("The filter keywords include: " + Arrays.toString(keywords));

            List<ModifiedLambda> allModifiedLambdas = new ArrayList<>();

            for (String project : projectList)
            {
                List<ModifiedLambda> modifiedLambdas = new ArrayList<>();
                System.err.println("Mining project " + project + "...");
                //String url = "https://github.com/ACRA/acra.git";
                String url = "https://github.com/" + project + ".git";
                String repoURL = url.substring(0, url.lastIndexOf(".git"));
                //GumtreeLambdaFilter filter = new GumtreeLambdaFilter(modifiedLambdas, url, null, 10);
                BadLambdaFinder finder = new BadLambdaFinder(modifiedLambdas, url, null, 10, keywords, 10);
                //remove repeated modified lambdas
                finder.reduceRepeatedLambdas(modifiedLambdas);
                System.err.println("project " + project + " mining completed!");
                allModifiedLambdas.addAll(modifiedLambdas);
            }
            System.err.println("size of lambda list: " + allModifiedLambdas.size());
            //List<DiffEntry> diffEntries = new ArrayList<>();
//            for (ModifiedLambda modifiedLambda : modifiedLambdas)
//            {
//                if (diffEntries.contains(modifiedLambda.diffEntry))
//                {
//                    System.err.println("diff repeated! commit hash: " + modifiedLambda.currentCommit.toString() + "file path: " + modifiedLambda.diffEntry.getNewPath());
//                    System.out.println(modifiedLambda.diffEntry.getDiffAttribute());
//                }
//                else diffEntries.add(modifiedLambda.diffEntry);
//            }
            //System.err.println("size of repeated diff: " + diffEntries.size());

            //serialization of lambda list:
            List<SimplifiedModifiedLambda> simplifiedModifiedLambdas = new ArrayList<>();
            for (ModifiedLambda modifiedLambda : allModifiedLambdas)
            {
                simplifiedModifiedLambdas.add(new SimplifiedModifiedLambda(modifiedLambda));
            }
            SimplifiedModifiedLambda[] modifiedLambdasForSerial = new SimplifiedModifiedLambda[simplifiedModifiedLambdas.size()];
            //ModifiedLambda[] modifiedLambdasForSerial = new ModifiedLambda[allModifiedLambdas.size()];
            simplifiedModifiedLambdas.toArray(modifiedLambdasForSerial);
            //allModifiedLambdas.toArray(modifiedLambdasForSerial);
            FileOutputStream fileOut = new FileOutputStream("ser/" + ft.format(date) + "-lambdas.ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(modifiedLambdasForSerial);
            serOut.close();
            fileOut.close();
            System.err.println("Serialized data is saved in ser/" + ft.format(date) + "-lambdas.ser");

            for (ModifiedLambda modifiedLambda : allModifiedLambdas)
            {
                System.out.println("\n" + "######################################");
                System.out.println("MODIFIED LAMBDA");
                System.out.println("repo: " + modifiedLambda.repo.toString());
                System.out.println("new file path: " + modifiedLambda.diffEntry.getNewPath());
                System.out.println("old file path: " + modifiedLambda.diffEntry.getOldPath());
                System.out.println("modified lambda line: " + "L" + modifiedLambda.pos.beginLine + " - " + "L" + modifiedLambda.pos.endLine);
                System.out.println("current commit: " + modifiedLambda.currentCommit);
                System.out.println("parent commit: " + modifiedLambda.parentCommit);
                System.out.println("git command:    " + "git diff " + modifiedLambda.parentCommit.toString().split(" ")[1] + " "
                        + modifiedLambda.currentCommit.toString().split(" ")[1] + " " + modifiedLambda.diffEntry.getNewPath());
                System.out.println("files modified: " + modifiedLambda.fileModified);
                System.out.println("commit message: " + modifiedLambda.currentCommit.getFullMessage());
                System.out.println("commit link: " + modifiedLambda.commitURL);
                System.out.println("diff hash code: " + modifiedLambda.diffEntry.hashCode());
                System.out.println(modifiedLambda.pos.node);
                //System.out.println("diffEntry: " + modifiedLambda.diffEntry.toString());

//                    System.out.println("max statistics------------ ");
//                    System.out.println("max action depth: " + modifiedLambda.actionDepth_max);
//                    System.out.println("max action height: " + modifiedLambda.actionHeight_max);
//                    System.out.println("max action size: " + modifiedLambda.actionSize_max);
//                    System.out.println("average statistics-------- ");
//                    System.out.println("average action depth: " + modifiedLambda.actionDepth_avg);
//                    System.out.println("average action height: " + modifiedLambda.actionHeight_max);
//                    System.out.println("average action size: " + modifiedLambda.actionSize_avg);
//                    System.out.println("average actions per line: " + modifiedLambda.action_avg);
//
//                    System.out.println("number of actions: " + modifiedLambda.actionNum);
//                    //System.out.println("number of modified nodes: " + modifiedLambda.ac);
//                    //System.out.println("number of nodes in lambda: " + modifiedLambda.nodesNum);
//
//                    //*******some problems: many actions' area overlap. so the proportion can be larger than 1
//                    //System.out.println("position length of lambda: " + modifiedLambda.posLength);
//                    System.out.println("proportion of modified position: " + modifiedLambda.proportionOfModPos);
//                    //System.out.println("total number of nodes in lambda: " + modifiedLambda.nodesNum);
//                    System.out.println("proportion of modified nodes: " + modifiedLambda.proportionOfModNodes);
//
//                    System.out.println("set of actions: " + modifiedLambda.actionTypeSet);
//                    System.out.println("map of actions: " + modifiedLambda.actionTypeMap);
                System.out.println();
        }
            System.setOut(out);
            System.out.println("Program Finished.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws IOException, GitAPIException
    {
        //String[] projectList = {"apache/tomcat"};
        //String[] keywords = null;
        String[] projectList1 = { "google/guava", "jenkinsci/jenkins", "bazelbuild/bazel", "apache/skywalking", "apache/flink"};
        String[] projectList2 = {"apache/camel", "flyway/flyway", "apache/storm", "elastic/elasticsearch", "TooTallNate/Java-WebSocket"};
        //String[] projectList = {"oracle/graal", "eclipse/deeplearning4j", "eclipse-vertx/vert.x", "realm/realm-java", "prestodb/presto"};
//        String[] projectList = { "google/guava", "jenkinsci/jenkins", "bazelbuild/bazel", "apache/skywalking", "apache/flink",
//                            "apache/camel", "flyway/flyway", "apache/storm", "elastic/elasticsearch", "TooTallNate/Java-WebSocket"};
        String[] projectList_apache = {
                "apache/hadoop",
                "apache/cloudstack",
                "apache/geode",
                "apache/ambari",
                "apache/hive",
                "apache/hbase",
                "apache/cocoon",
                "apache/myfaces",
                "apache/beam",
                "apache/netbeans",
                "apache/tomcat"
        };
        String[] projectList_test = {"apache/skywalking"};
        String listPath = "oopsla_2017_list.txt";
        String repoPath = "../repos";
        BufferedReader bf = new BufferedReader(new FileReader(listPath));
        String s = null;
        List<String> lines = new ArrayList<>();
        while ((s = bf.readLine()) != null)
        {
            lines.add(s);
        }
        //String[] projectList = lines.toArray(new String[0]);
        bf.close();

        String[] projectList = projectList_test;

        //Keywords below might not be used now, please ignore them......
        String[] keywords_lambda = {"lambda"};
        String[] keywords_performance = {"optimize", "efficiency", "performance", "overhead", "cost", "perf"};
        String[] keywords_lazy = {"lazy", "eager", "outdated", "evaluate", "execute"};
        String[] keywords_serialization = {"serialize" , "transient"};
        String[] keywords_compatibility = {"compatible", "version", "jdk"};
        String[] keywords_concurrentMod = {"concurrent", "parallel", "race", "hang"};
        String[] keywords_flaky = {"flaky"};
        String[] keywords = null;
        //String[] keywords = {"overhead"};


        PrintStream out = System.out;
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("MM-dd");
        //PrintStream ps = new PrintStream("log-file/log-" + ft.format(date) + ".txt");
        //System.setOut(ps);
        System.out.println("This is the filter result of projects: " + Arrays.toString(projectList));
        System.out.println("The filter keywords include: " + Arrays.toString(keywords));

        //List<ModifiedLambda> allModifiedLambdas = new ArrayList<>();

        for (String project : projectList)
        {
            List<ModifiedLambda> modifiedLambdas = new ArrayList<>();
            System.err.println("Mining project " + project + "...");
            //String url = "https://github.com/ACRA/acra.git";
            String url = "https://github.com/" + project + ".git";
            String repoURL = url.substring(0, url.lastIndexOf(".git"));
            //GumtreeLambdaFilter filter = new GumtreeLambdaFilter(modifiedLambdas, url, null, 10);

            BadLambdaFinder finder = new BadLambdaFinder(modifiedLambdas, url, repoPath, 10, keywords, 10);
            //remove repeated modified lambdas
            //finder.reduceRepeatedLambdas(modifiedLambdas);
            System.err.println("project " + project + " mining completed!");
            System.err.println("size of lambda list of " + project + ": " + modifiedLambdas.size());
            List<SimplifiedModifiedLambda> simplifiedModifiedLambdas = new ArrayList<>();
            for (ModifiedLambda modifiedLambda : modifiedLambdas)
            {
                simplifiedModifiedLambdas.add(new SimplifiedModifiedLambda(modifiedLambda));
            }
            SimplifiedModifiedLambda[] modifiedLambdasForSerial = new SimplifiedModifiedLambda[simplifiedModifiedLambdas.size()];
            simplifiedModifiedLambdas.toArray(modifiedLambdasForSerial);
            File file = new File("ser/" + ft.format(date));
            if (!file.exists())
            {
                file.mkdirs();
            }
            //System.out.println(project.replace("/", " "));

            FileOutputStream fileOut = new FileOutputStream("ser/" + ft.format(date) + "/" + project.replace("/", " ") + "-" + "revfilter-not-reduced" + ".ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(modifiedLambdasForSerial);
            serOut.close();
            fileOut.close();
            System.err.println("Serialized data is saved in ser/" + ft.format(date) + "/" + project.replace("/", " ") + "-" + "revfilter" + ".ser");
        }
    }
}