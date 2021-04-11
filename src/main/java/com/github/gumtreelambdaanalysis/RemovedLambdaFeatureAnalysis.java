package com.github.gumtreelambdaanalysis;

import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.gumtreelambdaanalysis.BadLambdaFinder.getMergedEdits;

class AuthorInfo
{
    String createAuthor, removeAuthor;
    String createLambdaLine, removeLambdaLine;
    Integer numOfCommit;
    public AuthorInfo(String createAuthor, String removeAuthor)
    {
        this.createAuthor = createAuthor;
        this.removeAuthor = removeAuthor;
    }
    public void setCreateLambdaLine(String createLambdaLine)
    {
        this.createLambdaLine = createLambdaLine;
    }
    public void setRemoveLambdaLine(String removeLambdaLine)
    {
        this.removeLambdaLine = removeLambdaLine;
    }
    public void setNumOfCommit(Integer numOfCommit)
    {
        this.numOfCommit = numOfCommit;
    }
}

public class RemovedLambdaFeatureAnalysis
{
    /*
    We are going to explore the following features:
    1. context code word cloud (including context and lambda body)
    2. context comments word cloud
    3. caller method word cloud (for lambdas in method invocation)
    4. call site function name word cloud
    5. call site class name word cloud
    6. implemented functional interface statistics
    7. AST parent node statistics                                                               finished
    8. whether in production code or in test code                                               finished
    9. parameters number                                                                        finished
    10. length                                                                                  finished
    11. stateful or stateless
    12. implicit or explicit
    13. call site class serializable or not (file before commit and after commit version)
     */

