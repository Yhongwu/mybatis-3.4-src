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
package org.apache.ibatis.type;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;

/**
 * MyBatis 初始化过程中，会为所有己知的 TypeHandler 创建对象，
 * 并实现注册到TypeHandlerRegistry 中，由 TypeHandlerRegistry负责管理这些 TypeHandler 对象
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public final class TypeHandlerRegistry {

    /**
     * 记录 JdbcType 与 TypeHandler 之间的对应关系，其中 JdbcType 是一个枚举类型，它定义对应的Jdbc类型
     * 该集合主要用于从结果集读取数据时，将数据从 Jdbc 类型转换成 Java 类型 编码
     */
    private final Map<JdbcType, TypeHandler<?>> JDBC_TYPE_HANDLER_MAP = new EnumMap<JdbcType, TypeHandler<?>>(JdbcType.class);
    /**
     * 记录了 Java 类型向指定 JdbcType 转换时，需使用的 TypeHandler 对象。 例如： Java 类型中的 String 可能
     * 转换成数据库的 char 、 varchar 等多种类型，所以存在一对多关系
     */
    private final Map<Type, Map<JdbcType, TypeHandler<?>>> TYPE_HANDLER_MAP = new ConcurrentHashMap<Type, Map<JdbcType, TypeHandler<?>>>();
    private final TypeHandler<Object> UNKNOWN_TYPE_HANDLER = new UnknownTypeHandler(this);
    /**
     * 记录了全部 TypeHandler 的类型以及该类型相应的 TypeHandler 对象
     */
    private final Map<Class<?>, TypeHandler<?>> ALL_TYPE_HANDLERS_MAP = new HashMap<Class<?>, TypeHandler<?>>();
    /**
     * 空 TypeHandler 集合的标识
     */
    private static final Map<JdbcType, TypeHandler<?>> NULL_TYPE_HANDLER_MAP = new HashMap<JdbcType, TypeHandler<?>>();

    /**
     * 为基础类型注册对应的 TypeHandler 对象
     */
    public TypeHandlerRegistry() {
        register(Boolean.class, new BooleanTypeHandler());
        register(boolean.class, new BooleanTypeHandler());
        register(JdbcType.BOOLEAN, new BooleanTypeHandler());
        register(JdbcType.BIT, new BooleanTypeHandler());

        register(Byte.class, new ByteTypeHandler());
        register(byte.class, new ByteTypeHandler());
        register(JdbcType.TINYINT, new ByteTypeHandler());

        register(Short.class, new ShortTypeHandler());
        register(short.class, new ShortTypeHandler());
        register(JdbcType.SMALLINT, new ShortTypeHandler());

        register(Integer.class, new IntegerTypeHandler());
        register(int.class, new IntegerTypeHandler());
        register(JdbcType.INTEGER, new IntegerTypeHandler());

        register(Long.class, new LongTypeHandler());
        register(long.class, new LongTypeHandler());

        register(Float.class, new FloatTypeHandler());
        register(float.class, new FloatTypeHandler());
        register(JdbcType.FLOAT, new FloatTypeHandler());

        register(Double.class, new DoubleTypeHandler());
        register(double.class, new DoubleTypeHandler());
        register(JdbcType.DOUBLE, new DoubleTypeHandler());

        register(Reader.class, new ClobReaderTypeHandler());
        register(String.class, new StringTypeHandler());
        register(String.class, JdbcType.CHAR, new StringTypeHandler());
        register(String.class, JdbcType.CLOB, new ClobTypeHandler());
        register(String.class, JdbcType.VARCHAR, new StringTypeHandler());
        register(String.class, JdbcType.LONGVARCHAR, new ClobTypeHandler());
        register(String.class, JdbcType.NVARCHAR, new NStringTypeHandler());
        register(String.class, JdbcType.NCHAR, new NStringTypeHandler());
        register(String.class, JdbcType.NCLOB, new NClobTypeHandler());
        register(JdbcType.CHAR, new StringTypeHandler());
        register(JdbcType.VARCHAR, new StringTypeHandler());
        register(JdbcType.CLOB, new ClobTypeHandler());
        register(JdbcType.LONGVARCHAR, new ClobTypeHandler());
        register(JdbcType.NVARCHAR, new NStringTypeHandler());
        register(JdbcType.NCHAR, new NStringTypeHandler());
        register(JdbcType.NCLOB, new NClobTypeHandler());

        register(Object.class, JdbcType.ARRAY, new ArrayTypeHandler());
        register(JdbcType.ARRAY, new ArrayTypeHandler());

        register(BigInteger.class, new BigIntegerTypeHandler());
        register(JdbcType.BIGINT, new LongTypeHandler());

        register(BigDecimal.class, new BigDecimalTypeHandler());
        register(JdbcType.REAL, new BigDecimalTypeHandler());
        register(JdbcType.DECIMAL, new BigDecimalTypeHandler());
        register(JdbcType.NUMERIC, new BigDecimalTypeHandler());

        register(InputStream.class, new BlobInputStreamTypeHandler());
        register(Byte[].class, new ByteObjectArrayTypeHandler());
        register(Byte[].class, JdbcType.BLOB, new BlobByteObjectArrayTypeHandler());
        register(Byte[].class, JdbcType.LONGVARBINARY, new BlobByteObjectArrayTypeHandler());
        register(byte[].class, new ByteArrayTypeHandler());
        register(byte[].class, JdbcType.BLOB, new BlobTypeHandler());
        register(byte[].class, JdbcType.LONGVARBINARY, new BlobTypeHandler());
        register(JdbcType.LONGVARBINARY, new BlobTypeHandler());
        register(JdbcType.BLOB, new BlobTypeHandler());

        register(Object.class, UNKNOWN_TYPE_HANDLER);
        register(Object.class, JdbcType.OTHER, UNKNOWN_TYPE_HANDLER);
        register(JdbcType.OTHER, UNKNOWN_TYPE_HANDLER);

        register(Date.class, new DateTypeHandler());
        register(Date.class, JdbcType.DATE, new DateOnlyTypeHandler());
        register(Date.class, JdbcType.TIME, new TimeOnlyTypeHandler());
        register(JdbcType.TIMESTAMP, new DateTypeHandler());
        register(JdbcType.DATE, new DateOnlyTypeHandler());
        register(JdbcType.TIME, new TimeOnlyTypeHandler());

        register(java.sql.Date.class, new SqlDateTypeHandler());
        register(java.sql.Time.class, new SqlTimeTypeHandler());
        register(java.sql.Timestamp.class, new SqlTimestampTypeHandler());

        // mybatis-typehandlers-jsr310
        try {
            // since 1.0.0
            register("java.time.Instant", "org.apache.ibatis.type.InstantTypeHandler");
            register("java.time.LocalDateTime", "org.apache.ibatis.type.LocalDateTimeTypeHandler");
            register("java.time.LocalDate", "org.apache.ibatis.type.LocalDateTypeHandler");
            register("java.time.LocalTime", "org.apache.ibatis.type.LocalTimeTypeHandler");
            register("java.time.OffsetDateTime", "org.apache.ibatis.type.OffsetDateTimeTypeHandler");
            register("java.time.OffsetTime", "org.apache.ibatis.type.OffsetTimeTypeHandler");
            register("java.time.ZonedDateTime", "org.apache.ibatis.type.ZonedDateTimeTypeHandler");
            // since 1.0.1
            register("java.time.Month", "org.apache.ibatis.type.MonthTypeHandler");
            register("java.time.Year", "org.apache.ibatis.type.YearTypeHandler");
            // since 1.0.2
            register("java.time.YearMonth", "org.apache.ibatis.type.YearMonthTypeHandler");
            register("java.time.chrono.JapaneseDate", "org.apache.ibatis.type.JapaneseDateTypeHandler");

        } catch (ClassNotFoundException e) {
            // no JSR-310 handlers
        }

        // issue #273
        register(Character.class, new CharacterTypeHandler());
        register(char.class, new CharacterTypeHandler());
    }

    public boolean hasTypeHandler(Class<?> javaType) {
        return hasTypeHandler(javaType, null);
    }

    public boolean hasTypeHandler(TypeReference<?> javaTypeReference) {
        return hasTypeHandler(javaTypeReference, null);
    }

    public boolean hasTypeHandler(Class<?> javaType, JdbcType jdbcType) {
        return javaType != null && getTypeHandler((Type) javaType, jdbcType) != null;
    }

    public boolean hasTypeHandler(TypeReference<?> javaTypeReference, JdbcType jdbcType) {
        return javaTypeReference != null && getTypeHandler(javaTypeReference, jdbcType) != null;
    }

    public TypeHandler<?> getMappingTypeHandler(Class<? extends TypeHandler<?>> handlerType) {
        return ALL_TYPE_HANDLERS_MAP.get(handlerType);
    }

    public <T> TypeHandler<T> getTypeHandler(Class<T> type) {
        return getTypeHandler((Type) type, null);
    }

    public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference) {
        return getTypeHandler(javaTypeReference, null);
    }

    public TypeHandler<?> getTypeHandler(JdbcType jdbcType) {
        return JDBC_TYPE_HANDLER_MAP.get(jdbcType);
    }

    public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType) {
        return getTypeHandler((Type) type, jdbcType);
    }

    public <T> TypeHandler<T> getTypeHandler(TypeReference<T> javaTypeReference, JdbcType jdbcType) {
        return getTypeHandler(javaTypeReference.getRawType(), jdbcType);
    }

    @SuppressWarnings("unchecked")
    /**
     * 根据指的 Java 类型和JdbcType 类型查找相应的 TypeHandler 对象
     */
    private <T> TypeHandler<T> getTypeHandler(Type type, JdbcType jdbcType) {
        // 查找（或初始化） Java 类型对应的 TypeHandler 集合
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = getJdbcHandlerMap(type);
        TypeHandler<?> handler = null;
        if (jdbcHandlerMap != null) {
            // 根据 JdbcType 类型查找 TypeHandler 对象
            handler = jdbcHandlerMap.get(jdbcType);
            if (handler == null) {
                handler = jdbcHandlerMap.get(null);
            }
            if (handler == null) {
                // #591
                // 如果jdbcHandlerMap 只注册了一个 TypeHandler ，则使用此 TypeHandler 对象
                handler = pickSoleHandler(jdbcHandlerMap);
            }
        }
        // type drives generics here
        return (TypeHandler<T>) handler;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    /**
     * 查找指定 Java 类型对应的 TypeHandler 集合
     */
    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMap(Type type) {
        // 查找指定 Java 类型对应的 TypeHandler 集合
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = TYPE_HANDLER_MAP.get(type);
        // 检测是否为空集合标识
        if (NULL_TYPE_HANDLER_MAP.equals(jdbcHandlerMap)) {
            return null;
        }
        // 初始化指定 Java 类型的 TypeHandler 集合
        if (jdbcHandlerMap == null && type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            if (clazz.isEnum()) {
                // 枚举类型的处理
                jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(clazz);
                if (jdbcHandlerMap == null) {
                    register(clazz, new EnumTypeHandler(clazz));
                    return TYPE_HANDLER_MAP.get(clazz);
                }
            } else {
                // 查找父类对应的 TypeHandler 集合，并作为初始集合
                jdbcHandlerMap = getJdbcHandlerMapForSuperclass(clazz);
            }
        }
        TYPE_HANDLER_MAP.put(type, jdbcHandlerMap == null ? NULL_TYPE_HANDLER_MAP : jdbcHandlerMap);
        return jdbcHandlerMap;
    }

    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForEnumInterfaces(Class<?> clazz) {
        for (Class<?> iface : clazz.getInterfaces()) {
            Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = TYPE_HANDLER_MAP.get(iface);
            if (jdbcHandlerMap == null) {
                jdbcHandlerMap = getJdbcHandlerMapForEnumInterfaces(iface);
            }
            if (jdbcHandlerMap != null) {
                return jdbcHandlerMap;
            }
        }
        return null;
    }

    /**
     * 查找父类对应的 TypeHandler 集合，并作为初始集合
     * @param clazz
     * @return
     */
    private Map<JdbcType, TypeHandler<?>> getJdbcHandlerMapForSuperclass(Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null || Object.class.equals(superclass)) {
            return null;
        }
        Map<JdbcType, TypeHandler<?>> jdbcHandlerMap = TYPE_HANDLER_MAP.get(superclass);
        if (jdbcHandlerMap != null) {
            return jdbcHandlerMap;
        } else {
            return getJdbcHandlerMapForSuperclass(superclass);
        }
    }

    private TypeHandler<?> pickSoleHandler(Map<JdbcType, TypeHandler<?>> jdbcHandlerMap) {
        TypeHandler<?> soleHandler = null;
        for (TypeHandler<?> handler : jdbcHandlerMap.values()) {
            if (soleHandler == null) {
                soleHandler = handler;
            } else if (!handler.getClass().equals(soleHandler.getClass())) {
                // More than one type handlers registered.
                return null;
            }
        }
        return soleHandler;
    }

    public TypeHandler<Object> getUnknownTypeHandler() {
        return UNKNOWN_TYPE_HANDLER;
    }

    public void register(JdbcType jdbcType, TypeHandler<?> handler) {
        JDBC_TYPE_HANDLER_MAP.put(jdbcType, handler);
    }

    //
    // REGISTER INSTANCE
    //

    // Only handler

    @SuppressWarnings("unchecked")
    /**
     * 重载2
     */
    public <T> void register(TypeHandler<T> typeHandler) {
        boolean mappedTypeFound = false;
        // 获取@MappedTypes 注解，并根据自MappedTypes 注解指定的 Java 类型进行注册，逻辑与重载①类似
        MappedTypes mappedTypes = typeHandler.getClass().getAnnotation(MappedTypes.class);
        if (mappedTypes != null) {
            for (Class<?> handledType : mappedTypes.value()) {
                // 交由重载③处理
                register(handledType, typeHandler);
                mappedTypeFound = true;
            }
        }
        // @since 3.1.0 - try to auto-discover the mapped type
        // 从3.1.0版本开始，可以根据 TypeHandler 类型自动查找对应的 Java 类型，这需要
        // 我们的 TypeHandler 实现类同时继承 TypeReference 这个抽象类
        if (!mappedTypeFound && typeHandler instanceof TypeReference) {
            try {
                TypeReference<T> typeReference = (TypeReference<T>) typeHandler;
                // 交由重载③处理
                register(typeReference.getRawType(), typeHandler);
                mappedTypeFound = true;
            } catch (Throwable t) {
                // maybe users define the TypeReference with a different type and are not assignable, so just ignore it
            }
        }
        // 类型转换后，由重载③处理
        if (!mappedTypeFound) {
            register((Class<T>) null, typeHandler);
        }
    }

    // java type + handler

    public <T> void register(Class<T> javaType, TypeHandler<? extends T> typeHandler) {
        register((Type) javaType, typeHandler);
    }

    /**
     * 重载3 配置文件没有指明JdbcType的处理 会先尝试读取@MappedJdbcTypes
     * @param javaType
     * @param typeHandler
     * @param <T>
     */
    private <T> void register(Type javaType, TypeHandler<? extends T> typeHandler) {
        // 尝试读取 TypeHandler 类中定义的＠MappedJdbcTypes 注解
        MappedJdbcTypes mappedJdbcTypes = typeHandler.getClass().getAnnotation(MappedJdbcTypes.class);
        if (mappedJdbcTypes != null) {
            // 根据@MappedJdbcTypes注解指定的 JDBC 类型进行注册
            for (JdbcType handledJdbcType : mappedJdbcTypes.value()) {
                // 交由重载④完成注册
                register(javaType, handledJdbcType, typeHandler);
            }
            if (mappedJdbcTypes.includeNullJdbcType()) {
                // jdbcType = null
                register(javaType, null, typeHandler);
            }
        } else {
            // 交由重载4完成注册
            register(javaType, null, typeHandler);
        }
    }

    public <T> void register(TypeReference<T> javaTypeReference, TypeHandler<? extends T> handler) {
        register(javaTypeReference.getRawType(), handler);
    }

    /**
     * register()1-4方法重载都是在 向 TYPE_HANDLER_MAP 集合和 ALL_TYPE_HANDLERS_MAP 集合注册 TypeHandler 对象
     * 而重载5是向 JDBC_TYPE_HANDLER_MAP集合注册 TypeHandler 对象
     *
     * 1-5都是单个对象的注册
     * 6是扫描包路径的 TypeHandler 接口实现类并完成其注册
     */

    // java type + jdbc type + handler

    /**
     * 重载5
     * @param type
     * @param jdbcType
     * @param handler
     * @param <T>
     */
    public <T> void register(Class<T> type, JdbcType jdbcType, TypeHandler<? extends T> handler) {
        // 注册 JDBC 类型对应的 TypeHandler
        register((Type) type, jdbcType, handler);
    }

    /**
     * register所有重载的最终处理方法
     * 重载4: 三个参数分别指定了 TypeHandler 能够处理的 Java 类型、 Jdbc 类型以及TypeHandler 对象
     * @param javaType
     * @param jdbcType
     * @param handler
     */
    private void register(Type javaType, JdbcType jdbcType, TypeHandler<?> handler) {
        // 检测是否明确指定了 TypeHandler 能够处理的 Java 类型
        if (javaType != null) {
            // 获取指定 Java 类型在 TYPE_HANDLER_MAP 集合中对应的 TypeHandler 集合
            Map<JdbcType, TypeHandler<?>> map = TYPE_HANDLER_MAP.get(javaType);
            // 创建新的 TypeHandler 集合，并添加到TYPE_HANDLER_MAP中
            if (map == null) {
                map = new HashMap<JdbcType, TypeHandler<?>>();
                TYPE_HANDLER_MAP.put(javaType, map);
            }
            // 一个Java类型可能多应多个类型转换
            // 将 TypeHandler 对象注册到 TYPE_HANDLER_MAP集合
            map.put(jdbcType, handler);
        }
        // 向ALL_TYPE_HANDLERS_MAP集合注册 TypeHandler 类型和对应的 TypeHandler 对象，即存储所有的 TypeHandler
        ALL_TYPE_HANDLERS_MAP.put(handler.getClass(), handler);
    }

    //
    // REGISTER CLASS
    //

    // Only handler type

    /**
     * 重载1：使用者未配置javaType和jdbcType属性
     * @param typeHandlerClass
     */
    public void register(Class<?> typeHandlerClass) {
        boolean mappedTypeFound = false;
        // 获取@MappedTypes注解
        MappedTypes mappedTypes = typeHandlerClass.getAnnotation(MappedTypes.class);
        if (mappedTypes != null) {
            // 根据@MappedTypes注解中指定的 Java 类型进行注册
            for (Class<?> javaTypeClass : mappedTypes.value()) {
                // 经过强制类型转换以及使用反射创建 TypeHandler 对象之后，交由重载③继续处理
                register(javaTypeClass, typeHandlerClass);
                mappedTypeFound = true;
            }
        }
        // 未指定＠MappedTypes 注解，交由重载2继续处理
        if (!mappedTypeFound) {
            register(getInstance(null, typeHandlerClass));
        }
    }

    // java type + handler type

    public void register(String javaTypeClassName, String typeHandlerClassName) throws ClassNotFoundException {
        register(Resources.classForName(javaTypeClassName), Resources.classForName(typeHandlerClassName));
    }

    public void register(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
        register(javaTypeClass, getInstance(javaTypeClass, typeHandlerClass));
    }

    // java type + jdbc type + handler type

    public void register(Class<?> javaTypeClass, JdbcType jdbcType, Class<?> typeHandlerClass) {
        register(javaTypeClass, jdbcType, getInstance(javaTypeClass, typeHandlerClass));
    }

    // Construct a handler (used also from Builders)

    @SuppressWarnings("unchecked")
    public <T> TypeHandler<T> getInstance(Class<?> javaTypeClass, Class<?> typeHandlerClass) {
        if (javaTypeClass != null) {
            try {
                Constructor<?> c = typeHandlerClass.getConstructor(Class.class);
                return (TypeHandler<T>) c.newInstance(javaTypeClass);
            } catch (NoSuchMethodException ignored) {
                // ignored
            } catch (Exception e) {
                throw new TypeException("Failed invoking constructor for handler " + typeHandlerClass, e);
            }
        }
        try {
            Constructor<?> c = typeHandlerClass.getConstructor();
            return (TypeHandler<T>) c.newInstance();
        } catch (Exception e) {
            throw new TypeException("Unable to find a usable constructor for " + typeHandlerClass, e);
        }
    }

    // scan

    /**
     * 重载6：
     * 扫描整个包下的 TypeHandler 接口实现类，并将完成这些 TypeHandler 实现类的注册
     * @param packageName
     */
    public void register(String packageName) {
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
        // 查找指定包下的 TypeHandler 接口实现类
        resolverUtil.find(new ResolverUtil.IsA(TypeHandler.class), packageName);
        Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();
        for (Class<?> type : handlerSet) {
            //Ignore inner classes and interfaces (including package-info.java) and abstract classes
            // 过滤掉内部类 、接口以及抽象类
            if (!type.isAnonymousClass() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
                // 交由重载①继续后续注册操作
                register(type);
            }
        }
    }

    // get information

    /**
     * @since 3.2.2
     */
    public Collection<TypeHandler<?>> getTypeHandlers() {
        return Collections.unmodifiableCollection(ALL_TYPE_HANDLERS_MAP.values());
    }

}
