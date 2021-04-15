package com.github.gumtreelambdaanalysis;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class RemainedLambda implements Serializable
{
    private static final long serialVersionUID = 1L;

    String repo;
    String commitURL;
    String filePath;
    String node;
    String parentNode;
    int beginLine;
    int endLine;
    int beginPos;
    int endPos;
    //int nodesNum;
    String commitMessage;
    String introducedCommit;
    String introducedCommitHash, NLaterCommitHash;
    String gitCommand;
    String lambdaContext;
    String introducedDate;
    int filesModified;
    int javaFilesModified;
    int revNumFromIntroduced;
    boolean introducedWhenTheFileCreated;

    public RemainedLambda(Repository repo, RevCommit introducedCommit, String url, String filePath, PositionTuple positionTuple,
              String lambdaContext, String NLaterCommitHash, int filesModified, int javaFilesModified, int revNumFromIntroduced, boolean introducedWhenTheFileCreated)
    {
        this.repo = repo.toString();
        this.commitURL = url.replace(".git", "") + "/commit/" + introducedCommit.getName();
        this.filePath = filePath;
        this.node = positionTuple.node.toString();
        this.parentNode = positionTuple.node.getParent().toString();
        this.beginLine = positionTuple.beginLine;
        this.endLine = positionTuple.endLine;
        this.beginPos = positionTuple.beginPos;
        this.endPos = positionTuple.endPos;
        this.commitMessage = introducedCommit.getFullMessage();
        this.introducedCommit = introducedCommit.toString();
        this.gitCommand = "git log --pretty=format:%h%x09%an%x09%ad%x09%s " + filePath;
        this.lambdaContext = lambdaContext;
        this.introducedCommitHash = introducedCommit.getName();
        this.NLaterCommitHash = NLaterCommitHash;
        this.introducedWhenTheFileCreated = introducedWhenTheFileCreated;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = Long.parseLong(String.valueOf(introducedCommit.getCommitTime())) * 1000;
        this.introducedDate = formatter.format(new Date(timestamp));
        this.filesModified = filesModified;
        this.javaFilesModified = javaFilesModified;
        this.revNumFromIntroduced = revNumFromIntroduced;
    }

    public RemainedLambda(Repository repo, RevCommit introducedCommit, String url, String filePath, PositionTuple positionTuple,
                          String lambdaContext, String NLaterCommitHash, boolean introducedWhenTheFileCreated)
    {
        this.repo = repo.toString();
        this.commitURL = url.replace(".git", "") + "/commit/" + introducedCommit.getName();
        this.filePath = filePath;
        this.node = positionTuple.node.toString();
        this.parentNode = positionTuple.node.getParent().toString();
        this.beginLine = positionTuple.beginLine;
        this.endLine = positionTuple.endLine;
        this.beginPos = positionTuple.beginPos;
        this.endPos = positionTuple.endPos;
        this.commitMessage = introducedCommit.getFullMessage();
        this.introducedCommit = introducedCommit.toString();
        this.gitCommand = "git log --pretty=format:%h%x09%an%x09%ad%x09%s " + filePath;
        this.lambdaContext = lambdaContext;
        this.introducedCommitHash = introducedCommit.getName();
        this.NLaterCommitHash = NLaterCommitHash;
        this.introducedWhenTheFileCreated = introducedWhenTheFileCreated;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = Long.parseLong(String.valueOf(introducedCommit.getCommitTime())) * 1000;
        this.introducedDate = formatter.format(new Date(timestamp));
    }

    public static void deserializeGoodLambdas()
    {
        String writePath = "statistics/" + "remained-lambdas-list/remote/";
        String serPath = "ser/good-lambdas/remote";
        File writeFile = new File( writePath + "data-of-103repos.csv");
        List<RemainedLambda> remainedLambdaList = new ArrayList<>();
        try {
            //String[] readPath = {serPath + "\\03-24", serPath + "\\03-25", serPath + "\\03-26", serPath + "\\03-27", serPath + "\\03-30"};
            String[] readPath = {serPath};
            for (String path : readPath)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (!serFile.toString().endsWith(".ser")) continue;
                    FileInputStream fileIn = new FileInputStream(serFile);
                    ObjectInputStream in = new ObjectInputStream(fileIn);
                    RemainedLambda[] remainedLambdaArray = (RemainedLambda[]) in.readObject();
                    remainedLambdaList.addAll(new ArrayList<>(Arrays.asList(remainedLambdaArray)));
                }
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(writeFile));
            writer.write("seq,project,file path,file name,lambda line number,introduced when file created,introduced url," +
                    "present url,git diff,git log,introduced date,revisions from introduced, java files modified,file link,commit link");
            int seq = 1;
            for (RemainedLambda remainedLambda : remainedLambdaList)
            {
//                System.out.println("################");
//                System.out.println(remainedLambda.lambdaContext);
                writer.newLine();
                int temp = remainedLambda.filePath.split("/").length;
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                messageDigest.update(remainedLambda.filePath.getBytes("UTF-8"));
                String diffHash = Test.byte2Hex(messageDigest.digest());
                String projectName = remainedLambda.commitURL.replace("https://github.com/", "").split("/commit")[0];
                writer.write(seq + "," + projectName
                + "," + remainedLambda.filePath + "," + remainedLambda.filePath.split("/")[temp - 1] + ","
                + "L" + remainedLambda.beginLine + "-" + "L" + remainedLambda.endLine + "," + remainedLambda.introducedWhenTheFileCreated + ","
                + "https://github.com/" + projectName + "/blob/" + remainedLambda.introducedCommitHash + "/" + remainedLambda.filePath + "#L" + remainedLambda.beginLine + ","
                + "https://github.com/" + projectName + "/blob/" + remainedLambda.NLaterCommitHash + "/" + remainedLambda.filePath + ","
                + "git diff " + remainedLambda.introducedCommitHash + " " + remainedLambda.NLaterCommitHash + " " + remainedLambda.filePath + ","
                + remainedLambda.gitCommand + "," + remainedLambda.introducedDate + "," + remainedLambda.revNumFromIntroduced + "," + remainedLambda.javaFilesModified + ","
                + remainedLambda.commitURL + "#diff-" + diffHash + "R" + remainedLambda.beginLine + "," + remainedLambda.commitURL);
                seq += 1;
            }
            writer.flush();
            writer.close();
        } catch (IOException | ClassNotFoundException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        RemainedLambda.deserializeGoodLambdas();
    }
}
