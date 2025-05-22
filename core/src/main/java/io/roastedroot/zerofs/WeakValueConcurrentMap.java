package io.roastedroot.zerofs;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WeakValueConcurrentMap<K, V> implements ConcurrentMap<K, V> {

    private final ConcurrentMap<K, WeakValue<K, V>> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<V> queue = new ReferenceQueue<>();

    private static class WeakValue<K, V> extends WeakReference<V> {
        final K key;

        WeakValue(K key, V value, ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
        }
    }

    private void processQueue() {
        WeakValue<K, V> ref;
        while ((ref = (WeakValue<K, V>) queue.poll()) != null) {
            map.remove(ref.key, ref);
        }
    }

    @Override
    public V get(Object key) {
        processQueue();
        WeakValue<K, V> ref = map.get(key);
        return ref == null ? null : ref.get();
    }

    @Override
    public V put(K key, V value) {
        processQueue();
        WeakValue<K, V> ref = new WeakValue<>(key, value, queue);
        WeakValue<K, V> previous = map.put(key, ref);
        return previous == null ? null : previous.get();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        processQueue();
        WeakValue<K, V> newRef = new WeakValue<>(key, value, queue);
        for (; ; ) {
            WeakValue<K, V> existing = map.putIfAbsent(key, newRef);
            if (existing == null) return null;
            V existingVal = existing.get();
            if (existingVal != null) return existingVal;
            map.remove(key, existing); // remove stale reference and retry
        }
    }

    @Override
    public boolean remove(Object key, Object value) {
        processQueue();
        WeakValue<K, V> ref = map.get(key);
        if (ref != null && value.equals(ref.get())) {
            return map.remove(key, ref);
        }
        return false;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        processQueue();
        WeakValue<K, V> existing = map.get(key);
        if (existing != null && oldValue.equals(existing.get())) {
            return map.replace(key, existing, new WeakValue<>(key, newValue, queue));
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        processQueue();
        WeakValue<K, V> existing = map.get(key);
        if (existing != null && existing.get() != null) {
            WeakValue<K, V> replaced = map.replace(key, new WeakValue<>(key, value, queue));
            return replaced == null ? null : replaced.get();
        }
        return null;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        processQueue();
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        processQueue();
        WeakValue<K, V> ref = map.remove(key);
        return ref == null ? null : ref.get();
    }

    @Override
    public void clear() {
        map.clear();
        while (queue.poll() != null) {} // drain reference queue
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        processQueue();
        for (WeakValue<K, V> ref : map.values()) {
            if (value.equals(ref.get())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<K> keySet() {
        processQueue();
        return map.keySet();
    }

    @Override
    public int size() {
        processQueue();
        int count = 0;
        for (WeakValue<K, V> ref : map.values()) {
            if (ref.get() != null) count++;
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException("entrySet not supported due to weak values");
    }

    @Override
    public java.util.Collection<V> values() {
        throw new UnsupportedOperationException("values() not supported due to weak references");
    }
}
