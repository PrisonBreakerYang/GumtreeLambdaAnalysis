package com.github.gumtreelambdaanalysis;

import com.github.gumtreediff.gen.SyntaxException;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
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
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class RemainedLambdaFinder
{
    private final Repository repo;
    private final Git git;
    String url;
    int context;
    int timeThreshold;
    int filesThreshold;
    String repoName;
    int initialCount;
    private List<RemainedLambda> remainedLambdas = new ArrayList<>();

    public RemainedLambdaFinder(String url, String repoPath, int context, int timeThreshold, int filesThreshold) throws GitAPIException, IOException {
        assert url.endsWith(".git");
        if (!Files.exists(Paths.get("../repos")))
        {
            new File("..repos").mkdirs();
        }

        repoName = url.substring(url.lastIndexOf("/") + 1, url.lastIndexOf(".git"));
        if (repoPath == null) repoPath = "../repos/" + repoName;
        else repoPath = repoPath + "/" + repoName;

        if (!Files.exists(Paths.get(repoPath)))
        {
            System.err.printf("Cloning into %s...\n", repoPath);
            Git.cloneRepository().setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.err))).setURI(url).setDirectory(new File(repoPath)).call();
        }
        repo = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build();
        git = new Git(repo);
        this.url = url;
        this.context = context;
        this.timeThreshold = timeThreshold;
        this.filesThreshold = filesThreshold;
        this.initialCount = 0;
        List<String> javaPaths = getAllJavaFiles();
        findGoodLambdas(javaPaths, timeThreshold);

        repo.close();
    }

//    void walkAllCommits(Repository repo, String url)throws GitAPIException, IOException
//    {
//        List<Ref> call = git.branchList().call();
//        Ref head= call.get(0);
//        System.out.println(head);
//        assert call.size() == 1;
//        RevWalk walk = new RevWalk(repo);
//        RevCommit commit = walk.parseCommit(head.getObjectId());
//        System.out.println("Start commit: " + commit);
//        System.out.println("Walking all commits starting at HEAD");
//        walk.markStart(commit);
//        walk.setRevFilter(RevFilter.NO_MERGES);
//        int count = 0;
//        for (RevCommit currentCommit : walk) {
//            if (currentCommit.getParentCount() == 0) {
//                // this is the initial commit of the repo
//                break;
//            } else {
//                //if (currentCommit.getParents().length > 1) System.out.println(currentCommit.getParents().length + "\n" + currentCommit.getName());
//                for (RevCommit parentCommit : currentCommit.getParents()) {
//                    gumTreeDiffWithParent(currentCommit, parentCommit, url);
//                }
//            }
//
//            count++;
//        }
//        walk.dispose();
//    }


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

    void findGoodLambdas(List<String> javaPaths, int timeThreshold) throws IOException, GitAPIException {
        // SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (RevWalk revWalk = new RevWalk(repo)) {
            int count = 1;
            RevCommit latestCommit = revWalk.parseCommit(repo.resolve(Constants.HEAD));
            for (String javaPath : javaPaths) {
                count++;
                // if the current version of the file doesn't even have a "->", it can be excluded without doubt
                String currentFile = new String(repo.open(TreeWalk.forPath(repo, javaPath, latestCommit.getTree()).getObjectId(0)).getBytes());
                if (!currentFile.contains("->")) continue;

                // if the oldest commit is still to near to now, it can be excluded
                Iterable<RevCommit> revCommits = git.log().add(repo.resolve(Constants.HEAD)).addPath(javaPath).call();
                List<RevCommit> revCommitList = new ArrayList<>();
                revCommits.forEach(revCommitList::add);
                Collections.reverse(revCommitList);
                if (commitTimeTooNear(revCommitList.get(0).getCommitTime())) continue;

                processRemainedLambdasForInitialCommit(revCommitList, javaPath);
                for (int i = 0; i < revCommitList.size() - 1; i++)
                {
                    RevCommit lastCommit = revCommitList.get(i);
                    RevCommit currentCommit = revCommitList.get(i + 1);
                    if (commitTimeTooNear(currentCommit.getCommitTime())) break;

                    if (TreeWalk.forPath(repo, javaPath, currentCommit.getTree()) == null || TreeWalk.forPath(repo, javaPath, lastCommit.getTree()) == null)
                    {
                        System.out.println(javaPath);
                        continue;
                    }
                    String fileBeforeCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, lastCommit.getTree()).getObjectId(0)).getBytes());
                    String fileAfterCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, currentCommit.getTree()).getObjectId(0)).getBytes());
                    if (!fileAfterCommit.contains("->")) continue;

                    //calculate modified files and java files
                    int filesModified = 0;
                    int javaFilesModified = 0;

                    RevWalk tempRevWalk = new RevWalk(repo);
                    tempRevWalk.setRevFilter(RevFilter.NO_MERGES);
                    RevCommit parentCommit = tempRevWalk.parseCommit(repo.resolve(currentCommit.getName())).getParent(0);
