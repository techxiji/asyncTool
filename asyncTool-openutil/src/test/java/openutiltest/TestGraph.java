package openutiltest;

import com.jd.platform.async.openutil.collection.CommonDirectedGraph;
import com.jd.platform.async.openutil.collection.DirectedGraph;

import java.util.Arrays;

/**
 * 测试图工具类的使用
 *
 * @author create by TcSnZh on 2021/5/16-下午11:25
 */
public class TestGraph {
    public static void main(String[] args) {
        test_CommonDirectedGraph();
    }

    private static void test_CommonDirectedGraph() {
        System.out.println("\n\n ==================== 测试正常使用 ==================");
        //noinspection unchecked
        DirectedGraph<String, String> graph =
                new PrintProxy<>(DirectedGraph.class).proxyTo(new CommonDirectedGraph<>(), "graph");
        graph.isDirected();
        graph.addNode("胖虎");
        graph.addNode("大雄");
        graph.putRelation("胖虎", "打", "大雄");
        graph.addNode("静香");
        graph.nodesView().addAll(Arrays.asList("小夫", "胖虎的妹妹", "哆啦A梦"));
        graph.putRelation("胖虎", "是其哥", "胖虎的妹妹");
        graph.putRelation("胖虎的妹妹", "是其妹", "胖虎");
        graph.putRelation("胖虎的妹妹", "喜欢", "大雄");
        graph.putRelation("胖虎", "????", "小夫");
        graph.putRelation("大雄", "喜欢", "静香");
        graph.removeNode("大雄");
        graph.getRelations();
    }
}