    static void contextCodeAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            System.out.println("#################################");
            System.out.println();
        }
    }

    static void addStringToMap(Map<String, Integer> map, String str)
    {
        if (!map.containsKey(str))
        {
            map.put(str, 1);
        }
        else map.put(str, map.get(str) + 1);
    }

    static void addIntToMap(Map<Integer, Integer> map, Integer integer)
    {
        if (!map.containsKey(integer))
        {
            map.put(integer, 1);
        }
        else map.put(integer, map.get(integer) + 1);
    }
    static void bodyAnalysis(List<SimplifiedModifiedLambda> lambdas) throws IOException {
        Map<Integer, Integer> bodyHeightFreq = new HashMap<>(), bodySizeFreq = new HashMap<>(), maxSubTreeFreq = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode body = lambdaExpression.getBody();
                    FileWriter writer = new FileWriter("old-new-file/bodyAnalysis.java");
                    writer.write(fileBeforeCommit);
                    writer.flush();
                    writer.close();
                    Tree fileTree = new JdtTreeGenerator().generateFrom().file("old-new-file/bodyAnalysis.java").getRoot();
                    //Tree lambdaBody = fileTree.getTreesBetweenPositions(positionTuple.beginPos, positionTuple.endPos).get(0);
                    Tree lambdaBody = fileTree.getTreesBetweenPositions(body.getStartPosition(), body.getStartPosition() + body.getLength()).get(0);
                    int maxSubTreeSize = 0;
                    for (Tree subTree : lambdaBody.getChildren())
                    {
                        maxSubTreeSize = Integer.max(maxSubTreeSize, subTree.getMetrics().size);
                    }
                    addIntToMap(bodyHeightFreq, lambdaBody.getMetrics().height);
                    addIntToMap(bodySizeFreq, lambdaBody.getMetrics().size);
                    addIntToMap(maxSubTreeFreq, maxSubTreeSize);
                    break;
                }
            }
        }
        System.out.println("#######################################");
        bodyHeightFreq.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println("#######################################");
        bodySizeFreq.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println("#######################################");
        maxSubTreeFreq.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
    }

    static void parameterAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int explicitLambda = 0, implicitLambda = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            boolean found = false;
            boolean explicit = false;
            boolean empty = false;
            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    found = true;
                    LambdaExpression lambdaExpression = positionTuple.node;
                    List paras = lambdaExpression.parameters();
                    if (paras.size() == 0)
                    {
                        empty = true;
                        break;
                    }
                    List<String> paraString = new ArrayList<>();
                    for (Object para : paras)
                    {
                        paraString.add(para.toString());
                    }
                    for (String para : paraString)
                    {
                        if (para.contains(" "))
                        {
                            explicit = true;
                            explicitLambda += 1;
                            break;
                        }
                    }
                    if (!explicit)
                    {
                        //System.out.println(paraString);
                        implicitLambda += 1;
                    }
                    break;
                }
            }
            if (!found) System.out.println("error! not found!");
        }
        System.out.println(lambdas.size());
        System.out.println("explicit lambda - implicit lambda: " + explicitLambda + " - " + implicitLambda);
    }

    static void locationAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int count = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            if (lambda.filePath.toLowerCase().contains("test"))
            {
                //printLambdaInfo(lambda);
                count ++;
            }
        }
        System.out.println(count);
        //System.out.println((double)count/lambdas.size());
    }

    static void codeMigrationAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Map<String, Integer>> map = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            if (lambda.commitMessage.toLowerCase().contains("this reverts commit")) continue;
            if (!map.containsKey(lambda.commitURL))
            {
                Map<String, Integer> tempMap = new HashMap<>();
                tempMap.put(lambda.filePath, 1);
                map.put(lambda.commitURL, tempMap);
            }
            else
            {
                Map<String, Integer> tempMap = map.get(lambda.commitURL);
                if (!tempMap.containsKey(lambda.filePath))
                {
                    tempMap.put(lambda.filePath, 1);
                }
                else
                {
                    tempMap.put(lambda.filePath, tempMap.get(lambda.filePath) + 1);
                }
                map.put(lambda.commitURL, tempMap);
            }
        }

        printLambdaMap(map, "lambda");
    }

    static void printLambdaMap(Map<String, Map<String, Integer>> map, String countByFilesOrLambdas)
    {
        if (countByFilesOrLambdas.equals("file"))
        {
            Map<String, Integer> countByFiles = new HashMap<>();
            for (String url : map.keySet())
            {
                countByFiles.put(url, map.get(url).keySet().size());
            }
            countByFiles.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(stringIntegerEntry -> {
                System.out.println(stringIntegerEntry.getKey() + " - " + stringIntegerEntry.getValue());
            });
        }

        if (countByFilesOrLambdas.equals("lambda"))
        {
            Map<String, Integer> countByLambdas = new HashMap<>();
            for (String url : map.keySet())
            {
                int count = 0;
                for (Map.Entry<String, Integer> entry : map.get(url).entrySet())
                {
                    count += entry.getValue();
                }
                countByLambdas.put(url, count);
            }
            countByLambdas.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(stringIntegerEntry -> {
                System.out.println(stringIntegerEntry.getKey() + " - " + stringIntegerEntry.getValue());
            });
        }
    }

    static void classNameAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> classNameFreq = new HashMap<>();
        int serialInterface = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            boolean found = false;
            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    found = true;
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof TypeDeclaration) && !(parentNode instanceof EnumDeclaration))
                    {
                        if (lambdaExpression.getRoot().equals(parentNode))
                        {
                            System.err.println("type declaration not found");
                            printLambdaInfo(lambda);
                            break;
                        }
                        parentNode = parentNode.getParent();
                    }
                    assert parentNode instanceof TypeDeclaration || parentNode instanceof EnumDeclaration;
                    if (parentNode instanceof TypeDeclaration)
                    {
                        TypeDeclaration typeDeclaration = (TypeDeclaration)parentNode;
                        addStringToMap(classNameFreq, typeDeclaration.getName().toString());
                        for (Object superInterface : typeDeclaration.superInterfaceTypes())
                        {
                            if (superInterface.toString().equals("Serializable"))
                            {
                                serialInterface ++;
                                break;
                            }
                        }
                    }
                    if (parentNode instanceof EnumDeclaration)
                    {
                        EnumDeclaration enumDeclaration = (EnumDeclaration) parentNode;
                        addStringToMap(classNameFreq, enumDeclaration.getName().toString());
                        for (Object superInterface : enumDeclaration.superInterfaceTypes())
                        {
                            if (superInterface.toString().equals("Serializable"))
                            {
                                serialInterface ++;
                                break;
                            }
                        }
                    }
                    break;
                }
            }
            if (!found) System.out.println("error! not found!");
        }
        classNameFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println("number of classes implemented Serializable: " + serialInterface + "/" + lambdas.size());
    }

    static void functionNameAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> functionNameFreq = new HashMap<>();
        Map<String, Integer> locationOfLambda = new HashMap<>();
//        locationOfLambda.put("MethodDeclaration", 0);
//        locationOfLambda.put("FieldDeclaration", 0);
//        locationOfLambda.put("Initializer", 0);
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            boolean found = false;
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    found = true;
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof MethodDeclaration) && !(parentNode instanceof FieldDeclaration) &&
                           !(parentNode instanceof Initializer)) {
                        if (parentNode.equals(lambdaExpression.getRoot())) break;
                        parentNode = parentNode.getParent();
                    }
                    addStringToMap(locationOfLambda, ASTNode.nodeClassForType(parentNode.getNodeType()).getName());
                    break;
