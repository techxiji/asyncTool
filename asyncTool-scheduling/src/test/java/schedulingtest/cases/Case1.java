package schedulingtest.cases;

import com.jd.platform.async.scheduling.SchedulingJsonParser;
import schedulingtest.FileStringReader;

/**
 * @author create by TcSnZh on 2021/5/17-上午11:49
 */
class Case1 {
    public static void main(String[] args) {
        String json = FileStringReader.readFile("test.json");
        System.out.println(SchedulingJsonParser.getDefaultInstance().parse(json));
    }
}
