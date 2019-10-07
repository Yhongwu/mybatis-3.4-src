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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 *
 * 通过反射获取目标类的 getter 方法及其返回值类型，setter 方法及其参数值类型等元信息。并将获取到的元信息缓存到相应的集合中，供后续使用。
 */
public class Reflector {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * 解析目标类
     */
    private Class<?> type;
    /**
     * 可读属性名称数组，用于保存 getter 方法对应的属性名称
     */
    private String[] readablePropertyNames = EMPTY_STRING_ARRAY;
    /**
     * 可写属性名称数组，用于保存 setter 方法对应的属性名称
     */
    private String[] writeablePropertyNames = EMPTY_STRING_ARRAY;
    /**
     * 用于保存属性名称到 Invoke 的映射。setter 方法会被封装到 MethodInvoker 对象中
     */
    private Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
    /**
     * 用于保存属性名称到 Invoke 的映射。同上，getter 方法也会被封装到 MethodInvoker 对象中
     */
    private Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
    /**
     * 用于保存 setter 对应的属性名与参数类型的映射
     */
    private Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
    /**
     * 用于保存 getter 对应的属性名与返回值类型的映射
     */
    private Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
    /**
     * 保存默认构造器
     */
    private Constructor<?> defaultConstructor;
    /**
     * 用于保存大写属性名与属性名之间的映射，比如 <NAME, name>
     */
    private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

    /**
     * 构造方法
     * 包含了很多初始化逻辑，目标类的元信息解析过程也是在构造方法中完成的，
     * 这些元信息最终会被保存到 Reflector 的成员变量
     * @param clazz
     */
    public Reflector(Class<?> clazz) {
        type = clazz;
        // 解析目标类的默认构造方法，并赋值给 defaultConstructor 变量
        addDefaultConstructor(clazz);
        // 解析 getter 方法，并将解析结果放入 getMethods 中
        addGetMethods(clazz);
        // 解析 setter 方法，并将解析结果放入 setMethods 中
        addSetMethods(clazz);
        // 解析属性字段，并将解析结果添加到 setMethods 或 getMethods 中
        addFields(clazz);
        // 从 getMethods 映射中获取可读属性名数组
        readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
        // 从 setMethods 映射中获取可写属性名数组
        writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
        // 将所有属性名的大写形式作为键，属性名作为值，存入到 caseInsensitivePropertyMap 中
        for (String propName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
        for (String propName : writeablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
    }

    private void addDefaultConstructor(Class<?> clazz) {
        Constructor<?>[] consts = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : consts) {
            if (constructor.getParameterTypes().length == 0) {
                if (canAccessPrivateMethods()) {
                    try {
                        constructor.setAccessible(true);
                    } catch (Exception e) {
                        // Ignored. This is only a final precaution, nothing we can do.
                    }
                }
                if (constructor.isAccessible()) {
                    this.defaultConstructor = constructor;
                }
            }
        }
    }

    /**
     * 解析getXXX的方法，也会解析isXXX方法
     * @param cls
     */
    private void addGetMethods(Class<?> cls) {
        Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
        // 获取当前类，接口，以及父类中的方法
        Method[] methods = getClassMethods(cls);
        for (Method method : methods) {
            // get is方法不包含参数的
            if (method.getParameterTypes().length > 0) {
                continue;
            }
            String name = method.getName();
            // getXXX或isXXX方法
            if ((name.startsWith("get") && name.length() > 3)
                    || (name.startsWith("is") && name.length() > 2)) {
                // 将 getXXX 或 isXXX 等方法名转成相应的属性，比如 getName -> name
                name = PropertyNamer.methodToProperty(name);
                // 将方法和属性名对应添加到Map<String, List<Method>>中，List<Method>>是考虑出现冲突的情况
                // 暂存冲突的方法 比如getA和isA 属性名都是a
                addMethodConflict(conflictingGetters, name, method);
            }
        }
        // 解决 getter 冲突
        resolveGetterConflicts(conflictingGetters);
    }

    /**
     * 解决 getter 冲突，并将方法加入getMethods，方法返回值加入getTypes
     * 主要根据返回类型判断：
     * 如果两个方法返回类型一致，并且不是boolean，无法判断，抛异常
     * 如果两个方法返回类型一致，并且是boolean，选择isXXX方法
     * 如果两个方法返回类型不一致，并且有继承关系，选择更具体的即子类方法
     * 如果两个方法返回类型不一致，并且无继承关系，无法判断，抛异常
     * @param conflictingGetters
     */
    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
            Method winner = null;
            String propName = entry.getKey();
            for (Method candidate : entry.getValue()) {
                if (winner == null) {
                    winner = candidate;
                    continue;
                }
                // winner != null 说明有多个一个属性对应了多个方法
                // 获取返回值类型
                Class<?> winnerType = winner.getReturnType();
                Class<?> candidateType = candidate.getReturnType();
                // 判断两个方法返回类型是否一致
                if (candidateType.equals(winnerType)) {
                    // 返回类型一致并且返回类型不为boolean 无法确定哪个合适 抛异常
                    if (!boolean.class.equals(candidateType)) {
                        throw new ReflectionException(
                                "Illegal overloaded getter method with ambiguous type for property "
                                        + propName + " in class " + winner.getDeclaringClass()
                                        + ". This breaks the JavaBeans specification and can cause unpredictable results.");
                    } else if (candidate.getName().startsWith("is")) {
                        // 返回类型一致并且返回类型为boolean, 则选择isXXX方法
                        winner = candidate;
                    }
                    // 两个方法返回类型不一致
                } else if (candidateType.isAssignableFrom(winnerType)) {
                    // winnerType 是 candidateType 的子类 winnerType更具体 所以选择 winner方法
                    // OK getter type is descendant
                } else if (winnerType.isAssignableFrom(candidateType)) {
                    // candidateType 是 winnerType 的子类 candidateType更具体 所以选择 candidate
                    winner = candidate;
                } else {
                    throw new ReflectionException(
                            "Illegal overloaded getter method with ambiguous type for property "
                                    + propName + " in class " + winner.getDeclaringClass()
                                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
                }
            }
            // 将筛选出的方法添加到 getMethods 中，并将方法返回值添加到 getTypes 中
            addGetMethod(propName, winner);
        }
    }