//                    if (parentNode instanceof MethodDeclaration) {
//                        //printLambdaInfo(lambda);
//                        MethodDeclaration methodDeclaration = (MethodDeclaration)parentNode;
//                        if (!functionNameFreq.containsKey(methodDeclaration.getName().toString()))
//                        {
//                            functionNameFreq.put(methodDeclaration.getName().toString(), 1);
//                        }
//                        else
//                        {
//                            functionNameFreq.put(methodDeclaration.getName().toString(),
//                            functionNameFreq.get(methodDeclaration.getName().toString()) + 1);
//                        }
//                        //System.out.println(methodDeclaration.getName());
//                    }
                }
            }
        }
        locationOfLambda.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
        //System.out.println("number of lambdas in functions: " + locationOfLambda.size());
    }

    @Deprecated
    static void stateAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        //hard to implement!
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            boolean found = false;
            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    found = true;
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof TypeDeclaration) && !(parentNode instanceof EnumDeclaration))
                    {
                        parentNode = parentNode.getParent();
                    }
                    parentNode.accept(new ASTVisitor() {
                        @Override
                        public boolean visit(VariableDeclarationExpression node) {
                            System.out.println(node);
                            return true;
                        }
                    });
                    ASTNode body = lambdaExpression.getBody();
                    body.accept(new ASTVisitor() {
                        @Override
                        public boolean visit(MethodInvocation node) {
                            System.out.println(node.getExpression());
                            return true;
                        }
                    });
                    break;
                }
            }
            if (!found) System.err.println("not found!");
        }
    }

    static String getSrcPath(String completePath)
    {
        if (!completePath.contains("src")) {
            System.err.println("contain no src file in this path: " + completePath);
            return null;
        }
        return completePath.substring(0, completePath.lastIndexOf("src")) + "src";
    }

    static void functionalInterfaceAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> functionalInterfaceFreq = new HashMap<>();
        final int[] resolved = {0};
        final int[] built_in = {0};
        final int[] self_defined = {0};
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = getSourceCode(lambda);
            ASTParser parser = ASTParser.newParser(AST.JLS14);
            parser.setResolveBindings(true); // we need bindings later on
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setBindingsRecovery(true);
            String unitName = lambda.commitURL + ":" + lambda.filePath;
            parser.setUnitName(unitName);
            String repoName = lambda.commitURL.replace("https://github.com/", "").split("apache/")[1].split("/commit")[0];
            //String[] sources = {"../repos/" + repoName + "/" + getSrcPath(lambda.filePath)};
            String[] sources = {""};
            String[] classpath = {""};
            parser.setEnvironment(classpath, sources, new String[]{"UTF-8"}, true);
            Map<String, String> options = JavaCore.getDefaultOptions();
            options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_14);
            parser.setCompilerOptions(options);
            assert fileBeforeCommit != null;
            parser.setSource(fileBeforeCommit.toCharArray());
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    ASTNode targetNode = positionTuple.node;
                    cu.accept(new ASTVisitor() {
                        @Override
                        public boolean visit(LambdaExpression node) {
                            try {
                                if (node.getStartPosition() == targetNode.getStartPosition() &&
                                        node.toString().equals(targetNode.toString())) {
                                    ITypeBinding typeBinding = node.resolveTypeBinding();
                                    IMethodBinding methodBinding = node.resolveMethodBinding();

                                    if (node.getParent() instanceof MethodInvocation && ((MethodInvocation) node.getParent()).resolveMethodBinding() != null
                                        //&& node.resolveTypeBinding() != null
                                    ) {
                                        resolved[0]++;
                                        int seq = -1;
                                        for (int i = 0; i < ((MethodInvocation) node.getParent()).arguments().size(); i++) {
                                            if (((MethodInvocation) node.getParent()).arguments().get(i).toString().equals(node.toString())) {
                                                seq = i;
                                                break;
                                            }
                                        }
                                        if (((MethodInvocation) node.getParent()).resolveMethodBinding().getParameterTypes().length != ((MethodInvocation) node.getParent()).arguments().size()) {
                                            return false;
                                        } else {
                                            ITypeBinding binding = ((MethodInvocation) node.getParent()).resolveMethodBinding().getParameterTypes()[seq];
                                            if (binding.getBinaryName().equals("java.lang.String") || binding.getBinaryName().equals("java.lang.Object")) {
                                                return false;
                                            }
                                            addStringToMap(functionalInterfaceFreq, binding.getBinaryName());
                                            if (binding.getBinaryName().startsWith("java")) built_in[0]++;
                                            else self_defined[0]++;
                                        }
                                    }
                                }
                                return true;
                            } catch (IndexOutOfBoundsException e)
                            {
                                return false;
                            }
                        }
                    });
                    break;
                }
            }
        }
        System.out.println("resolved functional interface: " + resolved[0]);
        int map_count = 0;
        for (String name : functionalInterfaceFreq.keySet())
        {
            map_count += functionalInterfaceFreq.get(name);
        }
        System.out.println("map size: " + map_count);
        functionalInterfaceFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println("built-in:" + built_in[0]);
        System.out.println("self-defined: " + self_defined[0]);
    }

    static void methodInvocationAnalysis(List<SimplifiedModifiedLambda> lambdas) {
        Map<String, Integer> methodFullNameFreq = new HashMap<>();
        methodFullNameFreq.put("self defined", 0);
        int lambdaCount = 0;
        int methodInvocationCount = 0;
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = getSourceCode(lambda);
            ASTParser parser = ASTParser.newParser(AST.JLS14);
            parser.setResolveBindings(true); // we need bindings later on
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setBindingsRecovery(true);
            String unitName = lambda.commitURL + ":" + lambda.filePath;
            parser.setUnitName(unitName);
            String repoName = lambda.commitURL.replace("https://github.com/", "").split("apache/")[1].split("/commit")[0];
            //String[] sources = {"../repos/" + repoName + "/" + getSrcPath(lambda.filePath)};
            String[] sources = {""};
            String[] classpath = {""};
            parser.setEnvironment(classpath, sources, new String[]{"UTF-8"}, true);
            Map<String, String> options = JavaCore.getDefaultOptions();
            options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_14);
            parser.setCompilerOptions(options);
            //parser.setCompilerOptions(JavaCore.getOptions());
            assert fileBeforeCommit != null;
            parser.setSource(fileBeforeCommit.toCharArray());
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    ASTNode parentNode = positionTuple.node.getParent();
                    lambdaCount ++;
                    if (parentNode instanceof MethodInvocation)
                    {
                        methodInvocationCount ++;
                        cu.accept(new ASTVisitor() {
                            @Override
                            public boolean visit(LambdaExpression node) {
//                                if (node.getParent().getStartPosition() == parentNode.getStartPosition() &&
//                                        node.getParent().toString().equals(parentNode.toString()))
                                if (node.getStartPosition() == positionTuple.node.getStartPosition())
                                //if ()
                                {
                                    MethodInvocation methodInvocation = (MethodInvocation)(node.getParent());
                                    IMethodBinding binding = methodInvocation.resolveMethodBinding();
                                    if (binding == null || !binding.getDeclaringClass().getPackage().toString().split(" ")[1].startsWith("java"))
                                    {
                                        methodFullNameFreq.put("self defined", methodFullNameFreq.get("self defined") + 1);
                                    }
                                    else {
                                        String pack = binding.getDeclaringClass().getPackage().toString().split(" ")[1];
//                                        if (!pack.startsWith("java"))
//                                        {
//                                            methodFullNameFreq.put("self defined", methodFullNameFreq.get("self defined") + 1);
//                                        }
//                                        else {
                                            String className = methodInvocation.resolveMethodBinding().getDeclaringClass().getName();
                                            //className = className.replaceAll("<(.*?)>", "").replaceAll(">", "");
                                            String methodFullName = pack + "." + className + "." + methodInvocation.resolveMethodBinding();
                                            //String methodFullName = methodInvocation.resolveTypeBinding().getBinaryName();
                                            System.out.println(methodFullName);
                                            addStringToMap(methodFullNameFreq, methodFullName);
//                                        }
                                    }
                                }
                                return true;
                            }
                        });
                    }
                    break;
                }
            }
        }
        int built_in = 0;
        for (String methodName : methodFullNameFreq.keySet())
        {
            if (!methodName.equals("self defined"))
            {
                built_in += methodFullNameFreq.get(methodName);
            }
        }
        methodFullNameFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println("built-in: " + built_in);
        System.out.println("self-defined: " + methodFullNameFreq.get("self defined"));
        System.out.println("lambda count: " + lambdaCount);
        System.out.println("method invocation count: " + methodInvocationCount);
    }

    static void parentNodeTypeAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> parentTypeFreq = new HashMap<>();
        Set<String> parentNodeTypes = new HashSet<>();
        Map<String, Integer> methodInvocationFreq = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            boolean found = false;
            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    found = true;
