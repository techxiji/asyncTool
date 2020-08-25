

# 并行框架说明
有问题、或者有特定的场景需求可以给作者发邮件说明，感谢您的意见。wuweifeng10@jd.com, liwangyang@jd.com

该框架目前正在 **京东App后台** 接受苛刻、高并发、海量用户等复杂场景业务的检验测试，随时会根据实际情况发布更新和bugFix。

有对区块链感兴趣的，可以参考项目作者另一个[GVP项目](https://gitee.com/tianyalei/md_blockchain)，java区块链底层入门。

有对高并发场景下，被热key打爆存储层，秒杀等场景中热数据本地缓存、热数据（刷子用户）限流等需要热key探测的，可关注[京东热key探测缓存框架](http://https://gitee.com/jd-platform-opensource/hotkey)。热key框架正在灰度内测期，已上线3000台服务器进行灰度。


## 并行常见的场景
1 客户端请求服务端接口，该接口需要调用其他N个微服务的接口

`譬如 请求我的购物车，那么就需要去调用用户的rpc、商品详情的rpc、库存rpc、优惠券等等好多个服务。同时，这些服务还有相互依赖关系，譬如必须先拿到商品id后，才能去库存rpc服务请求库存信息。 最终全部获取完毕后，或超时了，就汇总结果，返回给客户端。`

2 并行执行N个任务，后续根据这1-N个任务的执行结果来决定是否继续执行下一个任务

`如用户可以通过邮箱、手机号、用户名登录，登录接口只有一个，那么当用户发起登录请求后，我们需要并行根据邮箱、手机号、用户名来同时查数据库，只要有一个成功了，都算成功，就可以继续执行下一步。而不是先试邮箱能否成功、再试手机号……`

`再如某接口限制了每个批次的传参数量，每次最多查询10个商品的信息，我有45个商品需要查询，就可以分5堆并行去查询，后续就是统计这5堆的查询结果。就看你是否强制要求全部查成功，还是不管有几堆查成功都给客户做返回`

`再如某个接口，有5个前置任务需要处理。其中有3个是必须要执行完毕才能执行后续的，另外2个是非强制的，只要这3个执行完就可以进行下一步，到时另外2个如果成功了就有值，如果还没执行完，就是默认值。`

3 需要进行线程隔离的多批次任务

`如多组任务， 各组任务之间彼此不相关，每组都需要一个独立的线程池，每组都是独立的一套执行单元的组合。有点类似于hystrix的线程池隔离策略。`

4 单机工作流任务编排

5 其他有顺序编排的需求

## 并行场景之核心——任意编排
1 多个执行单元的串行请求

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/092905_55771221_303698.png "屏幕截图.png")

2 多个执行单元的并行请求

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/092925_060c01a5_303698.png "屏幕截图.png")

3 阻塞等待，串行的后面跟多个并行

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/092935_5babe488_303698.png "屏幕截图.png")

4 阻塞等待，多个并行的执行完毕后才执行某个

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/092952_c5647879_303698.png "屏幕截图.png")

5 串并行相互依赖

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/093006_d8cd133c_303698.png "屏幕截图.png")

6 复杂场景

![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/093023_357a2912_303698.png "屏幕截图.png")

## 并行场景可能存在的需求之——每个执行结果的回调
传统的Future、CompleteableFuture一定程度上可以完成任务编排，并可以把结果传递到下一个任务。如CompletableFuture有then方法，但是却无法做到对每一个执行单元的回调。譬如A执行完毕成功了，后面是B，我希望A在执行完后就有个回调结果，方便我监控当前的执行状况，或者打个日志什么的。失败了，我也可以记录个异常信息什么的。

此时，CompleteableFuture就无能为力了。

我的框架提供了这样的回调功能。并且，如果执行异常、超时，可以在定义这个执行单元时就设定默认值。

## 并行场景可能存在的需求之——执行顺序的强依赖和弱依赖
如上图的3，A和B并发执行，最后是C。

有些场景下，我们希望A和B都执行完毕后，才能执行C，CompletableFuture里有个allOf(futures...).then()方法可以做到。

