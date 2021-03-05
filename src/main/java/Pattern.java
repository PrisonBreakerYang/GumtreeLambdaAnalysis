////import com.helger.commons.csv.CSVWriter;
//import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
//import org.apache.poi.ss.usermodel.Cell;
//import org.apache.poi.ss.usermodel.Row;
//import org.apache.poi.ss.usermodel.Sheet;
//import org.apache.poi.ss.usermodel.Workbook;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.csvreader.CsvWriter;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import opennlp.tools.stemmer.PorterStemmer;

public class Pattern
{
    List<ModifiedLambda> modifiedLambdas;
    public void calculate(List<ModifiedLambda> modifiedLambdas)
    {

    }
    public void statisticsToCsv() throws IOException {
        List<ModifiedLambda> modifiedLambdas = this.modifiedLambdas;
        Set<String> allActionTypeSet = new HashSet<>();
        for (ModifiedLambda lambda : modifiedLambdas)
        {
            allActionTypeSet.addAll(lambda.actionTypeSet);
        }
        //Map<String, Integer> map = set.stream().collect(Collectors.toMap(x -> x, x -> 0));
        Map<String, Integer> allActionNumberMap = allActionTypeSet.stream().collect(Collectors.toMap(x -> x, x -> 0));
        for (ModifiedLambda lambda : modifiedLambdas)
        {
            for (String pattern : lambda.actionTypeMap.keySet())
            {
                allActionNumberMap.put(pattern, allActionNumberMap.get(pattern) + lambda.actionTypeMap.get(pattern));
            }
        }

        String filePath = "statistics/statistics.csv";
        CsvWriter csvWriter = new CsvWriter(filePath, ',', Charset.forName("GBK"));
        String[] headers = {"patterns", "amount"};

        List<Map.Entry<String, Integer>> list = new ArrayList<>(allActionNumberMap.entrySet());
        list.sort(Map.Entry.comparingByValue());

        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list)
        {
            result.put(entry.getKey(), entry.getValue());
        }
        allActionNumberMap = result;

        for (Map.Entry<String, Integer> entry : allActionNumberMap.entrySet())
        {
            headers = new String[]{entry.getKey(), entry.getValue().toString()};
            csvWriter.writeRecord(headers);
        }
        csvWriter.close();
    }
    public Pattern(List<ModifiedLambda> modifiedLambdas)
    {
        this.modifiedLambdas = modifiedLambdas;
    }
    static boolean commitRelatedToKeywords(String message, String[] keywords)
    {
        PorterStemmer stemmer = new PorterStemmer();
        if (keywords == null)
        {
            return true;
        }
        String stemmed = stemmer.stem(message);
        System.out.println(stemmed);
        String stemMsg = stemmer.toString();
        String[] tokens = stemMsg.split("\\W+");

        for (String keyword : keywords)
        {
            for (String token : tokens)
            {
                if (token.equals(keyword))
                {
                    return true;
                }
            }
        }
        return false;
    }
    public static void main(String[] args) throws IOException {
        String message = "overhead";
        System.out.println(Pattern.commitRelatedToKeywords(message, new String[]{"overhead"}));
    }
}
