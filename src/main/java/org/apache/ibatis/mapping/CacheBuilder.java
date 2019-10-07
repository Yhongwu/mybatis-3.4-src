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
package org.apache.ibatis.mapping;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;
import org.apache.ibatis.builder.InitializingObject;
import org.apache.ibatis.cache.decorators.BlockingCache;
import org.apache.ibatis.cache.decorators.LoggingCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.ScheduledCache;
import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.decorators.SynchronizedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 * Cache 的建造者
 */
public class CacheBuilder {
    // Cache 对象的唯一标识， 一般情况下对应映射文件中的配置 namespace
    private String id;
    // Cache 接口的真正实现类，默认位是前面介绍的 PerpetualCache
    private Class<? extends Cache> implementation;
    //  装饰器集合，默认只包含 LruCache . class
    private List<Class<? extends Cache>> decorators;
    // Cache 大小
    private Integer size;
    // 清理时间周期
    private Long clearInterval;
    // 是否可读写
    private boolean readWrite;
    // 是否阻塞
    private Properties properties;
    // 其他配置信息
    private boolean blocking;

    public CacheBuilder(String id) {
        this.id = id;
        this.decorators = new ArrayList<Class<? extends Cache>>();
    }

    public CacheBuilder implementation(Class<? extends Cache> implementation) {
        this.implementation = implementation;
        return this;
    }

    public CacheBuilder addDecorator(Class<? extends Cache> decorator) {
        if (decorator != null) {
            this.decorators.add(decorator);
        }
        return this;
    }

    public CacheBuilder size(Integer size) {
        this.size = size;
        return this;
    }

    public CacheBuilder clearInterval(Long clearInterval) {
        this.clearInterval = clearInterval;
        return this;
    }

    public CacheBuilder readWrite(boolean readWrite) {
        this.readWrite = readWrite;
        return this;
    }

    public CacheBuilder blocking(boolean blocking) {
        this.blocking = blocking;
        return this;
    }

    public CacheBuilder properties(Properties properties) {
        this.properties = properties;
        return this;
    }

    /**
     * Cache实例的构建
     * @return
     */
    public Cache build() {
        // 如果implementation 字段和 decorators 集合为空，则为其设立默认佳， implementation 默认
        // 是 PerpetualCache.class, decorators 集合，默认只包含LruCache.class，即设置默认的缓存类型和装饰器
        setDefaultImplementations();
        // 根据 implementation 指定的类型 ，通过反射获取参数为 String 类型的构造方法，并通过该构造方法创建 Cache 对象
        Cache cache = newBaseCacheInstance(implementation, id);
        // 根据<cache>节点下设置的<property>信息，初始化 Cache 对象
        setCacheProperties(cache);
        // issue #352, do not apply decorators to custom caches
        // 检测 cache 对象的类型，如果是 PerpetualCache 类型，则为其添加 decorators 集合中
        //的装饰器，如采是自定义类型的 Cache 接口实现，则不添加 decorators 集合中的装饰
        if (PerpetualCache.class.equals(cache.getClass())) {
            for (Class<? extends Cache> decorator : decorators) {
                // 通过反射获取参数为 Cache 类型的构造方法，并通过该构造方法创建装饰器
                cache = newCacheDecoratorInstance(decorator, cache);
                // 配置 cache 对象的属性
                setCacheProperties(cache);
            }
            // 添加 MyBatis 中提供的标准装饰器
            cache = setStandardDecorators(cache);
        } else if (!LoggingCache.class.isAssignableFrom(cache.getClass())) {
            // 如果不是 LoggingCache 的子类，则添加 LoggingCache 装饰器
            // 对非 LoggingCache 类型的缓存应用 LoggingCache 装饰器
            cache = new LoggingCache(cache);
        }
        return cache;
    }

    private void setDefaultImplementations() {
        // implementation 为空则添加默认
        if (implementation == null) {
            // 设置默认的缓存实现类
            implementation = PerpetualCache.class;
            if (decorators.isEmpty()) {
                // 添加LruCache装饰器
                decorators.add(LruCache.class);
            }
        }
    }

