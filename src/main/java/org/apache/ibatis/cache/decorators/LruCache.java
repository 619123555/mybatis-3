/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * Lru (least recently used) cache decorator.
 * 最近最少使用.
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  private final Cache delegate;
  // 这个缓存对象只是维护一个LinkedHashMap链表.
  // 用户每次put时,满足最大长度后,则将链表尾部的key清除.
  private Map<Object, Object> keyMap;
  private Object eldestKey;

  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // size默认1024,可通过配置定制.
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    // 重写LinkedHashMap中的removeEldestEntry()方法,该方法会在hashMap.put完成时进行调用.
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        boolean tooBig = size() > size;
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    // 添加到基础缓存对象中.
    delegate.putObject(key, value);
    // 将key添加到链表第一位,并且清空缓存链表第1024个元素,同时清空基础缓存中对应的key.
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    // 由于参数中的key,刚刚获取一次,则调整到链表的第一位.
    keyMap.get(key); // touch
    // 从PerpetualCache基础缓存中获取value.
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    // 直接删除PerpetualCache基础缓存中的key,不需要删除Lru中的key,只有添加和删除操作,才需要调整Lru(最近最少使用)链表.
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  private void cycleKeyList(Object key) {
    // 将参数对象添加到链表第一位,同时删除链表第1024个元素.
    //    HashMap在添加完元素后,会调用子类实现的removeEldestEntry()方法,
    //    并且LruCache在setSize(1024)方法中,也重写了LinkedHashMap的removeEldestEntry()方法,所以会执行该方法,对尾部元素进行删除.
    keyMap.put(key, key);
    // 将在链表中刚删除的尾部的元素,在PerpetualCache基础缓存对象中也删除.
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
