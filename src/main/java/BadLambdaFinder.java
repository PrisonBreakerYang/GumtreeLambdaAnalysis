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
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.nio.file.Files;
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
    List<ModifiedLambda> modifiedLambdas;
    public BadLambdaFinder()
    {
        this.repo = null;
        this.git = null;
        this.url = null;
        this.stemmer = null;
        this.threshold = 0;
    }

    public BadLambdaFinder(List<ModifiedLambda> modifiedLambdas, String url, String repoPath, int editThreshold) throws IOException, GitAPIException {
        assert url.endsWith(".git");
        if (!Files.exists(Paths.get("repos"))) {
            new File("repos").mkdirs();
        }

        String repoName = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf(".git"));
        //String ownerName = url.split("[/ : . # %]")[5];
        if (repoPath == null) {
            repoPath = "repos/" + repoName;
        }
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

        walkAllCommits(repo, url);
    }


    void gumTreeDiffBetweenParent(RevCommit currentCommit, RevCommit parentCommit, String url) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(repo);
        List<DiffEntry> diffs = formatter.scan(parentCommit, currentCommit);
        //String[] keywords = {"bug", "fix", "issue", "error", "crash"};
//        if (!commitRelatedToKeywords(currentCommit, keywords))
//        {
//            return;
//        }
        diffs.forEach(diff ->
        {
            if (diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.ADD
                    || ((diff.getChangeType() == DiffEntry.ChangeType.RENAME) && (!diff.getOldPath().equals(diff.getNewPath())))) {
                return;
            }
            try {
                boolean isJavaFile = false;
                FileHeader fileHeader = formatter.toFileHeader(diff);

                if (fileHeader.getOldPath().endsWith(".java")) {
                    isJavaFile = true;
                }

                if (!isJavaFile) {
                    return;
                }

                formatter.setContext(0);
                formatter.format(diff);
                String content = outputStream.toString();
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

//                    for (HunkHeader hunk : fileHeader.getHunks())
//                    {
//                        for (Edit edit : hunk.toEditList())
//                        {
//
//                        }
//                    }
                    
                    FileWriter oldFile, newFile;
                    try {
                        oldFile = new FileWriter("old-new-file\\oldfile.java");
                        oldFile.write("");
                        oldFile.write(new String(fileContentBeforeCommit.getBytes()));
                        oldFile.flush();

                        newFile = new FileWriter("old-new-file\\newfile.java");
                        newFile.write("");
                        newFile.write(new String(fileContentAfterCommit.getBytes()));
                        newFile.flush();

                        Run.initGenerators();

                        Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file\\oldfile.java").getRoot();
                        Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file\\newfile.java").getRoot();


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
                            PositionTuple positionTupleNew;
                            if (mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0)) == null)
                            {
                                //表示commit前文件的lambda在commit后文件中找不到
                                int nodesNum = oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0).getMetrics().size;
                                ModifiedLambda newLambda = new ModifiedLambda(repo, currentCommit, parentCommit, diff, gumtreeJDTDriver.cu, positionTuple, nodesNum);
                                this.modifiedLambdas.add(newLambda);
                            }

//                            {
//                                Tree lambdaNewTree = mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0));
//                                int lambdaNewTreePos = lambdaNewTree.getPos();
//                                int lambdaNewTreeEndPos = lambdaNewTree.getEndPos();
//                                int lambdaNewTreeStartLine = gumtreeJDTDriver_new.cu.getLineNumber(lambdaNewTreePos);
//                                int lambdaNewTreeEndLine = gumtreeJDTDriver_new.cu.getLineNumber(lambdaNewTreeEndPos);
//                                positionTupleNew = new PositionTuple(lambdaNewTreePos, lambdaNewTreeEndPos, lambdaNewTreeStartLine, lambdaNewTreeEndLine);
//                            }

                            //对当前positionTuple，遍历所有action
//                            for (Action action : actions)
//                            {
//                                if (!action.getName().contains("insert") && action.getNode().getPos() >= positionTuple.beginPos &&
//                                        action.getNode().getEndPos() <= positionTuple.endPos)
//                                {
//                                    ModifiedLambda newLambda = new ModifiedLambda(repo, currentCommit, parentCommit, diff, gumtreeJDTDriver.cu, positionTuple, nodesNum);
//                                    this.modifiedLambdas.add(newLambda);
//                                    break;
//                                }
//                            }


                        }

                        oldFile.close();
                        newFile.close();
                    } catch (SyntaxException e) {
                        return;
                        //e.printStackTrace();
                    }
                }
                if (fileContentBeforeCommit == null) {
                    return;
                }

                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    void walkAllCommits(Repository repo, String url) throws GitAPIException, IOException {
        Ref head = repo.exactRef("refs/heads/master");
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(head.getObjectId());
        System.out.println("Start commit: " + commit);

        System.out.println("Walking all commits starting at HEAD");
        walk.markStart(commit);
        int count = 0;
        for (RevCommit currentCommit : walk) {
            if (currentCommit.getParentCount() == 0) {
                // this is the initial commit of the repo
                break;
            } else {
                String logMsg = currentCommit.getFullMessage().toLowerCase();
                boolean dropThisCommit = false;
                if (!dropThisCommit) {
                    for (RevCommit parentCommit : currentCommit.getParents()) {
                        gumTreeDiffBetweenParent(currentCommit, parentCommit, url);
                    }
                }
            }
            //System.out.println("Commit: " + rev);

            count++;
        }
        //System.out.println(count);
        walk.dispose();
    }
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
    public boolean commitRelatedToKeywords(RevCommit commit, String[] keywords)
    {
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
    public void statisticsToExcel(List <ModifiedLambda> modifiedLambdas)
    {

    }

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
    public static void main(String[] args) throws IOException, GitAPIException {

        try {
            //String[] keywords = {"bug", "fix", "issue", "error", "crash"};

            //String[] projectList = { "google/guava", "jenkinsci/jenkins", "bazelbuild/bazel", "apache/skywalking", "apache/flink"};
            String[] projectList = {"ACRA/acra"};
            PrintStream out = System.out;
            Date date = new Date();
            SimpleDateFormat ft = new SimpleDateFormat("MM-dd-HH-mm");
            PrintStream ps = new PrintStream("log-file/log-" + ft.format(date) + ".txt");
            System.setOut(ps);
            System.out.println("This is the filter result of projects: " + Arrays.toString(projectList));
            //System.out.println("The filter keywords include: " + Arrays.toString(keywords));

            List<ModifiedLambda> allModifiedLambdas = new ArrayList<>();

            for (String project : projectList)
            {
                List<ModifiedLambda> modifiedLambdas = new ArrayList<>();
                System.err.println("Mining project " + project + "...");
                //String url = "https://github.com/ACRA/acra.git";
                String url = "https://github.com/" + project + ".git";
                String repoURL = url.substring(0, url.lastIndexOf(".git"));
                //GumtreeLambdaFilter filter = new GumtreeLambdaFilter(modifiedLambdas, url, null, 10);
                BadLambdaFinder finder = new BadLambdaFinder(modifiedLambdas, url, null, 10);
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
                System.out.println("commit message: " + modifiedLambda.currentCommit.getFullMessage());
                System.out.println("diff hash code: " + modifiedLambda.diffEntry.hashCode());
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
}