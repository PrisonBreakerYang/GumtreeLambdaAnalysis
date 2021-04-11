package com.github.gumtreelambdaanalysis;

import com.kennycason.kumo.CollisionMode;
import com.kennycason.kumo.WordCloud;
import com.kennycason.kumo.WordFrequency;
import com.kennycason.kumo.bg.RectangleBackground;
import com.kennycason.kumo.font.scale.SqrtFontScalar;
import com.kennycason.kumo.nlp.FrequencyAnalyzer;
import com.kennycason.kumo.nlp.filter.Filter;
import com.kennycason.kumo.nlp.normalize.LowerCaseNormalizer;
import com.kennycason.kumo.nlp.tokenizers.EnglishWordTokenizer;
import com.kennycason.kumo.palette.ColorPalette;
import com.kennycason.kumo.placement.RTreeWordPlacer;
import opennlp.tools.stemmer.PorterStemmer;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import static com.helger.commons.io.resource.ClassPathResource.getInputStream;

public class CommitMsgAnalysis
{
    private static HashSet<String> repoSet;
    private static void loadRepoSet() throws IOException {
        if (repoSet == null) repoSet = new HashSet<>();
        File reposPath = new File("D:\\coding\\java_project\\repos");
        assert reposPath.isDirectory();
        for (String file : reposPath.list())
        {
            if (!file.startsWith("apache_list")) continue;
            FileInputStream fileInputStream = new FileInputStream(reposPath + "\\" + file);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String repo;
            while((repo = bufferedReader.readLine()) != null) {
                repoSet.add(repo.split("/")[1]);
            }
            fileInputStream.close();
            inputStreamReader.close();
            bufferedReader.close();
        }
    }
    public static void recordCommonCommitsMessage(String commonCommitsMsgPath) throws IOException, GitAPIException {
        loadRepoSet();
        System.out.println(repoSet);

        if (!Files.exists(Paths.get(commonCommitsMsgPath))) {
            new File(commonCommitsMsgPath);
        }
        else return;
        for (String repo : repoSet)
        {
            String repoPath = "../repos/" + repo;
            Repository repository = new FileRepositoryBuilder().setGitDir(new File(repoPath + "/.git")).build();
            Git git = new Git(repository);
            System.out.println(repo);
            Iterable<RevCommit> revCommits = git.log().call();
            List<String> msgList = new ArrayList<>();
            revCommits.forEach(revCommit -> msgList.add(msgProcess(revCommit.getFullMessage())));
            //System.out.println(repo + ":" + msgList.size());
            //msgList.forEach(msg -> String.join(" ", tokenizer.tokenize(msg)));
            toTxt(msgList, commonCommitsMsgPath);
        }
    }
    public static List<SimplifiedModifiedLambda> removeDuplicateCommits(List<SimplifiedModifiedLambda> lambdas)
    {
        List<SimplifiedModifiedLambda> processedLambdas = new ArrayList<>();
        Set<String> msgSet = new HashSet<>();
        for (SimplifiedModifiedLambda lambda : lambdas)
        {
            if (msgSet.contains(lambda.commitURL)) continue;
            else
            {
                msgSet.add(lambda.commitURL);
                processedLambdas.add(lambda);
            }
        }
        return processedLambdas;
    }

    public static void removeRevertCommitMsg(List<String> msgList)
    {
        msgList.removeIf(msg -> msg.contains("This reverts commit"));
    }

    public static void toTxt(List<String> msgList, String txtPath) throws IOException {
        FileWriter fw = new FileWriter(new File(txtPath),true);
        BufferedWriter bw = new BufferedWriter(fw);
        EnglishWordTokenizer tokenizer = new EnglishWordTokenizer();
        for (String msg : msgList)
        {
            List<String> words = tokenizer.tokenize(msg);
            String tokenizedMsg = String.join(" ", words);
            bw.write(tokenizedMsg);
            bw.write("\n");
        }
        bw.close(); fw.close();
    }

    public static String stemming(String msg)
    {
        PorterStemmer stemmer = new PorterStemmer();
        SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
        String[] words = tokenizer.tokenize(msg);
        StringBuilder stemmedMsg = new StringBuilder();
        for (String word : words)
        {
            stemmedMsg.append(stemmer.stem(word));
            stemmer.reset();
            stemmedMsg.append(" ");
        }
        return stemmedMsg.toString();
    }

