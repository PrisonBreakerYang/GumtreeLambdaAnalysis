import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.IO;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.github.gumtreediff.tree.Tree;

class PositionTuple
{
    public int beginPos;
    public int endPos;
    public int beginLine;
    public int endLine;
    public boolean count;
    public PositionTuple(int beginPos, int endPos, int beginLine, int endLine)
    {
        this.beginPos = beginPos;
        this.endPos = endPos;
        this.beginLine = beginLine;
        this.endLine =endLine;
        this.count = false;
    }
}

class PositionTupleMap
{
    PositionTuple positionTupleOld, positionTupleNew;
    String type;
    public PositionTupleMap(int none, PositionTuple positionTupleNew)
    {
        this.positionTupleOld = null;
        this.positionTupleNew = positionTupleNew;
        this.type = "lambda only in new file";
    }
    public PositionTupleMap(PositionTuple positionTupleOld, int none)
    {
        this.positionTupleOld = positionTupleOld;
        this.positionTupleNew = null;
        this.type = "lambda only in old file";
    }
    public PositionTupleMap(PositionTuple positionTupleOld, PositionTuple positionTupleNew)
    {
        this.positionTupleOld = positionTupleOld;
        this.positionTupleNew = positionTupleNew;
        this.type = "lambda map";
    }
}

public class GumtreeLambdaFilter {
    private final Repository repo;
    private final Git git;
    private final String url;
    private final Stemmer stemmer;
    private final int threshold;
    List<ModifiedLambda> modifiedLambdas;

    public GumtreeLambdaFilter(List<ModifiedLambda> modifiedLambdas, List<LambdaNode> lambdaNodeList, String url, String repoPath, int editThreshold) throws IOException, GitAPIException {
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

        walkAllCommits(repo, lambdaNodeList, url);
    }

    boolean actionRelatedToLambda(Action action, List<PositionTuple> lambdaPositions, List<PositionTuple> lambdaPositions_new) {
        List<PositionTuple> lambdaPositions_target;
        if (action.getName().contains("insert"))
        {
            lambdaPositions_target = lambdaPositions_new;
        } else lambdaPositions_target = lambdaPositions;
        for (PositionTuple lambdaPosition : lambdaPositions_target)
        {
            if (action.getNode().getPos() >= lambdaPosition.beginPos && action.getNode().getEndPos() <= lambdaPosition.endPos)
            {
                lambdaPosition.count = true;
                return true;
            }
        }
        return false;
    }

    //此函数由commit前文件的lambda表达式位置得到commit后文件lambda表达式位置
//    List<PositionTupleMap> getPositionTupleMap(List<PositionTuple> positionTupleOldList)
//    {
//        List<PositionTupleMap> positionTupleMapList = new ArrayList<>();
//        for (PositionTuple positionTupleOld : positionTupleOldList)
//        {
//
//            positionTupleMapList.add(PositionTupleMap(positionTupleOld, ));
//        }
//        return positionTupleMapList;
//    }
    void gumTreeDiffBetweenParent(RevCommit currentCommit, RevCommit parentCommit, List<LambdaNode> lambdaNodeList, String url) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(repo);
        List<DiffEntry> diffs = formatter.scan(parentCommit, currentCommit);
        //System.out.println(diffs.size());

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
                    List<PositionTuple> positionTupleList_new = new ArrayList<>();
                    GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(new String(fileContentBeforeCommit.getBytes()), positionTupleList);
                    GumtreeJDTDriver gumtreeJDTDriver_new = new GumtreeJDTDriver(new String(fileContentAfterCommit.getBytes()), positionTupleList_new);

                    FileWriter oldFile, newFile;
                    try {
                        oldFile = new FileWriter("D:\\SEproject\\BugFinder-master\\old-new-file\\oldfile.java");
                        oldFile.write("");
                        oldFile.write(new String(fileContentBeforeCommit.getBytes()));
                        oldFile.flush();

                        newFile = new FileWriter("D:\\SEproject\\BugFinder-master\\old-new-file\\newfile.java");
                        newFile.write("");
                        newFile.write(new String(fileContentAfterCommit.getBytes()));
                        newFile.flush();

                        Run.initGenerators();
                        Tree oldFileTree = TreeGenerators.getInstance().getTree("D:\\SEproject\\BugFinder-master\\old-new-file\\oldfile.java").getRoot();
                        Tree newFileTree = TreeGenerators.getInstance().getTree("D:\\SEproject\\BugFinder-master\\old-new-file\\newfile.java").getRoot();
                        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
                        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);
                        //System.out.println(mappings);
                        EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator();
                        EditScript actions = editScriptGenerator.computeActions(mappings);

