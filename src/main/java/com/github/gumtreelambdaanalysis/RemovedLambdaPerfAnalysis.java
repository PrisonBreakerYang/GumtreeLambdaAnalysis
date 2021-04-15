package com.github.gumtreelambdaanalysis;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class RemovedLambdaPerfAnalysis
{
    static void methodModifiersAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> locationOfLambda = new HashMap<>();
        Map<String, Integer> methodModifiers = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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
            assert found;
        }
        methodModifiers.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
    }

    static void classModifiersAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> classModifiers = new HashMap<>();
        int classCount = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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

    static void codePathAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        String[] keywords = {"core", "base", "engine", "util"};
        int corePath = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
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

    static void loopAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int loopCount = 0;
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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

    static void fieldAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int variableDeclaration = 0;
        int noModifier = 0;
        Map<String, Integer> declarationModifiers = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            boolean found = false;
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    ASTNode parentNode = positionTuple.node.getParent();
                    if (parentNode instanceof VariableDeclarationFragment) {
                        RemovedLambdaFeatureAnalysis.printLambdaInfo(lambda);
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
        loopAnalysis(filteredLambdaList);
    }
}