    private void addGetMethod(String name, Method method) {
        if (isValidPropertyName(name)) {
            getMethods.put(name, new MethodInvoker(method));
            // 解析返回值类型
            Type returnType = TypeParameterResolver.resolveReturnType(method, type);
            // 将返回值类型由 Type 转为 Class，并将转换后的结果缓存到 getTypes 中
            getTypes.put(name, typeToClass(returnType));
        }
    }


    /**
     * 解析 setter 方法，并将解析结果放入 setMethods 中
     * @param cls
     */
    private void addSetMethods(Class<?> cls) {
        Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
        // 获取当前类，接口，以及父类中的方法。
        Method[] methods = getClassMethods(cls);
        for (Method method : methods) {
            String name = method.getName();
            // set开头并且只有一个参数
            if (name.startsWith("set") && name.length() > 3) {
                if (method.getParameterTypes().length == 1) {
                    name = PropertyNamer.methodToProperty(name);
                    // 将方法和属性名对应添加到Map<String, List<Method>>中，List<Method>>是考虑出现冲突的情况
                    // 可能出现冲突的情况 重载，比如setA(String a)、setA(int a)
                    addMethodConflict(conflictingSetters, name, method);
                }
            }
        }
        // 解决setter冲突
        resolveSetterConflicts(conflictingSetters);
    }