有些场景下，我们希望A或者B任何一个执行完毕，就执行C，CompletableFuture里有个anyOf(futures...).then()方法可以做到。

我的框架同样提供了类似的功能，通过设定wrapper里的addDepend依赖时，可以指定依赖的任务是否must执行完毕。如果依赖的是must要执行的，那么就一定会等待所有的must依赖项全执行完毕，才执行自己。

如果依赖的都不是must，那么就可以任意一个依赖项执行完毕，就可以执行自己了。

注意：这个依赖关系是有必须和非必须之分的，还有一个重要的东西是执行单元不能重复执行。譬如图4，如果B执行完毕，然后执行了A，此时C终于执行完了，然后也到了A，此时就会发现A已经在执行，或者已经完毕（失败），那么就不应该再重复执行A。

还有一种场景，如下图，A和D并行开始，D先执行完了，开始执行Result任务，此时B和C都还没开始，然后Result执行完了，虽然B和C都还没执行，但是已经没必要执行了。B和C这些任务是可以被跳过的，跳过的原则是他们的NextWrapper已经有结果了或者已经在执行了。我提供了checkNextWrapperResult方法来控制，当后面的任务已经执行了，自己还要不要执行的逻辑控制。当然，这个控制仅限于nextWrapper只有一个时才成立。

![输入图片说明](https://images.gitee.com/uploads/images/2020/0217/183223_de7e3fa7_303698.png "屏幕截图.png")

## 并发场景可能存在的需求之——依赖上游的执行结果作为入参
譬如A-B-C三个执行单元，A的入参是String，出参是int，B呢它需要用A的结果作为自己的入参。也就是说A、B并不是独立的，而是有结果依赖关系的。

在A执行完毕之前，B是取不到结果的，只是知道A的结果类型。

那么，我的框架也支持这样的场景。可以在编排时，就取A的结果包装类，作为B的入参。虽然此时尚未执行，必然是空，但可以保证A执行完毕后，B的入参会被赋值。

在V1.3后，框架支持在worker的action的入参Map<String, WorkerWrapper>中获取任意一个执行单元的执行结果，当然，可以取其中的1个、多个执行结果作为自己的入参。Key就是在定义wrapper时通过id传进来的唯一id标识。详情demo可以查看test包下dependnew包案例。

## 并发场景可能存在的需求之——全组任务的超时
一组任务，虽然内部的各个执行单元的时间不可控，但是我可以控制全组的执行时间不超过某个值。通过设置timeOut，来控制全组的执行阈值。

## 并发场景可能存在的需求之——高性能、低线程数
该框架全程无锁，不依靠线程锁来保证顺序。

创建线程量少。![输入图片说明](https://images.gitee.com/uploads/images/2019/1226/093227_9633e2a8_303698.png "屏幕截图.png")
如这样的，A会运行在B、C执行更慢的那个单元的线程上，而不会额外创建线程。
## asyncTool特点
解决任意的多线程并行、串行、阻塞、依赖、回调的并发框架，可以任意组合各线程的执行顺序，带全链路回调和超时控制。

其中的A、B、C分别是一个最小执行单元（worker），可以是一段耗时代码、一次Rpc调用等，不局限于你做什么。

该框架可以将这些worker，按照你想要的各种执行顺序，加以组合编排。最终得到结果。

并且，该框架 **为每一个worker都提供了执行结果的回调和执行失败后自定义默认值** 。譬如A执行完毕后，A的监听器会收到回调，带着A的执行结果（成功、超时、异常）。

根据你的需求，将各个执行单元组合完毕后，开始在主线程执行并阻塞，直到最后一个执行完毕。并且 **可以设置全组的超时时间** 。

 **该框架支持后面的执行单元以前面的执行单元的结果为自己的入参** 。譬如你的执行单元B的入参是ResultA，ResultA就是A的执行结果，那也可以支持。在编排时，就可以预先设定B或C的入参为A的result，即便此时A尚未开始执行。当A执行完毕后，自然会把结果传递到B的入参去。


## 快速开始
[点此开启实战](https://gitee.com/jd-platform-opensource/asyncTool/blob/master/QuickStart.md)