    public static Collection<String> loadStopWords() throws IOException, GitAPIException {
        Collection<String> stopWords = new ArrayList<>();

        //common commits stop words
        String commonCommitsMsgPath = "statistics/commit-message-analysis/common-commit-messages-of-" + repoSet.toString() + ".txt";
        recordCommonCommitsMessage(commonCommitsMsgPath);
        final FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
        frequencyAnalyzer.setStopWords(repoSet);
        frequencyAnalyzer.setWordFrequenciesToReturn(100);
        frequencyAnalyzer.addFilter(new Filter() {
            @Override
            public boolean test(String s) {
                return !isNumeric(s);
            }
        });
        final List<WordFrequency> wordFrequencies = frequencyAnalyzer.load(new File(commonCommitsMsgPath));
        wordFrequencies.sort(WordFrequency::compareTo);

        //NLTK stop words
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
        fileInputStream.close();
        inputStreamReader.close();
        bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopWords.addAll(repoSet); //project names are stop words
        wordFrequencies.forEach(s -> stopWords.add(s.getWord()));
        return stopWords;
    }
    public static Set<String> loadNLTKStopWords()
    {
        String NLTK_stop_words_path = "resources/NLTK's-list-of-english-stopwords.txt";
        Set<String> stopWords = new HashSet<>();
        try {
            File stopWordsFile = new File(NLTK_stop_words_path);
            FileInputStream fileInputStream = new FileInputStream(stopWordsFile);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String text;
            while((text = bufferedReader.readLine()) != null) {
                stopWords.add(text); //stop words from NLTK
            }
            fileInputStream.close();
            inputStreamReader.close();
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stopWords;
    }
    public static boolean isNumeric(final String str) {
        // null or empty
        if (str == null || str.length() == 0) {
            return false;
        }
        return str.chars().allMatch(Character::isDigit);
    }
    public static void drawWordCloud(File msgPool) throws IOException, GitAPIException {
        final FrequencyAnalyzer frequencyAnalyzer = new FrequencyAnalyzer();
        frequencyAnalyzer.setStopWords(loadStopWords());
        //frequencyAnalyzer.setMinWordLength(5);
        frequencyAnalyzer.setWordTokenizer(new EnglishWordTokenizer());
        frequencyAnalyzer.addFilter(new Filter() {
            @Override
            public boolean test(String s) {
                return !isNumeric(s);
            }
        });
        frequencyAnalyzer.addNormalizer(new LowerCaseNormalizer());
        frequencyAnalyzer.setWordFrequenciesToReturn(100);
        final List<WordFrequency> wordFrequencies = frequencyAnalyzer.load(msgPool);
        final Dimension dimension = new Dimension(600, 600);
        final WordCloud wordCloud = new WordCloud(dimension, CollisionMode.RECTANGLE);
        //final WordCloud wordCloud = new ParallelLayeredWordCloud(1, dimension, CollisionMode.PIXEL_PERFECT).getAt(0);
        wordCloud.setWordPlacer(new RTreeWordPlacer());
        wordCloud.setPadding(2);
        wordCloud.setBackground(new RectangleBackground(dimension));
        wordCloud.setColorPalette(new ColorPalette(new Color(0x4055F1), new Color(0x408DF1), new Color(0x40AAF1), new Color(0x40C5F1), new Color(0x40D3F1), new Color(0xFFFFFF)));
        wordCloud.setFontScalar(new SqrtFontScalar(10, 40));
        wordCloud.build(wordFrequencies);
        wordCloud.writeToFile("output/datarank_wordcloud_circle_sqrt_font.png");
    }

    public static String removeStopWords(String msg)
    {
        msg = msg.toLowerCase();
        SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
        String[] words = tokenizer.tokenize(msg);
        Set<String> stopWords = loadNLTKStopWords();
        if (repoSet!=null) stopWords.addAll(repoSet);
        //System.out.println(stopWords);
        StringBuilder processedMsg = new StringBuilder();
        for (String word : words)
        {
            if (stopWords.contains(word)) continue;
            if (isNumeric(word)) continue;
            processedMsg.append(word);
            processedMsg.append(" ");
        }
        return processedMsg.toString();
    }
    public static String msgProcess(String msg)
    {
        String regEx="[-\n`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。， 、？]";
        String aa = " ";//这里是将特殊字符换为aa字符串,""代表直接去掉
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(msg);//这里把想要替换的字符串传进来
        msg = m.replaceAll(aa).trim(); //将替换后的字符串存在变量newString中
        msg = removeStopWords(msg);
        msg = stemming(msg);
        EnglishWordTokenizer tokenizer = new EnglishWordTokenizer();
        return String.join(" ", tokenizer.tokenize(msg.strip()));
    }
    public static void main(String[] args) throws IOException, GitAPIException {
        repoSet = new HashSet<>();
        String serPath = "ser/bad-lambdas";
        String writePath = "statistics/commit-message-analysis";
        List<SimplifiedModifiedLambda> removedLambdaList = new ArrayList<>();

        try {
            //String[] readPath = {serPath + "03-15", serPath + "03-16", serPath + "03-17", serPath + "03-18"};
            String[] readPath = {serPath};
            for (String path : readPath)
            {
                File file = new File(path);
                File[] fileList = file.listFiles();
                assert fileList != null;
                for (File serFile : fileList)
                {
                    if (!serFile.toString().endsWith("51repos-filtered-without-edit-limit.ser")) continue;
                    FileInputStream fileIn = new FileInputStream(serFile);
                    ObjectInputStream in = new ObjectInputStream(fileIn);
                    SimplifiedModifiedLambda[] removedLambdaArray = (SimplifiedModifiedLambda[]) in.readObject();
                    removedLambdaList.addAll(new ArrayList<>(Arrays.asList(removedLambdaArray)));
                    fileIn.close();
                    in.close();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        removedLambdaList.removeIf(lambda -> lambda.commitMessage.contains("This reverts commit"));
        removedLambdaList = removeDuplicateCommits(removedLambdaList);
        File txt = new File("statistics/msg-and-url-of-removed-lambdas-51repos-without-edit-limit.txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(txt));

        for (SimplifiedModifiedLambda lambda : removedLambdaList)
        {
            //if (lambda.javaFileModified > 10) continue;
            //if (lambda.commitMessage.contains("This reverts commit")) continue;
            repoSet.add(lambda.commitURL.split("/")[4]);
            //if (!lambda.commitMessage.contains("lambda")) continue;
            //writer.write(msgProcess(lambda.commitMessage));
            //writer.write(msgProcess(lambda.commitMessage));
            writer.write(lambda.commitURL + "\t\t" + msgProcess(lambda.commitMessage));
            writer.newLine();
        }
        writer.flush();
        writer.close();


//        List<String> msgList = new ArrayList<>();
//
//        int wordLengthThreshold = 100;
//
////        Collections.sort(removedLambdaList, (removedLambda1, removedLambda2) -> removedLambda1.commitMessage.compareTo(removedLambda2.commitMessage));
//        for (SimplifiedModifiedLambda removedLambda : removedLambdaList)
//        {
//            repoSet.add(removedLambda.commitURL.split("/")[4]);
//            if (removedLambda.javaFileModified > 10) continue;
////            EnglishWordTokenizer tokenizer = new EnglishWordTokenizer();
////            if (tokenizer.tokenize(removedLambda.commitMessage).size() > wordLengthThreshold) continue;
//            msgList.add(removedLambda.commitMessage);
//        }
//
//        //removeRevertCommitMsg(msgList);
//        System.out.println(msgList.size());
//        //msgList = removeDuplicateCommits(msgList);
//        //System.out.println(msgList.size());
//        String txtPath = writePath + "/msg-pool/msg-of-" + "apache-list-1-3" + ".txt";
//        //String txtPath = "statistics/commit-message-analysis/msg-pool/msg-of-[hive, beam, cloudstack, tomcat, ambari, myfaces, geode, hadoop, netbeans, hbase].txt";
//        if (!Files.exists(Paths.get(txtPath))) {
//            new File(txtPath);
//            toTxt(msgList, txtPath);
//        }
//
//        String commonCommitsMsgPath = "statistics/commit-message-analysis/common-commit-messages-of-" + "apache-list-1-3" + ".txt";
//        recordCommonCommitsMessage(commonCommitsMsgPath);

        //File msgPool = new File(txtPath);
        //drawWordCloud(msgPool);
        //removeStopWords(msgList);
    }

    public static void main2(String[] args) throws IOException, GitAPIException {
        recordCommonCommitsMessage("D:\\coding\\java_project\\GumtreeLambdaAnalysis\\statistics\\msg-of-common-commits-5list.txt");
    }
    public static void main3(String[] args)
    {
        String a = "asdf";
        String b = "asdf";
        List<String> list = new ArrayList<>();
        list.add(a);
        list.add("123");
        System.out.println(list.contains(a));
        System.out.println(list.contains(b));
    }
}
