package com.github.gumtreelambdaanalysis;

import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeMetrics;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static com.github.gumtreelambdaanalysis.BadLambdaFinder.getMergedEdits;

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
            //String[] classpath = {"D:\\software\\jdk\\lib"};
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
            //String[] classpath = {"D:\\software\\jdk\\lib"};
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
                                            String methodFullName = pack + "." + className + "." + methodInvocation.resolveMethodBinding().getName();
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
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine)
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
            String serPath = "ser\\bad-lambdas\\test";
            String[] paths = {serPath + "\\03-15", serPath + "\\03-16", serPath + "\\03-17", serPath + "\\03-18"};
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
            FileOutputStream fileOut = new FileOutputStream("ser/bad-lambdas/" + "51repos-filtered.ser");
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
    public static void main(String[] args) throws IOException, CoreException, ClassNotFoundException {
        //filterLambdasInDeleteEdit();
        FileInputStream fileIn = new FileInputStream("ser/bad-lambdas/51repos-filtered.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        SimplifiedModifiedLambda[] simplifiedModifiedLambdaArray = (SimplifiedModifiedLambda[]) in.readObject();
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(simplifiedModifiedLambdaArray)));
        List<SimplifiedModifiedLambda> filteredLambdaList = new ArrayList<>();
        double threshold = 0.5;
        for (SimplifiedModifiedLambda simplifiedModifiedLambda : simplifiedModifiedLambdaList)
        {
            double proportion = (double) (Integer.min(simplifiedModifiedLambda.editEndLine, simplifiedModifiedLambda.endLine) - simplifiedModifiedLambda.beginLine + 1) / (simplifiedModifiedLambda.editEndLine - simplifiedModifiedLambda.editBeginLine + 1);
            if (proportion >= threshold) filteredLambdaList.add(simplifiedModifiedLambda);
        }
        //System.out.println(filteredLambdaList.size());
        methodInvocationAnalysis(filteredLambdaList);
    }
}
