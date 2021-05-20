package com.jd.platform.async.scheduling.drawings;

import com.jd.platform.async.scheduling.exception.IllegalSchedulingPropertyException;
import com.jd.platform.async.scheduling.model.ObjectModel;
import com.jd.platform.async.scheduling.model.SchedulingDrawingsModel;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author tcsnzh[zh.jobs@foxmail.com] create this in 2021/5/20-下午4:38
 */
class DefaultSchedulingDrawingsParser implements SchedulingDrawingsParser {
    static final DefaultSchedulingDrawingsParser instance = new DefaultSchedulingDrawingsParser();

    @Override
    public SchedulingDrawings parseDrawings(final SchedulingDrawingsModel model) throws IllegalSchedulingPropertyException {
        final SchedulingDrawingsImpl drawings = new SchedulingDrawingsImpl();
        final String drawingsName = model.getDrawingsName();
        drawings.setName(drawingsName == null ? UUID.randomUUID().toString() : drawingsName);
        final List<SchedulingDrawingsModel.WrapperModel> wrappers =
                propRequireNotNull(model.getWrappers(), "wrappers");
        // 缓存WrapperModelMap并检查id是否唯一
        final LinkedHashMap<String, SchedulingDrawingsModel.WrapperModel> wrapperId2ModelMap =
                new LinkedHashMap<>(wrappers.size());
        for (int i = 0; i < wrappers.size(); i++) {
            final SchedulingDrawingsModel.WrapperModel wrapper = wrappers.get(i);
            // 检查id是否重复，并存入map
            final String id = propRequireNotNull(wrapper.getId(), "wrappers[" + i + "].id");
            if (null != wrapperId2ModelMap.put(id, wrapper)) {
                throw new IllegalSchedulingPropertyException("Wrapper id \"" + id + "\" duplicate .");
            }
        }
        // 缓存ObjectModelMap并检查
        final Map<Long, ObjectModel> wrappersObjectModelMap = wrappersObjectModelMap(wrappers, wrapperId2ModelMap.keySet());
        // 构造wrapperFactory
        // todo 还没写完
        return null;
    }

    // ========== private method ==========


    // ========== util static methods ==========

    /**
     * 缓存sameObjectId有有效值的ObjectModel，并检查：
     * 1. ObjectModel参数是否符合格式规范
     * 2. wrapperStrategy.dependOnUpWrapperStrategyMapper 的键id是否存在于wrappers的id属性集合中
     *
     * @param wrappers      wrappers属性值的列表
     * @param wrappersIdSet 已经包括wrappers列表中所有id属性的Set
     * @return 返回Map的键为ObjectModel的sameObjectId，值为ObjectModel
     * @throws IllegalSchedulingPropertyException 格式错误，抛出异常
     */
    protected static Map<Long, ObjectModel> wrappersObjectModelMap(
            List<SchedulingDrawingsModel.WrapperModel> wrappers,
            Set<String> wrappersIdSet
    ) throws IllegalSchedulingPropertyException {
        final LinkedHashMap<Long, ObjectModel> objectModelMap = new LinkedHashMap<>();
        for (final SchedulingDrawingsModel.WrapperModel wrapper : wrappers) {
            final String prefixPropName = "wrappers[id=" + wrapper.getId() + "]";
            final SchedulingDrawingsModel.WrapperModel.ParamModel param = wrapper.getParam();
            // 将param参数中的objectModel检查并存入map
            if (param != null && Boolean.TRUE.equals(
                    propRequireNotNull(param.getUseObjectModel(), prefixPropName + ".param.useObjectModel")
            )) {
                final Object value = param.getValue();
                if (value instanceof ObjectModel) {
                    checkAndPutObjectModelHasDifferentId(
                            (ObjectModel) value,
                            objectModelMap,
                            prefixPropName + ".param.value");
                } else {
                    throw IllegalSchedulingPropertyException.illegalFieldParameter(
                            prefixPropName + ".param.value",
                            value,
                            "it should instanceof ObjectModel."
                    );
                }
            }
            // 检查并缓存worker的objectModel
            final String _workerPropName = prefixPropName + ".worker";
            checkAndPutObjectModelHasDifferentId(propRequireNotNull(wrapper.getWorker(), _workerPropName),
                    objectModelMap, _workerPropName);
            // 检查并缓存callback的objectModel
            checkAndPutObjectModelHasDifferentId(wrapper.getCallback(), objectModelMap, prefixPropName + ".callback");
            // 检查wrapperStrategy
            final SchedulingDrawingsModel.WrapperModel.WrapperStrategyModel wrapperStrategy = wrapper.getWrapperStrategy();
            if (wrapperStrategy != null) {
                // 检查并缓存dependOnUpWrapperStrategyMapper
                final Map<String, ObjectModel> dependOnUpWrapperStrategyMapper =
                        wrapperStrategy.getDependOnUpWrapperStrategyMapper();
                if (dependOnUpWrapperStrategyMapper != null) {
                    for (Map.Entry<String, ObjectModel> entry :
                            dependOnUpWrapperStrategyMapper.entrySet()) {
                        final String wrapperId = entry.getKey();
                        final String mapperPropName = prefixPropName + ".wrapperStrategy.dependOnUpWrapperStrategyMapper";
                        if (!wrappersIdSet.contains(wrapperId)) {
                            throw IllegalSchedulingPropertyException.illegalFieldParameter(
                                    mapperPropName,
                                    dependOnUpWrapperStrategyMapper,
                                    "the key(wrapperId) of \"" + wrapperId + "\n not in wrappers list"
                            );
                        }
                        checkAndPutObjectModelHasDifferentId(
                                propRequireNotNull(entry.getValue(), mapperPropName),
                                objectModelMap,
                                mapperPropName
                        );
                    }
                }
                // 检查并缓存dependenceStrategy
                final ObjectModel dependenceStrategy = wrapperStrategy.getDependenceStrategy();
                final String dependenceStrategyPropName = prefixPropName + ".wrapperStrategy.dependenceStrategy";
                if (dependenceStrategy != null) {
                    checkAndPutObjectModelHasDifferentId(
                            dependenceStrategy,
                            objectModelMap,
                            dependenceStrategyPropName
                    );
                }
                // 检查并缓存skipStrategy
                final String skipStrategyPropName = prefixPropName + ".wrapperStrategy.skipStrategy";
                final ObjectModel skipStrategy = wrapperStrategy.getSkipStrategy();
                if (skipStrategy != null) {
                    checkAndPutObjectModelHasDifferentId(
                            skipStrategy,
                            objectModelMap,
                            skipStrategyPropName);
                }
            }
        }
        return objectModelMap;
    }

