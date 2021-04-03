package com.github.gumtreelambdaanalysis;

import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompareBetweenOS
{
    public static void test() throws IOException {
        String repoPath = "../repos/";
        String repoName = "skywalking";
        String javaPath = "oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/query/TopologyQueryService.java";
        String currentCommitHash = "babc6d293097c1bbdf87b84b87ae0bdaa63f1c02";
        String parentCommitHash = "fd83aa55afa4375db6a14f4046c8df1d1cb129d8";

        try (Repository repo = new RepositoryBuilder().setGitDir(new File(repoPath + repoName + "/.git")).build())
        {
            Git git = new Git(repo);
            try (RevWalk revWalk = new RevWalk(repo))
            {
                RevCommit parentCommit = revWalk.parseCommit(repo.resolve(parentCommitHash));
                RevCommit currentCommit = revWalk.parseCommit(repo.resolve(currentCommitHash));
                String fileBeforeCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, parentCommit.getTree()).getObjectId(0)).getBytes());
                String fileAfterCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, currentCommit.getTree()).getObjectId(0)).getBytes());

                FileWriter oldFile, newFile;

                oldFile = new FileWriter("old-new-file/oldfile.java");
                oldFile.write("");
                oldFile.write(fileBeforeCommit);    //i
                oldFile.flush();
                newFile = new FileWriter("old-new-file/newfile.java");
                newFile.write("");
                newFile.write(fileAfterCommit);     //i + 1
                newFile.flush();


                List<PositionTuple> positionTupleListAfterCommit = new ArrayList<>();
                List<RemainedLambda> remainedLambdaCandidates = new ArrayList<>();
                GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(fileAfterCommit, positionTupleListAfterCommit, true);

                Tree oldFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/oldfile.java")).getRoot();
                Tree newFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/newfile.java")).getRoot();
                //System.out.println(newFileTree.toTreeString());
                Matcher defaultMatcher = Matchers.getInstance().getMatcher();
                MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);
                for (PositionTuple positionTuple : positionTupleListAfterCommit)
                {
                    System.out.println(positionTuple.beginPos + "-" + positionTuple.endPos);
                    System.out.println(positionTuple.node);
                    Tree newLambdaTree = newFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0);
                    System.out.println(newLambdaTree.toTreeString());
                    System.out.println(newLambdaTree.getType());
                    System.out.println(mappings.getSrcForDst(newFileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0)).toTreeString());
                }
                oldFile.close();
                newFile.close();
            }
        }
    }
    public static void main(String[] args) throws IOException {
        test();
    }
}
