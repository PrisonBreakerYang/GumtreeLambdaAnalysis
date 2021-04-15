package com.github.gumtreelambdaanalysis;

import org.eclipse.jdt.core.dom.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class RemainedLambdaPerfAnalysis {
    static void methodModifiersAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> locationOfLambda = new HashMap<>();
        Map<String, Integer> methodModifiers = new HashMap<>();
        for (RemainedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof MethodDeclaration) && !(parentNode instanceof FieldDeclaration) &&
                            !(parentNode instanceof Initializer)) {
                        if (parentNode.equals(lambdaExpression.getRoot())) break;
                        parentNode = parentNode.getParent();
                    }
                    if (parentNode instanceof MethodDeclaration)
                    {
                        //this lambda is located in a method
                        MethodDeclaration methodDeclaration = (MethodDeclaration)parentNode;
                        for (Object modifier : methodDeclaration.modifiers())
                        {
                            String modifierStr = modifier.toString();
                            if (modifierStr.contains("(")) modifierStr = modifierStr.substring(0, modifierStr.indexOf("("));
                            System.out.println(modifierStr);
                            RemovedLambdaFeatureAnalysis.addStringToMap(methodModifiers, modifierStr);
                        }
                    }
                    RemainedLambdaFeatureAnalysis.addStringToMap(locationOfLambda, ASTNode.nodeClassForType(parentNode.getNodeType()).getName());
                    break;
                }
            }
        }
        methodModifiers.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
    }

    static void classModifiersAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> classModifiers = new HashMap<>();
        int classCount = 0;
        for (RemainedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            for (PositionTuple positionTuple : positionTuples)
            {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node))
                {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof TypeDeclaration) && !(parentNode instanceof EnumDeclaration))
                    {
                        assert !lambdaExpression.getRoot().equals(parentNode);
                        parentNode = parentNode.getParent();
                    }
                    //assert parentNode instanceof TypeDeclaration || parentNode instanceof EnumDeclaration;
                    if (parentNode instanceof TypeDeclaration)
                    {
                        classCount ++;
                        TypeDeclaration typeDeclaration = (TypeDeclaration)parentNode;
                        for (Object modifier : typeDeclaration.modifiers())
                        {
                            String modifierStr = modifier.toString();
                            if (modifierStr.contains("(")) modifierStr = modifierStr.substring(0, modifierStr.indexOf("("));
                            System.out.println(modifierStr);
                            RemainedLambdaFeatureAnalysis.addStringToMap(classModifiers, modifierStr);
                        }
                    }
                    break;
                }
            }
        }
        classModifiers.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println("class number: " + classCount);
    }

    static void codePathAnalysis(List<RemainedLambda> lambdas)
    {
        String[] keywords = {"core", "base", "engine", "util"};
        int corePath = 0;
        for (RemainedLambda lambda : lambdas)
        {
            for (String keyword : keywords)
            {
                if (lambda.filePath.contains(keyword))
                {
                    corePath ++;
                    break;
                }
            }
        }
        System.out.println("core path: " + corePath);
        System.out.println("others: " + (lambdas.size() - corePath));
    }

    static void loopAnalysis(List<RemainedLambda> lambdas)
    {
        int loopCount = 0;
        for (RemainedLambda lambda : lambdas) {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);

            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof ForStatement) && !(parentNode instanceof WhileStatement) &&
                            !(parentNode instanceof EnhancedForStatement) && !(parentNode instanceof DoStatement)) {
                        if (parentNode.equals(lambdaExpression.getRoot())) break;
                        parentNode = parentNode.getParent();
                    }
                    if (parentNode instanceof ForStatement || parentNode instanceof WhileStatement || parentNode instanceof EnhancedForStatement
                            || parentNode instanceof DoStatement) {
                        loopCount++;
                    }
                    break;
                }
            }
        }
        System.out.println("loop: " + loopCount);
        System.out.println("others: " + (lambdas.size() - loopCount));
    }

    static void fieldAnalysis(List<RemainedLambda> lambdas)
    {
        int variableDeclaration = 0;
        int noModifier = 0;
        Map<String, Integer> declarationModifiers = new HashMap<>();
        for (RemainedLambda lambda : lambdas) {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    ASTNode parentNode = positionTuple.node.getParent();
                    if (parentNode instanceof VariableDeclarationFragment) {
                        RemainedLambdaFeatureAnalysis.printLambdaInfo(lambda);
                        VariableDeclarationFragment fragment = (VariableDeclarationFragment) parentNode;
                        System.out.println("++++++++++++++++++++++++++++");
                        System.out.println(ASTNode.nodeClassForType(fragment.getParent().getNodeType()));
                        assert (fragment.getParent() instanceof VariableDeclarationStatement || fragment.getParent() instanceof FieldDeclaration);
                        if (fragment.getParent() instanceof VariableDeclarationStatement) {
                            VariableDeclarationStatement statement = (VariableDeclarationStatement) fragment.getParent();
                            if (statement.modifiers().size() == 0) noModifier ++;
                            for (Object modifier : statement.modifiers())
                            {
                                RemainedLambdaFeatureAnalysis.addStringToMap(declarationModifiers, modifier.toString());
                            }
                        }
                        if (fragment.getParent() instanceof FieldDeclaration)
                        {
                            FieldDeclaration declaration = (FieldDeclaration) fragment.getParent();
                            if (declaration.modifiers().size() == 0) noModifier ++;
                            for (Object modifier : declaration.modifiers())
                            {
                                RemainedLambdaFeatureAnalysis.addStringToMap(declarationModifiers, modifier.toString());
                            }
                        }
                        variableDeclaration ++;
                    }
                    break;
                }
            }
        }
        declarationModifiers.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
        System.out.println(variableDeclaration);
        System.out.println(noModifier);
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //filterByModifiedJavaFiles();
        FileInputStream fileIn = new FileInputStream("ser/good-lambdas/51repos-onestep-without-merge_T=10.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        RemainedLambda[] remainedLambdaArray = (RemainedLambda[]) in.readObject();
        List<RemainedLambda> remainedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(remainedLambdaArray)));
        loopAnalysis(remainedLambdaList);
        //System.out.println(remainedLambdaList.size());
    }
}