    /**
     * 本方法为{@link #wrappersObjectModelMap(List, Set)}的子方法。
     * 用于抽取ObjectModel格式的判断逻辑。
     *
     * @param objectModel    对象模型
     * @param objectModelMap 要存入的Map
     * @param propNameSup    属性名
     * @throws IllegalSchedulingPropertyException 格式错误，抛出异常
     */
    private static void checkAndPutObjectModelHasDifferentId(ObjectModel objectModel,
                                                             Map<Long, ObjectModel> objectModelMap,
                                                             Supplier<String> propNameSup)
            throws IllegalSchedulingPropertyException {
        final String constObjectName = objectModel.getConstObjectName();
        if (constObjectName != null) {
            if (!ObjectModel.containConstObject(constObjectName)) {
                final String propName;
                throw new IllegalSchedulingPropertyException(
                        (propNameSup == null || (propName = propNameSup.get()) == null ? "" : "Property " + propName + " ") +
                                "ObjectModel doesn't have a constant object named \""
                                + constObjectName + "\" . objectModel is " + objectModel);
            }
            return;
        }
        final Long sameObjectId = objectModel.getSameObjectId();
        if (sameObjectId != null) {
            final Map<String, Object> properties = objectModel.getProperties();
            if (objectModelMap.containsKey(sameObjectId) &&
                    // 同一id的对象，其他属性不允许重复设置
                    (objectModel.getClassName() != null || properties != null && !properties.isEmpty())) {
                throw new IllegalSchedulingPropertyException(
                        "The objectModel which sameObjectId=" + sameObjectId +
                                " cannot be set \"className\" or \"properties\" again . the two in conflict is " +
                                objectModel + " and " + objectModelMap.get(sameObjectId) + " ."
                );
            }
            objectModelMap.put(sameObjectId, objectModel);
            return;
        }
        propRequireNotNull(objectModel.getClassName(), propNameSup.get());

    }

    private static void checkAndPutObjectModelHasDifferentId(ObjectModel objectModel,
                                                             Map<Long, ObjectModel> objectModelMap,
                                                             String propName)
            throws IllegalSchedulingPropertyException {
        checkAndPutObjectModelHasDifferentId(objectModel, objectModelMap, () -> propName);
    }

    /**
     * 检查属性是否为null
     *
     * @param prop     属性值
     * @param propName 属性名
     * @param reason   原因说明。
     *                 默认值为{@code "it's not allow null"}，用于{@link #propRequireNotNull(Object, String)}与
     *                 {@link #propRequireNotNull(Object, Supplier)}方法中。
     * @param <T>      属性值泛型
     * @return 返回传入的属性值，供继续调用
     * @throws IllegalSchedulingPropertyException 如果属性为null，抛出异常
     */
    private static <T> T propRequireNotNull(T prop, Supplier<String> propName, Supplier<String> reason)
            throws IllegalSchedulingPropertyException {
        if (prop == null) {
            throw IllegalSchedulingPropertyException.illegalFieldParameter(
                    propName.get(),
                    null,
                    reason.get()
            );
        }
        return prop;
    }

    private static <T> T propRequireNotNull(T prop, String propName) throws IllegalSchedulingPropertyException {
        return propRequireNotNull(prop, () -> propName);
    }

    private static <T> T propRequireNotNull(T prop, Supplier<String> propName) throws IllegalSchedulingPropertyException {
        return propRequireNotNull(prop, propName, () -> "it's not allow null");
    }
}
