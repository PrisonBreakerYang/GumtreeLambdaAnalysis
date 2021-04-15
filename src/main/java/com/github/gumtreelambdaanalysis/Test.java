package com.github.gumtreelambdaanalysis;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Test
{
    public static int getCommitInfo(SimplifiedModifiedLambda lambda, StringBuffer commitDate, StringBuffer committerId, StringBuffer committerEmail,
                                    StringBuffer authorId, StringBuffer authorEmail) throws IOException, GitAPIException {
        String repoPath = "../repos/" + lambda.commitURL.replace("https://github.com/apache/", "").split("/commit")[0];
        Repository repo = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build();
        RevWalk tempRevWalk = new RevWalk(repo);
        RevCommit commit = tempRevWalk.parseCommit(repo.resolve(lambda.currentCommit.split(" ")[1]));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long timestamp = Long.parseLong(String.valueOf(commit.getCommitTime())) * 1000;
        commitDate.append(formatter.format(new Date(timestamp)));
        committerId.append(commit.getCommitterIdent().getName().replace(",", " "));
        committerEmail.append(commit.getCommitterIdent().getEmailAddress());
        authorId.append(commit.getAuthorIdent().getName().replace(",", " "));
        authorEmail.append(commit.getAuthorIdent().getEmailAddress());

        assert commit.getParentCount() == 1;
        RevCommit parentCommit = commit.getParent(0);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter tempFormatter = new DiffFormatter(outputStream);
        tempFormatter.setRepository(repo);
        //formatter.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.MYERS));
        List<DiffEntry> diffs = tempFormatter.scan(parentCommit, commit);

        int linesDeleted = 0;
        //int linesDeleted = 0;
        for (DiffEntry diff : diffs)
        {
            if (diff.getChangeType() == DiffEntry.ChangeType.DELETE
                    || diff.getChangeType() == DiffEntry.ChangeType.ADD) continue;
            FileHeader fileHeader = tempFormatter.toFileHeader(diff);
            if (!fileHeader.getOldPath().endsWith(".java")) continue;
            for (Edit edit : fileHeader.toEditList()) {
                linesDeleted += edit.getEndA() - edit.getBeginA();
                //linesAdded += edit.getEndB() - edit.getBeginB();
            }
        }
        tempFormatter.close();
        //if (committerId.toString().equals("GitHub")) System.out.println(new String(commit.getRawBuffer(), commit.getEncoding()));
        repo.close();
        tempRevWalk.close();
        return linesDeleted;
    }
    static String byte2Hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        String temp = null;
        for (byte aByte : bytes) {
            temp = Integer.toHexString(aByte & 0xFF);
            if (temp.length() == 1) {
                // 1得到一位的进行补0操作
                sb.append("0");
            }
            sb.append(temp);
        }
        return sb.toString();
    }
    public static void show() throws IOException, ClassNotFoundException {
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>();
        String[] keywords = null;
        File writeFile = new File("statistics/" + "removed-lambdas-list/"+ "data-of-103repos" + ".csv");
        try {
            String serPath = "ser/bad-lambdas";
            //String[] paths = {serPath + "\\03-15", serPath + "\\03-16", serPath + "\\03-17", serPath + "\\03-18"};
            String[] paths = {serPath};
            for (String path : paths)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (serFile.toString().endsWith("51repos-filtered-without-edit-limit.ser"))
                    {
                        //System.out.println("line 332");
                        FileInputStream fileIn = new FileInputStream(serFile);
                        ObjectInputStream in = new ObjectInputStream(fileIn);
                        SimplifiedModifiedLambda[] simplifiedModifiedLambdas = (SimplifiedModifiedLambda[]) in.readObject();
                        simplifiedModifiedLambdaList.addAll(new ArrayList<>(Arrays.asList(simplifiedModifiedLambdas)));
                    }
                }
                toCsv(simplifiedModifiedLambdaList, writeFile);
            }

        }catch (FileNotFoundException | GitAPIException | NoSuchAlgorithmException e)
        {
            System.err.println("can't find the file");
            e.printStackTrace();
        }
    }

    static void toCsv(List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList, File writeFile) throws IOException, GitAPIException, NoSuchAlgorithmException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(writeFile));
        //writer.newLine();

        writer.write("seq,project,file path,file name,percentage(commit),deleted lines(commit),lambda lines,lambda line number,edit line number,percentage(edit)," +
                "modified java files,git command,remove date,link to jira,committer id,committer mail,author id,author mail,diff file link,commit link");
        int seq = 1;
        DecimalFormat df = new DecimalFormat("#0.000");
        Map<String, Set<Integer>> lambdaLinesInsideEditMap = removedLambdaLinesInsideEdit(simplifiedModifiedLambdaList);
        Map<String, Integer> lambdaLinesOfCommitMap = removedLambdaLinesOfCommits(lambdaLinesInsideEditMap, simplifiedModifiedLambdaList);
        for (SimplifiedModifiedLambda lambda : simplifiedModifiedLambdaList)
        {
            StringBuffer commitDate = new StringBuffer(""), committerId = new StringBuffer(""), committerEmail = new StringBuffer(""), authorId = new StringBuffer(), authorEmail = new StringBuffer();
            int linesDeletedThisCommit = getCommitInfo(lambda, commitDate, committerId, committerEmail, authorId, authorEmail);
            writer.newLine();
            int temp = lambda.filePath.split("/").length;
            int lambdaLines = lambda.endLine - lambda.beginLine + 1;
            double proportion = (double)lambdaLinesInsideEditMap.get(getEditPos(lambda)).size() /
                    (double) (lambda.editEndLine - lambda.editBeginLine + 1);
            String issue = RemovedLambdaFeatureAnalysis.linkToJiraIssue(lambda).equals("FALSE") ?
            RemovedLambdaFeatureAnalysis.msgKeyword(lambda) : RemovedLambdaFeatureAnalysis.linkToJiraIssue(lambda);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(lambda.filePath.getBytes("UTF-8"));
            String diffHash = byte2Hex(messageDigest.digest());
            writer.write(seq + "," + lambda.commitURL.replace("https://github.com/", "").split("/commit")[0]
                    + "," + lambda.filePath + "," + lambda.filePath.split("/")[temp - 1] + ","
                    + df.format((float)(lambdaLinesOfCommitMap.get(lambda.commitURL)) / (float)(linesDeletedThisCommit)) + "," + linesDeletedThisCommit + "," + lambdaLines + ","
                    + "L" + lambda.beginLine + "-" + "L" + lambda.endLine + ","
                    + "L" + lambda.editBeginLine + "-" + "L" + lambda.editEndLine + "," + df.format(proportion) + "," + lambda.javaFileModified
                    + "," + lambda.gitCommand  + "," + commitDate + "," + issue + "," + committerId + "," + committerEmail
                    + "," + authorId + "," + authorEmail + "," + lambda.commitURL + "#diff-" + diffHash + "L" + lambda.beginLine + "," + lambda.commitURL);
            seq += 1;
        }
        writer.flush();
        writer.close();
    }

    static Map<String, Integer> removedLambdaLinesOfCommits(Map<String, Set<Integer>> removedLambdaLinesInsideEditMap,
                                                            List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> lambdaLinesPerCommit = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            lambdaLinesPerCommit.put(lambda.commitURL, 0);
        }

        for (String commitURL : lambdaLinesPerCommit.keySet())
        {
            for (String removedLambdaPos : removedLambdaLinesInsideEditMap.keySet())
            {
                if (removedLambdaPos.startsWith(commitURL))
                {
                    lambdaLinesPerCommit.put(commitURL, lambdaLinesPerCommit.get(commitURL)
                    + removedLambdaLinesInsideEditMap.get(removedLambdaPos).size());
                }
            }
        }
        return lambdaLinesPerCommit;
    }

    static Map<String, Set<Integer>> removedLambdaLinesInsideEdit(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Set<Integer>> lambdaLinesPerEdit = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String editPos = getEditPos(lambda);
            if (!lambdaLinesPerEdit.containsKey(editPos))
            {
                lambdaLinesPerEdit.put(editPos, getLambdaLines(lambda));
            }
            else
            {
                Set<Integer> lambdaLines = new HashSet<>();
                lambdaLines.addAll(lambdaLinesPerEdit.get(editPos));
                lambdaLines.addAll(getLambdaLines(lambda));
                lambdaLinesPerEdit.put(editPos, lambdaLines);
            }
        }
        return lambdaLinesPerEdit;
    }

    static Set<Integer> getLambdaLines(SimplifiedModifiedLambda lambda)
    {
        Set<Integer> lambdaLines = new HashSet<>();
        for (int i = lambda.beginLine; i <= Integer.min(lambda.endLine, lambda.editEndLine); i++)
        {
            lambdaLines.add(i);
        }
        return lambdaLines;
    }

    static String getEditPos(SimplifiedModifiedLambda lambda)
    {
        return lambda.commitURL + "$" + lambda.filePath + "#L" + lambda.editBeginLine + "-" + lambda.editEndLine;
    }

    public static void main(String[] args) throws IOException, GitAPIException, ClassNotFoundException, NoSuchAlgorithmException {
        show();
    }
}