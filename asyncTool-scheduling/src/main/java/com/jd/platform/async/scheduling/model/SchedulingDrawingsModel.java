package com.jd.platform.async.scheduling.model;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工厂图纸模型对象。
 * 具体参数含义，请查阅 QuickStart.md
 *
 * @author create by TcSnZh on 2021/5/17-上午1:16
 */
@SuppressWarnings("unused")
public class SchedulingDrawingsModel {
    protected String drawingsName;
    protected List<WrapperModel> wrappers;
    protected List<RelationModel> relations;
    protected BeginWorkModel beginWork;

    public static class WrapperModel {
        protected String id;
        protected ParamModel param;
        protected ObjectModel worker;
        protected ObjectModel callback;
        protected WrapperStrategyModel wrapperStrategy;
        protected Boolean allowInterrupt;
        protected Boolean enableTimeout;
        protected Long timeoutLength;
        protected TimeUnit timeUnit;
        protected String extendConfig;

        public static class ParamModel {
            protected Boolean useObjectModel;
            protected Object value;  // true - ObjectModel ; false - the json converted to type, such as Map\List\String...

            public Boolean getUseObjectModel() {
                return useObjectModel;
            }

            public void setUseObjectModel(Boolean useObjectModel) {
                this.useObjectModel = useObjectModel;
            }

            public Object getValue() {
                return value;
            }

            public void setValue(Object value) {
                this.value = value;
            }
        }

        public static class WrapperStrategyModel {
            protected Map<String, ObjectModel> dependOnUpWrapperStrategyMapper;
            protected ObjectModel dependenceStrategy;
            protected ObjectModel skipStrategy;

            public Map<String, ObjectModel> getDependOnUpWrapperStrategyMapper() {
                return dependOnUpWrapperStrategyMapper;
            }

            public void setDependOnUpWrapperStrategyMapper(Map<String, ObjectModel> dependOnUpWrapperStrategyMapper) {
                this.dependOnUpWrapperStrategyMapper = dependOnUpWrapperStrategyMapper;
            }

            public ObjectModel getDependenceStrategy() {
                return dependenceStrategy;
            }

            public void setDependenceStrategy(ObjectModel dependenceStrategy) {
                this.dependenceStrategy = dependenceStrategy;
            }

            public ObjectModel getSkipStrategy() {
                return skipStrategy;
            }

            public void setSkipStrategy(ObjectModel skipStrategy) {
                this.skipStrategy = skipStrategy;
            }
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public ParamModel getParam() {
            return param;
        }

        public void setParam(ParamModel param) {
            this.param = param;
        }

        public ObjectModel getWorker() {
            return worker;
        }

        public void setWorker(ObjectModel worker) {
            this.worker = worker;
        }

        public ObjectModel getCallback() {
            return callback;
        }

        public void setCallback(ObjectModel callback) {
            this.callback = callback;
        }

        public WrapperStrategyModel getWrapperStrategy() {
            return wrapperStrategy;
        }

        public void setWrapperStrategy(WrapperStrategyModel wrapperStrategy) {
            this.wrapperStrategy = wrapperStrategy;
        }

        public Boolean getAllowInterrupt() {
            return allowInterrupt;
        }

        public void setAllowInterrupt(Boolean allowInterrupt) {
            this.allowInterrupt = allowInterrupt;
        }

        public Boolean getEnableTimeout() {
            return enableTimeout;
        }

        public void setEnableTimeout(Boolean enableTimeout) {
            this.enableTimeout = enableTimeout;
        }

        public Long getTimeoutLength() {
            return timeoutLength;
        }

        public void setTimeoutLength(Long timeoutLength) {
            this.timeoutLength = timeoutLength;
        }

        public TimeUnit getTimeUnit() {
            return timeUnit;
        }

        public void setTimeUnit(TimeUnit timeUnit) {
            this.timeUnit = timeUnit;
        }

        public String getExtendConfig() {
            return extendConfig;
        }

        public void setExtendConfig(String extendConfig) {
            this.extendConfig = extendConfig;
        }
    }

    public static class RelationModel {
        protected Object from; // from和to最多有一个数组，剩下的都是String
        protected Object to;

        public Object getFrom() {
            return from;
        }

        public void setFrom(Object from) {
            this.from = from;
        }

        public Object getTo() {
            return to;
        }

        public void setTo(Object to) {
            this.to = to;
        }
    }

    public static class BeginWorkModel {
        protected Long timeoutLength;
        protected TimeUnit timeoutUnit;
        protected List<String> wrappers;
        protected String executor;

        public Long getTimeoutLength() {
            return timeoutLength;
        }

        public void setTimeoutLength(Long timeoutLength) {
            this.timeoutLength = timeoutLength;
        }

        public TimeUnit getTimeoutUnit() {
            return timeoutUnit;
        }

        public void setTimeoutUnit(TimeUnit timeoutUnit) {
            this.timeoutUnit = timeoutUnit;
        }

        public List<String> getWrappers() {
            return wrappers;
        }

        public void setWrappers(List<String> wrappers) {
            this.wrappers = wrappers;
        }

        public String getExecutor() {
            return executor;
        }

        public void setExecutor(String executor) {
            this.executor = executor;
        }
    }

    public String getDrawingsName() {
        return drawingsName;
    }

    public void setDrawingsName(String drawingsName) {
        this.drawingsName = drawingsName;
    }

    public List<WrapperModel> getWrappers() {
        return wrappers;
    }

    public void setWrappers(List<WrapperModel> wrappers) {
        this.wrappers = wrappers;
    }

    public List<RelationModel> getRelations() {
        return relations;
    }

    public void setRelations(List<RelationModel> relations) {
        this.relations = relations;
    }

    public BeginWorkModel getBeginWork() {
        return beginWork;
    }

    public void setBeginWork(BeginWorkModel beginWork) {
        this.beginWork = beginWork;
    }
}
