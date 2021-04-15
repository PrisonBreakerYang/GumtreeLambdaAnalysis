package com.github.gumtreelambdaanalysis;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

public class ResolveBindingTest {

    public static void main(String[] args) throws IOException, CoreException, ClassNotFoundException, GitAPIException {
        //filterLambdasInDeleteEdit();
        FileInputStream fileIn = new FileInputStream("ser/bad-lambdas/103repos-filtered-without-edit-limit.ser");
        ObjectInputStream in = new ObjectInputStream(fileIn);
        SimplifiedModifiedLambda[] simplifiedModifiedLambdaArray = (SimplifiedModifiedLambda[]) in.readObject();
        List<SimplifiedModifiedLambda> simplifiedModifiedLambdaList = new ArrayList<>(new ArrayList<>(Arrays.asList(simplifiedModifiedLambdaArray)));
        List<SimplifiedModifiedLambda> filteredLambdaList = new ArrayList<>();
        double threshold = 0.5;
        Map<String, Set<Integer>> lambdaLinesInsideEditMap = Test.removedLambdaLinesInsideEdit(simplifiedModifiedLambdaList);
        for (SimplifiedModifiedLambda lambda : simplifiedModifiedLambdaList)
        {
            double proportion = (double)lambdaLinesInsideEditMap.get(Test.getEditPos(lambda)).size() /
                    (double) (lambda.editEndLine - lambda.editBeginLine + 1);
            //set specific project
            String issue = RemovedLambdaFeatureAnalysis.linkToJiraIssue(lambda).equals("FALSE") ?
                    RemovedLambdaFeatureAnalysis.msgKeyword(lambda) : RemovedLambdaFeatureAnalysis.linkToJiraIssue(lambda);
            if (proportion >= threshold && !issue.equals("FALSE") && lambda.javaFileModified <= 20 && lambda.repo.contains("aries")) filteredLambdaList.add(lambda);
        }
        RemovedLambdaFeatureAnalysis.methodInvocationAnalysis(filteredLambdaList);
    }
}
