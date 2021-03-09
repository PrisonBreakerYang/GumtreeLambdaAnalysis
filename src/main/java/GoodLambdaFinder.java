import org.eclipse.jgit.api.Git;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
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
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import com.github.gumtreediff.tree.Tree;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.util.List;


public class GoodLambdaFinder
{
    private final Repository repo;
    private final Git git;
    String url;
    int context;
    int N;
    String repoName;
    private List<RemainedLambda> remainedLambdas = new ArrayList<>();
    public GoodLambdaFinder(String url, String repoPath, int editThreshold, int context, int N) throws IOException, GitAPIException {
        assert url.endsWith(".git");
        if (!Files.exists(Paths.get("../repos")))
        {
            new File("../repos").mkdirs();
        }

        repoName = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(".git"));
        if (repoPath == null)
        {
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
        this.context = context;
        this.N = N;
        List<String> javaPaths = getAllJavaFiles();
        findGoodLambdas(javaPaths, N);
    }

    void walkAllCommits(Repository repo, String url, String repoPath) throws GitAPIException, IOException {
        List<Ref> call = git.branchList().call();
        assert call.size() == 1;

        String repoName = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf(".git"));
        //The default branch name of some projects are not "master", we can get the proper name in the "heads" directory

        Ref head= call.get(0);
        System.out.println(head);

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
                for (RevCommit parentCommit : currentCommit.getParents()) {

                    //gumTreeDiffBetweenParent(currentCommit, parentCommit, url);
                }
            }

            count++;
        }
        walk.dispose();
    }

    List<String> getAllJavaFiles() throws IOException
    {
        Ref head = repo.findRef("HEAD");
        List<String> javaPaths = new ArrayList<>();
        try (RevWalk walk = new RevWalk(repo))
        {
            RevCommit commit = walk.parseCommit(head.getObjectId());
            RevTree tree = commit.getTree();
            //System.out.println(tree);
            try (TreeWalk treeWalk = new TreeWalk(repo))
            {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                while (treeWalk.next())
                {
                    if (treeWalk.getPathString().endsWith(".java")) javaPaths.add(treeWalk.getPathString());
                    //System.out.println(treeWalk.getPathString());
                }
            }
        }
        return javaPaths;
    }

    void findGoodLambdas(List<String> javaPaths, int N) throws GitAPIException, IOException
    {
        int count = 1;
        for (String javaPath : javaPaths)
        {
            Iterable<RevCommit> revCommits = git.log().add(repo.resolve(Constants.HEAD)).addPath(javaPath).call();
            List<RevCommit> revCommitList = new ArrayList<>();
            revCommits.forEach(revCommitList::add);
            if (revCommitList.size() > N)
            {
                //System.out.println(revCommitList.size());
//                for (RevCommit revCommit : revCommitList)
//                {
//                    System.out.println(javaPath + " commit time: " + new Date(revCommit.getCommitTime())); //time decrease in order
//                }
                List<RemainedLambda> remainedLambdasThisFile = new ArrayList<>();
                Collections.reverse(revCommitList);

                processRemainedLambdasForInitialCommit(revCommitList.get(0), revCommitList.get(N), javaPath);

                for (int i = 0; i < revCommitList.size() - N - 1; i++)
                {
                    RevCommit parentCommit = revCommitList.get(i);
                    RevCommit currentCommit = revCommitList.get(i + 1);

                    if (TreeWalk.forPath(repo, javaPath, currentCommit.getTree()) == null || TreeWalk.forPath(repo, javaPath, parentCommit.getTree()) == null) continue;
                    //System.out.println(javaPath);
                    String fileBeforeCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, parentCommit.getTree()).getObjectId(0)).getBytes());
                    String fileAfterCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, currentCommit.getTree()).getObjectId(0)).getBytes());
                    if (!fileAfterCommit.contains("->")) continue;
                    FileWriter oldFile, newFile;

                    try {
                        oldFile = new FileWriter("old-new-file\\oldfile.java");
                        oldFile.write("");
                        oldFile.write(fileBeforeCommit);    //i
                        oldFile.flush();
                        newFile = new FileWriter("old-new-file\\newfile.java");
                        newFile.write("");
                        newFile.write(fileAfterCommit);     //i + 1
                        newFile.flush();

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        DiffFormatter formatter = new DiffFormatter(outputStream);
                        formatter.setRepository(repo);
                        ObjectReader reader = repo.newObjectReader();
                        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                        oldTreeIter.reset(reader, parentCommit.getTree().getId());
                        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                        newTreeIter.reset(reader, currentCommit.getTree().getId());
                        List<DiffEntry> diffs = git
                                .diff()
                                .setPathFilter(PathFilter.create(javaPath))
                                .setOldTree(oldTreeIter)
                                .setNewTree(newTreeIter)
                                .call();
                        //System.out.println(diffs.size());
                        //assert diffs.size() == 1;

                        List<Edit> editsInsertedOrReplaced = new ArrayList<>();
                        for (DiffEntry diff : diffs)
                        {
                            formatter.setContext(0);
                            formatter.format(diff);
                            outputStream.reset();
                            FileHeader fileHeader = formatter.toFileHeader(diff);
                            for (HunkHeader hunk : fileHeader.getHunks())
                            {
                                for (Edit edit : hunk.toEditList())
                                {
                                    if (edit.getType() == Edit.Type.INSERT || edit.getType() == Edit.Type.REPLACE)
                                    {
                                        editsInsertedOrReplaced.add(edit);
                                    }
                                }
                            }
                            //List<Edit> mergedEdits = BadLambdaFinder.getMergedEdits(editsInsertedOrReplaced);
                        }
                        if (editsInsertedOrReplaced.isEmpty()) continue;

                        Run.initGenerators();
                        Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file\\oldfile.java").getRoot();    //i
                        Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file\\newfile.java").getRoot();    //i + 1
                        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
                        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

                        List<PositionTuple> positionTupleListAfterCommit = new ArrayList<>();
                        List<RemainedLambda> remainedLambdasCandidate = new ArrayList<>();
                        GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(fileAfterCommit, positionTupleListAfterCommit, true);  //i + 1

                        //System.out.println(positionTupleListAfterCommit.size());
                        for (PositionTuple positionTuple : positionTupleListAfterCommit)
                        {
                            if (mappings.getSrcForDst(newFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0)) == null
                            && BadLambdaFinder.lambdaInEdits(positionTuple, BadLambdaFinder.getMergedEdits(editsInsertedOrReplaced), "B"))
                            {
                                //表示确实有新lambda生成
                                remainedLambdasCandidate.add(new RemainedLambda(repo, currentCommit, url, javaPath, positionTuple, BadLambdaFinder.lambda_context(
                                fileAfterCommit, positionTuple.beginLine, positionTuple.endLine, this.context), false));
                            }
                        }
                        if (remainedLambdasCandidate.size() > 0)
                        {
                            compareCommitWithNLater(currentCommit, revCommitList.get(i + 1 + N), remainedLambdasCandidate, javaPath);
                        }
                        oldFile.close();
                        newFile.close();
                    } catch (SyntaxException e) {continue;}
                }
                System.out.println(repoName + " files covered: " + count + "/" + javaPaths.size());
            }
            count++;
        }
    }

    void processRemainedLambdasForInitialCommit(RevCommit initialCommit, RevCommit laterNCommit, String javaPath) throws IOException {
        List<PositionTuple> positionTupleListInitialCommit = new ArrayList<>();
        List<RemainedLambda> remainedLambdasCandidate = new ArrayList<>();
        String initialCommitContent = new String(repo.open(TreeWalk.forPath(repo, javaPath, initialCommit.getTree()).getObjectId(0)).getBytes());
        GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(initialCommitContent, positionTupleListInitialCommit, true);
        for (PositionTuple positionTuple : positionTupleListInitialCommit)
        {
            remainedLambdasCandidate.add(new RemainedLambda(repo, initialCommit, url, javaPath, positionTuple, BadLambdaFinder.lambda_context(
                    initialCommitContent, positionTuple.beginLine, positionTuple.endLine, this.context), true));
        }
        if (remainedLambdasCandidate.size() > 0)
        {
            compareCommitWithNLater(initialCommit, laterNCommit, remainedLambdasCandidate, javaPath);
        }
    }

    void compareCommitWithNLater(RevCommit currentCommit, RevCommit laterNCommit, List<RemainedLambda> remainedLambdasCandidate, String javaPath) throws IOException {
        //if (TreeWalk.forPath(repo, javaPath, currentCommit.getTree()) == null || TreeWalk.forPath(repo, javaPath, laterNCommit.getTree()) == null) return; //seemed unnecessary here......
        String fileCurrentCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, currentCommit.getTree()).getObjectId(0)).getBytes());
        String fileLaterNCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, laterNCommit.getTree()).getObjectId(0)).getBytes());
        FileWriter oldFile, newFile;
        oldFile = new FileWriter("old-new-file\\oldfile.java");
        oldFile.write("");
        oldFile.write(fileCurrentCommit);
        oldFile.flush();
        newFile = new FileWriter("old-new-file\\newfile.java");
        newFile.write("");
        newFile.write(fileLaterNCommit);
        newFile.flush();

        Run.initGenerators();
        Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file\\oldfile.java").getRoot();
        Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file\\newfile.java").getRoot();
        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

        for (RemainedLambda remainedLambda : remainedLambdasCandidate)
        {
            if (mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(remainedLambda.beginPos,
                    remainedLambda.endPos).get(0)) != null) //maybe we need to add more conditions here (looser)
            {
                remainedLambda.introducedCommitHash = currentCommit.getName();
                remainedLambda.NLaterCommitHash = laterNCommit.getName();
                remainedLambdas.add(remainedLambda);
            }
        }

        oldFile.close();
        newFile.close();
    }

    public static void main(String[] args) throws IOException, GitAPIException
    {
        String repoPath = "../repos";
//        GoodLambdaFinder goodLambdaFinder = new GoodLambdaFinder("github.com/apache/hadoop.git", repoPath, 0, 10);
        String listPath = "apache_list.txt";
        BufferedReader bf = new BufferedReader(new FileReader(listPath));
        List<String> lines = new ArrayList<>();
        String s = "";
        while ((s = bf.readLine()) != null)
        {
            lines.add(s);
        }
        bf.close();

        String[] projectList_test = {"apache/skywalking"};
        //String[] projectList = lines.toArray(new String[0]);
        String[] projectList = projectList_test;

        //PrintStream out = System.out;
        Date date = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("MM-dd");
        System.out.println("Good lambdas data:");
        System.out.println("This is the filter result of projects: " + Arrays.toString(projectList));

        for (String project : projectList)
        {
            System.err.println("Mining project " + project + "...");
            String url = "https://github.com/" + project + ".git";
            String repoUrl = url.substring(0, url.lastIndexOf(".git"));
            GoodLambdaFinder goodLambdaFinder = new GoodLambdaFinder(url, repoPath, 0, 10, 10);
            //TO-DO: reduce repeated introduced lambdas
            System.err.println("project " + project + "mining completed!");
            System.err.println("size of lambda list of " + project + ": " + goodLambdaFinder.remainedLambdas.size());
            RemainedLambda[] remainedLambdasForSerial = new RemainedLambda[goodLambdaFinder.remainedLambdas.size()];
            goodLambdaFinder.remainedLambdas.toArray(remainedLambdasForSerial);
            File file  = new File("ser/good-lambdas/test/" + ft.format(date));
            if (!file.exists())
            {
                file.mkdirs();
            }
            FileOutputStream fileOut = new FileOutputStream("ser/good-lambdas/test/" + ft.format(date) + "/" +
                    project.replace("/", " ") + "N=" + goodLambdaFinder.N + ".ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(remainedLambdasForSerial);
            serOut.close();
            fileOut.close();
            System.err.println("Serialized data is saved in ser/good-lambdas/test/" + ft.format(date) + "/" +
                    project.replace("/", " ") + "N=" + goodLambdaFinder.N + ".ser");
        }

    }
}
