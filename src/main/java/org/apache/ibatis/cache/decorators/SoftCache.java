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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 * 弱引用缓存
 * @author Clinton Begin
 */
public class SoftCache implements Cache {
    /**
     * LinkedList
     * 最近使用的对象会被添加到该Deque，使用强引用引用其value，使其不会被回收
     */
    private final Deque<Object> hardLinksToAvoidGarbageCollection;
    /**
     *  ReferenceQueue ， 引用队列，用于记录已经被 GC 回收的缓存项所对应的 SoftEnt ry 对象
     */
    private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
    private final Cache delegate;
    /**
     * 强连接的个数， 默认值是 256
     */
    private int numberOfHardLinks;

    public SoftCache(Cache delegate) {
        this.delegate = delegate;
        this.numberOfHardLinks = 256;
        this.hardLinksToAvoidGarbageCollection = new LinkedList<Object>();
        this.queueOfGarbageCollectedEntries = new ReferenceQueue<Object>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        removeGarbageCollectedItems();
        return delegate.getSize();
    }


    public void setSize(int size) {
        this.numberOfHardLinks = size;
    }

    /**
     * 除了向缓存中添加缓存项，还会清除己经被 GC 回收的缓存项，
     * @param key Can be any object but usually it is a {@link CacheKey}
     * @param value The result of a select.
     */
    @Override
    public void putObject(Object key, Object value) {
        // 清除已经被 GC 回收的缓存项
        removeGarbageCollectedItems();
        // 向缓存中添加缓存项
        delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
    }

    /**
     * 除 了从缓存 中 查找对应 的 value ，处理被 GC 回收的 value 对应的
     * 缓存项 ， 还会更新 hardLinksToAvoidGarbageCollection 集合
     * @param key The key
     * @return
     */
    @Override
    public Object getObject(Object key) {
        Object result = null;
        @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
                SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
        // 检测缓存 中是否有对应的缓存项
        if (softReference != null) {
            result = softReference.get();
            // 已经被 GC 回收
            if (result == null) {
                // 从缓存中 清除对应的缓存项
                delegate.removeObject(key);
            } else {
                // See #586 (and #335) modifications need more than a read lock
                synchronized (hardLinksToAvoidGarbageCollection) {
                    // 缓存项 的 value 添加到 hardLinksToAvoidGarbageCollection 集合中保存
                    // 超过则删除最老的 有点类型先进先出 最近使用的用强引用hardLinksToAvoidGarbageCollection引用 避免被gc
                    hardLinksToAvoidGarbageCollection.addFirst(result);
                    if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
                        hardLinksToAvoidGarbageCollection.removeLast();
                    }
                }
            }
        }
        return result;
    }

    /**
     *
     * @param key The key
     * @return
     */
    @Override
    public Object removeObject(Object key) {
        // 清理被 GC 回收的缓存项
        removeGarbageCollectedItems();
        return delegate.removeObject(key);
    }

    /**
     * 首先清理 hardLinksToAvoidGarbageCollection 集合，然后清理被 GC 回
     * 收的缓存项， 最后清理底层 delegate 缓存中的缓存项
     */
    @Override
    public void clear() {
        synchronized (hardLinksToAvoidGarbageCollection) {
            hardLinksToAvoidGarbageCollection.clear();
        }
        removeGarbageCollectedItems();
        delegate.clear();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return null;
    }

    private void removeGarbageCollectedItems() {
        SoftEntry sv;
        // 造历 queueOfGarbageCollectedEntries 集合
        while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
            // 将已经被 GC 回收的 value 对象对应的缓存项清除
            delegate.removeObject(sv.key);
        }
    }

    /**
     * So位Cache 中缓存项的 value 是 SoftEn町对象， SoftEntry 继承 了 Soft:Reference ， 其中指向
     * key 的引用是强引用， 而指向 value 的引用是软引用 。
     */
    private static class SoftEntry extends SoftReference<Object> {
        // 强引用
        private final Object key;

        SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
            // 指向 value 的引用是软引用，且关联了引用队列
            super(value, garbageCollectionQueue);
            this.key = key;
        }

    }
}