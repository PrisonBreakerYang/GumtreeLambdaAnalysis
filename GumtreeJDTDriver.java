import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GumtreeJDTDriver
{
    ASTParser parser;
    CompilationUnit cu;
    public List<PositionTuple> positionTupleList;
    public GumtreeJDTDriver(String sourceCode)
    {
        //for commits_after
        parser = ASTParser.newParser(AST.JLS14);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        Map<String, String> options = JavaCore.getDefaultOptions();
        // support java 14
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_14);
        parser.setCompilerOptions(options);
        setSourceCode(sourceCode);
    }

    public GumtreeJDTDriver(String sourceCode, List<PositionTuple> positionTupleList)
    {
        this.positionTupleList = positionTupleList;
        parser = ASTParser.newParser(AST.JLS14);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        Map<String, String> options = JavaCore.getDefaultOptions();
        // support java 14
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_14);
        parser.setCompilerOptions(options);
        setSourceCode(sourceCode);

        LambdaFinder lambdaFinder = new LambdaFinder(cu, 0, Integer.MAX_VALUE);
        cu.accept(lambdaFinder);

    }

    public void setSourceCode(String sourceCode)
    {
        parser.setSource(sourceCode.toCharArray());
        cu = (CompilationUnit) parser.createAST(null);
    }

    private class LambdaFinder extends ASTVisitor
    {
        // is code snippet which overlaps [startLine, endLine] related to lambda expression
        public boolean isRelatedToLambda = false;
        private final CompilationUnit cu;
        private final int startLine, endLine;
        public List<PositionTuple> positionTupleList;

        private LambdaFinder(CompilationUnit compilationUnit, int startLine, int endLine)
        {
            cu = compilationUnit;
            this.startLine = startLine;
            this.endLine = endLine;

        }

        public boolean visit(LambdaExpression node)
        {
            ASTNode parent = node.getParent();
            while (!(parent instanceof Statement || parent instanceof FieldDeclaration || parent instanceof VariableDeclaration))
            {
                // According to oracle's document, a lambda expression can only occurs in a
                // program in someplace other than assignment context, an invocation context,
                // or a casting context
                if (parent.getParent() == null)
                {
                    break;
                }
                parent = parent.getParent();
            }

            int nodeStartLine = cu.getLineNumber(parent.getStartPosition()), nodeEndPos =
                    parent.getStartPosition() + parent.getLength(), nodeEndLine = cu.getLineNumber(nodeEndPos);
            if (nodeStartLine > endLine || nodeEndLine < startLine)
            {
                // there is no need to visit the children
                return false;
            }
            isRelatedToLambda = true;
            //此处不确定要不要取上一层
            PositionTuple newPositionTuple = new PositionTuple(parent.getStartPosition(), parent.getStartPosition() + parent.getLength(),
                    cu.getLineNumber(parent.getStartPosition()), cu.getLineNumber(parent.getStartPosition() + parent.getLength()));
            //PositionTuple newPositionTuple = new PositionTuple(node.getStartPosition(), node.getStartPosition() + node.getLength());
            GumtreeJDTDriver.this.positionTupleList.add(newPositionTuple);

            return true;
        }
    }

    public static void main(String[] args) throws IOException
    {
        Path path = Paths.get("C:\\Users\\28902\\gumtree\\dist\\build\\install\\gumtree\\bin\\lc01.java");
        String content = Files.readString(path);
        List<PositionTuple> positionTupleList= new ArrayList<>();
        GumtreeJDTDriver jdtDriver = new GumtreeJDTDriver(content, positionTupleList);


        Run.initGenerators();
        Tree oldFileTree = TreeGenerators.getInstance().getTree("C:\\Users\\28902\\gumtree\\dist\\build\\install\\gumtree\\bin\\lc01.java").getRoot();
        Tree newFileTree = TreeGenerators.getInstance().getTree("C:\\Users\\28902\\gumtree\\dist\\build\\install\\gumtree\\bin\\lc02.java").getRoot();
        Matcher defaultMatcher = Matchers.getInstance().getMatcher();
        MappingStore mappings = defaultMatcher.match(oldFileTree, newFileTree);

        //Tree lambdaNewTree = mappings.getDstForSrc(oldFileTree.getTreesBetweenPositions(299, 309).isEmpty());
        //System.out.println(oldFileTree.getTreesBetweenPositions(299, 309).isEmpty());
        //System.out.println("start: " + lambdaNewTree.getPos());
        //System.out.println("end: " + lambdaNewTree.getEndPos());
        //System.out.println(mappings);
        EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator();
        EditScript actions = editScriptGenerator.computeActions(mappings);
//        for (Action action : actions) {
//            System.out.println(action.getName());
//            System.out.println(action.getNode());
//        }
    }
}