    /**
     * 将属性名和对应方法存放到Map<String, List<Method>>
     * @param conflictingMethods
     * @param name
     * @param method
     */
    private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
        List<Method> list = conflictingMethods.get(name);
        if (list == null) {
            list = new ArrayList<Method>();
            conflictingMethods.put(name, list);
        }
        list.add(method);
    }

    /**
     * 解决 setter 冲突，将解决冲突后的方法放入setMethods中，并将方法参数值添加到 setTypes 中
     * 借助getter方法的返回值来解决：
     * 如果setter方法的参数类型和对应的getter方法的返回类型一致，则确定为该方法
     * 否则，如果两个方法参数有继承关系，则选用子类的那个方法，否则，抛异常
     * @param conflictingSetters
     */
    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        for (String propName : conflictingSetters.keySet()) {
            List<Method> setters = conflictingSetters.get(propName);
            // 获取 getter 方法的返回值类型，用getter的返回值类型来帮助判断哪个setter方法更合适
            Class<?> getterType = getTypes.get(propName);
            Method match = null;
            ReflectionException exception = null;
            for (Method setter : setters) {
                // 获取参数类型
                Class<?> paramType = setter.getParameterTypes()[0];
                // 参数类型和返回类型一致，则选择该方法，并结束循环
                if (paramType.equals(getterType)) {
                    // should be the best match
                    match = setter;
                    break;
                }
                if (exception == null) {
                    try {
                        match = pickBetterSetter(match, setter, propName);
                    } catch (ReflectionException e) {
                        // there could still be the 'best match'
                        match = null;
                        exception = e;
                    }
                }
            }
            // 没找到合适的方法 抛出异常
            if (match == null) {
                throw exception;
            } else {
                // 将解决冲突后的方法放入 setMethods 中，并将方法参数值添加到 setTypes 中
                addSetMethod(propName, match);
            }
        }
    }

    /**
     * 从两个setter方法选择更合适的
     * @param setter1
     * @param setter2
     * @param property
     * @return
     */
    private Method pickBetterSetter(Method setter1, Method setter2, String property) {
        if (setter1 == null) {
            return setter2;
        }
        Class<?> paramType1 = setter1.getParameterTypes()[0];
        Class<?> paramType2 = setter2.getParameterTypes()[0];
        // 如果paramType2是paramType1的子类 即paramType2可以赋值给paramType1,则认为paramType2的方法更合适
        if (paramType1.isAssignableFrom(paramType2)) {
            return setter2;
        } else if (paramType2.isAssignableFrom(paramType1)) {
            return setter1;
        }
        // 两个参数类型没继承关系 抛异常
        throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
                + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
                + paramType2.getName() + "'.");
    }

    /**
     * 将setter方法放入setMethods中，并将方法参数值添加到 setTypes 中
     * @param name
     * @param method
     */
    private void addSetMethod(String name, Method method) {
        // 判断属性名是否合法
        if (isValidPropertyName(name)) {
            setMethods.put(name, new MethodInvoker(method));
            // 解析参数类型列表
            Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
            // 将参数类型由 Type 转为 Class，并将转换后的结果缓存到 setTypes
            setTypes.put(name, typeToClass(paramTypes[0]));
        }
    }

    private Class<?> typeToClass(Type src) {
        Class<?> result = null;
        if (src instanceof Class) {
            result = (Class<?>) src;
        } else if (src instanceof ParameterizedType) {
            result = (Class<?>) ((ParameterizedType) src).getRawType();
        } else if (src instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) {
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                Class<?> componentClass = typeToClass(componentType);
                result = Array.newInstance((Class<?>) componentClass, 0).getClass();
            }
        }
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    private void addFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (canAccessPrivateMethods()) {
                try {
                    field.setAccessible(true);
                } catch (Exception e) {
                    // Ignored. This is only a final precaution, nothing we can do.
                }
            }
            if (field.isAccessible()) {
                if (!setMethods.containsKey(field.getName())) {
                    // issue #379 - removed the check for final because JDK 1.5 allows
                    // modification of final fields through reflection (JSR-133). (JGB)
                    // pr #16 - final static can only be set by the classloader
                    int modifiers = field.getModifiers();
                    if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
                        addSetField(field);
                    }
                }
                if (!getMethods.containsKey(field.getName())) {
                    addGetField(field);
                }
            }
        }
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());
        }
    }

    private void addSetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            setMethods.put(field.getName(), new SetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addGetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    /**
     * 校验属性名
     * @param name
     * @return
     */
    private boolean isValidPropertyName(String name) {
        return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
    }

    /*
     * This method returns an array containing all methods
     * declared in this class and any superclass.
     * We use this method, instead of the simpler Class.getMethods(),
     * because we want to look for private methods as well.
     *
     * @param cls The class
     * @return An array containing all methods in this class
     */
    private Method[] getClassMethods(Class<?> cls) {
        Map<String, Method> uniqueMethods = new HashMap<String, Method>();
        Class<?> currentClass = cls;
        while (currentClass != null) {
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

            // we also need to look for interface methods -
            // because the class may be abstract
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }

            currentClass = currentClass.getSuperclass();
        }

        Collection<Method> methods = uniqueMethods.values();

        return methods.toArray(new Method[methods.size()]);
    }

    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            if (!currentMethod.isBridge()) {
                String signature = getSignature(currentMethod);
                // check to see if the method is already known
                // if it is known, then an extended class must have
                // overridden a method
                if (!uniqueMethods.containsKey(signature)) {
                    if (canAccessPrivateMethods()) {
                        try {
                            currentMethod.setAccessible(true);
                        } catch (Exception e) {
                            // Ignored. This is only a final precaution, nothing we can do.
                        }
                    }

                    uniqueMethods.put(signature, currentMethod);
                }
            }
        }
    }

    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?> returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getName()).append('#');
        }
        sb.append(method.getName());
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i == 0) {
                sb.append(':');
            } else {
                sb.append(',');
            }
            sb.append(parameters[i].getName());
        }
        return sb.toString();
    }

    private static boolean canAccessPrivateMethods() {
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (null != securityManager) {
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /*
     * Gets the name of the class the instance provides information for
     *
     * @return The class name
     */
    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        } else {
            throw new ReflectionException("There is no default constructor for " + type);
        }
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    /*
     * Gets the type for a property setter
     *
     * @param propertyName - the name of the property
     * @return The Class of the propery setter
     */
    public Class<?> getSetterType(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /*
     * Gets the type for a property getter
     *
     * @param propertyName - the name of the property
     * @return The Class of the propery getter
     */
    public Class<?> getGetterType(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /*
     * Gets an array of the readable properties for an object
     *
     * @return The array
     */
    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    /*
     * Gets an array of the writeable properties for an object
     *
     * @return The array
     */
    public String[] getSetablePropertyNames() {
        return writeablePropertyNames;
    }

    /*
     * Check to see if a class has a writeable property by name
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a writeable property by the name
     * 判断是否有对应的setter方法
     */
    public boolean hasSetter(String propertyName) {
        return setMethods.keySet().contains(propertyName);
    }

    /*
     * Check to see if a class has a readable property by name
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a readable property by the name
     */
    public boolean hasGetter(String propertyName) {
        return getMethods.keySet().contains(propertyName);
    }

    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }
}
