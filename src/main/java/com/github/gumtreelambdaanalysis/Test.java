package com.github.gumtreelambdaanalysis;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.io.*;
import java.util.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

class LambdaFilter
{
    private final Repository repo;
    private final Git git;
    private final String url;
    //private final Stemmer stemmer;
    private final int threshold;

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        //noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }


    void showDiff() throws IOException, GitAPIException {
        AbstractTreeIterator oldTreeParser = prepareTreeParser(repo, "1e048df35af7e685c5612b77f7198266ab3cf485");
        AbstractTreeIterator newTreeParser = prepareTreeParser(repo, "717a15b383e67c635870ec0369e77454541b994a");

        List<DiffEntry> diffs = git.diff().
                setOldTree(oldTreeParser).
                setNewTree(newTreeParser).
                call();
                //setPathFilter(PathFilter.create("x-pack/plugin/watcher/src/main/java/org/elasticsearch/xpack/watcher/WatcherLifeCycleService.java")).call();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(repo);
        //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));

        diffs.forEach(diff ->
        {
            try {
                formatter.setContext(0);
                formatter.format(diff);
                FileHeader fileHeader = formatter.toFileHeader(diff);
                List<Edit> editList = new ArrayList<>();
                //RevCommit parentCommit = new RevCommit(65898eb1a6d8287580136d98d7faf6a5b55571b6)

                //TreeWalk oldTreeWalk = TreeWalk.forPath(repo, diff.getOldPath(), oldTree);
                ObjectId oldObjectId = ObjectId.fromString("1e048df35af7e685c5612b77f7198266ab3cf485");
                ObjectId newObjectId = ObjectId.fromString("717a15b383e67c635870ec0369e77454541b994a");
                RevWalk walk = new RevWalk(repo);
                RevCommit oldCommit = walk.parseCommit(oldObjectId);
                RevCommit newCommit = walk.parseCommit(newObjectId);
                RevTree oldTree = oldCommit.getTree();
                RevTree newTree = newCommit.getTree();
                TreeWalk oldTreeWalk = TreeWalk.forPath(repo, diff.getOldPath(), oldTree);
                TreeWalk newTreeWalk = TreeWalk.forPath(repo, diff.getNewPath(), newTree);
                ObjectLoader fileContentBeforeCommit = null;
                ObjectLoader fileContentAfterCommit = null;
                if (oldTreeWalk != null && newTreeWalk != null) {
                    fileContentBeforeCommit = repo.open(oldTreeWalk.getObjectId(0));
                    fileContentAfterCommit = repo.open(newTreeWalk.getObjectId(0));
                }
                assert fileContentAfterCommit != null;
                String fileContent = new String(fileContentAfterCommit.getBytes());
                String[] fileContentLines = fileContent.split("\n");
                for (String line : fileContentLines)
                {
                    if (line.contains("->"))
                    {
                        //System.out.println(line);
                    }
                }

                //ObjectLoader fileContentAfterCommit = ContentSource.Pair.

                for (HunkHeader hunk : fileHeader.getHunks())
                {
                    for (Edit edit : hunk.toEditList())
                    {
                        if (edit.getType() == Edit.Type.DELETE || edit.getType() == Edit.Type.REPLACE)
                        editList.add(edit);
                        //System.out.println(edit);
                    }
                }
                List<Edit> mergedEditList = BadLambdaFinder.getMergedEdits(editList);

                for (Edit edit : mergedEditList)
                {
                    System.out.println(edit);
                }
            }catch (IOException e)
            {
                e.printStackTrace();
            }
        });
    }

