import com.github.gumtreediff.actions.model.Action;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;

public class ModifiedLambda
{
    List<Action> actionList;
    Repository repo;
    RevCommit currentCommit;
    RevCommit parentCommit;
    DiffEntry diffEntry;
    CompilationUnit cu;
    PositionTuple pos;


    public ModifiedLambda(Repository repo, RevCommit currentCommit, RevCommit parentCommit, DiffEntry diffEntry, CompilationUnit cu, PositionTuple pos)
    {
        this.repo = repo;
        this.currentCommit = currentCommit;
        this.parentCommit = parentCommit;
        this.diffEntry = diffEntry;
        this.actionList = new ArrayList<>();
        this.cu = cu;
        this.pos = pos;
    }
}