//            System.out.println(parentCommit.getName());
                    ByteArrayOutputStream tempOutputStream = new ByteArrayOutputStream();
                    DiffFormatter tempFormatter = new DiffFormatter(tempOutputStream);
                    tempFormatter.setRepository(repo);
                    //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));
                    List<DiffEntry> tempDiffs = tempFormatter.scan(parentCommit, currentCommit);

                    for (DiffEntry diff : tempDiffs)
                    {
                        if (diff.getOldPath().endsWith(".java"))
                        {
                            javaFilesModified += 1;
                        }
                    }
                    filesModified = tempDiffs.size();
                    tempRevWalk.close();
                    tempFormatter.close();
                    tempOutputStream.close();
                    if (javaFilesModified > this.filesThreshold) continue;

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
                        oldTreeIter.reset(reader, lastCommit.getTree().getId());
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

                        //Run.initGenerators();
                        Tree oldFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/oldfile.java")).getRoot();
                        Tree newFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/newfile.java")).getRoot();    //i + 1
                        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
                        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

                        List<PositionTuple> positionTupleListAfterCommit = new ArrayList<>();
                        List<RemainedLambda> remainedLambdaCandidates = new ArrayList<>();
                        GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(fileAfterCommit, positionTupleListAfterCommit, true);  //i + 1
                        //System.out.println(positionTupleListAfterCommit.size());


                        //System.out.println("https://github.com/apache/" + repoName + "/blob/" + currentCommit.getName() + "/" + javaPath + "\t" + positionTupleListAfterCommit.size());
                        //System.out.println(positionTupleListAfterCommit.size());
                        for (PositionTuple positionTuple : positionTupleListAfterCommit)
                        {
                            if (newFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).size() == 0) continue;
                            if (mappings.getSrcForDst(newFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0)) == null
                                    && BadLambdaFinder.lambdaInEdits(positionTuple, BadLambdaFinder.getMergedEdits(editsInsertedOrReplaced), "B"))
                            {
                                //表示确实有新lambda生成
                                remainedLambdaCandidates.add(new RemainedLambda(repo, currentCommit, url, javaPath, positionTuple, BadLambdaFinder.lambda_context(
                                        fileAfterCommit, positionTuple.beginLine, positionTuple.endLine, this.context), revCommitList.get(revCommitList.size() - 1).getName(),
                                        filesModified, javaFilesModified, revCommitList.size() - (i + 1), false));
                            }
                        }
                        if (remainedLambdaCandidates.size() > 0)
                        {
                            compareCommitWithPresent(currentCommit, latestCommit, remainedLambdaCandidates, javaPath);
                            //compareCommitWithPresentOneByOne(revCommitList.subList(i, revCommitList.size()), remainedLambdaCandidates, javaPath);
                        }
                        oldFile.close();
                        newFile.close();
                        outputStream.close();
                        formatter.close();
                    } catch (SyntaxException e) {
                        System.out.println(repoName);
                        System.out.println(revCommitList.get(i + 1).getName());
                        System.out.println(javaPath);
                    }
                }
                System.out.println(repoName + " files covered: " + count + "/" + javaPaths.size());
            }

        }
    }

    //This function counts how many java files are modified in a single commit