    public LambdaFilter(String url, String repoPath, int editThreshold) throws IOException, GitAPIException
    {
        assert url.endsWith(".git");
        if (!Files.exists(Paths.get("repos")))
        {
            new File("repos").mkdirs();
        }
        String repoName = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf(".git"));
        if (repoPath == null)
        {
            repoPath = "repos/" + repoName;
        }
        if (!Files.exists(Paths.get(repoPath)))
        {
            System.err.printf("Cloning into %s...\n", repoPath);
            Git.cloneRepository().setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.err))).setURI(url).setDirectory(new File(repoPath)).call();
        }
        repo = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build();
        git = new Git(repo);
        this.url = url;
        //stemmer = new Stemmer();
        threshold = editThreshold;
    }

    void diffBetweenParent(RevCommit currentCommit, RevCommit parentCommit) throws IOException
    {
        System.out.println("++++++++++++++" + currentCommit);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter formatter = new DiffFormatter(outputStream);
        formatter.setRepository(repo);
        formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));
        List<DiffEntry> diffs = formatter.scan(parentCommit, currentCommit);
        diffs.forEach(diff ->
        {
            if (diff.getChangeType() == DiffEntry.ChangeType.DELETE || diff.getChangeType() == DiffEntry.ChangeType.ADD || diff.getChangeType() == DiffEntry.ChangeType.RENAME)
            {
                return;
            }
            try
            {
                // keep the changed line only
                formatter.setContext(0);
                formatter.format(diff);
                //String content = outputStream.toString();
//                if (!content.contains("->"))
//                {
//                    return;
//                }
                outputStream.reset();

                // fetch file
                RevTree oldTree = parentCommit.getTree();
                TreeWalk oldTreeWalk = TreeWalk.forPath(repo, diff.getOldPath(), oldTree);
                ObjectLoader fileContentBeforeCommit = null;
                // get file content
                if (oldTreeWalk != null)
                {
                    fileContentBeforeCommit = repo.open(oldTreeWalk.getObjectId(0));
                }
                if (fileContentBeforeCommit == null)
                {
                    return;
                }
                //JDTDriver jdtDriver = new JDTDriver(new String(fileContentBeforeCommit.getBytes()));

                FileHeader fileHeader = formatter.toFileHeader(diff);
                // handle hunks
                for (HunkHeader hunk : fileHeader.getHunks())
                {
                    // handle each edit snippet
                    for (Edit edit : hunk.toEditList())
                    {
                        System.out.println("#####################################");
                        System.out.println(edit);
                        System.out.println(edit.getBeginA());
                        System.out.println(edit.getEndA());
                        //System.out.println(edit.before(edit));
                        //System.out.println(edit.after(edit));
                        System.out.println("#####################################");
                        /*
                          A modified region detected between two versions of roughly the same content.
                          An edit covers the modified region only. It does not cover a common region.

                          Regions should be specified using 0 based notation, so add 1 to the start and end
                          marks for line numbers in a file.

                          An edit where beginA == endA && beginB < endB is an insert edit, that is sequence B
                          inserted the elements in region [beginB, endB) at beginA.

                          An edit where beginA < endA && beginB == endB is a delete edit, that is sequence B has
                          removed the elements between [beginA, endA).

                          An edit where beginA < endA && beginB < endB is a replace edit, that is sequence B has
                          replaced the range of elements between [beginA, endA) with those found in [beginB, endB).
                         */
//                        if (edit.getType() != Edit.Type.INSERT && (edit.getEndA() - edit.getBeginA() < threshold))
//                        {
//                            assert url.startsWith("https://github.com");
//                            String repoURL = url.substring(0, url.lastIndexOf(".git"));
                            //MessageDigest md5 = MessageDigest.getInstance("MD5");
                            //byte[] hash = md5.digest(diff.getOldPath().getBytes());
                            //BigInteger number = new BigInteger(1, hash);
                            //StringBuilder hashText = new StringBuilder(number.toString(16));
//                            while (hashText.length() < 32)
//                            {
//                                hashText.insert(0, "0");
//                            }
//                            System.out.println((char) 27 + "[32m" +
//                                    "---------------------------------------------------------------------" + (char) 27 + "[0m");
//                            if (currentCommit.getParentCount() > 1)
//                            {
//                                System.out.println((char) 27 + "[31m" + "(This is a merge commit)" + (char) 27 + "[0m");
//                            }
//                            System.out.println(currentCommit.getFullMessage());
//                            System.out.println((char) 27 + "[32m" +
//                                    "---------------------------------------------------------------------" + (char) 27 + "[0m");
//                            System.out.printf("Current edit : %s\nOld Commit Hash: %s\nLocation: %s %d:%d\nDiff URL: "
//                                            + "%s\nNew File URL: %s\nOld File URL: %s\n\n", edit.toString(),
//                                    parentCommit.getName(), diff.getOldPath(), edit.getBeginA() + 1, edit.getEndA(),
//                                    repoURL + "/commit/" + currentCommit.getName() + "#diff-" + hashText + "L" + (edit.getBeginA() + 1), repoURL + "/blob/" + currentCommit.getName() + "/" + diff.getNewPath() + "#L" + (edit.getBeginB() + 1), repoURL + "/blob/" + parentCommit.getName() + "/" + diff.getOldPath() + "#L" + (edit.getBeginA() + 1));
//                            System.out.println((char) 27 + "[32m" +
//                                    "=====================================================================" + (char) 27 + "[0m");
//                        }
                    }
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        });
    }

    void walk() throws IOException, GitAPIException {
        Iterable<RevCommit> log = git.log().all().call();

        for (RevCommit currentCommit : log)
        {
            if (currentCommit.getParentCount() == 0)
            {
                break;
            }
            else
            {
                for (RevCommit parentCommit : currentCommit.getParents())
                {
                    diffBetweenParent(currentCommit, parentCommit);
                }
            }
        }
    }
}

