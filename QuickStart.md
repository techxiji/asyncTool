如果只是需要用这个框架，请往下看即可。如果需要深入了解这个框架是如何一步一步实现的，从接到需求，到每一步的思考，每个类为什么这么设计，为什么有这些方法，也就是如何从0到1开发出这个框架，作者在[csdn开了专栏](https://blog.csdn.net/tianyaleixiaowu/category_9637010.html)专门讲中间件如何从0开发，包括并不限于这个小框架。京东内部同事可在cf上搜索erp也能看到。

# 安装教程

代码不多，直接拷贝包过去即可。

#### 旧稳定版本v1.4

京东同事通过引用如下maven来使用。

```xml
<dependency>
    <groupId>com.jd.platform</groupId>
    <artifactId>asyncTool</artifactId>
    <version>1.4.1-SNAPSHOT</version>
</dependency>
```
外网请使用jitpack.io上打的包
先添加repositories节点

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
然后添加如下maven依赖

```xml
<dependency>
    <groupId>com.gitee.jd-platform-opensource</groupId>
    <artifactId>asyncTool</artifactId>
    <version>V1.4-SNAPSHOT</version>
</dependency>
```

#### 最新版本v1.5（不稳定）



从gitee上下载仓库到本地，切换到`dev`分支，然后maven安装到本地仓库。

```bash
git clone https://gitee.com/jd-platform-opensource/asyncTool.git
cd ./asyncTool
git checkout dev
mvn install
```

在项目中引入依赖。

```xml
<!-- 任务编排核心包 -->
<dependency>
    <artifactId>asyncTool-core</artifactId>
    <groupId>com.jd.platform</groupId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
<!-- 动态任务调度，有需要的话可以引入。依赖fastjson，请自行解决版本冲突 -->
<dependency>
    <artifactId>asyncTool-scheduling</artifactId>
    <groupId>com.jd.platform</groupId>
    <version>1.5.0-SNAPSHOT</version>
</dependency>
```

# 任务编排

> `asyncTool-core`核心模块提供了核心功能——任务编排
>
> 以下文档基于版本：
>
> ```xml
> <dependencies>
>        <dependency>
>            <groupId>com.jd.platform</groupId>
>            <artifactId>asyncTool-core</artifactId>
>            <version>1.5.0-SNAPSHOT</version>
>        </dependency>
> </dependencies>
> ```

### 基本组件

`IWorker`：  一个最小的任务执行单元。通常是一个网络调用，或一段耗时操作。

T，V两个泛型，分别是入参和出参类型。

譬如该耗时操作，入参是String，执行完毕的结果是Integer，那么就可以用泛型来定义。

多个不同的worker之间，没有关联，分别可以有不同的入参、出参类型。

```java
/**
 * 每个最小执行单元需要实现该接口
 *
 * @author wuweifeng wrote on 2019-11-19.
 */
@FunctionalInterface
public interface IWorker<T, V> {
    /**
     * 在这里做耗时操作，如rpc请求、IO等
     *
     * @param object      object
     * @param allWrappers 任务包装
     */
    V action(T object, Map<String, WorkerWrapper> allWrappers);

    /**
     * 超时、异常、跳过时，返回的默认值
     *
     * @return 默认值
     */
    default V defaultValue() {
        return null;
    }
}
```

`ICallback`：对每个worker的回调。worker执行完毕后，会回调该接口，带着执行成功、失败、原始入参、和详细的结果。

```java
/**
 * 每个执行单元执行完毕后，会回调该接口</p>
 * 需要监听执行结果的，实现该接口即可
 *
 * @author wuweifeng wrote on 2019-11-19.
 */
@FunctionalInterface
public interface ICallback<T, V> {

    /**
     * 任务开始的监听
     */
    default void begin() {

    }

    /**
     * 耗时操作执行完毕后，就给value注入值
     * <p/>
     * 只要Wrapper被调用后成功或失败/超时，该方法都会被执行。
     */
    void result(boolean success, T param, WorkResult<V> workResult);
}
```

wrapper：组合了worker和callback，是一个 **最小的调度单元** 。通过编排wrapper之间的关系，达到组合各个worker顺序的目的。

wrapper的泛型和worker的一样，决定了入参和结果的类型。

```java
// 创建一个WorkerWrapper
WorkerWrapper<String, String> w0 = WorkerWrapper.<String, String>builder()
                .id("0")
                .param("000")
                .worker((param, allWrappers) -> "hello : " + param)
                .build();
```

通过这一个类看一下，action里就是你的耗时操作，begin就是任务开始执行时的回调，result就是worker执行完毕后的回调。当你组合了多个执行单元时，每一步的执行，都在掌控之内。失败了，还会有自定义的默认值。这是CompleteableFuture无法做到的。

### 如何构造WorkerWrapper?

##### 推荐Builder模式

如果刚开始使用这个框架，则推荐使用如下方式进行构造：

```java
WorkerWrapper.<String, String>builder()
    .id()
    // 其他属性略。
    // 请在《简单示例》与《设置WorkerWrapper属性》中慢慢感受详细内容。
    // 因为这里地方小，写不下。
```

##### 复杂的快速构造

> 不推荐新手使用。
>
> 不推荐在业务中使用，使用Builder模式代码更加简洁，且会检查参数，不必节省这些性能。
>
> 该对象的构造方法不会检查属性。

在对WorkerWrapper属性有充足了解后，可使用“直接设置属性 + 关系图”的方式快速构造wrapper。

建议在扩展功能的时候使用该构造器，以提高效率。但是请记得检查参数。

以下为示例：

```java
package v15.cases;

import com.jd.platform.async.callback.DefaultCallback;
import com.jd.platform.async.executor.Async;
import com.jd.platform.async.openutil.collection.CommonDirectedGraph;
import com.jd.platform.async.openutil.collection.DirectedGraph;
import com.jd.platform.async.wrapper.QuickBuildWorkerWrapper;
import com.jd.platform.async.wrapper.WorkerWrapper;
import com.jd.platform.async.wrapper.strategy.WrapperStrategy;

import java.util.concurrent.*;

/**
 * 快速构造示例。
 *
 * @author create by TcSnZh on 2021/5/17-下午5:23
 */
class Case9 {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        DirectedGraph<WorkerWrapper<?, ?>, Object> graph = DirectedGraph.synchronizedDigraph(new CommonDirectedGraph<>());
        QuickBuildWorkerWrapper<Object, Object> w1 = new QuickBuildWorkerWrapper<>("id1",
                null,
                (object, allWrappers) -> {
                    System.out.println("I am IWorker 1");
                    return null;
                },
                new DefaultCallback<>(),
                false,
                true,
                100,
                TimeUnit.MILLISECONDS,
                new WrapperStrategy.DefaultWrapperStrategy(),
                graph
        );
        QuickBuildWorkerWrapper<Object, Object> w2 = new QuickBuildWorkerWrapper<>("id2",
                null,
                (object, allWrappers) -> {
                    System.out.println("I am IWorker 2");
                    return null;
                },
                new DefaultCallback<>(),
                false,
                true,
                100,
                TimeUnit.MILLISECONDS,
                new WrapperStrategy.DefaultWrapperStrategy(),
                graph
        );
        graph.addNode(w1, w2);
        graph.putRelation(w1, new Object(), w2);

//        System.out.println(graph);

        Async.beginWork(200, w1);

        System.out.println("    Begin work end .\n    w1 : " + w1 + "\n    w2 : " + w2 + "\n");

    }
}
```

### 简单示例

1.  3个任务并行

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/140256_8c015621_303698.png "屏幕截图.png")