                        boolean diffRelatedToLambda = false;
                        boolean actionTraveled = false;

                        List<PositionTupleMap> positionTupleMapList = new ArrayList<>();
//                        for (PositionTuple positionTuple : positionTupleList)
//                        {
//                            if (oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).isEmpty())
//                            {
//                                //表示commit前文件的lambda在commit后文件中找不到
//                                positionTupleMapList.add(new PositionTupleMap(positionTuple, 0));
//                            }
//                            else
//                            {
//                                Tree lambdaNewTree = mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0));
//                                int lambdaNewTreePos = mappings.getDstForSrc(lambdaNewTree).getPos();
//                                int lambdaNewTreeEndPos = mappings.getDstForSrc(lambdaNewTree).getEndPos();
//                                int lambdaNewTreeStartLine = gumtreeJDTDriver_new.cu.getLineNumber(lambdaNewTreePos);
//                                int lambdaNewTreeEndLine = gumtreeJDTDriver_new.cu.getLineNumber(lambdaNewTreeEndPos);
//                                positionTupleMapList.add(new PositionTupleMap(positionTuple, new PositionTuple(lambdaNewTreePos, lambdaNewTreeEndPos, lambdaNewTreeStartLine, lambdaNewTreeEndLine)));
//                            }
//                        }

                        //遍历当前diff所有positionTuple
                        for (PositionTuple positionTuple : positionTupleList)
                        {
                            PositionTuple positionTupleNew;
                            if (mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0)) == null)
                            {
                                //表示commit前文件的lambda在commit后文件中找不到
                                positionTupleNew = null;
                            }
                            else
                            {
                                Tree lambdaNewTree = mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0));
                                int lambdaNewTreePos = lambdaNewTree.getPos();
                                int lambdaNewTreeEndPos = lambdaNewTree.getEndPos();
                                int lambdaNewTreeStartLine = gumtreeJDTDriver_new.cu.getLineNumber(lambdaNewTreePos);
                                int lambdaNewTreeEndLine = gumtreeJDTDriver_new.cu.getLineNumber(lambdaNewTreeEndPos);
                                positionTupleNew = new PositionTuple(lambdaNewTreePos, lambdaNewTreeEndPos, lambdaNewTreeStartLine, lambdaNewTreeEndLine);
                            }
                            //对当前positionTuple，遍历所有action
                            for (Action action : actions)
                            {
                                if (!action.getName().contains("insert") && action.getNode().getPos() >= positionTuple.beginPos
                                && action.getNode().getEndPos() <= positionTuple.endPos)
                                {
                                    if (!positionTuple.count)
                                    {
                                        ModifiedLambda newLambda = new ModifiedLambda(repo, currentCommit, parentCommit, diff, gumtreeJDTDriver.cu, positionTuple);
                                        newLambda.actionList.add(action);
                                        this.modifiedLambdas.add(newLambda);
                                        positionTuple.count = true;
                                    }
                                    else
                                    {
                                        this.modifiedLambdas.get(this.modifiedLambdas.size() - 1).actionList.add(action);
                                    }
                                }
                                if (positionTupleNew != null && action.getName().contains("insert") && action.getNode().getPos() >= positionTupleNew.beginPos
                                && action.getNode().getEndPos() <= positionTupleNew.endPos)
                                {
                                    if (!positionTuple.count)
                                    {
                                        ModifiedLambda newLambda = new ModifiedLambda(repo, currentCommit, parentCommit, diff, gumtreeJDTDriver_new.cu, positionTuple);
                                        newLambda.actionList.add(action);
                                        this.modifiedLambdas.add(newLambda);
                                        positionTuple.count = true;
                                    }
                                    else
                                    {
                                        this.modifiedLambdas.get(this.modifiedLambdas.size() - 1).actionList.add(action);
                                    }
                                }
                            }
                        }