//    int numOfJavaFiles(List<DiffEntry> diffs) throws IOException {
//        int count = 0;
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        DiffFormatter formatter = new DiffFormatter(outputStream);
//        formatter.setRepository(repo);
//        //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));
//        for (DiffEntry diff : diffs)
//        {
//            if (!(diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.ADD
//                    || ((diff.getChangeType() == DiffEntry.ChangeType.RENAME) && (!diff.getOldPath().equals(diff.getNewPath()))))) {
//                if (formatter.toFileHeader(diff).getOldPath().endsWith(".java"))
//                {
//                    count += 1;
//                }
//            }
//        }
//        return count;
//    }

    void processRemainedLambdasForInitialCommit(List<RevCommit> revCommitList, String javaPath) throws IOException
    {
        List<PositionTuple> positionTupleListInitialCommit = new ArrayList<>();
        List<RemainedLambda> remainedLambdaCandidates = new ArrayList<>();
        //if the file of this revision is empty

        //if this commit is not the initial commit and it changed too many java files
        RevCommit initialCommit = revCommitList.get(0);
        assert initialCommit.getParentCount() == 0;
        String commitName = initialCommit.getName();
        RevCommit parentCommit;
        RevWalk revWalk = new RevWalk(repo);
        revWalk.setRevFilter(RevFilter.NO_MERGES);

        int filesModified = 0;
        int javaFilesModified = 0;
        if (revWalk.parseCommit(repo.resolve(commitName)).getParentCount() == 0)
        {
            //this is the very initial commit
            RevTree tree = initialCommit.getTree();
            try (TreeWalk treeWalk = new TreeWalk(repo))
            {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                while (treeWalk.next())
                {
                    if (treeWalk.getPathString().endsWith(".java"))
                    {
                        javaFilesModified += 1;
                    }
                    filesModified += 1;
                }
            }
        }
        else
        {
            parentCommit = revWalk.parseCommit(repo.resolve(commitName)).getParent(0);
//            System.out.println(parentCommit.getName());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            DiffFormatter formatter = new DiffFormatter(outputStream);
            formatter.setRepository(repo);
            //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));
            List<DiffEntry> diffs = formatter.scan(parentCommit, initialCommit);

            for (DiffEntry diff : diffs)
            {
                // FileHeader fileHeader = formatter.toFileHeader(diff);
//                if (fileHeader.getOldPath().endsWith(".java")) {
//                    javaFilesModified += 1;
//                }
                if (diff.getOldPath().endsWith(".java")) javaFilesModified += 1;
            }
            filesModified = diffs.size();
        }

        if (javaFilesModified > this.filesThreshold) return;
        //System.out.println(parentCommit.getName());

        String initialCommitContent = new String(repo.open(TreeWalk.forPath(repo, javaPath, revCommitList.get(0).getTree()).getObjectId(0)).getBytes());
        if (!initialCommitContent.contains("->")) return;

        GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(initialCommitContent, positionTupleListInitialCommit, true);
        for (PositionTuple positionTuple : positionTupleListInitialCommit)
        {
//            System.out.println("#####################");
//            System.out.println(url.replace(".git", "") + "/commit/" + initialCommit.getName());
//            System.out.println(javaPath);
//            System.out.println(positionTuple.node.toString());
//            System.out.println("processRemainedLambdasForInitialCommit");
            remainedLambdaCandidates.add(new RemainedLambda(repo, revCommitList.get(0), url, javaPath, positionTuple, BadLambdaFinder.lambda_context(
                    initialCommitContent, positionTuple.beginLine, positionTuple.endLine, this.context), revCommitList.get(revCommitList.size() - 1).getName(),
                    filesModified, javaFilesModified, revCommitList.size(), true));
        }
        if (remainedLambdaCandidates.size() > 0)
        {
            //compareCommitWithPresentOneByOne(revCommitList, remainedLambdaCandidates, javaPath);
            compareCommitWithPresent(initialCommit, revCommitList.get(revCommitList.size() - 1), remainedLambdaCandidates, javaPath);
        }
        revWalk.dispose();
    }

    void compareCommitWithPresent(RevCommit oldCommit, RevCommit latestCommit, List<RemainedLambda> remainedLambdaCandidates, String javaPath) throws IOException {
        String fileOldCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, oldCommit.getTree()).getObjectId(0)).getBytes());
        String fileLatestCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, latestCommit.getTree()).getObjectId(0)).getBytes());
        if (!fileLatestCommit.contains("->")) return;
        FileWriter oldFile, newFile;
        oldFile = new FileWriter("old-new-file\\oldfile.java");
        oldFile.write("");
        oldFile.write(fileOldCommit);
        oldFile.flush();
        newFile = new FileWriter("old-new-file\\newfile.java");
        newFile.write("");
        newFile.write(fileLatestCommit);
        newFile.flush();

        try {
            Tree oldFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/oldfile.java")).getRoot();
            Tree newFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/newfile.java")).getRoot();

            Matcher defaultMatcher = Matchers.getInstance().getMatcher();
            MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

            for (RemainedLambda remainedLambda : remainedLambdaCandidates) {
                if (oldFileTree.getTreesBetweenPositions(remainedLambda.beginPos, remainedLambda.endPos).size() == 0)
                    continue;
                if (mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(remainedLambda.beginPos,
                        remainedLambda.endPos).get(0)) != null) //maybe we need to add more conditions here (looser)
                {
                    remainedLambda.introducedCommitHash = oldCommit.getName();
                    remainedLambda.NLaterCommitHash = latestCommit.getName();
                    remainedLambdas.add(remainedLambda);
                }
            }

            oldFile.close();
            newFile.close();
        } catch (SyntaxException e)
        {
            System.out.println("Syntax error from JdtTreeGenerator!");
        }
    }

    TwoTuple compareCommitWithAdjacent(RevCommit currentCommit, RevCommit nextCommit, TwoTuple positionOfCandidate, String javaPath) throws IOException {
        try {
//            System.out.println("################################");
//            System.out.println("current commit:" + currentCommit.getName());
//            System.out.println("next commit:" + nextCommit.getName());
//            System.out.println("java path:" + javaPath);
            //System.out.println(positionOfCandidate.beginPos + "-" + positionOfCandidate.endPos);
            if (TreeWalk.forPath(repo, javaPath, currentCommit.getTree()) == null || TreeWalk.forPath(repo, javaPath, nextCommit.getTree()) == null) return null;
            String fileCurrentCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, currentCommit.getTree()).getObjectId(0)).getBytes());
            String fileNextCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, nextCommit.getTree()).getObjectId(0)).getBytes());
            if (!fileNextCommit.contains("->") || !fileCurrentCommit.contains("->")) return null;
            FileWriter oldFile, newFile;
            oldFile = new FileWriter("old-new-file\\oldfile.java");
            oldFile.write("");
            oldFile.write(fileCurrentCommit);
            oldFile.flush();
            newFile = new FileWriter("old-new-file\\newfile.java");
            newFile.write("");
            newFile.write(fileNextCommit);
            newFile.flush();

            //Run.initGenerators();