```java
class Test {
    static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    return null;
                });
    }

    public static void main(String[] args) {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> b = builder("B").build();
        WorkerWrapper<?, ?> c = builder("C").build();
        try {
            Async.beginWork(100, a, b, c);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        /* 输出:
        wrapper(id=A) is working
        wrapper(id=B) is working
        wrapper(id=C) is working
        */
    }
}
```


2.  1个执行完毕后，开启另外两个，另外两个执行完毕后，开始第4个

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/140405_93800bc7_303698.png "屏幕截图.png")

```java
class Test {
    static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    return null;
                });
    }

    public static void main(String[] args) {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> b = builder("B").depends(a).build();
        WorkerWrapper<?, ?> c = builder("C").depends(a).build();
        WorkerWrapper<?, ?> f = builder("F").depends(b, c).build();
        try {
            Async.beginWork(100, a);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        /* 输出:
        wrapper(id=A) is working
        wrapper(id=C) is working
        wrapper(id=B) is working
        wrapper(id=F) is working
        */
    }
}
```

如果觉得`.depneds()`方法的排序您不喜欢，也可以用`.nextOf()`这种方式：

```java
class Test {
    static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    return null;
                });
    }

    public static void main(String[] args) {
        WorkerWrapper<?, ?> f = builder("F").build();
        WorkerWrapper<?, ?> a = builder("A")
                .nextOf(builder("B").nextOf(f).build())
                .nextOf(builder("C").nextOf(f).build())
                .build();
        try {
            Async.beginWork(100, a);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        /* 输出:
        wrapper(id=A) is working
        wrapper(id=B) is working
        wrapper(id=C) is working
        wrapper(id=F) is working
        */
    }
}
```



3. 复杂点的

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/140445_8d52e4d6_303698.png "屏幕截图.png")

```java
class Case1 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
    }

    public static void main(String[] args) {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> d;
        builder("H")
                .depends(
                        builder("F")
                                .depends(builder("B").depends(a).build())
                                .depends(builder("C").depends(a).build())
                                .build(),
                        builder("G")
                                .depends(builder("E")
                                        .depends(d = builder("D").build())
                                        .build())
                                .build()
                )
                .build();
        try {
            Async.beginWork(1000, a, d);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        /* 输出:
        wrapper(id=D) is working
        wrapper(id=A) is working
        wrapper(id=E) is working
        wrapper(id=B) is working
        wrapper(id=C) is working
        wrapper(id=G) is working
        wrapper(id=F) is working
        wrapper(id=H) is working
        */
    }
}
```

4. 依赖别的worker执行结果作为入参

可以从action的参数中根据wrapper的id获取任意一个执行单元的执行结果。

但请注意执行顺序，如果尚未执行，则在调用`WorkerResult.getResult()`会得到null！

```java
class Case2 {
    static class AddWork implements IWorker<Integer, Integer> {
        private final String id1;
        private final String id2;

        public AddWork(String id1, String id2) {
            this.id1 = id1;
            this.id2 = id2;
        }

        public AddWork() {
            this(null, null);
        }

        @Override
        public Integer action(Integer param, Map<String, WorkerWrapper<?,?>> allWrappers) {
            // 传入的参数
            if (param != null) {
                return param;
            }
            // 将两个id所对应的wrapper的结果取出，相加并返回
            Integer i1 = (Integer) allWrappers.get(id1).getWorkResult().getResult();
            Integer i2 = (Integer) allWrappers.get(id2).getWorkResult().getResult();
            return i1 + i2;
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<Integer, Integer> wrapper100 = WorkerWrapper.<Integer, Integer>builder()
                .id("id:100").worker(new AddWork()).param(100).build();
        WorkerWrapper<Integer, Integer> wrapper200 = WorkerWrapper.<Integer, Integer>builder()
                .id("id:200").worker(new AddWork()).param(200).build();
        WorkerWrapper<Integer, Integer> add = WorkerWrapper.<Integer, Integer>builder().id("id:add")
                .worker(new AddWork("id:100", "id:200")).depends(wrapper100, wrapper200).build();
        Async.beginWork(20,wrapper100,wrapper200);
        System.out.println(add.getWorkResult());
        // 输出WorkResult{result=300, resultState=SUCCESS, ex=null}
    }
}
```

5.  其他的详见test包下的测试类，支持各种形式的组合、编排。

### 使用自定义线程池

`Async`工具类有多个方法可以使用自定义线程池

```java
public static boolean beginWork(long timeout,
                                    ExecutorService executorService,
                                    Collection<? extends WorkerWrapper<?,?>> workerWrappers);
    
public static boolean beginWork(long timeout, ExecutorService executorService, WorkerWrapper... workerWrapper);
```

另外，如果没有指定线程池，默认会使用`COMMON_POOL`，您可以调用这些方法获得/关闭此线程池：

> 此线程池将会在第一次使用时懒加载。

