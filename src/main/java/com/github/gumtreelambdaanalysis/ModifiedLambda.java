package com.github.gumtreelambdaanalysis;
import com.github.gumtreediff.actions.model.Action;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.Serializable;
import java.util.*;
class SimplifiedModifiedLambda implements Serializable
{
    String repo;
    String commitURL;
    String filePath;
    String node;
    String parentNode;
    int beginLine;
    int endLine;
    int nodesNum;
    int fileModified;
    String commitMessage;
    String currentCommit;
    String parentCommit;
    String gitCommand;
    int javaFileModified;
    int editBeginLine, editEndLine;
    String lambdaContext;

    public SimplifiedModifiedLambda(ModifiedLambda modifiedLambda)
    {
        this.repo = modifiedLambda.repo.toString();
        this.commitURL = modifiedLambda.commitURL;
        this.filePath = modifiedLambda.diffEntry.getNewPath();
        this.node = modifiedLambda.pos.node.toString();
        this.parentNode = modifiedLambda.pos.node.getParent().toString();
        this.beginLine = modifiedLambda.pos.beginLine;
        this.endLine = modifiedLambda.pos.endLine;
        this.nodesNum = modifiedLambda.nodesNum;
        this.fileModified = modifiedLambda.fileModified;
        this.commitMessage = modifiedLambda.currentCommit.getFullMessage();
        this.currentCommit = modifiedLambda.currentCommit.toString();
        this.parentCommit = modifiedLambda.parentCommit.toString();
        this.gitCommand = "git diff " + modifiedLambda.parentCommit.toString().split(" ")[1] + " "
                + modifiedLambda.currentCommit.toString().split(" ")[1] + " " + modifiedLambda.diffEntry.getNewPath();
        this.javaFileModified = modifiedLambda.javaFileModified;
        this.editBeginLine = modifiedLambda.editBeginLine;
        this.editEndLine = modifiedLambda.editEndLine;
        this.lambdaContext = modifiedLambda.lambdaContext;
    }
}
public class ModifiedLambda
{
    List<Action> actionList;
    Repository repo;
    String commitURL;
    RevCommit currentCommit;
    RevCommit parentCommit;
    DiffEntry diffEntry;
    CompilationUnit cu;
    PositionTuple pos;
    float actionNum;
    int posLength;      //count by position
    int lineLength;     //count by lines
    //int lengthOfActions;
    float proportionOfModPos, proportionOfModNodes;
    float action_avg;
    float actionDepth_avg;
    float actionHeight_avg;
    float actionSize_avg;
    int actionDepth_max, actionHeight_max, actionSize_max;
    boolean oneLineLambda;
    boolean messageRelatedToKeywords;
    int nodesNum;
    int fileModified;
    int javaFileModified;
    int editBeginLine, editEndLine;
    List<String> actionTypeBag;
    Map<String, Integer> actionTypeMap;
    Set<String> actionTypeSet;
    String lambdaContext;

    public ModifiedLambda(Repository repo, RevCommit currentCommit, RevCommit parentCommit, DiffEntry diffEntry, CompilationUnit cu, PositionTuple pos,
                          int nodesNum, String url, int fileModified, int javaFileModified, int editBeginLine, int editEndLine, String lambdaContext)
    {
        this.repo = repo;
        this.currentCommit = currentCommit;
        this.parentCommit = parentCommit;
        this.diffEntry = diffEntry;
        this.actionList = new ArrayList<>();
        this.actionTypeBag = new ArrayList<>();
        this.actionTypeMap = new HashMap<>();
        this.cu = cu;
        this.pos = pos;
        this.nodesNum = nodesNum;
        this.commitURL = url + "/commit/" + currentCommit.toString().split(" ")[1];
        this.fileModified = fileModified;
        this.javaFileModified = javaFileModified;
        this.editBeginLine = editBeginLine;
        this.editEndLine = editEndLine;
        this.lambdaContext = lambdaContext;
    }

    @Deprecated
    public ModifiedLambda(Repository repo, RevCommit currentCommit, RevCommit parentCommit, DiffEntry diffEntry, CompilationUnit cu, PositionTuple pos,
                          int nodesNum, String url, int fileModified)
    {
        this.repo = repo;
        this.currentCommit = currentCommit;
        this.parentCommit = parentCommit;
        this.diffEntry = diffEntry;
        this.actionList = new ArrayList<>();
        this.actionTypeBag = new ArrayList<>();
        this.actionTypeMap = new HashMap<>();
        this.cu = cu;
        this.pos = pos;
        this.nodesNum = nodesNum;
        this.commitURL = url + "/commit/" + currentCommit.toString().split(" ")[1];
        this.fileModified = fileModified;
    }
}
