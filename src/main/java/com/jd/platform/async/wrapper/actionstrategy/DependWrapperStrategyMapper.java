package com.jd.platform.async.wrapper.actionstrategy;

import com.jd.platform.async.wrapper.WorkerWrapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 对不同的{@link WorkerWrapper}调用者实行个性化依赖响应策略。
 * <p/>
 * 使用{@link DependWrapperStrategyMapper}本实现类对{@link DependenceStrategy}进行增强，
 *
 * @author create by TcSnZh on 2021/5/1-下午11:12
 */
public class DependWrapperStrategyMapper implements DependenceStrategy {
    private final Map<WorkerWrapper<?, ?>, DependWrapperActionStrategy> mapper = new ConcurrentHashMap<>(4);

    /**
     * 设置对应策略
     *
     * @param targetWrapper 要设置策略的WorkerWrapper
     * @param strategy      要设置的策略
     * @return 返回this，链式调用。
     */
    public DependWrapperStrategyMapper putMapping(WorkerWrapper<?, ?> targetWrapper, DependWrapperActionStrategy strategy) {
        mapper.put(targetWrapper, strategy);
        toStringCache = null;
        return this;
    }

    /**
     * 判断方法。
     * <p/>
     * 如果fromWrapper在{@link #mapper}中，则返回{@link DependWrapperActionStrategy}的判断返回值。否则返回{@link DependenceAction#JUDGE_BY_AFTER}
     *
     * @param dependWrappers （这里不会使用该值）thisWrapper.dependWrappers的属性值。
     * @param thisWrapper    （这里不会使用该值）thisWrapper，即为“被催促”的WorkerWrapper
     * @param fromWrapper    调用来源Wrapper。
     * @return 如果在mapper中有对fromWrapper的处理策略，则使用其进行判断。否则返回JUDGE_BY_AFTER交给下一个进行判断。
     */
    @Override
    public DependenceAction.WithProperty judgeAction(Set<WorkerWrapper<?,?>> dependWrappers,
                                                     WorkerWrapper<?, ?> thisWrapper,
                                                     WorkerWrapper<?, ?> fromWrapper) {
        DependWrapperActionStrategy strategy = mapper.get(fromWrapper);
        if (strategy == null) {
            return DependenceAction.JUDGE_BY_AFTER.emptyProperty();
        }
        return strategy.judge(fromWrapper);
    }

    /**
     * 缓存toString
     */
    private String toStringCache;

    @Override
    public String toString() {
        if (toStringCache == null) {
            toStringCache = "DependWrapperStrategyMapper{mapper=" + mapper.entrySet().stream()
                    .map(entry -> "{" + entry.getKey().getId() + ":" + entry.getValue() + "}")
                    .collect(Collectors.toList())
                    + "}";
        }
        return toStringCache;
    }
}
