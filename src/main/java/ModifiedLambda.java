import com.github.gumtreediff.actions.model.Action;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.*;

public class ModifiedLambda
{
    List<Action> actionList;
    Repository repo;
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
    List<String> actionTypeBag;
    Map<String, Integer> actionTypeMap;
    Set<String> actionTypeSet;


    public ModifiedLambda(Repository repo, RevCommit currentCommit, RevCommit parentCommit, DiffEntry diffEntry, CompilationUnit cu, PositionTuple pos, int nodesNum)
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
    }
}