//                    System.out.println("+++++++++++++++++++++");
                    ASTNode parentNode = positionTuple.node.getParent();
                    //System.out.println(ASTNode.nodeClassForType(parentNode.getNodeType()).getSimpleName());
                    String parentNodeName = ASTNode.nodeClassForType(parentNode.getNodeType()).getSimpleName();

                    if (!parentNodeTypes.contains(parentNodeName))
                    {
                        parentNodeTypes.add(parentNodeName);
                        parentTypeFreq.put(parentNodeName, 1);
                    }
                    else
                    {
                        parentTypeFreq.put(parentNodeName, parentTypeFreq.get(parentNodeName) + 1);
                    }

                    if (parentNodeName.equals("MethodInvocation"))
                    {
                        MethodInvocation methodInvocation = (MethodInvocation) parentNode;
                        if (!methodInvocationFreq.containsKey(methodInvocation.getName().toString()))
                        {
                            methodInvocationFreq.put(methodInvocation.getName().toString(), 1);
                        }
                        else methodInvocationFreq.put(methodInvocation.getName().toString(), methodInvocationFreq.get(methodInvocation.getName().toString()) + 1);
                    }
                    break;
                }
            }
            if (!found) System.err.println("line 585: error!");
        }
        parentTypeFreq.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(System.out::println);
        System.out.println("++++++++++++++++++++++++++++++++");
        methodInvocationFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);

    }

    static void paraNumStatistics(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<Integer, Integer> paraNumMap = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            boolean found = false;
            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    found = true;
                    LambdaExpression lambdaNode = positionTuple.node;
                    addIntToMap(paraNumMap, lambdaNode.parameters().size());
                    break;
                }
            }
        }
        paraNumMap.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
    }

    static void lineNumStatistics(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<Integer, Integer> lineNumMap = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            int lineNum = lambda.endLine - lambda.beginLine + 1;
            addIntToMap(lineNumMap, lineNum);
        }
        lineNumMap.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
    }

    static String getSourceCode(SimplifiedModifiedLambda lambda)
    {
        String repoName = lambda.commitURL.replace("https://github.com/", "").split("apache/")[1].split("/commit")[0];
        //System.out.println(repoName);
        String repoPath = "../repos/";
        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(repoPath + repoName + "/.git")).build()) {
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit parentCommit = revWalk.parseCommit(ObjectId.fromString(lambda.parentCommit.split(" ")[1]));
                return new String(repository.open(TreeWalk.forPath(repository, lambda.filePath,
                        parentCommit.getTree()).getObjectId(0)).getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.err.println("error!");
        return null;
    }

    static void printLambdaInfo(SimplifiedModifiedLambda lambda)
    {
        System.out.println("######################");
        System.out.println(lambda.commitURL);
        System.out.println(lambda.filePath);
        System.out.println(lambda.lambdaContext);
    }

    static boolean lambdaInDeletedEdit(SimplifiedModifiedLambda lambda)
    {
        String repoName = lambda.commitURL.replace("https://github.com/", "").split("apache/")[1].split("/commit")[0];
        String repoPath = "../repos/";
        //System.out.println("heoo");
        double threshold = 0.5;
        try (Repository repo = new FileRepositoryBuilder().setGitDir(new File(repoPath + repoName + "/.git")).build()) {
            try (RevWalk revWalk = new RevWalk(repo)) {
                RevCommit currentCommit = revWalk.parseCommit(ObjectId.fromString(lambda.currentCommit.split(" ")[1]));
                assert currentCommit.getParentCount() == 1;
                RevCommit parentCommit = currentCommit.getParent(0);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                DiffFormatter formatter = new DiffFormatter(outputStream);
                formatter.setRepository(repo);
                List<DiffEntry> diffs = formatter.scan(parentCommit, currentCommit);
                DiffEntry diff = null;
                for (DiffEntry tempDiff : diffs)
                {
                    if (tempDiff.getNewPath().equals(lambda.filePath))
                    {
                        diff = tempDiff;
                        break;
                    }
                }
                assert diff != null;
                List<Edit> editsDeletedOrReplaced = new ArrayList<>();
                List<Edit> mergedEdits;
                FileHeader fileHeader = formatter.toFileHeader(diff);
                for (HunkHeader hunk : fileHeader.getHunks())
                {
                    for (Edit edit : hunk.toEditList())
                    {
                        if (edit.getType() == Edit.Type.DELETE || edit.getType() == Edit.Type.REPLACE)
                        {
                            editsDeletedOrReplaced.add(edit);
                        }
                    }
                }
                assert !editsDeletedOrReplaced.isEmpty();
                mergedEdits = getMergedEdits(editsDeletedOrReplaced);
                if (mergedEdits.size() != editsDeletedOrReplaced.size()) System.out.println("merged edit!");
                //printLambdaInfo(lambda);
                boolean found = false;
                for (Edit edit : mergedEdits)
                {
                    //if (edit.getBeginA() + 1 == lambda.editBeginLine && edit.getEndA() == lambda.editEndLine)
                    if (lambda.beginLine >= edit.getBeginA() + 1 && lambda.beginLine <= edit.getEndA())
                    {
                        //found the matched edit
                        found = true;
                        //System.err.println("found matched!");
                        lambda.editBeginLine = edit.getBeginA() + 1;
                        lambda.editEndLine = edit.getEndA();
                        if (edit.getType() == Edit.Type.REPLACE)
                        {
                            //return proportion < threshold;
                            return false;
                        }
                        else if (edit.getType() == Edit.Type.DELETE)
                        {
                            return true;
                        }
                        else System.err.println("not a replace or delete!");
                        break;
                    }
                }
                if (!found) System.err.println("not found a matched edit!");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void filterLambdasInDeleteEdit()
    {
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>();
        List<SimplifiedModifiedLambda> lambdasNotInDeletedEdit = new ArrayList<>();
        try {
            String serPath = "ser/bad-lambdas/test";
            //String[] paths = {serPath + "\\03-15", serPath + "\\03-16", serPath + "\\03-17", serPath + "\\03-18"};
            String[] paths = {serPath + "/04-05"};
            for (String path : paths)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (serFile.toString().endsWith(".ser"))
                    {
                        FileInputStream fileIn = new FileInputStream(serFile);
                        ObjectInputStream in = new ObjectInputStream(fileIn);
                        SimplifiedModifiedLambda[] simplifiedModifiedLambdas = (SimplifiedModifiedLambda[]) in.readObject();
                        simplifiedModifiedLambdaList.addAll(new ArrayList<>(Arrays.asList(simplifiedModifiedLambdas)));
                    }
                }
            }

            for (SimplifiedModifiedLambda lambda : simplifiedModifiedLambdaList)
            {
                if (!lambdaInDeletedEdit(lambda)) lambdasNotInDeletedEdit.add(lambda);
            }

            SimplifiedModifiedLambda[] lambdasNotInDeletedEditArray = new SimplifiedModifiedLambda[lambdasNotInDeletedEdit.size()];
            lambdasNotInDeletedEdit.toArray(lambdasNotInDeletedEditArray);
            FileOutputStream fileOut = new FileOutputStream("ser/bad-lambdas/" + "51repos-filtered-without-edit-limit.ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(lambdasNotInDeletedEditArray);
            serOut.close();
            fileOut.close();
            //System.out.println(lambdasNotInDeletedEdit.size());

            //Test.toCsv(lambdasNotInDeletedEdit, new File("statistics/removed-lambdas-list/data-of-51repos-filtered-new.csv"));

        }catch (FileNotFoundException e)
        {
            System.err.println("can't find the file");
            e.printStackTrace();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static String linkToJiraIssue(SimplifiedModifiedLambda lambda)
    {
        String msg = lambda.commitMessage.toUpperCase().strip();
        String repoName = lambda.commitURL.split("apache/")[1].split("/commit")[0].toUpperCase();
        try {
            if (msg.startsWith(repoName)) {
                String formatted = msg.split(" ")[0].split(":")[0].split("\\.")[0].split(",")[0]
                        .split(";")[0].split("\n")[0].split("/")[0].replace("_", "-");

                if (!formatted.contains("-")) return "FALSE";
                if (!StringUtils.isNumeric(formatted.split("-")[1])) return "FALSE";
                if (!formatted.split("-")[0].equals(repoName) || !StringUtils.isNumeric(formatted.split("-")[1])) {
                    System.out.println("################");
                    System.out.println(formatted);
                    System.out.println(lambda.commitURL);
                    System.out.println(lambda.commitMessage);
                }
                return "https://issues.apache.org/jira/browse/" + formatted;
            }
            if (msg.startsWith("[" + repoName))
            {
                String formatted = msg.substring(1, msg.indexOf("]")).split(",")[0];
                if (!formatted.contains("-")) return "FALSE";
                if (!StringUtils.isNumeric(formatted.split("-")[1])) return "FALSE";
                if (!formatted.split("-")[0].equals(repoName) || !StringUtils.isNumeric(formatted.split("-")[1])) {
                    System.out.println("################");
                    System.out.println(formatted);
                    System.out.println(lambda.commitURL);
                    System.out.println(lambda.commitMessage);
                }
                return "https://issues.apache.org/jira/browse/" + formatted;
            }

            Pattern r = Pattern.compile("[A-Z]+-\\d+");
            Matcher m = r.matcher(msg);
            if (m.find() && m.start() < 2)
            {
                return "https://issues.apache.org/jira/browse/" + m.group(0);
            }
            //System.out.println(msg.substring(0, Integer.min(15, msg.length())));

        }catch (ArrayIndexOutOfBoundsException e)
        {
            System.err.println("ERROR: " + lambda.commitURL);
        }
        return "FALSE";
//        if (msg.startsWith("[" + repoName.toUpperCase()))

    }

    public static String msgKeyword(SimplifiedModifiedLambda lambda)
    {
        String rawMsg = lambda.commitMessage.toLowerCase().strip();
        String stemmedMsg = CommitMsgAnalysis.msgProcess(rawMsg);
        String[] keywords = {"lambda", "lamda", "lamba", "bug", "fix", "issue", "problem", "abuse", "error", "performance", "optimize", "efficiency", "simplify"};
        String[] stemmedKeywords = {"issu", "perf", "perform", "optim", "effici", "simplifi"};

        for (String keyword : keywords)
        {
            if (rawMsg.contains(keyword)) return keyword;
        }
        for (String stemmedKeyword : stemmedKeywords)
        {
            for (String token : stemmedMsg.split(" "))
            {
                if (token.equals(stemmedKeyword)) return stemmedKeyword;
            }
        }
        return "FALSE";
    }

    static AuthorInfo lambdaEvolutionAnalysis(List<RevCommit> revCommitList, Set<String> otherDeveloperModifiedLambdaSet,
                                              TwoTuple removedLambdaPos, String javaPath, Repository repo) throws IOException {
        int commitCount = 0;
        TwoTuple lastLambdaPos;
        RemainedLambdaFinder finder = new RemainedLambdaFinder(repo);
        TwoTuple positionOfCandidate = new TwoTuple(removedLambdaPos.beginPos, removedLambdaPos.endPos);
        //at i=0, the lambda is removed

        if (revCommitList.size() == 2)
        {
            lastLambdaPos = finder.compareCommitWithAdjacent(revCommitList.get(0), revCommitList.get(1), positionOfCandidate, javaPath);
            //assert lastLambdaPos != null;
            //System.err.println("revCommitList.size is 2");
            AuthorInfo authorInfo = new AuthorInfo(revCommitList.get(1).getAuthorIdent().getName(), revCommitList.get(0).getAuthorIdent().getName());
            authorInfo.setNumOfCommit(1); //removed after i commits
            return authorInfo;
        }
        for (int i = 1; i < revCommitList.size() - 1; i++)
        {
            //lastLambdaPos = compareBetweenTwoCommits(revCommitList.get(i), revCommitList.get(i+1), removedLambdaPos, javaPath, repo);
            lastLambdaPos = finder.compareCommitWithAdjacent(revCommitList.get(i), revCommitList.get(i + 1), positionOfCandidate, javaPath);
            positionOfCandidate = lastLambdaPos;
            //System.out.println(i);

            if (lastLambdaPos == null)
            {
                //this lambda is created in commit i
                AuthorInfo authorInfo = new AuthorInfo(revCommitList.get(i).getAuthorIdent().getName(), revCommitList.get(0).getAuthorIdent().getName());
                authorInfo.setNumOfCommit(i); //removed after i commits
                return authorInfo;
            }
            else
            {
                if (i == revCommitList.size() - 2)
                {
                    AuthorInfo authorInfo = new AuthorInfo(revCommitList.get(i + 1).getAuthorIdent().getName(), revCommitList.get(0).getAuthorIdent().getName());
                    authorInfo.setNumOfCommit(i + 1);
                    return authorInfo;
                }
                //System.out.println(lastLambdaPos.beginPos + "-" + lastLambdaPos.endPos);
                //this lambda is not created in commit i
                commitCount ++;
                if (lastLambdaPos.modified) {
                    //System.out.println("modified");
                    otherDeveloperModifiedLambdaSet.add(revCommitList.get(i).getAuthorIdent().getName());
                }
            }
        }
        return null;
    }

    static TwoTuple compareBetweenTwoCommits(RevCommit currentCommit, RevCommit parentCommit, TwoTuple removedLambdaPos, String javaPath, Repository repo) throws IOException {
        assert (TreeWalk.forPath(repo, javaPath, currentCommit.getTree()) != null && TreeWalk.forPath(repo, javaPath, parentCommit.getTree()) == null);
        String fileCurrentCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, currentCommit.getTree()).getObjectId(0)).getBytes());
        String fileParentCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, parentCommit.getTree()).getObjectId(0)).getBytes());
        if (!fileParentCommit.contains("->") || !fileCurrentCommit.contains("->")) return null;
        FileWriter currentFile, parentFile;
        currentFile = new FileWriter("old-new-file/oldfile.java");
        currentFile.write("");
        currentFile.write(fileCurrentCommit);
        currentFile.flush();
        parentFile = new FileWriter("old-new-file/newfile.java");
        parentFile.write("");
        parentFile.write(fileParentCommit);
        parentFile.flush();

        //Run.initGenerators();