```java
/**
 * 该方法将会返回{@link #COMMON_POOL}，如果还未初始化则会懒加载初始化后再返回。
 */
public static ThreadPoolExecutor getCommonPool();

/**
 * @param now 是否立即关闭
 * @return 如果尚未调用过{@link #getCommonPool()}，即没有初始化默认线程池，返回false。否则返回true。
 */
public static synchronized boolean shutDownCommonPool(boolean now);
```

以下是一个使用自定义线程池的简单代码示例：

```java
Async.beginWork(1000, Executors.newFixedThreadPool(2),a);
```

## WorkerWrapper基本属性

### 执行流程

WorkerWrapper会在这些情况被运行：

* 在`Async.beginWork`中传入wrapper
* 上游wrapper完成后被调用

开始运行时，执行逻辑如下图所示：

![wrapper执行流程](https://gitee.com/tcsn_site/picture/raw/master/wrapper-process.png)

> processOn流程图文件放在同仓库。

### 属性

#### id

`WorkerWrapper`的id属性非常重要。

可在builder的该属性设置id，如果不设置，默认使用UUID。

```java
public interface WorkerWrapperBuilder<T, V> {
    /**
     * 设置唯一id。
     * 如果不设置，{@link StableWorkerWrapperBuilder}会使用UUID
     */
    WorkerWrapperBuilder<T, V> id(String id);
    
    // 略
}
```

例如如果你需要在`IWorker`中调用上游wrapper，则可以根据id来获取到。

> 该map的键即为`WorkerWrapper`的id。

```java
V action(T object, Map<String, WorkerWrapper<?,?>> allWrappers);
```

请程序员确保在一次任务执行的一组wrapper中，id不会重复。在执行过程中不会进行检查。

#### 其他省略

> 其他属性都写在源码注释中，可下载源码慢慢查看。

## 设置WorkerWrapper属性

### 设置依赖策略

#### 快速上手

`WorkerWrapperBuilder`提供了这些方法来设置依赖策略：

```java
public interface WorkerWrapperBuilder<T, V> {
    // 略
    
    // 各种depends方法都是简便设置依赖的。
    default WorkerWrapperBuilder<T, V> depends(/* 略 */);
    
    // 切换到SetDepend模式
    SetDepend<T, V> setDepend();

    interface SetDepend<T, V> {
        // 其中各种方法都是用来设置策略的，具体点开源码看注释就行。设置完end()回到Builder模式。
    }
    
    // 略
}
```

如果没有具体设置策略的话，默认使用`DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS`。具体效果可到《策略器组件默认实现》这一章查看。

例如：

> A之后是B1、B2、B3、B4、B5
>
> 其中B1与B2全部成功后才能执行C1，
>
> B3、B4、B5任意一个成功后就能执行C2。

以下为代码实现：

```java
class Case3 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> b1 = builder("B1").depends(a).build();
        WorkerWrapper<?, ?> b2 = builder("B2").depends(a).build();
        WorkerWrapper<?, ?> b3 = builder("B3").depends(a).build();
        WorkerWrapper<?, ?> b4 = builder("B4").depends(a).build();
        WorkerWrapper<?, ?> b5 = builder("B5").depends(a).build();
        WorkerWrapper<?, ?> c1 = builder("C1")
                .depends(DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS, b1, b2)
                .build();
        WorkerWrapper<?, ?> c2 = builder("C2")
                .depends(DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS, b3, b4, b5)
                .build();
        // 这里用线程数较少的线程池做示例，对于ALL_DEPENDENCIES_ANY_SUCCESS“仅需一个”的效果会好一点
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.beginWork(1000, pool, a);
        } finally {
            pool.shutdown();
        }
        /* 输出:
        wrapper(id=A) is working
		wrapper(id=B3) is working
		wrapper(id=B1) is working
		wrapper(id=B2) is working
		wrapper(id=C2) is working
		wrapper(id=C1) is working
		wrapper(id=B4) is working
		// 我们看到B5被跳过了，没有执行callback
		*/
    }
}
```

##### 策略器组件

wrapper每次被上游wrapper所调用时，若是还没有结束且不应跳过，都会去根据自己的上游wrapper的状态，来决定自己这次该做什么。

`DependenceStrategy`这个函数式接口就是用来判断“这次该做什么的”。

```java
package com.jd.platform.async.wrapper.dependaction;

//...

import com.jd.platform.async.wrapper.strategy.depend.DependenceAction;

@FunctionalInterface
public interface DependenceStrategy {
    /**
     * 核心判断策略
     *
     * @param dependWrappers thisWrapper.dependWrappers的属性值。
     * @param thisWrapper    thisWrapper，即为“被催促”的WorkerWrapper
     * @param fromWrapper    调用来源Wrapper。
     *                       <p>
     *                       该参数不会为null。
     *                       因为在{@link WorkerWrapper#work(ExecutorService, long, Map, WrapperEndingInspector)}方法中传入的的第一批无依赖的Wrapper，
     *                       不会被该策略器所判断，而是不论如何直接执行。
     *                       </p>
     * @return 返回枚举值内部类，WorkerWrapper将会根据其值来决定自己如何响应这次调用。 {@link DependenceAction.WithProperty}
     */
    DependenceAction.WithProperty judgeAction(Set<WorkerWrapper<?, ?>> dependWrappers,
                                              WorkerWrapper<?, ?> thisWrapper,
                                              WorkerWrapper<?, ?> fromWrapper);

    // consts 略

    // methods 略
}
```

其返回值`DependenceAction.WithProperty`是枚举`DependenceAction`的一个内部实体类，作用是让返回的枚举值可以多带几个参数。

```java
public enum DependenceAction {
    START_WORK,
    TAKE_REST,
    FAST_FAIL,
    JUDGE_BY_AFTER;
    
    // methods ...
    
    public class WithProperty {/* ... */}
}
```

| 枚举值           | 含义                                                         |
| ---------------- | ------------------------------------------------------------ |
| `START_WORK`     | 开始工作。WorkerWrapper会执行工作方法。                      |
| `TAKE_REST`      | 还没轮到，休息一下。WorkerWrapper中的调用栈会返回，以等待其他上游wrapper调用它，或是会一生无缘被调用。 |
| `FAST_FAIL`      | 立即失败。WorkerWrapper会去执行快速失败的方法。              |
| `JUDGE_BY_AFTER` | 交给下层`{@link DependenceStrategy}`进行判断。 由于`{@link DependenceStrategy#thenJudge(DependenceStrategy)}`的责任链设计模式，该返回值的意义就是调用责任链上下一个策略。 |

> 如果wrapper被跳过，ResultState将为`DEFAULT`。
>
> 

##### 策略器组件默认实现

* `DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS`，该值为默认值，若builder未设置则默认使用这个。
  1. 被依赖的所有Wrapper都必须成功才能开始工作。
  2. 如果其中任一Wrapper还没有执行且不存在失败，则休息。
  3. 如果其中任一Wrapper失败则立即失败。*（跳过不算失败）*
* `DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS`
  1. 被依赖的Wrapper中任意一个成功了就可以开始工作。
  2. 如果其中所有Wrapper还没有执行，则休息。
  3. 如果其中一个Wrapper失败且不存在成功则立即失败。*（跳过不算失败）*
* `DependenceStrategy.ALL_DEPENDENCIES_NONE_FAILED`
  *   如果被依赖的工作中任一失败，则立即失败。*（跳过不算失败）*
  *   否则就开始工作（不论之前的工作有没有开始）。
* `DependenceStrategy.theseWrapperAllSuccess(Set<WorkerWrapper<?,?>>)`
  *   该方法传入一个`Set`指定wrapper，只有当指定的这些Wrapper都成功时，才会开始工作。任一失败会快速失败。任一还没有执行且不存在失败，则休息。
* 不建议使用：~~`DependenceStrategy.IF_MUST_SET_NOT_EMPTY_ALL_SUCCESS_ELSE_ANY`~~
  * 此值用于适配v1.4及之前的must开关模式，当`wrapperStrategy`的`dependMustStrategyMapper`的`mustDependSet`不为空时，则休息（因为能判断到这个责任链说明set中存在不满足的值）。为空时，则任一成功则执行。

##### `WorkerWrapper`的策略器责任链

`WorkerWrapper`在判断时，并不是只使用一个策略进行判断的，而是在`WrapperStrategy`进行了最多三层的判断：

```java
public interface WrapperStrategy extends DependenceStrategy, SkipStrategy {
    // ========== 这三个策略器用于链式判断是否要开始工作 ==========

    // 从前往后依次判断的顺序为 dependWrapperStrategyMapper -> dependMustStrategyMapper -> dependenceStrategy

    /**
     * 设置对特殊Wrapper专用的依赖响应策略。
     *
     * @return 该值允许为null
     */
    DependOnUpWrapperStrategyMapper getDependWrapperStrategyMapper();

    /**
     * 对必须完成的（must的）Wrapper的依赖响应策略。
     * 这是一个不得不向历史妥协的属性。用于适配must开关方式。
     *
     * @return 该值允许为null
     */
    DependMustStrategyMapper getDependMustStrategyMapper();

    /**
     * 底层全局策略。
     *
     * @return 该值不允许为null
     */
    DependenceStrategy getDependenceStrategy();

    // ========== 这是跳过策略 ==========

    /**
     * 跳过策略
     *
     * @return 不允许为null
     */
    SkipStrategy getSkipStrategy();
    
    // 其他属性略，自行查看源码即可
}
```

正如注释所言，三个策略器将依次调用`judgeAction(Set,WorkerWrapper,WorkerWrapper)`方法进行判断，每次判断会返回`DependenceAction.WithProperty`类型。

前两个策略器的返回值若不为枚举`JUDGE_BY_AFTER`的内部类时，整个三层责任链将返回此返回值；若为`JUDGE_BY_AFTER`，则交给下个策略器进行判断。该方法具体由以下方法实现：

```java
public interface DependenceStrategy {
    // 略
    
    /**
     * 如果本策略器的judge方法返回了JUDGE_BY_AFTER，则交给下一个策略器来判断。
     *
     * @param after 下层策略器
     * @return 返回一个“封装的多层策略器”
     */
    default DependenceStrategy thenJudge(DependenceStrategy after) {
        DependenceStrategy that = this;
        return new DependenceStrategy() {
            @Override
            public DependenceAction.WithProperty judgeAction(
                Set<WorkerWrapper<?, ?>> dependWrappers,
                WorkerWrapper<?, ?> thisWrapper,
                WorkerWrapper<?, ?> fromWrapper
            ) {
                DependenceAction.WithProperty judge = that.judgeAction(dependWrappers, thisWrapper, fromWrapper);
                if (judge.getDependenceAction() == DependenceAction.JUDGE_BY_AFTER) {
                    return after.judgeAction(dependWrappers, thisWrapper, fromWrapper);
                }
                return judge;
            }

            @Override
            public String toString() {
                return that + " ----> " + after;
            }
        };
    }
    
    // 略
}
```



#### 自定义依赖策略

##### 自定义全局策略

以下是一个自定义依赖策略的示例。

效果是，在B1~B10共10个wrapper，只需3个wrapper成功，即可执行C

```java
class Case4 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> a = builder("A").build();
        WorkerWrapper<?, ?> c = builder("C")
                .setDepend().strategy(new DependenceStrategy() {
                    @Override
                    public DependenceAction.WithProperty judgeAction(Set<WorkerWrapper<?, ?>> dependWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper) {
                        return dependWrappers.stream()
                                .filter(workerWrapper -> workerWrapper.getWorkResult().getResultState() == ResultState.SUCCESS)
                                .count() > 3 ?
                                DependenceAction.START_WORK.emptyProperty()
                                : DependenceAction.TAKE_REST.emptyProperty();
                    }
                }).end()
                .build();
        for (int i = 1; i < 10; i++) {
            builder("B" + i).depends(a).nextOf(c).build();
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.beginWork(1000, pool, a);
        } finally {
            pool.shutdown();
        }
        /* 输出:
        wrapper(id=A) is working
        wrapper(id=B2) is working
        wrapper(id=B1) is working
        wrapper(id=B4) is working
        wrapper(id=B3) is working
        wrapper(id=B5) is working
        wrapper(id=C) is working
        由于B1-B10是并行的，所以正好仅有3个wrapper成功，在多线程环境中是比较难遇到的。
         */
    }
}
```

##### ~~设置一组必须完成的wrapper（不推荐使用）~~

使用以下两个方法，指定的上游wrapper必须全部执行成功，本Wrapper才能执行。

```java
public interface WorkerWrapperBuilder<T, V> {
    // 略
    
    SetDepend<T, V> setDepend();
    
    interface SetDepend<T, V> {
        // 略
        
        /**
         * 设置必须要执行成功的Wrapper，当所有被该方法设为的上游Wrapper执行成功时，本Wrapper才能执行
         */
        SetDepend<T, V> mustRequireWrapper(WorkerWrapper<?, ?> wrapper);

        default SetDepend<T, V> mustRequireWrapper(WorkerWrapper... wrappers){
            /*...*/}
        
  		// 略
    }
    
    // 略
}
```

以下是一个不推荐使用的示例：

```java
class Case5 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
    }

    @Deprecated
    public static void main(String[] args) throws ExecutionException, InterruptedException {

        WorkerWrapper<?, ?> a1 = builder("A1").build();
        WorkerWrapper<?, ?> a2 = builder("A2").build();
        WorkerWrapper<?, ?> a3 = builder("A3").build();
        WorkerWrapper<?, ?> a4 = builder("A4").build();
        WorkerWrapper<?, ?> a5 = builder("A5").build();
        WorkerWrapper<?, ?> a6 = builder("A6").build();
        WorkerWrapper<?, ?> a7 = builder("A7").build();
        WorkerWrapper<?, ?> a8 = builder("A8").build();
        WorkerWrapper<?, ?> a9 = builder("A9").build();
        WorkerWrapper<?, ?> a10 = builder("A10").build();
        builder("B")
                .setDepend()
                // 必须a3、a4成功才能执行
                .mustRequireWrapper(a3, a4)
                // 如果a3、a4没有成功，则休息
                .strategy((dependWrappers, thisWrapper, fromWrapper) -> DependenceAction.TAKE_REST.emptyProperty())
                .wrapper(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)
                .end()
                .build();
        WorkerWrapper<?, ?> start = builder("start").nextOf(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10).build();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Async.beginWork(1000, pool, start);
        } finally {
            pool.shutdown();
        }
        /* 输出：
        wrapper(id=A1) is working
        wrapper(id=A2) is working
        wrapper(id=A4) is working
        wrapper(id=A3) is working
        wrapper(id=A5) is working
        wrapper(id=B) is working
        wrapper(id=A6) is working
        我们可以看到，A3、A4执行后，B也执行了，之后的wrapper被跳过了
        （这里之所以a5、a6还在执行，只是因为他两正好在WORKING，所以没发现后面的B已经可以跳过了）
        */
    }
}
```

`.mustRequireWrapper(a3, a4)`方法设置的策略，优先级高于`.strategy()`设置的底层策略。

##### 对单个wrapper设置“上克下”策略

我们面临着这种问题：

> 一般来说，都是下游wrapper去根据上游wrapper的状态进行策略判断，然后给出响应。

那么，能不能让上游wrapper根据自己的状态独自决定下游wrapper响应呢？

可以。`DependOnUpWrapperStrategy`函数式接口  与    `DependOnUpWrapperStrategyMapper`这两个类即可完成这个功能。

```java
/**
 * 由上游wrapper决定本wrapper行为的单参数策略。
 *
 * @author create by TcSnZh on 2021/5/1-下午11:16
 */
@FunctionalInterface
public interface DependOnUpWrapperStrategy {
    /**
     * 仅使用一个参数（即调用自身的上游wrapper）的判断方法
     *
     * @param fromWrapper 调用本Wrapper的上游Wrapper
     * @return 返回 {@link DependenceAction.WithProperty}
     */
    DependenceAction.WithProperty judge(WorkerWrapper<?, ?> fromWrapper);

    // ========== 送几个供链式调用的默认值 ==========

    /**
     * 成功时，交给下一个策略器判断。
     * 未运行时，休息。
     * 失败时，失败。
     */
    DependOnUpWrapperStrategy SUCCESS_CONTINUE = /*略*/ ;
    /**
     * 成功时，开始工作。
     * 未运行时，交给下一个策略器判断。
     * 失败时，失败。
     */
    DependOnUpWrapperStrategy SUCCESS_START_INIT_CONTINUE = /*略*/ ;
}
```

在`DependOnUpWrapperStrategyMapper`的`mapper`属性中，每个`WorkerWrapper<?, ?>`对应了一个`DependOnUpWrapperStrategy`，实现了让wrapper对不同的上游做出不同的响应策略。

```java
public class DependOnUpWrapperStrategyMapper implements DependenceStrategy {
    private final Map<WorkerWrapper<?, ?>, DependOnUpWrapperStrategy> mapper = new ConcurrentHashMap<>(4);
	// 以下略   
}
```

在《`WorkerWrapper`的三层策略器责任链》这一章中，我们可以看到，第一层策略器就是此`DependOnUpWrapperStrategyMapper`。

###### 简单使用与示例

我们在`SetDepend<T, V> setDepend();`模式时，可以使用如下方法进行设置

```java
/**
 * 对单个Wrapper设置特殊策略。
 *
 * @param wrapper  需要设置特殊策略的Wrapper。
 * @param strategy 特殊策略。
 */
SetDepend<T, V> specialDependWrapper(DependWrapperActionStrategy strategy, WorkerWrapper<?, ?> wrapper);

default SetDepend<T, V> specialDependWrapper(DependWrapperActionStrategy strategy, WorkerWrapper... wrappers);
```

也可以在 `SetNext<T, V> setNext();`模式时进行设置。

```java
/**
 * 调用该方法将会让传入的此下游workerWrappers对本Wrapper进行特殊策略判断，
 *
 * @param strategy 对本Wrapper的特殊策略。
 * @param wrapper  依赖本Wrapper的下游Wrapper。
 * @return 返回Builder自身。
 */
SetNext<T, V> specialToNextWrapper(DependWrapperActionStrategy strategy, WorkerWrapper<?, ?> wrapper);
```

以下为示例：

```java
class Case6 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return WorkerWrapper.<String, String>builder()
            .id(id)
            .worker((param, allWrappers) -> {
                System.out.println("wrapper(id=" + id + ") is working");
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> b = builder("B")
            // 这里设置了，不论a怎么样b都会快速失败。但是，a设置的对wrapper的特殊策略把它覆盖了。
            .depends((dependWrappers, thisWrapper, fromWrapper) ->
                     DependenceAction.FAST_FAIL
                     .fastFailException(ResultState.EXCEPTION, new RuntimeException("b 必定失败，除非有上游wrapper救他"))
                    )
            .callback(ICallback.PRINT_EXCEPTION_STACK_TRACE)
            .build();
        WorkerWrapper<?, ?> a = builder("A")
            .setNext()
            // a将会使b直接开始工作
            // 若是去掉这行代码，则b会失败
            .specialToNextWrapper(fromWrapper -> DependenceAction.START_WORK.emptyProperty(), b)
            .wrapper(b)
            .end().build();
        Async.beginWork(1000, a);
        System.out.println(a.getWorkResult());
        System.out.println(b.getWorkResult());
        /* 输出：
        wrapper(id=A) is working
        wrapper(id=B) is working
        WorkResult{result=null, resultState=SUCCESS, ex=null}
        WorkResult{result=null, resultState=SUCCESS, ex=null}
        */
    }
}
```

###### 提供常量

* `DependWrapperActionStrategy.SUCCESS_CONTINUE`
  * 成功时，交给下一个策略器判断。未运行时，休息。失败时，失败。
* `DependWrapperActionStrategy SUCCESS_START_INIT_CONTINUE`
  * 成功时，开始工作。未运行时，交给下一个策略器判断。失败时，失败。

### 设置跳过策略

当wrapper发现下游wrapper居然已经被执行了，那是不是可以跳过自身呢?

 of course，当wrapper被跳过时，其`getWorkResult`返回的值通常是：

```json
{
    result: null,
    // 注意：如果wrapper被跳过，ResultState将为DEFAULT
    resultState: "ResultState.DEFAULT",
    ex: "com.jd.platform.async.exception.SkippedException"
}
```

下面是跳过策略的设置接口：

```java
@FunctionalInterface
public interface SkipStrategy {
    /**
     * 跳过策略函数。返回true将会使WorkerWrapper跳过执行。
     *
     * @param nextWrappers 下游WrapperSet
     * @param thisWrapper  本WorkerWrapper
     * @param fromWrapper  呼叫本Wrapper的上游Wrapper
     * @return 返回true将会使WorkerWrapper跳过执行。
     */
    boolean shouldSkip(Set<WorkerWrapper<?, ?>> nextWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper);
    
    // consts 略
    
}
```

有以下几个默认值：

* `SkipStrategy.NOT_SKIP`
  * 不进行跳过检查，也不打算跳过
* `SkipStrategy.CHECK_ONE_LEVEL`，该值为默认值，若builder未设置则默认使用这个。
  * 仅检查深度为1的下游wrapper。如果其全部不在初始化状态，则自己会被跳过。

以下是一个示例：

```java
/**
 * @author create by TcSnZh on 2021/5/9-下午4:12
 */
class Case7 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return builder(id, -1L);
    }

    private static WorkerWrapperBuilder<?, ?> builder(String id, long sleepTime) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("wrapper(id=" + id + ") is working");
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                });
    }

    /**
     * A ==> B(10ms) ==> C ==> D  (D可在E、C任意一个完成后执行)
     * . \====> E(5ms) ====/
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> d = builder("D").depends(DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS).build();
        WorkerWrapper<?, ?> a = builder("A")
                .nextOf(builder("B", 10)
                                .nextOf(builder("C")
                                        .nextOf(d)
                                        // 这里我们没有设置C的跳过策略，因为默认使用CHECK_ONE_LEVEL，可将下行代码注释去掉，则C会执行
//                                        .setSkipStrategy(SkipStrategy.NOT_SKIP)
                                        .build())
                                .build(),
                        builder("E", 5).nextOf(d).build()
                ).build();
        Async.beginWork(1000, a);
        /* 输出：
        wrapper(id=A) is working
        wrapper(id=E) is working
        wrapper(id=B) is working
        wrapper(id=D) is working
        */
    }
}
```

### 设置超时

可以在`Async.beginWork(/* ... */)`中传入总超时时间，也可以对单个wrapper设置超时时间。

#### 总任务时间超时

在`Async.beginWork`方法中可以指定总超时时间。

#### 单wrapper任务时间超时

可在Builder中设置超时选项，以下是几个关键方法：

```java
public interface WorkerWrapperBuilder<T, V> {
    // 略
    
    /**
     * 设置超时时间的具体属性
     */
    SetTimeOut<T, V> setTimeOut();

    interface SetTimeOut<T, V> {
        /**
         * 是否启动超时判断。
         * <p>
         * 默认为true
         *
         * @param enableElseDisable 是则true
         */
        SetTimeOut<T, V> enableTimeOut(boolean enableElseDisable);

        /**
         * 设置单个WorkerWrapper的超时时间。若不设置则不进行超时判断
         *
         * @param time 时间数值
         * @param unit 时间单位
         */
        SetTimeOut<T, V> setTime(long time, TimeUnit unit);

        WorkerWrapperBuilder<T, V> end();
    }
    
    /**
     * 便携式设置单个WorkerWrapper的超时时间。若不设置则不进行超时判断
     *
     * @param time 时间数值
     * @param unit 时间单位
     */
    default WorkerWrapperBuilder<T, V> timeout(long time, TimeUnit unit) {
        return timeout(true, time, unit);
    }

    default WorkerWrapperBuilder<T, V> timeout(boolean enableTimeOut, long time, TimeUnit unit) {
        return setTimeOut().enableTimeOut(enableTimeOut).setTime(time, unit).end();
    }
    
    // 略
}
```



#### 测试示例

示例：

```java
class Case8 {
    private static WorkerWrapperBuilder<?, ?> builder(String id) {
        return builder(id, -1L);
    }

    private static WorkerWrapperBuilder<?, ?> builder(String id, long sleepTime) {
        return WorkerWrapper.<String, String>builder()
                .id(id)
                .worker((param, allWrappers) -> {
                    System.out.println("\twrapper(id=" + id + ") is working");
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    return null;
                })
                .callback((new ICallback<String, String>() {
                    @Override
                    public void begin() {
                        System.out.println("wrapper(id=" + id + ") has begin . ");
                    }

                    @Override
                    public void result(boolean success, String param, WorkResult<String> workResult) {
                        System.out.println("\t\twrapper(id=" + id + ") callback "
                                + (success ? "success " : "fail ")
                                + ", workResult is " + workResult);
                    }
                }));
    }

    /**
     * A ==> B(10ms) ==> C(20ms)
     */
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        WorkerWrapper<?, ?> a = builder("A")
                .nextOf(builder("B", 10)
                        .nextOf(builder("C", 20).build())
                        .build())
                .build();
        Async.beginWork(15, a);
        /* 输出：
        wrapper(id=A) has begin .
            wrapper(id=A) is working
                wrapper(id=A) callback success , workResult is WorkResult{result=null, resultState=SUCCESS, ex=null}
        wrapper(id=B) has begin .
            wrapper(id=B) is working
                wrapper(id=B) callback success , workResult is WorkResult{result=null, resultState=TIMEOUT, ex=null}
        wrapper(id=C) has begin .
                wrapper(id=C) callback fail , workResult is WorkResult{result=null, resultState=TIMEOUT, ex=null}
        java.lang.InterruptedException: sleep interrupted
            at java.lang.Thread.sleep(Native Method)
            ...
            以下异常信息省略
        */
    }
}
```

### 设置是否允许被打断线程

可通过该选项去设置允许线程被打断：

```java
public interface WorkerWrapperBuilder<T, V> {
    // 略
    
    /**
     * 是否允许被试图中断线程
     *
     * @param allow 是则true
     */
    WorkerWrapperBuilder<T, V> allowInterrupt(boolean allow);
    
    // 略
}
```

#### 线程会被打断的具体情况

开启之后，在以下情况，会试图打断正处于WORKING状态的工作线程。

* 总任务超时，但本wrapper在WORKING。
* 单wrapper超时，但本wrapper在WORKING。
* wrapper应当被跳过，但本wrapper在WORKING。

* 调用`WorkerWrapper#failNow()`方法，且wrapper在WORKING状态。

# 开放工具类

> `asyncTool-openutil`工具模块提供了一些便于开发的工具类。
>
> 可单独引入依赖：
>
> ```xml
> <dependencies>
>     <dependency>
>         <groupId>com.jd.platform</groupId>
>         <artifactId>asyncTool-openutil</artifactId>
>         <version>1.5.0-SNAPSHOT</version>
>     </dependency>
> </dependencies>
> ```

### 集合类

> `com.jd.platform.async.openutil.collection.*`

这里不详述，要用的话源码里有注释。

* `SparseArray2D` 稀疏矩阵。
* `CommonDirectedGraph` 有向图。
* `CommonStoreArk`  id储物柜。

### 定时器

> `com.jd.platform.async.openutil.timer.*`

* `HashedWheelTimer`  从netty里抄来的时间轮工具类。

### 其他

`com.jd.platform.async.openutil`

* `BiInt`  一个表示两个int值的实体类，内含默认比较器、缓冲区间。

# 动态任务调度

>引入依赖
>
>```xml
><!-- 动态任务调度，有需要的话可以引入。依赖fastjson，请自行解决版本冲突 -->
><dependency>
>        <artifactId>asyncTool-scheduling</artifactId>
>        <groupId>com.jd.platform</groupId>
>        <version>1.5.0-SNAPSHOT</version>
></dependency>
>```

动态任务调度，传个json就能让调度工厂随心所欲的构造wrapper与执行。

如果您已经熟悉**《任务编排》**这一章的内容，则可以试试这玩意。

## 基本组件

## 模型属性

### 字段属性详解

以下是一个传入的json的格式示例，注释中描述了属性含义与格式：

```json
{
    // 图纸名称。可使用 字符串、null、不写。为null或不写，则会使用uuid
    drawingsName :"",
    // 提供的全部WorkerWrapper列表。
    // 内部格式请参考《wrappers》这章。
    wrappers:[],
    // wrapper顺序关系。
    // 内部格式请参考《relations》这章
    relations: [],
    // 任务启动参数
    // 内部格式请参考《beginWork》这章
    beginWork:{}
}
```

#### wrappers

wrappers数组中，只允许存入满足以下格式的对象：

```json
wrappers:[
    {
        // id，传入字符串。
        // 而且：不能与wrappers数组中其他的对象的id属性相同。即必须保证id唯一。
        // 不允许undefined，不允许null。
        "id": "first",
        // param，即参数。请看《param》
        // useObjectModel代表value的值是否是“对象模型”。
        // 允许undefined或null，视为{"useObjectModel": false,"value": null}
        "param": {
            "useObjectModel": false,
            "value": "JackMa"
        },
        // 传入对象模型，请看《对象模型ObjectModel》
        // 不允许undefined或null
        "worker": {
            "sameObjectId": 1,
            "className:": "schedulingtest.impl.SelectUserByName"
        },
        // 传入对象模型
        // 允许undefined与null。如果为两者则使用com.jd.platform.async.callback.DefaultCallback
        "callback": {
            "sameObjectId": 1
        },
        // wrapper策略
        // 允许undefined与null。如果为两者则使用com.jd.platform.async.wrapper.strategy.WrapperStrategy.DefaultWrapperStrategy
        // 即"ALL_DEPENDENCIES_ALL_SUCCESS"与"CHECK_ONE_LEVEL"
        "wrapperStrategy": {
            // 传入{}键值对，键名为即wrapper的id属性，值为对象模型。
            // 允许undefined和null，两者之意与空键值对{}并无二致
            "dependOnUpWrapperStrategyMapper": null,
            // -- 这里不再向历史妥协，舍弃了DependMustStrategyMapper
            // 基础策略器，传入对象模型
            // 允许undefined和null，如果是两者则使用"ALL_DEPENDENCIES_ALL_SUCCESS"
            "dependenceStrategy": {
                "constObjectName": "ALL_DEPENDENCIES_ALL_SUCCESS"
            },
            // 跳过策略，传入对象模型
            // 允许undefined和null，如果是两者则使用"CHECK_ONE_LEVEL"
            "skipStrategy": {
                "constObjectName": "CHECK_ONE_LEVEL"
            }
        },
        // 是否允许打断，传入boolean值。允许undefined和null，视为false
        "allowInterrupt": true,
        // 是否启动单wrapper计时，允许undefined和null，视为false
        "enableTimeout": true,
        // 单wrapper超时时间数值。传入long(int64)值
        // 在enableTimeout为true的情况下不允许为undefined或null或小于等于0的值，否则允许任何值
        "timeoutLength": 50,
        // 单wrapper超时时间单位，有以下几个取值：（即java.util.concurrent.TimeUnit的枚举值）
        // "NANOSECONDS"、"MICROSECONDS"、"MILLISECONDS"
        // "SECONDS"、"MINUTES"、"HOURS"、"DAYS"
        // 允许为undefined和null，视为"MILLISECONDS"
        // 但是除了undefined、null和以上7个值外，不允许任何值
        "timeoutUnit": "MILLISECONDS"
    },
    // 这是第二个wrapper属性，这里可以省略很多选用默认值的属性。
    {
        "id": "second",
        "param":{
            "useObjectModel": false,
            "value":"first"
        },
        "worker": {
            "className": "schedulingtest.impl.PayTaxes",
            "sameObjectId": 2
        },
        callback:{
            "sameObjectId": 2
        }
    },
    // 这是第三个属性，这里的"extendConfig"属性是个省力的好东西。
    {
        "id": "third",
        // 传入存在的id属性
        // 效果是：本wrapper配置将继承此id表示的wrapper配置的所有属性
        // 并且本wrapper可以有选择的覆写配置——只需设置某个对象的值即可。
        // 可以为null或undefined，表示不继承配置
        "extendConfig": "second"
    },
    /* , { ... } */
]
```

##### param

`useObjectModel`属性用于说明`value`属性的所代表的对象类型：

* 为false：使用json所对应的类型。
* 为true：使用《对象模型`ObjectModel`》中的我们自定义的对象模型。

```json
{
    "useObjectModel": false,
    "value": "JackMa"
},
```

```json
{
    "useObjectModel": true,
    "value": {
        sameObjectId: 3
    }
}
```

#### relations

```json
relation: {
    // 可使用 数组(仅当to使用字符串时)、字符串
    // 不允许为null或undefined，如果两个wrapper之间无关系，宁可不写这整个对象。
    from: "",
    // 可使用 数组(仅当from使用字符串时)、字符串
    // 不允许为null或undefined，如果两个wrapper之间无关系，宁可不写这整个对象。
    to: ""
}
```

`from`和`to`两个属性传入的字符串，必须是在`wrappers`数组中所含有的`id`属性。

这两个属性只有在对方为字符串时，才能设置自己为数组，以表示“一对多”关系。

#### beginWork

```json
beginWork: {
    // 设置全组超时时间数值，使用long(int64)值
    // 可以传入<=0的值表示不限制超时时间。
    // 允许为null、undefined，视为不限制超时时间。
    timeoutLength: 100,
    // 全组超时时间单位，有以下几个取值：（即java.util.concurrent.TimeUnit的枚举值）
    // "NANOSECONDS"、"MICROSECONDS"、"MILLISECONDS"
    // "SECONDS"、"MINUTES"、"HOURS"、"DAYS"
    // 允许为undefined和null，视为"MILLISECONDS"
    // 但是除了undefined、null和以上7个值外，不允许任何值
    timeoutUnit: "MILLISECONDS",
    // 传入的启动wrapper的id数组，不允许null、undefined。可以为空，但是这不符合常理。
    wrappers: [
        "first"
    ],
    // 执行的线程池
    // 可以传入"COMMON_POOL"，代表使用asyncTool的默认线程池。
    // 可以为null或undefined，视为"COMMON_POOL"
    // 也可以以如下格式传入全限定名+字段/方法名字符串，则使用反射调取该字段的所指向的ExecutorService：
    // 全限定名#字段名     或     全限定名##方法名
    executor: "COMMON_POOL"
}
```

### 其他特殊格式

#### 对象模型`ObjectModel`

* 有时我们需要指定接口实现类，或者是默认的接口实现对象，并自定义这些对象的属性。

* 或是指定多个json字段代表一个共同的对象。

那么，我们就需要高度自定义此对象的属性，并封装为`com.jd.platform.async.scheduling.model.ObjectModel`类。

```json
{
    // 如果需要使用asyncTool指名道姓的常量实现对象，则请见下方《常量对象规范》，并在这里传入名称字符串。
    // 当设置了该属性时，其他的属性均会被忽视。
    "constObjectName": "NOT_SKIP",
    // 如果希望指定多个json字段代表一个共同的对象：
    // 则将其设为相同的id。
    // 如果设置了该属性，其他的属性均会被忽视。（优先级低于constObjectName）
    "sameObjectId": 1,
    // 提供类的全限定名字符串，将调用无参构造方法进行初始化
    // 如果constObjectName设置了非null且非undefined值，则此值允许为null或undefined
    // 如果sameObjectId设置了非null且非undefined值，则id相同的对象模型中允许且只允许一个值为非null或非undefined，其他的都必须为null或undefined
    "className": "your.package.name.YourKlassName",
    // 初始化后，会根据该值来修改对象属性
    // 允许为null或undefined，表示不额外设置属性
    
    "properties": {
        // 其中的键值对为各字段名。这些字段需要有getter、setter方法。
        "myIntegerField": 123123
    }
}
```

##### 常量对象大全

在`constObjectName`属性中设置，用以下字符串代表如下对象：

| 对象名字符串                     | 常量值                                                  |
| -------------------------------- | ------------------------------------------------------- |
| `"NOT_SKIP"`                     | `SkipStrategy.NOT_SKIP`                                 |
| `"CHECK_ONE_LEVEL"`              | `SkipStrategy.CHECK_ONE_LEVEL`                          |
| `"ALL_DEPENDENCIES_ALL_SUCCESS"` | `DependenceStrategy.ALL_DEPENDENCIES_ALL_SUCCESS`       |
| `"ALL_DEPENDENCIES_ANY_SUCCESS"` | `DependenceStrategy.ALL_DEPENDENCIES_ANY_SUCCESS`       |
| `"ALL_DEPENDENCIES_NONE_FAILED"` | ` DependenceStrategy.ALL_DEPENDENCIES_NONE_FAILED`      |
| `"SUCCESS_CONTINUE"`             | `DependOnUpWrapperStrategy.SUCCESS_CONTINUE`            |
| `"SUCCESS_START_INIT_CONTINUE"`  | `DependOnUpWrapperStrategy.SUCCESS_START_INIT_CONTINUE` |
| `"PRINT_EXCEPTION_STACK_TRACE"`  | `ICallback.PRINT_EXCEPTION_STACK_TRACE`                 |

详见请参考`com.jd.platform.async.scheduling.model.Constants`类中的具体代码。

