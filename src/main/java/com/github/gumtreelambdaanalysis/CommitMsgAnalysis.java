package com.github.gumtreelambdaanalysis;

import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.bg.CircleBackground;
import com.kennycason.kumo.font.scale.SqrtFontScalar;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.nlp.normalize.LowerCaseNormalizer;
import com.kennycason.kumo.nlp.tokenizers.EnglishWordTokenizer;
import com.kennycason.kumo.palette.ColorPalette;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;

//import static com.helger.commons.io.resource.ClassPathResource.getInputStream;

public class CommitMsgAnalysis
{
    private static HashSet<String> repo;
    public static void removeStopWords(List<String> msgList)
    {
        for (String msg : msgList)
        {
//            String[] lines = msg.split("\n");
//            if (lines.length == 1 && msg.length() <= 40)
//            {
//                System.out.println("####################################");
//                System.out.println(msg);
//            }
        }
    }
    public static void removeDuplicateCommits(List<String> msgList)
    {
        HashSet<String> msgSet = new HashSet<>(msgList);
        //System.out.println(msgSet.size());
        msgList = new ArrayList<>(msgSet);
    }

    public static void removeRevertCommitMsg(List<String> msgList)
    {
        msgList.removeIf(msg -> msg.contains("This reverts commit"));
    }

    public static void toTxt(List<String> msgList, String txtPath) throws IOException {
        File msgPool = new File(txtPath);
        FileOutputStream msgTxt = new FileOutputStream(msgPool);
        for (String msg : msgList)
        {
            msgTxt.write(msg.getBytes());
        }
        msgTxt.flush();
        msgTxt.close();
    }

    public static Collection<String> loadStopWords() throws FileNotFoundException {
        Collection<String> stopWords = new ArrayList<>();
        String NLTK_stop_words_path = "resources/NLTK's-list-of-english-stopwords.txt";
        try {
        File stopWordsFile = new File(NLTK_stop_words_path);
        FileInputStream fileInputStream = new FileInputStream(stopWordsFile);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String text;
        while((text = bufferedReader.readLine()) != null) {
            stopWords.add(text); //stop words from NLTK
        }
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopWords.addAll(repo); //project names are stop words
        return stopWords;
    }
    public static void drawWordCloud(File msgPool) throws IOException {
        final FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
        frequencyAnalyzer.setStopWords(loadStopWords());
        frequencyAnalyzer.setMinWordLength(5);
        frequencyAnalyzer.setWordTokenizer(new EnglishWordTokenizer());
        //frequencyAnalyzer.setFilter(new UrlFilter());
        frequencyAnalyzer.addNormalizer(new LowerCaseNormalizer());
        final List<WordFrequency> wordFrequencies = frequencyAnalyzer.load(msgPool);
        final Dimension dimension = new Dimension(600, 600);
        final WordCloud wordCloud = new WordCloud(dimension, CollisionMode.PIXEL_PERFECT);
        wordCloud.setPadding(2);
        wordCloud.setBackground(new CircleBackground(300));
        wordCloud.setColorPalette(new ColorPalette(new Color(0x4055F1), new Color(0x408DF1), new Color(0x40AAF1), new Color(0x40C5F1), new Color(0x40D3F1), new Color(0xFFFFFF)));
        wordCloud.setFontScalar(new SqrtFontScalar(10, 40));
        wordCloud.build(wordFrequencies);
        wordCloud.writeToFile("output/datarank_wordcloud_circle_sqrt_font.png");
    }

    public static void main(String[] args) throws IOException {
        repo = new HashSet<>();
        String serPath = "ser/bad-lambdas/test/";
        String writePath = "statistics/log-message-analysis";
        List<SimplifiedModifiedLambda> removedLambdaList = new ArrayList<>();

        try {
            String[] readPath = {serPath + "03-13"};
            for (String path : readPath)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (!serFile.toString().endsWith(".ser")) continue;
                    FileInputStream fileIn = new FileInputStream(serFile);
                    ObjectInputStream in = new ObjectInputStream(fileIn);
                    SimplifiedModifiedLambda[] removedLambdaArray = (SimplifiedModifiedLambda[]) in.readObject();
                    removedLambdaList.addAll(new ArrayList<>(Arrays.asList(removedLambdaArray)));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        List<String> msgList = new ArrayList<>();

//        Collections.sort(removedLambdaList, (removedLambda1, removedLambda2) -> removedLambda1.commitMessage.compareTo(removedLambda2.commitMessage));
        for (SimplifiedModifiedLambda removedLambda : removedLambdaList)
        {
            repo.add(removedLambda.commitURL.split("/")[4]);
            msgList.add(removedLambda.commitMessage);
        }

        removeRevertCommitMsg(msgList);
        removeDuplicateCommits(msgList);
        //System.out.println(msgList.size());
        //String txtPath = "statistics/commit-message-analysis/msg-pool/msg-of-" + repo.toString() + ".txt";
        String txtPath = "statistics/commit-message-analysis/msg-pool/msg-of-[hive, beam, cloudstack, tomcat, ambari, myfaces, geode, hadoop, netbeans, hbase].txt";
        //toTxt(msgList, txtPath);
        File msgPool = new File(txtPath);
        drawWordCloud(msgPool);
        //removeStopWords(msgList);
    }
}
