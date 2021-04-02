package com.github.gumtreelambdaanalysis;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
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

                List<PositionTuple> positionTupleListAfterCommit = new ArrayList<>();
                List<RemainedLambda> remainedLambdaCandidates = new ArrayList<>();
                GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(fileAfterCommit, positionTupleListAfterCommit, true);
                System.out.println(positionTupleListAfterCommit.size());
            }
        }
    }
    public static void main(String[] args) throws IOException {
        test();
    }
}
