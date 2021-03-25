package com.github.gumtreelambdaanalysis;

import com.kennycason.kumo.nlp.tokenizers.EnglishWordTokenizer;
import opennlp.tools.stemmer.PorterStemmer;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.*;
import java.lang.constant.Constable;
import java.util.*;

public class LambdaFeatureAnalysis
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

    static void codeProcess() throws IOException {
//        String processedCode = "";
//        String[] lines = contextCode.split("\n");
//        EnglishWordTokenizer tokenizer = new EnglishWordTokenizer();
//        PorterStemmer stemmer = new PorterStemmer();
//        for (String line : lines)
//        {
//            line.strip()
//        }
    }

    static void parameterAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int explicitLambda = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            boolean found = false;
            boolean explicit = false;
            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    found = true;
                    LambdaExpression lambdaExpression = positionTuple.node;
                    List paras = lambdaExpression.parameters();
                    //printLambdaInfo(lambda);
                    //System.out.println(lambdaExpression.parameters());
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
//                    if (explicit)
//                    {
//                        printLambdaInfo(lambda);
//                        System.out.println(lambdaExpression.parameters());
//                    }

                    break;
                }
            }
            if (!found) System.out.println("error! not found!");
        }
        System.out.println("explicit lambda - implicit lambda: " + explicitLambda + " - " + (lambdas.size() - explicitLambda));
    }

    static void locationAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int count = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            if (lambda.filePath.toLowerCase().contains("test"))
            {
                printLambdaInfo(lambda);
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
                    while (!ASTNode.nodeClassForType(parentNode.getNodeType()).getSimpleName().equals("TypeDeclaration"))
                    {
                        if (lambdaExpression.getRoot().equals(parentNode))
                        {
                            System.err.println("type declaration not found");
                            printLambdaInfo(lambda);
                            break;
                        }
                        parentNode = parentNode.getParent();
                    }
                    if (ASTNode.nodeClassForType(parentNode.getNodeType()).getSimpleName().equals("TypeDeclaration"))
                    {
                        TypeDeclaration typeDeclaration = (TypeDeclaration)parentNode;
                        //typeDeclaration
                        //printLambdaInfo(lambda);
                        //System.out.println(typeDeclaration.getName());
                        //System.out.println(typeDeclaration.superInterfaceTypes());
                        for (Object superInterface : typeDeclaration.superInterfaceTypes())
                        {
                            if (superInterface.toString().equals("Serializable"))
                            {
//                                printLambdaInfo(lambda);
//                                System.out.println(typeDeclaration.getName());
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
        System.out.println("number of classes implemented Serializable: " + serialInterface + "/" + lambdas.size());
    }
    static void parentNodeTypeAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> parentTypeFreq = new HashMap<>();
        Set<String> parentNodeTypes = new HashSet<>();
        Map<String, Integer> methodInvocationFreq = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
//            System.out.println("###########################");
//            System.out.println(lambda.lambdaContext);
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
                        printLambdaInfo(lambda);
                        System.out.println(methodInvocation.getName());


                        if (!methodInvocationFreq.containsKey(methodInvocation.getName().toString()))
                        {
                            methodInvocationFreq.put(methodInvocation.getName().toString(), 1);
                        }
                        else methodInvocationFreq.put(methodInvocation.getName().toString(), methodInvocationFreq.get(methodInvocation.getName().toString()) + 1);

                        ASTParser parser = ASTParser.newParser(AST.JLS14);
                        parser.setResolveBindings(true); // we need bindings later on
                        parser.setKind(ASTParser.K_COMPILATION_UNIT);
                        parser.setBindingsRecovery(true);
                        Map<String, String> options = JavaCore.getDefaultOptions();
                        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_14);
                        parser.setCompilerOptions(options);
                        parser.setSource(getSourceCode(lambda).toCharArray());


                        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
                        if (cu.getAST().hasBindingsRecovery()) {
                            System.out.println("Binding activated.");
                        }
                        cu.accept(new ASTVisitor() {
                            @Override
                            public boolean visit(MethodInvocation node) {
                                if (node.toString().equals(parentNode.toString())) {
                                    IBinding binding = node.resolveMethodBinding();
                                    System.out.println("Type: " + binding.toString());
                                    return true;
                                }
                                return false;
                            }
                        });
                        //System.out.println("Type:" + expression.resolveTypeBinding().toString());
                    }
                    break;
                }
            }
            if (!found) System.err.println("line 109: error!");
        }
        //System.out.println(parentNodeTypes);
        for (String parentNodeType : parentTypeFreq.keySet())
        {
            System.out.println(parentNodeType + ":" + parentTypeFreq.get(parentNodeType));
        }
        System.out.println("++++++++++++++++++++++++++++++++");
        methodInvocationFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
