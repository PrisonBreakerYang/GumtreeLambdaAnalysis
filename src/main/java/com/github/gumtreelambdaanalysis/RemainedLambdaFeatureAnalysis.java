package com.github.gumtreelambdaanalysis;

import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.tree.Tree;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.*;
import java.util.*;

public class RemainedLambdaFeatureAnalysis
{
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

    static void paraNumStatistics(List<RemainedLambda> lambdas)
    {
        Map<Integer, Integer> paraNumMap = new HashMap<>();
        for (RemainedLambda lambda : lambdas)
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

    static void lineNumStatistics(List<RemainedLambda> lambdas)
    {
        Map<Integer, Integer> lineNumMap = new HashMap<>();
        for (RemainedLambda lambda : lambdas)
        {
            int lineNum = lambda.endLine - lambda.beginLine + 1;
            addIntToMap(lineNumMap, lineNum);
        }
        lineNumMap.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
    }

    static String getSourceCode(RemainedLambda lambda)
    {
        String repoName = lambda.commitURL.replace("https://github.com/", "").split("apache/")[1].split("/commit")[0];
        //System.out.println(repoName);
        String repoPath = "../repos/";
        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(repoPath + repoName + "/.git")).build()) {
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit introducedCommit = revWalk.parseCommit(ObjectId.fromString(lambda.introducedCommitHash));
                return new String(repository.open(TreeWalk.forPath(repository, lambda.filePath,
                        introducedCommit.getTree()).getObjectId(0)).getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.err.println("error!");
        return null;
    }

    static void bodyAnalysis(List<RemainedLambda> lambdas) throws IOException {
        Map<Integer, Integer> bodyHeightFreq = new HashMap<>(), bodySizeFreq = new HashMap<>(), maxSubTreeFreq = new HashMap<>();
        for (RemainedLambda lambda : lambdas)
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

    static void parameterAnalysis(List<RemainedLambda> lambdas)
    {
        int explicitLambda = 0, implicitLambda = 0;
        for (RemainedLambda lambda : lambdas)
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

    static void locationAnalysis(List<RemainedLambda> lambdas)
    {
        int count = 0;
        for (RemainedLambda lambda : lambdas)
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

    static void functionalInterfaceAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> functionalInterfaceFreq = new HashMap<>();
        final int[] resolved = {0};
        final int[] built_in = {0};
        final int[] self_defined = {0};
        for (RemainedLambda lambda : lambdas) {
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

    static void methodInvocationAnalysis(List<RemainedLambda> lambdas) {
        Map<String, Integer> methodFullNameFreq = new HashMap<>();
        methodFullNameFreq.put("self defined", 0);
        int lambdaCount = 0;
        int methodInvocationCount = 0;
        for (RemainedLambda lambda : lambdas) {
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
                                    else
                                    {
                                        //String pack = binding.getDeclaringClass().getPackage().toString().split(" ")[1];
                                        String className = methodInvocation.resolveMethodBinding().getDeclaringClass().getBinaryName();
                                        String methodFullName = className + "." + methodInvocation.getName();
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

    static void parentNodeTypeAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> parentTypeFreq = new HashMap<>();
        Set<String> parentNodeTypes = new HashSet<>();
//        Map<String, Integer> methodInvocationFreq = new HashMap<>();
        for (RemainedLambda lambda : lambdas)
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

//                    if (parentNodeName.equals("MethodInvocation"))
//                    {
//                        MethodInvocation methodInvocation = (MethodInvocation) parentNode;
//                        if (!methodInvocationFreq.containsKey(methodInvocation.getName().toString()))
//                        {
//                            methodInvocationFreq.put(methodInvocation.getName().toString(), 1);
//                        }
//                        else methodInvocationFreq.put(methodInvocation.getName().toString(), methodInvocationFreq.get(methodInvocation.getName().toString()) + 1);
//                    }
                    break;
                }
            }
            if (!found) System.err.println("line 191: error!");
        }
        //System.out.println(parentNodeTypes);
        for (String parentNodeType : parentTypeFreq.keySet())
        {
            System.out.println(parentNodeType + ":" + parentTypeFreq.get(parentNodeType));
        }
//        System.out.println("++++++++++++++++++++++++++++++++");
//        methodInvocationFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
//        for (String methodName : methodInvocationFreq.keySet())
//        {
//            System.out.println(methodName + ":" + methodInvocationFreq.get(methodName));
//        }
    }

    static void classNameAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> classNameFreq = new HashMap<>();
        int serialInterface = 0;
        for (RemainedLambda lambda : lambdas)
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
                                System.out.println(typeDeclaration.getName().toString());
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

    static void functionNameAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> functionNameFreq = new HashMap<>();
        Map<String, Integer> locationOfLambda = new HashMap<>();
//        locationOfLambda.put("MethodDeclaration", 0);
//        locationOfLambda.put("FieldDeclaration", 0);
//        locationOfLambda.put("Initializer", 0);
        for (RemainedLambda lambda : lambdas) {
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

    static void printLambdaInfo(RemainedLambda lambda)
    {
        System.out.println("######################");
        System.out.println(lambda.commitURL);
        System.out.println(lambda.filePath);
        System.out.println(lambda.lambdaContext);
    }

    static boolean isMergedCommit(RemainedLambda lambda)
    {
        String repoName = lambda.commitURL.replace("https://github.com/", "").split("apache/")[1].split("/commit")[0];
        //System.out.println(repoName);
        String repoPath = "../repos/";
        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(repoPath + repoName + "/.git")).build()) {
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit introducedCommit = revWalk.parseCommit(ObjectId.fromString(lambda.introducedCommitHash));
                if (introducedCommit.getParentCount() > 1)
                {
                    //System.out.println(introducedCommit.getParentCount());
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public static void filterByModifiedJavaFiles()
    {
        List<RemainedLambda> remainedLambdaList = new ArrayList<>();
        //List<RemainedLambda> remainedLambdaFilteredList = new ArrayList<>();
        try {
            String serPath = "ser/good-lambdas/test";
            String[] paths = {serPath + "/03-24", serPath + "/03-25", serPath + "/03-26", serPath + "/03-27", serPath + "/03-30"};
            //String[] paths = {serPath + "\\03-17"};
            for (String path : paths)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (serFile.toString().endsWith("T=100.ser"))
                    {
                        FileInputStream fileIn = new FileInputStream(serFile);
                        ObjectInputStream in = new ObjectInputStream(fileIn);
                        RemainedLambda[] remainedLambdas = (RemainedLambda[]) in.readObject();
                        for (RemainedLambda remainedLambda : remainedLambdas)
                        {
                            if (remainedLambda.javaFilesModified <= 10 && !isMergedCommit(remainedLambda))
                            {
                                remainedLambdaList.add(remainedLambda);
                            }
                        }
                    }
                }
            }
            RemainedLambda[] remainedLambdasForSerial = new RemainedLambda[remainedLambdaList.size()];
            remainedLambdaList.toArray(remainedLambdasForSerial);
            FileOutputStream fileOut = new FileOutputStream("ser/good-lambdas/" + "51repos-onestep-without-merge_T=10.ser");
            ObjectOutputStream serOut = new ObjectOutputStream(fileOut);
            serOut.writeObject(remainedLambdasForSerial);
            serOut.close();
            fileOut.close();
        }catch (FileNotFoundException e)
        {
            System.err.println("can't find the file");
            e.printStackTrace();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //filterByModifiedJavaFiles();
        FileInputStream fileIn = new FileInputStream("ser/good-lambdas/51repos-onestep-without-merge_T=10.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        RemainedLambda[] remainedLambdaArray = (RemainedLambda[]) in.readObject();
        List<RemainedLambda> remainedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(remainedLambdaArray)));
        //System.out.println(remainedLambdaList.size());
        parameterAnalysis(remainedLambdaList);
    }
}