public class Test
{
    public static String getCommitTime(SimplifiedModifiedLambda lambda) throws IOException {
        String repoPath = "../repos/" + lambda.commitURL.replace("https://github.com/apache/", "").split("/commit")[0];
        Repository repo = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build();
        RevWalk tempRevWalk = new RevWalk(repo);
        RevCommit commit = tempRevWalk.parseCommit(repo.resolve(lambda.currentCommit.split(" ")[1]));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = Long.parseLong(String.valueOf(commit.getCommitTime())) * 1000;
        String date = formatter.format(new Date(timestamp));
        repo.close();
        tempRevWalk.close();
        return date;
    }
    public static void toCsv() throws IOException, ClassNotFoundException {
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>();

        String[] keywords = null;
        File writeFile = new File("statistics/" + "removed-lambdas-list/"+ "data-of-51-repos-with-date" + ".csv");
        try {
            String serPath = "ser\\bad-lambdas\\test";
            String[] paths = {serPath + "\\03-15", serPath + "\\03-16", serPath + "\\03-17", serPath + "\\03-18"};
            for (String path : paths)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (serFile.toString().endsWith(".ser"))
                    {
                        //System.out.println("line 332");
                        FileInputStream fileIn = new FileInputStream(serFile);
                        ObjectInputStream in = new ObjectInputStream(fileIn);
                        SimplifiedModifiedLambda[] simplifiedModifiedLambdas = (SimplifiedModifiedLambda[]) in.readObject();
                        simplifiedModifiedLambdaList.addAll(new ArrayList<>(Arrays.asList(simplifiedModifiedLambdas)));
                    }
                }
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(writeFile));
            //writer.newLine();
            writer.write("seq,project,file path,file name,lambda line number,edit line number,modified java files,git command,remove date,commit link");
            int seq = 1;
            for (SimplifiedModifiedLambda simplifiedModifiedLambda : simplifiedModifiedLambdaList)
            {
                writer.newLine();
                int temp = simplifiedModifiedLambda.filePath.split("/").length;
                writer.write(seq + "," + simplifiedModifiedLambda.commitURL.replace("https://github.com/", "").split("/commit")[0]
                        + "," + simplifiedModifiedLambda.filePath + "," + simplifiedModifiedLambda.filePath.split("/")[temp - 1] + ","
                + "L" + simplifiedModifiedLambda.beginLine + "-" + "L" + simplifiedModifiedLambda.endLine + ","
                + "L" + simplifiedModifiedLambda.editBeginLine + "-" + "L" + simplifiedModifiedLambda.editEndLine + "," + simplifiedModifiedLambda.javaFileModified
                + "," + simplifiedModifiedLambda.gitCommand  + "," + getCommitTime(simplifiedModifiedLambda) + "," + simplifiedModifiedLambda.commitURL);
                seq += 1;
            }
            writer.flush();
            writer.close();
        }catch (FileNotFoundException e)
        {
            System.err.println("can't find the file");
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException, GitAPIException, ClassNotFoundException {
        Test.toCsv(); //extract bad lambdas information form .ser file
    }
}