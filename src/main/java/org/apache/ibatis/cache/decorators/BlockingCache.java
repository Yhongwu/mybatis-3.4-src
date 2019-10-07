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
package org.apache.ibatis.cache.decorators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * Simple blocking decorator 
 *
 * Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 * 阻塞版本的缓存
 * @author Eduardo Macarron
 *
 */
public class BlockingCache implements Cache {
    /**
     * 超时时间
     */
    private long timeout;
    /**
     * 被装饰的缓存对象
     */
    private final Cache delegate;
    /**
     * 每个key对应一个ReentrantLock
     */
    private final ConcurrentHashMap<Object, ReentrantLock> locks;

    public BlockingCache(Cache delegate) {
        this.delegate = delegate;
        this.locks = new ConcurrentHashMap<Object, ReentrantLock>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    @Override
    public void putObject(Object key, Object value) {
        try {
            // 设置缓存
            delegate.putObject(key, value);
        } finally {
            // 释放锁(如果在getObject中获取不到数据 则不会释放锁 接着调用该方法会释放该锁)
            releaseLock(key);
        }
    }

    @Override
    public Object getObject(Object key) {
        // 获取key锁
        acquireLock(key);
        // 调用装饰的上层对象的获取方法 如果是第一层装饰 则为PerpetualCache中的方法
        Object value = delegate.getObject(key);
        // 如果获取到 则释放锁
        if (value != null) {
            releaseLock(key);
        }
        return value;
    }

    @Override
    public Object removeObject(Object key) {
        // despite of its name, this method is called only to release locks
        releaseLock(key);
        return null;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    /**
     * 获取key对应的锁对象 不存在则新建
     * @param key
     * @return
     */
    private ReentrantLock getLockForKey(Object key) {
        ReentrantLock lock = new ReentrantLock();
        // 不存在 则新建 存在则返回对应的value
        ReentrantLock previous = locks.putIfAbsent(key, lock);
        return previous == null ? lock : previous;
    }

    private void acquireLock(Object key) {
        // 获取key锁
        Lock lock = getLockForKey(key);
        if (timeout > 0) {
            try {
                // 锁住指定超时时间
                boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    throw new CacheException("Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
                }
            } catch (InterruptedException e) {
                throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
            }
        } else {
            lock.lock();
        }
    }

    private void releaseLock(Object key) {
        ReentrantLock lock = locks.get(key);
        // 是否被当前线程持有
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}