import java.util.ArrayList;
import java.util.List;
import java.io.FileFilter;

public class lc01 {
    //ArrayList<Integer> list = new ArrayList<Integer>();

    Runnable runnable = () -> { System.out.println("hi"); };

    public void fun1()
    {
        int a, b, c;
        System.out.println("fun1");
    }
    int a = 1;
    public static void main(String[] args){
        System.out.println("hahaha");
        System.out.println("hehehe");
        FileFilter java = f -> f.getName().endsWith(".java");
        System.out.println("this is lc01.java");

        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(3);
        List<Integer> aList = new ArrayList<>();
        list.forEach(e -> { aList.add(e); }); // Legal, open over variables
    }
}