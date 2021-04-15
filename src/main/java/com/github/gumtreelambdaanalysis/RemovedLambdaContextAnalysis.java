package com.github.gumtreelambdaanalysis;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class RemovedLambdaContextAnalysis {

    static void interfaceAnalysis(List<SimplifiedModifiedLambda> lambdas)
    {
        Map<String, Integer> interfaceFreq = new HashMap<>();
        for (SimplifiedModifiedLambda lambda : lambdas) {
            String fileBeforeCommit = RemovedLambdaFeatureAnalysis.getSourceCode(lambda);
            List<PositionTuple> positionTuples = new ArrayList<>();
            GumtreeJDTDriver driver = new GumtreeJDTDriver(fileBeforeCommit, positionTuples, true);
            for (PositionTuple positionTuple : positionTuples) {
                if (positionTuple.beginLine == lambda.beginLine && positionTuple.endLine == lambda.endLine && positionTuple.node.toString().equals(lambda.node)) {
                    LambdaExpression lambdaExpression = positionTuple.node;
                    ASTNode parentNode = lambdaExpression.getParent();
                    while (!(parentNode instanceof TypeDeclaration) && !(parentNode instanceof EnumDeclaration))
                    {
                        parentNode = parentNode.getParent();
                    }
                    if (parentNode instanceof TypeDeclaration)
                    {
                        TypeDeclaration typeDeclaration = (TypeDeclaration)parentNode;
                        ITypeBinding binding = typeDeclaration.resolveBinding();
                        for (Object interf : typeDeclaration.superInterfaceTypes())
                        {
                            String interfName = interf.toString();
                            if (interfName.contains("<")) interfName = interfName.substring(0, interfName.indexOf("<"));
                            System.out.println(interfName);
                            RemainedLambdaFeatureAnalysis.addStringToMap(interfaceFreq, interfName);
                        }
                    }
                }
            }
        }
        interfaceFreq.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue()).forEach(System.out::println);
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
        interfaceAnalysis(filteredLambdaList);
    }
}