    /**
     * 判断是否有设置某些属性 否则添加默认修饰器
     * @param cache
     * @return
     */
    private Cache setStandardDecorators(Cache cache) {
        try {
            // 创建 cache 对象对应的 MetaObject 对象
            MetaObject metaCache = SystemMetaObject.forObject(cache);
            if (size != null && metaCache.hasSetter("size")) {
                metaCache.setValue("size", size);
            }
            if (clearInterval != null) {
                // clearInterval 不为空，应用 ScheduledCache 装饰器
                cache = new ScheduledCache(cache);
                ((ScheduledCache) cache).setClearInterval(clearInterval);
            }
            if (readWrite) {
                // readWrite 为 true，应用 SerializedCache 装饰器
                cache = new SerializedCache(cache);
            }
            // 应用 LoggingCache，SynchronizedCache 装饰器，使原缓存具备打印日志和线程同步的能力
            cache = new LoggingCache(cache);
            cache = new SynchronizedCache(cache);
            // 是否阻塞，对应添加 BlockingCache 装饰器
            if (blocking) {
                cache = new BlockingCache(cache);
            }
            return cache;
        } catch (Exception e) {
            throw new CacheException("Error building standard cache decorators.  Cause: " + e, e);
        }
    }

    private void setCacheProperties(Cache cache) {
        if (properties != null) {
            // 最终调用了MetaClass的forClass方法。
            // cache 对应的创建MetaObject对象
            MetaObject metaCache = SystemMetaObject.forObject(cache);
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                // 配置项的名称，Cache 对应的属性名称
                String name = (String) entry.getKey();
                // 配置项的值，Cache 对应的属性值
                String value = (String) entry.getValue();
                // 检测 cache 是否有该属性对应的 setter 方法
                if (metaCache.hasSetter(name)) {
                    // 获取该属性的类型
                    Class<?> type = metaCache.getSetterType(name);
                    // 根据参数类型对属性值进行转换，并将转换后的值通过 setter 方法设置到 Cache 实例中
                    if (String.class == type) {
                        metaCache.setValue(name, value);
                    } else if (int.class == type
                            || Integer.class == type) {
                        metaCache.setValue(name, Integer.valueOf(value));
                    } else if (long.class == type
                            || Long.class == type) {
                        metaCache.setValue(name, Long.valueOf(value));
                    } else if (short.class == type
                            || Short.class == type) {
                        metaCache.setValue(name, Short.valueOf(value));
                    } else if (byte.class == type
                            || Byte.class == type) {
                        metaCache.setValue(name, Byte.valueOf(value));
                    } else if (float.class == type
                            || Float.class == type) {
                        metaCache.setValue(name, Float.valueOf(value));
                    } else if (boolean.class == type
                            || Boolean.class == type) {
                        metaCache.setValue(name, Boolean.valueOf(value));
                    } else if (double.class == type
                            || Double.class == type) {
                        metaCache.setValue(name, Double.valueOf(value));
                    } else {
                        throw new CacheException("Unsupported property type for cache: '" + name + "' of type " + type);
                    }
                }
            }
        }
        // 如果 Cache 类继承了InitializingObject 接口，则调用其 initialize()方法继续 自定义的初始化操作
        if (InitializingObject.class.isAssignableFrom(cache.getClass())) {
            try {
                ((InitializingObject) cache).initialize();
            } catch (Exception e) {
                throw new CacheException("Failed cache initialization for '" +
                        cache.getId() + "' on '" + cache.getClass().getName() + "'", e);
            }
        }
    }

    private Cache newBaseCacheInstance(Class<? extends Cache> cacheClass, String id) {
        Constructor<? extends Cache> cacheConstructor = getBaseCacheConstructor(cacheClass);
        try {
            return cacheConstructor.newInstance(id);
        } catch (Exception e) {
            throw new CacheException("Could not instantiate cache implementation (" + cacheClass + "). Cause: " + e, e);
        }
    }

    private Constructor<? extends Cache> getBaseCacheConstructor(Class<? extends Cache> cacheClass) {
        try {
            return cacheClass.getConstructor(String.class);
        } catch (Exception e) {
            throw new CacheException("Invalid base cache implementation (" + cacheClass + ").  " +
                    "Base cache implementations must have a constructor that takes a String id as a parameter.  Cause: " + e, e);
        }
    }

    private Cache newCacheDecoratorInstance(Class<? extends Cache> cacheClass, Cache base) {
        Constructor<? extends Cache> cacheConstructor = getCacheDecoratorConstructor(cacheClass);
        try {
            return cacheConstructor.newInstance(base);
        } catch (Exception e) {
            throw new CacheException("Could not instantiate cache decorator (" + cacheClass + "). Cause: " + e, e);
        }
    }

    private Constructor<? extends Cache> getCacheDecoratorConstructor(Class<? extends Cache> cacheClass) {
        try {
            return cacheClass.getConstructor(Cache.class);
        } catch (Exception e) {
            throw new CacheException("Invalid cache decorator (" + cacheClass + ").  " +
                    "Cache decorators must have a constructor that takes a Cache instance as a parameter.  Cause: " + e, e);
        }
    }
}