//        for (String methodName : methodInvocationFreq.keySet())
//        {
//            System.out.println(methodName + ":" + methodInvocationFreq.get(methodName));
//        }
    }

    static void parserTest(String path) throws IOException {
        File javaFile = new File(path);
        FileInputStream in = new FileInputStream(javaFile);
        int size = in.available();
        byte[] buffer = new byte[size];
        in.read(buffer);
        in.close();
        String str = new String(buffer);

        ASTParser parser = ASTParser.newParser(AST.JLS14);
        parser.setResolveBindings(true); // we need bindings later on
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setBindingsRecovery(true);
        String[] sources = {"D:\\coding\\java_project\\GumtreeLambdaAnalysis\\example"};
        String[] classpath = {"D:\\coding\\java_project\\GumtreeLambdaAnalysis\\example", "C:\\Program Files\\Java\\jre1.8.0_281\\lib\\rt.jar"};
        parser.setEnvironment(classpath, sources, new String[]{"UTF-8"}, true);
        Map<String, String> options = JavaCore.getDefaultOptions();
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_14);
        parser.setCompilerOptions(options);
        parser.setSource(str.toCharArray());

        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        if (cu.getAST().hasBindingsRecovery()) {
            System.out.println("Binding activated.");
        }
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                System.out.println(node);
                IBinding binding = node.resolveMethodBinding();
                System.out.println("Type: " + binding.toString());
                return true;
            }
        });
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
                    //if (lambdaNode.parameters().size() > 4) printLambdaInfo(lambda);
                    if (!paraNumMap.containsKey(lambdaNode.parameters().size()))
                    {
                        paraNumMap.put(lambdaNode.parameters().size(), 1);
                    }
                    else paraNumMap.put(lambdaNode.parameters().size(), paraNumMap.get(lambdaNode.parameters().size()) + 1);
                    break;
                }
            }
        }
        System.out.println(paraNumMap);
    }

    static void lineNumStatistics(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<Integer, Integer> lineNumMap = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            int lineNum = lambda.endLine - lambda.beginLine + 1;
            if (lineNum > 20) printLambdaInfo(lambda);
            if (!lineNumMap.containsKey(lineNum))
            {
                lineNumMap.put(lineNum, 1);
            }
            else lineNumMap.put(lineNum, lineNumMap.get(lineNum) + 1);
        }
        System.out.println(lineNumMap);

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

    public static void main1(String[] args)
    {
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>();

        try {
            String serPath = "ser\\bad-lambdas\\test";
            String[] paths = {serPath + "\\03-15", serPath + "\\03-16", serPath + "\\03-17", serPath + "\\03-18"};
            //String[] paths = {serPath + "\\03-17"};
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
            //callerMethodAnalysis(simplifiedModifiedLambdaList);
            classNameAnalysis(simplifiedModifiedLambdaList);
            //System.out.println(simplifiedModifiedLambdaList.get(0).currentCommit);
        }catch (FileNotFoundException e)
        {
            System.err.println("can't find the file");
            e.printStackTrace();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) throws IOException {
        parserTest("example/lc01.java");
        //codeProcess();
    }
}