//            Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file\\oldfile.java").getRoot();
//            Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file\\newfile.java").getRoot();
            Tree oldFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/oldfile.java")).getRoot();
            Tree newFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/newfile.java")).getRoot();

            Matcher defaultMatcher = Matchers.getInstance().getMatcher();
            MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

            Tree result;
            if (oldFileTree.getTreesBetweenPositions(positionOfCandidate.beginPos, positionOfCandidate.endPos).size() == 0)
            {
                result = null;
            }
            else result = mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(positionOfCandidate.beginPos, positionOfCandidate.endPos).get(0));
            //System.out.println("new pos:" + result.getPos() + "\t" + result.getEndPos());
            oldFile.close();
            newFile.close();

            if (result == null) return null;
            else return new TwoTuple(result.getPos(), result.getEndPos());
        } catch (IndexOutOfBoundsException e)
        {
            System.out.println("begin pos and end pos" + positionOfCandidate.beginPos + "\t" + positionOfCandidate.endPos);
            System.out.println("https://github.com/apache/" + repoName + "/blob/" + currentCommit.getName() + "/" + javaPath);
        }
        return null;
    }
    void compareCommitWithPresentOneByOne(List<RevCommit> revCommitListToProcess, List<RemainedLambda> remainedLambdaCandidates, String javaPath) throws IOException
    {
        for (RemainedLambda remainedLambda : remainedLambdaCandidates)
        {
            TwoTuple positionOfCandidate = new TwoTuple(remainedLambda.beginPos, remainedLambda.endPos);
            boolean alive = true;
            for (int i = 0; i < revCommitListToProcess.size() - 1; i++)
            {
                TwoTuple aliveTuple = compareCommitWithAdjacent(revCommitListToProcess.get(i), revCommitListToProcess.get(i + 1), positionOfCandidate, javaPath);
                positionOfCandidate = aliveTuple;
                if (aliveTuple == null)
                {
                    alive = false;
                    break;
                }
            }

            if (alive)
            {
                remainedLambdas.add(remainedLambda);
                System.out.println("alive");
            }
            else System.out.println("dead");
        }

    }
    boolean commitTimeTooNear(int commitTime)
    {
        int year = getCommitYear(commitTime);
        int month = getCommitMonth(commitTime);
        if (year > 2019) return true;
        if (year == 2019 && month > 3) return true;
        return false;
    }
    int getCommitYear(int commitTime)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = Long.parseLong(String.valueOf(commitTime)) * 1000;
        String date = formatter.format(new Date(timestamp));
        int year = Integer.parseInt(date.split(" ")[0].split("-")[0]);
        return year;
    }
    int getCommitMonth(int commitTime)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = Long.parseLong(String.valueOf(commitTime)) * 1000;
        String date = formatter.format(new Date(timestamp));
        int month = Integer.parseInt(date.split(" ")[0].split("-")[1]);
        return month;
    }

    public static void main(String[] args) throws GitAPIException, IOException
    {
        //RemainedLambdaFinder finder = new RemainedLambdaFinder("https://github.com/apache/skywalking.git", null, 10, 0);
        String repoPath = "../repos";
//        GoodLambdaFinder goodLambdaFinder = new GoodLambdaFinder("github.com/apache/hadoop.git", repoPath, 0, 10);
        String listPath = "apache_list_new.txt";
        BufferedReader bf = new BufferedReader(new FileReader(listPath));
        List<String> lines = new ArrayList<>();
        String s = "";
        while ((s = bf.readLine()) != null)
        {
            lines.add(s);
        }
        bf.close();

        String[] projectList_test = {"apache/ambari"};
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
            RemainedLambdaFinder finder = new RemainedLambdaFinder(url, repoPath, 10, 10, 100);
            //TO-DO: reduce repeated introduced lambdas //seems not necessary?
            System.err.println("project " + project + " mining completed!");
            System.err.println("size of lambda list of " + project + ": " + finder.remainedLambdas.size());

            RemainedLambda[] remainedLambdasForSerial = new RemainedLambda[finder.remainedLambdas.size()];
            finder.remainedLambdas.toArray(remainedLambdasForSerial);
            File file  = new File("ser/good-lambdas/test/" + ft.format(date));
            if (!file.exists())
            {
                file.mkdirs();
            }
            FileOutputStream fileOut = new FileOutputStream("ser/good-lambdas/test/" + ft.format(date) + "/" +
                    project.replace("/", " ") + "-onestep_T=100.ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(remainedLambdasForSerial);
            serOut.close();
            fileOut.close();
            System.err.println("Serialized data is saved in ser/good-lambdas/test/" + ft.format(date) + "/" +
                    project.replace("/", " ") + "-onestep_T=100.ser");
            System.out.println(finder.initialCount);
        }
    }
}
