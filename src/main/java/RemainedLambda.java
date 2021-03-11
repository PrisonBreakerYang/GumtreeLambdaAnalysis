import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
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
    boolean introducedWhenTheFileCreated;

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
    }

    public static void deserializeGoodLambdas()
    {
        String[] projectList_test = {"apache skywalking"};
        //String[] projectList = lines.toArray(new String[0]);
        String[] projectList = projectList_test;
        String writePath = "statistics/" + "lambda-right-use/test/";
        String serPath = "ser/good-lambdas/test/";
        File writeFile = new File( writePath + Arrays.toString(projectList) + "compareNonebyone-new-new.csv");
        List<RemainedLambda> remainedLambdaList = new ArrayList<>();
        try {
            String[] readPath = {serPath + "03-11"};
            for (String path : readPath)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (!serFile.toString().endsWith("apache skywalkingN=10.ser")) continue;
                    FileInputStream fileIn = new FileInputStream(serFile);
                    ObjectInputStream in = new ObjectInputStream(fileIn);
                    RemainedLambda[] remainedLambdaArray = (RemainedLambda[]) in.readObject();
                    remainedLambdaList.addAll(new ArrayList<>(Arrays.asList(remainedLambdaArray)));
                }
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(writeFile));
            writer.write("seq,project,file path,file name,lambda line number,introduced when file created,introduced url,N later url,git diff,git log,commit link");
            int seq = 1;
            for (RemainedLambda remainedLambda : remainedLambdaList)
            {
//                System.out.println("################");
//                System.out.println(remainedLambda.lambdaContext);
                writer.newLine();
                int temp = remainedLambda.filePath.split("/").length;
                String projectName = remainedLambda.commitURL.replace("https://github.com/", "").split("/commit")[0];
                writer.write(seq + "," + projectName
                + "," + remainedLambda.filePath + "," + remainedLambda.filePath.split("/")[temp - 1] + ","
                + "L" + remainedLambda.beginLine + "-" + "L" + remainedLambda.endLine + "," + remainedLambda.introducedWhenTheFileCreated + ","
                + "https://github.com/" + projectName + "/blob/" + remainedLambda.introducedCommitHash + "/" + remainedLambda.filePath + ","
                + "https://github.com/" + projectName + "/blob/" + remainedLambda.NLaterCommitHash + "/" + remainedLambda.filePath + ","
                + "git diff " + remainedLambda.introducedCommitHash + " " + remainedLambda.NLaterCommitHash + " " + remainedLambda.filePath + ","
                + remainedLambda.gitCommand + "," + remainedLambda.commitURL);
                seq += 1;
            }
            writer.flush();
            writer.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        RemainedLambda.deserializeGoodLambdas();
    }
}
