package com.github.gumtreelambdaanalysis;

import org.eclipse.jdt.core.dom.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class RemainedLambdaContextAnalysis {
    static void interfaceAnalysis(List<RemainedLambda> lambdas)
    {
        Map<String, Integer> interfaceFreq = new HashMap<>();
        for (RemainedLambda lambda : lambdas) {
            String fileBeforeCommit = RemainedLambdaFeatureAnalysis.getSourceCode(lambda);
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

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        //filterByModifiedJavaFiles();
        FileInputStream fileIn = new FileInputStream("ser/good-lambdas/51repos-onestep-without-merge_T=10.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        RemainedLambda[] remainedLambdaArray = (RemainedLambda[]) in.readObject();
        List<RemainedLambda> remainedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(remainedLambdaArray)));
        interfaceAnalysis(remainedLambdaList);
        //System.out.println(remainedLambdaList.size());
    }
}
