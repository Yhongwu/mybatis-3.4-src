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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 通过 Reflector 和 PropertyTokenizer 组合使用， 实现了对复杂的属性表达式的解析，并实现了获取指定属性描述信息的功能
 * 对类级别的元信息的封装和处理(对象级别：ObjectWrapper)
 * @author Clinton Begin
 */
public class MetaClass {
    /**
     * ReflectorFactory 对象，Reflector的工厂类,也用于缓存 Reflector 对象
     */
    private ReflectorFactory reflectorFactory;
    /**
     * 在创建 MetaClass 时会指定一个类，该 Reflector 对象会用于记录该类相关的元信息
     */
    private Reflector reflector;

    // private 修饰
    private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
        this.reflectorFactory = reflectorFactory;
        // 根据类型创建 Reflector 对象
        this.reflector = reflectorFactory.findForClass(type);
    }

    /**
     * 使用静态方法创建 MetaClass 对象
     * @param type
     * @param reflectorFactory
     * @return
     */
    public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
        return new MetaClass(type, reflectorFactory);
    }

    public MetaClass metaClassForProperty(String name) {
        Class<?> propType = reflector.getGetterType(name);
        return MetaClass.forClass(propType, reflectorFactory);
    }

    // test: my.test01.Test01#test_1
    public String findProperty(String name) {
        // 委托给 buildProperty （）方法实现
        StringBuilder prop = buildProperty(name, new StringBuilder());
        return prop.length() > 0 ? prop.toString() : null;
    }


    public String findProperty(String name, boolean useCamelCaseMapping) {
        if (useCamelCaseMapping) {
            name = name.replace("_", "");
        }
        return findProperty(name);
    }

    public String[] getGetterNames() {
        return reflector.getGetablePropertyNames();
    }

    public String[] getSetterNames() {
        return reflector.getSetablePropertyNames();
    }

    public Class<?> getSetterType(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (prop.hasNext()) {
            MetaClass metaProp = metaClassForProperty(prop.getName());
            return metaProp.getSetterType(prop.getChildren());
        } else {
            return reflector.getSetterType(prop.getName());
        }
    }

    /**
     * name属性对应的类型
     * 比如user.roles[0].code 获取的是code的类型
     * 不一定需要存在getter方法 (reflector的原因 有属性就会有在集合里存在对应的getter和setter？？？)
     * test: my.test01.Test01#test_2
     * @param name
     * @return
     */
    public Class<?> getGetterType(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (prop.hasNext()) {
            MetaClass metaProp = metaClassForProperty(prop);
            return metaProp.getGetterType(prop.getChildren());
        }
        // issue #506. Resolve the type inside a Collection Object
        return getGetterType(prop);
    }

    /**
     * 底层会调用 MetaClass. getGetterType(PropertyTokenizer）方法，针对 PropertyTokenizer中是否包含索引信息做进一步处理
     * @param prop
     * @return
     */
    private MetaClass metaClassForProperty(PropertyTokenizer prop) {
        // 获取表达式所表示的属性的类型
        Class<?> propType = getGetterType(prop);
        return MetaClass.forClass(propType, reflectorFactory);
    }

    private Class<?> getGetterType(PropertyTokenizer prop) {
        // 获取属性类型
        Class<?> type = reflector.getGetterType(prop.getName());
        // 判断该表达式中是否使用［］指定了下标，且是Collection子类
        if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
            // 解析属性的类型
            Type returnType = getGenericGetterType(prop.getName());
            // 针对泛型集合类型进行处理
            if (returnType instanceof ParameterizedType) {
                // 获取实际的类型参数
                Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
                // actualTypeArguments.length == 1 可能是<T，T>???
                if (actualTypeArguments != null && actualTypeArguments.length == 1) {
                    returnType = actualTypeArguments[0];
                    if (returnType instanceof Class) {
                        type = (Class<?>) returnType;
                    } else if (returnType instanceof ParameterizedType) {
                        // ?? 泛型仍然为泛型的处理？
                        type = (Class<?>) ((ParameterizedType) returnType).getRawType();
                    }
                }
            }
        }
        return type;
    }

    private Type getGenericGetterType(String propertyName) {
        try {
            // 根据Reflector.getMethods集合中记录的Invoker实现类的类型，决定解析getter方法返回值类型还是解析字段类型
            Invoker invoker = reflector.getGetInvoker(propertyName);
            if (invoker instanceof MethodInvoker) {
                Field _method = MethodInvoker.class.getDeclaredField("method");
                _method.setAccessible(true);
                Method method = (Method) _method.get(invoker);
                return TypeParameterResolver.resolveReturnType(method, reflector.getType());
            } else if (invoker instanceof GetFieldInvoker) {
                Field _field = GetFieldInvoker.class.getDeclaredField("field");
                _field.setAccessible(true);
                Field field = (Field) _field.get(invoker);
                return TypeParameterResolver.resolveFieldType(field, reflector.getType());
            }
        } catch (NoSuchFieldException e) {
        } catch (IllegalAccessException e) {
        }
        return null;
    }

    /**
     * 判断属性表达式所表示的属性是否有对应的属性
     * 类似boolean hasGetter(String name)
     * @param name
     * @return
     */
    public boolean hasSetter(String name) {
        // 属性分词器，用于解析较为复杂的属性名
        PropertyTokenizer prop = new PropertyTokenizer(name);
        // hasNext 返回 true，则表明 name 是一个复合属性
        if (prop.hasNext()) {
            // 调用 reflector 的 hasSetter 方法
            if (reflector.hasSetter(prop.getName())) {
                // 为属性创建创建 MetaClass
                MetaClass metaProp = metaClassForProperty(prop.getName());
                // 再次调用 hasSetter
                return metaProp.hasSetter(prop.getChildren());
            } else {
                return false;
            }
        } else {
            return reflector.hasSetter(prop.getName());
        }
    }

    /**
     * 判断属性表达式所表示的属性是否有对应的属性
     * test: my.test01.Test01#test_2
     * @param name
     * @return
     */
    public boolean hasGetter(String name) {
        // 解析属性表达式
        PropertyTokenizer prop = new PropertyTokenizer(name);
        // 是否存在子表达式
        if (prop.hasNext()) {
            // PropertyTokenizer.name 指定的属性有 getter 方法(有属性就会有对应的setter和getter？？reflector获取的元信息的结果)，才能递归处理子表达式
            if (reflector.hasGetter(prop.getName())) {
                // 如果有对应的属性 更新metaProp为对应的子属性
                MetaClass metaProp = metaClassForProperty(prop);
                // 递归入口
                return metaProp.hasGetter(prop.getChildren());
            } else {
                // 递归出口
                return false;
            }
        } else {
            // 递归出口
            return reflector.hasGetter(prop.getName());
        }
    }

    public Invoker getGetInvoker(String name) {
        return reflector.getGetInvoker(name);
    }

    public Invoker getSetInvoker(String name) {
        return reflector.getSetInvoker(name);
    }

    /**
     * 通过 PropertyTokenizer 解析复杂的属性表达式
     * @param name
     * @param builder
     * @return
     */
    private StringBuilder buildProperty(String name, StringBuilder builder) {
        // 解析属性表达式
        PropertyTokenizer prop = new PropertyTokenizer(name);
        // 是否还有子表达式 见PropertyTokenizer类
        if (prop.hasNext()) {
            // 查找 PropertyTokenizer.name 对应的属性
            String propertyName = reflector.findPropertyName(prop.getName());
            if (propertyName != null) {
                // 追加属性名
                builder.append(propertyName);
                builder.append(".");
                // 为该属性创建对应的 MetaClass 对象
                MetaClass metaProp = metaClassForProperty(propertyName);
                // 递归解析 PropertyToken izer.children 字段，并将解析结果添加到 builder 中保存
                metaProp.buildProperty(prop.getChildren(), builder);
            }
        } else {
            // 递归出口
            String propertyName = reflector.findPropertyName(name);
            if (propertyName != null) {
                builder.append(propertyName);
            }
        }
        return builder;
    }

    public boolean hasDefaultConstructor() {
        return reflector.hasDefaultConstructor();
    }

}
