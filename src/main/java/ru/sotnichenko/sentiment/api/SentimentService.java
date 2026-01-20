package ru.sotnichenko.sentiment.api;

import ru.sotnichenko.sentiment.cache.LRUCache;
import ru.sotnichenko.sentiment.classifier.Classifier;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;
import ru.sotnichenko.sentiment.preprocessing.TextPreprocessor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Сервис анализа тональности.
 * Предоставляет высокоуровневый API для классификации текстов.
 */
public class SentimentService {

    private final TextPreprocessor preprocessor;
    private final Classifier classifier;
    private final LRUCache<String, SentimentResult> cache;
    private final ExecutorService executor;

    // Метрики
    private final AtomicLong totalRequests;
    private final AtomicLong totalProcessingTimeNs;

    public SentimentService(Classifier classifier) {
        this(classifier, TextPreprocessor.defaultPreprocessor(), 10000, 60000);
    }

    public SentimentService(Classifier classifier, TextPreprocessor preprocessor,
                           int cacheSize, long cacheTtlMs) {
        this.classifier = classifier;
        this.preprocessor = preprocessor;
        this.cache = new LRUCache<>(cacheSize, cacheTtlMs);
        this.executor = Executors.newWorkStealingPool();
        this.totalRequests = new AtomicLong(0);
        this.totalProcessingTimeNs = new AtomicLong(0);
    }

    /**
     * Анализирует тональность текста (синхронно).
     */
    public SentimentResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return createEmptyResult(text);
        }

        totalRequests.incrementAndGet();
        long startTime = System.nanoTime();

        try {
            // Пробуем взять из кэша
            String cacheKey = generateCacheKey(text);
            SentimentResult cached = cache.get(cacheKey);

            if (cached != null) {
                return cached;
            }

            // Препроцессинг и классификация
            TextDocument document = preprocessor.process(text);
            SentimentResult result = classifier.classify(document);

            // Кэшируем результат
            cache.put(cacheKey, result);

            return result;
        } finally {
            totalProcessingTimeNs.addAndGet(System.nanoTime() - startTime);
        }
    }

    /**
     * Анализирует тональность текста (асинхронно).
     */
    public CompletableFuture<SentimentResult> analyzeAsync(String text) {
        return CompletableFuture.supplyAsync(() -> analyze(text), executor);
    }

    /**
     * Анализирует несколько текстов (батч).
     */
    public List<SentimentResult> analyzeBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        return texts.stream()
                .map(this::analyze)
                .toList();
    }

    /**
     * Анализирует несколько текстов асинхронно и параллельно.
     */
    public CompletableFuture<List<SentimentResult>> analyzeBatchAsync(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<SentimentResult>> futures = texts.stream()
                .map(this::analyzeAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Генерирует ключ для кэша.
     */
    private String generateCacheKey(String text) {
        // Нормализуем текст для лучшего кэширования
        String normalized = text.toLowerCase().trim();
        // Используем хэш для длинных текстов
        if (normalized.length() > 100) {
            return String.valueOf(normalized.hashCode());
        }
        return normalized;
    }

    /**
     * Создаёт пустой результат.
     */
    private SentimentResult createEmptyResult(String text) {
        return SentimentResult.builder()
                .text(text != null ? text : "")
                .sentiment(ru.sotnichenko.sentiment.model.Sentiment.NEUTRAL)
                .confidence(0.0)
                .classifier(classifier.getName())
                .processingTimeMs(0)
                .build();
    }

    /**
     * Возвращает классификатор.
     */
    public Classifier getClassifier() {
        return classifier;
    }

    /**
     * Возвращает препроцессор.
     */
    public TextPreprocessor getPreprocessor() {
        return preprocessor;
    }

    /**
     * Очищает кэш.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Возвращает статистику кэша.
     */
    public LRUCache.CacheStats getCacheStats() {
        return cache.getStats();
    }

    /**
     * Возвращает статистику сервиса.
     */
    public ServiceStats getStats() {
        long requests = totalRequests.get();
        long processingTime = totalProcessingTimeNs.get();
        double avgLatencyMs = requests > 0 ? (processingTime / 1_000_000.0) / requests : 0.0;

        return new ServiceStats(
                requests,
                avgLatencyMs,
                cache.getStats(),
                classifier.getStats()
        );
    }

    /**
     * Сбрасывает статистику.
     */
    public void resetStats() {
        totalRequests.set(0);
        totalProcessingTimeNs.set(0);
        cache.resetStats();
    }

    /**
     * Останавливает сервис.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Статистика сервиса.
     */
    public record ServiceStats(
            long totalRequests,
            double avgLatencyMs,
            LRUCache.CacheStats cacheStats,
            Map<String, Object> classifierStats
    ) {
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"totalRequests\":").append(totalRequests).append(",");
            sb.append("\"avgLatencyMs\":").append(String.format("%.3f", avgLatencyMs)).append(",");
            sb.append("\"cache\":").append(cacheStats.toJson()).append(",");
            sb.append("\"classifier\":{");

            boolean first = true;
            for (Map.Entry<String, Object> entry : classifierStats.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");

                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else if (value instanceof Map) {
                    sb.append(mapToJson((Map<?, ?>) value));
                } else {
                    sb.append(value);
                }
                first = false;
            }

            sb.append("}}");
            return sb.toString();
        }

        private String mapToJson(Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                Object v = entry.getValue();
                if (v instanceof String) {
                    sb.append("\"").append(v).append("\"");
                } else {
                    sb.append(v);
                }
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Билдер для сервиса.
     */
    public static Builder builder(Classifier classifier) {
        return new Builder(classifier);
    }

    public static class Builder {
        private final Classifier classifier;
        private TextPreprocessor preprocessor = TextPreprocessor.defaultPreprocessor();
        private int cacheSize = 10000;
        private long cacheTtlMs = 60000;

        public Builder(Classifier classifier) {
            this.classifier = classifier;
        }

        public Builder preprocessor(TextPreprocessor preprocessor) {
            this.preprocessor = preprocessor;
            return this;
        }

        public Builder cacheSize(int size) {
            this.cacheSize = size;
            return this;
        }

        public Builder cacheTtlMs(long ttlMs) {
            this.cacheTtlMs = ttlMs;
            return this;
        }

        public SentimentService build() {
            return new SentimentService(classifier, preprocessor, cacheSize, cacheTtlMs);
        }
    }
}
