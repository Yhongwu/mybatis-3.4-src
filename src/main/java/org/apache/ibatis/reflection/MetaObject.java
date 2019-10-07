/**
 * Copyright ${license.git.copyrightYears} the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.CollectionWrapper;
import org.apache.ibatis.reflection.wrapper.MapWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;

/**
 * 对属性表达式解析过程
 * @author Clinton Begin
 */
public class MetaObject {

    /**
     * 原始JavaBean对象
     */
    private Object originalObject;
    /**
     * 封装了originalObject对象
     */
    private ObjectWrapper objectWrapper;
    /**
     * 负责实例化originalObject的工厂对象
     */
    private ObjectFactory objectFactory;
    /**
     * 负责创建 ObjectWrapper 的工厂对象
     */
    private ObjectWrapperFactory objectWrapperFactory;
    /**
     * 用于创建并缓存Reflector对象的工厂对象
     */
    private ReflectorFactory reflectorFactory;

    /**
     * 私有构造方法
     * @param object
     * @param objectFactory
     * @param objectWrapperFactory
     * @param reflectorFactory
     */
    private MetaObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
        this.originalObject = object;
        this.objectFactory = objectFactory;
        this.objectWrapperFactory = objectWrapperFactory;
        this.reflectorFactory = reflectorFactory;
        // 若原始对象已经是ObjectWrapper对象，则直接使用
        if (object instanceof ObjectWrapper) {
            this.objectWrapper = (ObjectWrapper) object;
        } else if (objectWrapperFactory.hasWrapperFor(object)) {
            // 若ObjectWrapperFactory能够为该原始对象创建对应的ObjectWrapper对象，则由优先使用Obj ectWrapperFactory，
            // 而 DefaultObjectWrapperFactory.hasWrapperFor（）始终返回 false 。用户可以自定义 ObjectWrapperFactory 实现进行扩展
            this.objectWrapper = objectWrapperFactory.getWrapperFor(this, object);
        } else if (object instanceof Map) {
            // 若原始对象为 Map 类型，则创建MapWrapper对象
            this.objectWrapper = new MapWrapper(this, (Map) object);
        } else if (object instanceof Collection) {
            // 若原始对象是Collection类型，则创建CollectionWrapper对象
            this.objectWrapper = new CollectionWrapper(this, (Collection) object);
        } else {
            //若原始对象是普通的JavaBean对象，则创建BeanWrapper对象
            this.objectWrapper = new BeanWrapper(this, object);
        }
    }

    /**
     * 通过静态方法创建 MetaObject 对象
     * @param object
     * @param objectFactory
     * @param objectWrapperFactory
     * @param reflectorFactory
     * @return
     */
    public static MetaObject forObject(Object object, ObjectFactory objectFactory, ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory) {
        if (object == null) {
            return SystemMetaObject.NULL_META_OBJECT;
        } else {
            return new MetaObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
        }
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public ObjectWrapperFactory getObjectWrapperFactory() {
        return objectWrapperFactory;
    }

    public ReflectorFactory getReflectorFactory() {
        return reflectorFactory;
    }

    public Object getOriginalObject() {
        return originalObject;
    }

    public String findProperty(String propName, boolean useCamelCaseMapping) {
        return objectWrapper.findProperty(propName, useCamelCaseMapping);
    }

    public String[] getGetterNames() {
        return objectWrapper.getGetterNames();
    }

    public String[] getSetterNames() {
        return objectWrapper.getSetterNames();
    }

    public Class<?> getSetterType(String name) {
        return objectWrapper.getSetterType(name);
    }

    public Class<?> getGetterType(String name) {
        return objectWrapper.getGetterType(name);
    }

    public boolean hasSetter(String name) {
        return objectWrapper.hasSetter(name);
    }

    public boolean hasGetter(String name) {
        return objectWrapper.hasGetter(name);
    }

    /**
     * 获取name属性表达式的值
     * test: my.test01.Test01#test_3
     * @param name
     * @return
     */
    public Object getValue(String name) {
        // 解析属性表达式
        PropertyTokenizer prop = new PropertyTokenizer(name);
        // 处理子表达式
        if (prop.hasNext()) {
            // 根据 PropertyTokenizer 解析后指定的属性，创建相应的 MetaObject 对象
            MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
            if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
                return null;
            } else {
                return metaValue.getValue(prop.getChildren());
            }
        } else {
            return objectWrapper.get(prop);
        }
    }

    /**
     * 设置对象object中属性表达式的属性值
     * test: my.test01.Test01#test_3
     * @param name
     * @param value
     */
    public void setValue(String name, Object value) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (prop.hasNext()) {
            MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
            if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
                if (value == null && prop.getChildren() != null) {
                    // don't instantiate child path if value is null
                    return;
                } else {
                    metaValue = objectWrapper.instantiatePropertyValue(name, prop, objectFactory);
                }
            }
            metaValue.setValue(prop.getChildren(), value);
        } else {
            objectWrapper.set(prop, value);
        }
    }

    public MetaObject metaObjectForProperty(String name) {
        // 获取指定的属性
        Object value = getValue(name);
        // 创建该属性对象相应的 MetaObject 对象
        return MetaObject.forObject(value, objectFactory, objectWrapperFactory, reflectorFactory);
    }

    public ObjectWrapper getObjectWrapper() {
        return objectWrapper;
    }

    public boolean isCollection() {
        return objectWrapper.isCollection();
    }

    public void add(Object element) {
        objectWrapper.add(element);
    }

    public <E> void addAll(List<E> list) {
        objectWrapper.addAll(list);
    }

}
