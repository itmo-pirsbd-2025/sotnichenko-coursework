package ru.sotnichenko.sentiment.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Потокобезопасный LRU кэш для результатов классификации.
 *
 * @param <K> тип ключа
 * @param <V> тип значения
 */
public class LRUCache<K, V> {

    private final int maxSize;
    private final LinkedHashMap<K, CacheEntry<V>> cache;
    private final ReentrantReadWriteLock lock;
    private final long ttlMs; // Time-to-live в миллисекундах

    // Статистика
    private long hits;
    private long misses;
    private long evictions;

    public LRUCache(int maxSize) {
        this(maxSize, 0); // 0 = без TTL
    }

    public LRUCache(int maxSize, long ttlMs) {
        this.maxSize = maxSize;
        this.ttlMs = ttlMs;
        this.lock = new ReentrantReadWriteLock();

        // LinkedHashMap с accessOrder=true для LRU поведения
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                boolean shouldRemove = size() > maxSize;
                if (shouldRemove) {
                    evictions++;
                }
                return shouldRemove;
            }
        };

        this.hits = 0;
        this.misses = 0;
        this.evictions = 0;
    }

    /**
     * Получает значение из кэша.
     */
    public V get(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);

            if (entry == null) {
                misses++;
                return null;
            }

            // Проверяем TTL
            if (isExpired(entry)) {
                // Нужна запись для удаления, освобождаем read lock
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(key);
                    misses++;
                    return null;
                } finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }

            hits++;
            return entry.value;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Добавляет значение в кэш.
     */
    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            cache.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Получает значение или вычисляет и кэширует его.
     */
    public V computeIfAbsent(K key, Function<K, V> computeFunction) {
        // Сначала пробуем прочитать
        V value = get(key);
        if (value != null) {
            return value;
        }

        // Вычисляем и кэшируем
        lock.writeLock().lock();
        try {
            // Повторная проверка под write lock
            CacheEntry<V> entry = cache.get(key);
            if (entry != null && !isExpired(entry)) {
                hits++;
                return entry.value;
            }

            // Вычисляем новое значение
            V computed = computeFunction.apply(key);
            cache.put(key, new CacheEntry<>(computed, System.currentTimeMillis()));
            return computed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Удаляет значение из кэша.
     */
    public V remove(K key) {
        lock.writeLock().lock();
        try {
            CacheEntry<V> entry = cache.remove(key);
            return entry != null ? entry.value : null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Проверяет наличие ключа в кэше.
     */
    public boolean containsKey(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);
            return entry != null && !isExpired(entry);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Очищает кэш.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Удаляет просроченные записи.
     */
    public int evictExpired() {
        if (ttlMs <= 0) {
            return 0;
        }

        lock.writeLock().lock();
        try {
            int count = 0;
            var iterator = cache.entrySet().iterator();

            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (isExpired(entry.getValue())) {
                    iterator.remove();
                    count++;
                    evictions++;
                }
            }

            return count;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Проверяет, истёк ли срок записи.
     */
    private boolean isExpired(CacheEntry<V> entry) {
        if (ttlMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() - entry.timestamp > ttlMs;
    }

    /**
     * Возвращает текущий размер кэша.
     */
    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Возвращает максимальный размер кэша.
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Возвращает количество попаданий в кэш.
     */
    public long getHits() {
        return hits;
    }

    /**
     * Возвращает количество промахов кэша.
     */
    public long getMisses() {
        return misses;
    }

    /**
     * Возвращает количество вытеснений.
     */
    public long getEvictions() {
        return evictions;
    }

    /**
     * Возвращает коэффициент попаданий.
     */
    public double getHitRate() {
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    /**
     * Сбрасывает статистику.
     */
    public void resetStats() {
        lock.writeLock().lock();
        try {
            hits = 0;
            misses = 0;
            evictions = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Возвращает статистику кэша.
     */
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            return new CacheStats(
                    cache.size(),
                    maxSize,
                    hits,
                    misses,
                    evictions,
                    getHitRate()
            );
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Запись в кэше с временной меткой.
     */
    private static class CacheEntry<V> {
        final V value;
        final long timestamp;

        CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    /**
     * Статистика кэша.
     */
    public record CacheStats(
            int currentSize,
            int maxSize,
            long hits,
            long misses,
            long evictions,
            double hitRate
    ) {
        public String toJson() {
            return String.format(
                    "{\"currentSize\":%d,\"maxSize\":%d,\"hits\":%d,\"misses\":%d,\"evictions\":%d,\"hitRate\":%.4f}",
                    currentSize, maxSize, hits, misses, evictions, hitRate
            );
        }
    }
}
