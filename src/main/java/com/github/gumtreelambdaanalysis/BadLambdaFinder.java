package com.github.gumtreelambdaanalysis;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.github.gumtreediff.tree.Tree;

public class BadLambdaFinder {
    private final Repository repo;
    private final Git git;
    private final String url;
    int context;
    List<ModifiedLambda> modifiedLambdas;
    String[] keywords;
    public BadLambdaFinder()
    {
        this.repo = null;
        this.git = null;
        this.url = null;
        //this.stemmer = null;
        //this.threshold = 0;
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
        //stemmer = new Stemmer();
        //threshold = editThreshold;
        //this.keywords = keywords;
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
            if (editList.get(seq).getBeginA() == mergedEdits.get(mergedEdits.size() - 1).getEndA())
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
            if (diff.getChangeType() == DiffEntry.ChangeType.MODIFY)
            {
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

//        if (!commitRelatedToKeywords(currentCommit, this.keywords))
//        {
//            return;
//        }
        diffs.forEach(diff ->
        {
            //We only focus on modified java files
            if (diff.getChangeType() != DiffEntry.ChangeType.MODIFY) return;

            try {
                boolean isJavaFile = false;
                FileHeader fileHeader = formatter.toFileHeader(diff);

                if (!fileHeader.getOldPath().endsWith(".java")) {
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
                    if (mergedEdits.size() < editsDeletedOrReplaced.size()) System.err.println("merge edit take effect!");

                    FileWriter oldFile, newFile;
                    try {
                        //Cache target file in local file directory, maybe it's not so necessary......
                        oldFile = new FileWriter("old-new-file/oldfile.java");
                        oldFile.write("");
                        String oldFileContent = new String(fileContentBeforeCommit.getBytes());
                        oldFile.write(oldFileContent);
                        oldFile.flush();

                        newFile = new FileWriter("old-new-file/newfile.java");
                        newFile.write("");
                        newFile.write(new String(fileContentAfterCommit.getBytes()));
                        newFile.flush();

                        //Run.initGenerators();
                        //Build tree for both old and new files by Gumtree tool

//                        Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file/oldfile.java").getRoot();
//                        Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file/newfile.java").getRoot();

                        Tree oldFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/oldfile.java")).getRoot();
                        Tree newFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/newfile.java")).getRoot();

                        //Diff information is stored in the variable "mappings"
                        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
                        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

                        //遍历当前diff所有positionTuple
                        for (PositionTuple positionTuple : positionTupleList)
                        {
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

                formatter.close();
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
                assert currentCommit.getParentCount() == 1;
                //if (currentCommit.getParents().length > 1) System.out.println(currentCommit.getParents().length + "\n" + currentCommit.getName());
                for (RevCommit parentCommit : currentCommit.getParents()) {
                    gumTreeDiffWithParent(currentCommit, parentCommit, url);
                }
            }

            count++;
        }
        walk.dispose();
    }

    public static void main(String[] args) throws IOException, GitAPIException
    {
        String[] projectList_test = {"apache/skywalking"};
        String listPath = "apache_list_5.txt";
        String repoPath = "../repos";
        BufferedReader bf = new BufferedReader(new FileReader(listPath));
        String s = null;
        List<String> lines = new ArrayList<>();
        while ((s = bf.readLine()) != null)
        {
            lines.add(s);
        }
        String[] projectList = projectList_test;
        //String[] projectList = lines.toArray(new String[0]);
        bf.close();

        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("MM-dd");
        System.out.println("This is the filter result of projects: " + Arrays.toString(projectList));

        for (String project : projectList)
        {
            List<ModifiedLambda> modifiedLambdas = new ArrayList<>();
            System.err.println("Mining project " + project + "...");
            //String url = "https://github.com/ACRA/acra.git";
            String url = "https://github.com/" + project + ".git";
            String repoURL = url.substring(0, url.lastIndexOf(".git"));

            BadLambdaFinder finder = new BadLambdaFinder(modifiedLambdas, url, repoPath, 0, null, 10);
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
            File file = new File("ser/bad-lambdas/test/" + ft.format(date));
            if (!file.exists())
            {
                file.mkdirs();
            }
            //System.out.println(project.replace("/", " "));

            FileOutputStream fileOut = new FileOutputStream("ser/bad-lambdas/test/" + ft.format(date) + "/" + project.replace("/", " ") + ".ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(modifiedLambdasForSerial);
            serOut.close();
            fileOut.close();
            System.err.println("Serialized data is saved in ser/bad-lambdas/test/" + ft.format(date) + "/" + project.replace("/", " ") + ".ser");
        }
    }
}