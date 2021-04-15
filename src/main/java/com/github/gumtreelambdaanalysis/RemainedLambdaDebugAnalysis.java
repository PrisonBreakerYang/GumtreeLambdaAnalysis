package com.github.gumtreelambdaanalysis;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class RemainedLambdaDebugAnalysis {

    static void returnStatementAnalysis(List<RemainedLambda> lambdas)
    {
        int returnNullParaLambda = 0;
        for (RemainedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    if (lambdaExpression.getParent() instanceof ReturnStatement && lambdaExpression.parameters().size() == 0)
                    {
                        RemainedLambdaFeatureAnalysis.printLambdaInfo(lambda);
                        returnNullParaLambda ++;
                    }
                    break;
                }
            }
        }
        System.out.println(returnNullParaLambda);
        System.out.println(lambdas.size());
    }

    static void nestedLambdaAnalysis(List<RemainedLambda> lambdas)
    {
        int nestedLambda = 0;
        for (RemainedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof LambdaExpression)) {
                        if (parentNode.equals(lambdaExpression.getRoot())) break;
                        parentNode = parentNode.getParent();
                    }
                    if (parentNode instanceof LambdaExpression) {
                        nestedLambda++;
                    }
                    break;
                }
            }
        }
        System.out.println(nestedLambda);
        System.out.println(lambdas.size());
    }

    static void assertAnalysis(List<RemainedLambda> lambdas)
    {
        int assertLambda = 0;
        for (RemainedLambda lambda : lambdas) {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    if (parentNode instanceof MethodInvocation)
                    {
                        MethodInvocation methodInvocation = (MethodInvocation) parentNode;
                        String methodName = methodInvocation.getName().getIdentifier();
                        //System.out.println(methodName);
                        if (methodName.contains("assert"))
                        {
                            RemainedLambdaFeatureAnalysis.printLambdaInfo(lambda);
                            assertLambda ++;
                        }
                    }
                    break;
                }
            }
        }
        System.out.println(assertLambda);
        System.out.println(lambdas.size());
    }

    static void instanceInitAnalysis(List<RemainedLambda> lambdas)
    {
        int initInTest = 0;
        for (RemainedLambda lambda : lambdas) {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    if (parentNode instanceof ClassInstanceCreation && lambda.filePath.toLowerCase().contains("test"))
                    {
//                        RemovedLambdaFeatureAnalysis.printLambdaInfo(lambda);
                        initInTest ++;
                    }
                    break;
                }
            }
        }
        System.out.println(initInTest);
        System.out.println(lambdas.size() - initInTest);
    }

    static void bodyTypeAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> bodyTypes = new HashMap<>();
        for (RemainedLambda lambda : lambdas) {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    String bodyType = ASTNode.nodeClassForType(lambdaExpression.getBody().getNodeType()).getSimpleName();
                    RemainedLambdaFeatureAnalysis.addStringToMap(bodyTypes, bodyType);
                    break;
                }
            }
        }
        bodyTypes.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
    }

    static void bodyStatementAnalysis(List<RemainedLambda> lambdas)
    {
        Map<Integer, Integer> bodyStatementsNum = new HashMap<>();
        for (RemainedLambda lambda : lambdas) {
            final int[] bodyStatements = {0};
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode body = lambdaExpression.getBody();
                    body.accept(new ASTVisitor()
                    {
                        @Override
                        public void preVisit(ASTNode node)
                        {
                            if (node instanceof Statement)
                            {
                                bodyStatements[0]++;
                            }
                        }
                    });
                    break;
                }
            }
            RemainedLambdaFeatureAnalysis.addIntToMap(bodyStatementsNum, bodyStatements[0]);
        }
        bodyStatementsNum.entrySet().stream().sorted(Map.Entry.<Integer, Integer>comparingByValue()).forEach(System.out::println);
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //filterLambdasInDeleteEdit();
        FileInputStream fileIn = new FileInputStream("ser/good-lambdas/51repos-onestep-without-merge_T=10.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        RemainedLambda[] remainedLambdaArray = (RemainedLambda[]) in.readObject();
        List<RemainedLambda> remainedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(remainedLambdaArray)));
        bodyStatementAnalysis(remainedLambdaList);
    }
}
