import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LambdaNode {
    //this is repo info
    Repository repository;

    //this is commit info
    String commitHash;
    String commitMessage;

    //this is diff info
    DiffEntry diff;
    String filePath;
    String diffUrl;

    //this is action info
    String actionChangeType;
    String actionNode;

    //this is node info
    int position;
    int endposition;

    public LambdaNode(Repository repository, String commitHash, String commitMessage, DiffEntry diff, String filePath, String url, String actionChangeType, String actionNode, int position, int endposition) throws NoSuchAlgorithmException {
        this.repository = repository;

        this.commitHash = commitHash;
        this.commitMessage = commitMessage;
        this.diff = diff;
        this.filePath = filePath;

        assert url.startsWith("https://github.com");
        String repoURL = url.substring(0, url.lastIndexOf(".git"));
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] hash = md5.digest(diff.getOldPath().getBytes());
        BigInteger number = new BigInteger(1, hash);
        StringBuilder hashText = new StringBuilder(number.toString(16));
        while (hashText.length() < 32)
        {
            hashText.insert(0, "0");
        }
        this.diffUrl = repoURL + "/commit/" + commitHash.split(" ")[1] + "#diff-" + hashText;
        this.actionChangeType = actionChangeType;
        this.actionNode = actionNode;
        this.position = position;
        this.endposition = endposition; 
    }

    public void printNodeInfo() {
        //System.out.println("diff_newId: " + diff.getScore() + "\n");
        //目前对diff的文件定位还是有问题
        System.out.println("\n" + "repo: " + repository.toString() + "\n" + "hash: " + commitHash + "\n" + "diff:" + diff.getOldId() + "\n"
                + "path: " + filePath + "\n" + "diffUrl: " + diffUrl + "\n" + "change type: " + actionChangeType + "\n" + "action node: "
                + actionNode +"\n" + "node position: " + position + "\n" + "node end position: " + endposition);
    }
    //private ObjectId
}
