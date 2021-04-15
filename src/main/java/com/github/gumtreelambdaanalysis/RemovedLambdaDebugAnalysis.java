package com.github.gumtreelambdaanalysis;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class RemovedLambdaDebugAnalysis {
    static void lambdaClassAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    RemovedLambdaFeatureAnalysis.printLambdaInfo(lambda);
                    ASTNode body = lambdaExpression.getBody();
                    break;
                }
            }
        }
    }

    static void returnStatementAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int returnNullParaLambda = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    if (lambdaExpression.getParent() instanceof ReturnStatement && lambdaExpression.parameters().size() == 0)
                    {
                        RemovedLambdaFeatureAnalysis.printLambdaInfo(lambda);
                        returnNullParaLambda ++;
                    }
                    break;
                }
            }
        }
        System.out.println(returnNullParaLambda);
        System.out.println(lambdas.size());
    }

    static void nestedLambdaAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int nestedLambda = 0;
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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

    static void assertAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        int assertLambda = 0;
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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
                            RemovedLambdaFeatureAnalysis.printLambdaInfo(lambda);
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

    static void bodyTypeAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> bodyTypes = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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

    static void bodyStatementAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<Integer, Integer> bodyStatementsNum = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas) {
            final int[] bodyStatements = {0};
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
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
        bodyStatementAnalysis(filteredLambdaList);
    }
}
