package v15.schedulingtest.cases.case1;

import com.jd.platform.async.scheduling.exception.IllegalSchedulingException;
import com.jd.platform.async.scheduling.model.SchedulingJsonModelParser;
import v15.schedulingtest.FileStringReader;

/**
 * @author create by TcSnZh on 2021/5/17-上午11:49
 */
class Case1 {
    public static void main(String[] args) throws IllegalSchedulingException {
        String json = FileStringReader.readFile("v15/case1_1.json");
        System.out.println(SchedulingJsonModelParser.getDefaultInstance().parseToModel(json));
    }
}
