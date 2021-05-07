package com.jd.platform.async.wrapper.skipstrategy;

import com.jd.platform.async.wrapper.WorkerWrapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by TcSnZh on 2021/5/6-下午3:02
 */
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

    /**
     * 不跳过
     */
    SkipStrategy NOT_SKIP = new SkipStrategy() {
        @Override
        public boolean shouldSkip(Set<WorkerWrapper<?, ?>> nextWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper) {
            return false;
        }

        @Override
        public String toString() {
            return "NOT_SKIP";
        }
    };

    SkipStrategy CHECK_ONE_LEVEL = new SkipStrategy() {
        private final SkipStrategy searchNextOneLevel = searchNextWrappers(SearchNextWrappers.SearchType.DFS, 1);

        @Override
        public boolean shouldSkip(Set<WorkerWrapper<?, ?>> nextWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper) {
            return searchNextOneLevel.shouldSkip(nextWrappers, thisWrapper, fromWrapper);
        }

        @Override
        public String toString() {
            return "CHECK_ONE_LEVEL";
        }
    };

    default SearchNextWrappers searchNextWrappers(SearchNextWrappers.SearchType searchType, int searchLevel) {
        return new SearchNextWrappers(searchType, searchLevel);
    }

    /**
     * 检查之后的Wrapper是否不在INIT状态
     */
    class SearchNextWrappers implements SkipStrategy {
        /**
         * 搜索策略
         */
        enum SearchType {
            DFS, BFS;
        }

        private final SearchType searchType;

        /**
         * 搜索深度
         */
        private final int searchLevel;

        public SearchNextWrappers(SearchType searchType, int searchLevel) {
            this.searchType = Objects.requireNonNull(searchType);
            this.searchLevel = searchLevel;
        }

        @Override
        public boolean shouldSkip(Set<WorkerWrapper<?, ?>> nextWrappers, WorkerWrapper<?, ?> thisWrapper, WorkerWrapper<?, ?> fromWrapper) {
            Set<WorkerWrapper<?, ?>> nextSet;
            if ((nextSet = nextWrappers) == null || nextSet.isEmpty()) {
                return false;
            }
            switch (searchType) {
                case DFS:
                    return nextSet.stream().allMatch(next ->
                            next.getState() != WorkerWrapper.INIT || dfsSearchShouldSkip(next, 1));
                case BFS:
                    LinkedList<BfsNode> queue = nextSet.stream().map(ww -> new BfsNode(ww, 0)).collect(Collectors.toCollection(LinkedList::new));
                    HashSet<WorkerWrapper<?, ?>> existed = new HashSet<>(nextSet);
                    while (!queue.isEmpty()) {
                        BfsNode node = queue.poll();
                        if (node.atLevel > searchLevel) {
                            continue;
                        }
                        if (node.wrapper.getState() != WorkerWrapper.INIT) {
                            return true;
                        }
                        if (node.atLevel < searchLevel) {
                            // 如果不是深度的最大值，则往队列里添加
                            node.wrapper.getNextWrappers().forEach(nextWrapper -> {
                                if (existed.contains(nextWrapper)) {
                                    return;
                                }
                                queue.offer(new BfsNode(nextWrapper, node.atLevel + 1));
                                existed.add(nextWrapper);
                            });
                        }
                    }
                    return false;
                default:
                    throw new IllegalStateException("searchType type illegal : " + searchType);
            }
        }

        private boolean dfsSearchShouldSkip(WorkerWrapper<?, ?> currentWrapper, int currentLevel) {
            if (currentLevel + 1 > searchLevel || currentWrapper == null) {
                return false;
            }
            for (WorkerWrapper<?, ?> nextWrapper : currentWrapper.getNextWrappers()) {
                if (nextWrapper != null &&
                        (nextWrapper.getState() != WorkerWrapper.INIT
                                || dfsSearchShouldSkip(nextWrapper, currentLevel + 1))) {
                    return true;
                }
            }
            return false;
        }

        static class BfsNode {
            final WorkerWrapper<?, ?> wrapper;
            final int atLevel;

            public BfsNode(WorkerWrapper<?, ?> wrapper, int atLevel) {
                this.wrapper = wrapper;
                this.atLevel = atLevel;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                BfsNode bfsNode = (BfsNode) o;
                return Objects.equals(wrapper, bfsNode.wrapper);
            }

            @Override
            public int hashCode() {
                return wrapper.hashCode();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SearchNextWrappers that = (SearchNextWrappers) o;
            return searchLevel == that.searchLevel && searchType == that.searchType;
        }

        @Override
        public int hashCode() {
            return searchLevel ^ searchType.ordinal();
        }

        @Override
        public String toString() {
            return "CheckNextWrapper{" +
                    "searchType=" + searchType +
                    ", searchLevel=" + searchLevel +
                    '}';
        }
    }
}