//                        for (PositionTuple positionTuple : positionTupleList_new)
//                        {
//                            for (Action action : actions)
//                            {
//                                if (action.getName().contains("insert") && action.getNode().getPos() >= positionTuple.beginPos
//                                && action.getNode().getEndPos() <= positionTuple.endPos)
//                                {
//                                    if (!positionTuple.count)
//                                    {
//                                        ModifiedLambda newLambda = new ModifiedLambda(repo, currentCommit, parentCommit, diff, gumtreeJDTDriver_new.cu, positionTuple);
//                                        newLambda.actionList.add(action);
//                                        this.modifiedLambdas.add(newLambda);
//                                        positionTuple.count = true;
//                                    }
//                                    else
//                                    {
//                                        this.modifiedLambdas.get(this.modifiedLambdas.size() - 1).actionList.add(action);
//                                    }
//                                }
//                            }
//                        }

                        oldFile.close();
                        newFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (fileContentBeforeCommit == null) {
                    return;
                }

//                System.out.println("###########################################################");
//                System.out.println(new String(fileContentBeforeCommit.getBytes()));
//                System.out.println("-----------------------------------------------------------");
//                System.out.println(new String(fileContentAfterCommit.getBytes()));
//                System.out.println("###########################################################");
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    void walkAllCommits(Repository repo, List<LambdaNode> lambdaNodeList, String url) throws GitAPIException, IOException {
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
                        gumTreeDiffBetweenParent(currentCommit, parentCommit, lambdaNodeList, url);
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

    }

    public static void main(String[] args) throws IOException, GitAPIException {
        try {
            PrintStream out = System.out;
            Date date = new Date();
            SimpleDateFormat ft = new SimpleDateFormat("MM-dd-HH-mm");
            PrintStream ps = new PrintStream("./log-" + ft.format(date) + ".txt");
            System.setOut(ps);

            List<LambdaNode> lambdaNodeList = new ArrayList<>();
            List<ModifiedLambda> modifiedLambdas = new ArrayList<>();

            //String url = "https://github.com/real-logic/aeron.git";
            String url = "https://github.com/ACRA/acra.git";
            String repoURL = url.substring(0, url.lastIndexOf(".git"));
            GumtreeLambdaFilter filter = new GumtreeLambdaFilter(modifiedLambdas, lambdaNodeList, url, null, 10);

            for (ModifiedLambda modifiedLambda : modifiedLambdas)
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
                        + modifiedLambda.currentCommit.toString().split(" ")[1] +" " + modifiedLambda.diffEntry.getNewPath());
                System.out.println("commit message: " + modifiedLambda.currentCommit.getFullMessage());
                System.out.println("diff hash code: " + modifiedLambda.diffEntry.hashCode());
                //System.out.println("diffEntry: " + modifiedLambda.diffEntry.toString());
                System.out.println("commit url: " + repoURL + "/commit/" + modifiedLambda.currentCommit.toString().split(" ")[1]);
                for (Action action : modifiedLambda.actionList)
                {
                    System.out.println("++++++++++++++++++++++++++++++++++++");
                    System.out.println("ACTION");
                    System.out.println("change type: " + action.getName());
                    System.out.println("action node: " + action.getNode());
                    System.out.println("action node position: " + "[" + action.getNode().getPos() + ", " + action.getNode().getEndPos() + "]");
//                    System.out.println("action node line: " + "L" + modifiedLambda.cu.getLineNumber(action.getNode().getPos())
//                        + " - " + "L" + modifiedLambda.cu.getLineNumber(action.getNode().getEndPos()));
                    System.out.println("action node line: " + "L" + modifiedLambda.cu.getLineNumber(action.getNode().getPos())
                            + " - " + "L" + modifiedLambda.cu.getLineNumber(action.getNode().getEndPos()));
                    System.out.println("action: " + action.toString());
                    System.out.println("\n");
                }
            }
            //String repoName = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf(".git"));
            System.setOut(out);
            System.out.println("Program Finished.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