//            Tree oldFileTree = TreeGenerators.getInstance().getTree("old-new-file\\oldfile.java").getRoot();
//            Tree newFileTree = TreeGenerators.getInstance().getTree("old-new-file\\newfile.java").getRoot();
        Tree currentFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/oldfile.java")).getRoot();
        Tree parentFileTree = new JdtTreeGenerator().generate(new FileReader("old-new-file/newfile.java")).getRoot();

        com.github.gumtreediff.matchers.Matcher defaultMatcher = Matchers.getInstance().getMatcher();
        MappingStore mappings = defaultMatcher.match(currentFileTree, parentFileTree);

        Tree result;
        assert currentFileTree.getTreesBetweenPositions(removedLambdaPos.beginPos, removedLambdaPos.endPos).size() > 0;
        if (currentFileTree.getTreesBetweenPositions(removedLambdaPos.beginPos, removedLambdaPos.endPos).size() == 0)
        {
            result = null;
        }
        else result = mappings.getDstForSrc(currentFileTree.getTreesBetweenPositions(removedLambdaPos.beginPos, removedLambdaPos.endPos).get(0));
        //System.out.println("new pos:" + result.getPos() + "\t" + result.getEndPos());
        currentFile.close();
        parentFile.close();
        if (result == null) return null;
        else return new TwoTuple(result.getPos(), result.getEndPos());
    }
    static void authorAnalysis(List<SimplifiedModifiedLambda> lambdas) throws IOException, GitAPIException {
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String repoName = lambda.commitURL.split("apache/")[1].split("/commit")[0];
            String commitHash = lambda.commitURL.split("/commit/")[1];
            String javaPath = lambda.filePath;
            try (Repository repo = new RepositoryBuilder().setGitDir(new File("../repos/" + repoName + "/.git")).build())
            {
                try (RevWalk walk = new RevWalk(repo))
                {
                    RevCommit currentCommit = walk.parseCommit(repo.resolve(commitHash));
                    List<PositionTuple> positionTupleListBeforeCommit = new ArrayList<>();
//                    System.out.println(currentCommit.getParent(0).getName());
//                    System.out.println(currentCommit.getParent(0).getTree());
                    RevCommit parentCommit = walk.parseCommit(currentCommit.getParent(0).getId());
//                    System.out.println(parentCommit.getTree());
//                    System.out.println(TreeWalk.forPath(repo, javaPath, currentCommit.getParent(0).getTree()));
                    String fileBeforeCommit = new String(repo.open(TreeWalk.forPath(repo, javaPath, parentCommit.getTree()).getObjectId(0)).getBytes());
                    GumtreeJDTDriver gumtreeJDTDriver = new GumtreeJDTDriver(fileBeforeCommit, positionTupleListBeforeCommit, true);
                    boolean found = false;
                    int beginPos = 0, endPos = 0;
                    for (PositionTuple positionTuple : positionTupleListBeforeCommit)
                    {
                        if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                        {
                            found = true;
                            beginPos = positionTuple.beginPos;
                            endPos = positionTuple.endPos;
                            break;
                        }
                    }
                    assert found;
                    walk.markStart(currentCommit);
                    walk.setRevFilter(RevFilter.NO_MERGES);
                    //walk.setRevFilter(new TreeRevFilter(walk, PathFilter.create(javaPath)));
                    //walk.setRevFilter(new TreeRevFilter(walk, TreeFilter.ANY_DIFF));
                    walk.setTreeFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(javaPath), TreeFilter.ANY_DIFF));
                    //System.out.println(currentCommit.getName());
                    List<RevCommit> revCommitList = new ArrayList<>();
                    RevCommit nextCommit = walk.next();
                    System.out.println("latest commit: " + currentCommit.getName());
                    System.out.println("next commit: " + nextCommit.getName());
                    //revCommitList.add(nextCommit);
                    while ((nextCommit != null))
                    {
                        revCommitList.add(nextCommit);
                        //System.out.println(nextCommit.getName());
                        nextCommit = walk.next();
                    }
                    //the last one is the initial commit, and the first one is the latest commit, i.e. current commit
                    assert revCommitList.size() >= 2;

                    TwoTuple positionOfCandidate = new TwoTuple(beginPos, endPos);
                    Set<String> otherDeveloperModifiedLambdaSet = new HashSet<>();
                    AuthorInfo authorInfo = lambdaEvolutionAnalysis(revCommitList, otherDeveloperModifiedLambdaSet, positionOfCandidate, javaPath, repo);
                    //if (revCommitList.size() == 2) System.err.println("revCommitList size is 2!");
                    assert authorInfo != null;
                    otherDeveloperModifiedLambdaSet.remove(authorInfo.createAuthor);
                    otherDeveloperModifiedLambdaSet.remove(authorInfo.removeAuthor);
                    authorInfo.setRemoveLambdaLine(getLambdaLineUrl(lambda.commitURL, lambda.filePath, lambda.beginLine, "remove"));
                    authorInfo.setCreateLambdaLine(getLambdaLineUrl(lambda.commitURL.substring(0, lambda.commitURL.lastIndexOf("/") + 1) +
                            revCommitList.get(authorInfo.numOfCommit).getName(), lambda.filePath, 1, "create"));

                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static String getLambdaLineUrl(String commitURL, String filePath, int beginLine, String removeOrCreate) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(filePath.getBytes("UTF-8"));
        String diffHash = Test.byte2Hex(messageDigest.digest());
        assert removeOrCreate.equals("remove") || removeOrCreate.equals("create");
        String leftOrRight = removeOrCreate.equals("remove") ? "L" : "R";
        return commitURL + "#diff-" + diffHash + leftOrRight + beginLine;
    }

    public static void main(String[] args) throws IOException, CoreException, ClassNotFoundException, GitAPIException {
        //filterLambdasInDeleteEdit();
        FileInputStream fileIn = new FileInputStream("ser/bad-lambdas/51repos-filtered-without-edit-limit.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        SimplifiedModifiedLambda[] simplifiedModifiedLambdaArray = (SimplifiedModifiedLambda[]) in.readObject();
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(simplifiedModifiedLambdaArray)));
        List<SimplifiedModifiedLambda> filteredLambdaList = new ArrayList<>();
        double threshold = 0.5;
        Map<String, Set<Integer>> lambdaLinesInsideEditMap = Test.removedLambdaLinesInsideEdit(simplifiedModifiedLambdaList);
        for (SimplifiedModifiedLambda simplifiedModifiedLambda : simplifiedModifiedLambdaList)
        {
            double proportion = (double)lambdaLinesInsideEditMap.get(Test.getEditPos(simplifiedModifiedLambda)).size() /
                    (double) (simplifiedModifiedLambda.editEndLine - simplifiedModifiedLambda.editBeginLine + 1);
            if (proportion >= threshold) filteredLambdaList.add(simplifiedModifiedLambda);
        }
        //System.out.println(filteredLambdaList.size());
        authorAnalysis(filteredLambdaList);
        //methodInvocationAnalysis(filteredLambdaList);
    }
}
