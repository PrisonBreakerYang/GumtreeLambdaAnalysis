package com.github.gumtreelambdaanalysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Utils {
    static String[] getJarPaths()
    {
        File jarDir = new File("D:/temp/output");
        File[] jars = jarDir.listFiles();
        assert jars != null;
        String[] jarPaths = new String[jars.length];
        for (int i = 0; i < jars.length; i++)
        {
            jarPaths[i] = jars[i].toString();
        }
        return jarPaths;
    }

    static String[] getSourcePaths(String rootDir)
    {
        List<String> sourceRootList = new ArrayList<>();
        traverseFolders(rootDir, sourceRootList);
        return sourceRootList.toArray(new String[0]);
    }

    static void traverseFolders(String folderPath, List<String> sourceRootList)
    {
//        System.out.println(folderPath);
        File file = new File(folderPath);
        if (file.exists())
        {
            File[] subFiles = file.listFiles();
//            System.out.println(Arrays.toString(subFiles));
            assert subFiles != null;
            boolean src = false, pom = false;
            if (subFiles.length ==0 ) return;
            for (File subFile : subFiles)
            {
//                System.out.println(subFile.getName());
                if (subFile.getName().equals("src")) src = true;
                if (subFile.getName().equals("pom.xml")) pom = true;
            }
            if (src && pom) {
                File javaRoot = new File(file.toString() + "/src/main/java");
                if (javaRoot.exists()) {

                    sourceRootList.add(javaRoot.toString());
                }
            }
            else
            {
                for (File subFile : subFiles)
                {
//                    System.out.println(subFile.getAbsoluteFile());
                    if (subFile.isDirectory()) traverseFolders(subFile.getAbsolutePath(), sourceRootList);
                }
            }

        }
        else
        {
            System.out.println("File " + folderPath + " not exist!");
        }
    }
    public static void main(String[] args)
    {
        for (String path : getSourcePaths("../repos/aries"))
        {
            System.out.println(path);
        }
    }
}
