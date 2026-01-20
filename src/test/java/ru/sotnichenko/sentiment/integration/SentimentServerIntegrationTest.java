package ru.sotnichenko.sentiment.integration;

import org.junit.jupiter.api.*;
import ru.sotnichenko.sentiment.api.SentimentService;
import ru.sotnichenko.sentiment.classifier.NaiveBayesClassifier;
import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;
import ru.sotnichenko.sentiment.preprocessing.TextPreprocessor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты для сервиса анализа тональности.
 */
@DisplayName("Sentiment Service Integration Tests")
class SentimentServerIntegrationTest {

    private SentimentService service;
    private NaiveBayesClassifier classifier;
    private TextPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = TextPreprocessor.builder()
                .removeStopWords(true)
                .applyStemming(true)
                .build();

        classifier = new NaiveBayesClassifier();
        classifier.train(createTrainData());

        service = SentimentService.builder(classifier)
                .preprocessor(preprocessor)
                .cacheSize(100)
                .cacheTtlMs(60000)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Nested
    @DisplayName("Синхронный анализ")
    class SyncAnalysisTests {

        @Test
        @DisplayName("Анализ позитивного текста")
        void analyzePositiveText() {
            SentimentResult result = service.analyze("This is an excellent and wonderful product!");

            assertNotNull(result);
            assertEquals(Sentiment.POSITIVE, result.getSentiment());
            assertTrue(result.getConfidence() > 0);
        }

        @Test
        @DisplayName("Анализ негативного текста")
        void analyzeNegativeText() {
            SentimentResult result = service.analyze("This is terrible and awful!");

            assertNotNull(result);
            assertEquals(Sentiment.NEGATIVE, result.getSentiment());
        }

        @Test
        @DisplayName("Анализ русского текста")
        void analyzeRussianText() {
            SentimentResult result = service.analyze("Отличный товар, очень доволен покупкой!");

            assertNotNull(result);
            // Может быть любой результат, главное что обработка работает
        }

        @Test
        @DisplayName("Анализ пустого текста")
        void analyzeEmptyText() {
            SentimentResult result = service.analyze("");

            assertNotNull(result);
            assertEquals(Sentiment.NEUTRAL, result.getSentiment());
        }

        @Test
        @DisplayName("Анализ null")
        void analyzeNull() {
            SentimentResult result = service.analyze(null);

            assertNotNull(result);
            assertEquals(Sentiment.NEUTRAL, result.getSentiment());
        }
    }

    @Nested
    @DisplayName("Асинхронный анализ")
    class AsyncAnalysisTests {

        @Test
        @DisplayName("Асинхронный анализ одного текста")
        void analyzeAsync() throws Exception {
            CompletableFuture<SentimentResult> future = service.analyzeAsync("Great product!");

            SentimentResult result = future.get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertNotNull(result.getSentiment());
        }

        @Test
        @DisplayName("Асинхронный batch анализ")
        void analyzeBatchAsync() throws Exception {
            List<String> texts = List.of(
                    "Excellent quality!",
                    "Terrible experience",
                    "Average product"
            );

            CompletableFuture<List<SentimentResult>> future = service.analyzeBatchAsync(texts);

            List<SentimentResult> results = future.get(10, TimeUnit.SECONDS);

            assertEquals(3, results.size());
            for (SentimentResult result : results) {
                assertNotNull(result);
                assertNotNull(result.getSentiment());
            }
        }
    }

    @Nested
    @DisplayName("Batch анализ")
    class BatchAnalysisTests {

        @Test
        @DisplayName("Анализ нескольких текстов")
        void analyzeBatch() {
            List<String> texts = List.of(
                    "This is great!",
                    "This is terrible!",
                    "This is okay."
            );

            List<SentimentResult> results = service.analyzeBatch(texts);

            assertEquals(3, results.size());
        }

        @Test
        @DisplayName("Пустой batch")
        void analyzeEmptyBatch() {
            List<SentimentResult> results = service.analyzeBatch(List.of());

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Null batch")
        void analyzeNullBatch() {
            List<SentimentResult> results = service.analyzeBatch(null);

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Кэширование")
    class CachingTests {

        @Test
        @DisplayName("Результат кэшируется")
        void resultIsCached() {
            String text = "This is a unique test text for caching";

            // Первый вызов
            service.analyze(text);

            // Проверяем кэш
            assertTrue(service.getCacheStats().currentSize() > 0);
        }

        @Test
        @DisplayName("Повторный запрос из кэша")
        void repeatedRequestFromCache() {
            String text = "Cached text example";

            // Первый вызов
            SentimentResult result1 = service.analyze(text);

            // Второй вызов (должен быть из кэша)
            SentimentResult result2 = service.analyze(text);

            assertEquals(result1.getSentiment(), result2.getSentiment());
            assertTrue(service.getCacheStats().hits() > 0);
        }

        @Test
        @DisplayName("Очистка кэша")
        void clearCache() {
            service.analyze("Some text");
            assertTrue(service.getCacheStats().currentSize() > 0);

            service.clearCache();

            assertEquals(0, service.getCacheStats().currentSize());
        }
    }

    @Nested
    @DisplayName("Статистика")
    class StatsTests {

        @Test
        @DisplayName("Статистика сервиса")
        void serviceStats() {
            service.analyze("Test text 1");
            service.analyze("Test text 2");

            SentimentService.ServiceStats stats = service.getStats();

            assertEquals(2, stats.totalRequests());
            assertTrue(stats.avgLatencyMs() >= 0);
        }

        @Test
        @DisplayName("JSON статистики")
        void statsToJson() {
            service.analyze("Test");

            SentimentService.ServiceStats stats = service.getStats();
            String json = stats.toJson();

            assertNotNull(json);
            assertTrue(json.contains("totalRequests"));
            assertTrue(json.contains("cache"));
        }

        @Test
        @DisplayName("Сброс статистики")
        void resetStats() {
            service.analyze("Test");
            assertTrue(service.getStats().totalRequests() > 0);

            service.resetStats();

            assertEquals(0, service.getStats().totalRequests());
        }
    }

    @Nested
    @DisplayName("Пайплайн предобработки")
    class PreprocessingTests {

        @Test
        @DisplayName("Текст проходит через препроцессор")
        void textIsPreprocessed() {
            // Текст с повторяющимися буквами и стоп-словами
            String text = "This is aaamazing!!!";

            SentimentResult result = service.analyze(text);

            assertNotNull(result);
            // Препроцессор должен нормализовать текст
        }

        @Test
        @DisplayName("Препроцессор доступен")
        void preprocessorAccessible() {
            TextPreprocessor pp = service.getPreprocessor();

            assertNotNull(pp);
        }
    }

    @Nested
    @DisplayName("Производительность")
    class PerformanceTests {

        @Test
        @DisplayName("Множественные запросы")
        void multipleRequests() {
            int count = 100;

            long start = System.currentTimeMillis();
            for (int i = 0; i < count; i++) {
                service.analyze("Test text number " + i);
            }
            long elapsed = System.currentTimeMillis() - start;

            // Должно обработать 100 запросов достаточно быстро
            assertTrue(elapsed < 5000, "Should process 100 requests in less than 5 seconds");
        }

        @Test
        @DisplayName("Параллельные запросы")
        void parallelRequests() throws Exception {
            int count = 50;

            List<CompletableFuture<SentimentResult>> futures =
                    java.util.stream.IntStream.range(0, count)
                            .mapToObj(i -> service.analyzeAsync("Parallel text " + i))
                            .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);

            // Все запросы должны завершиться
            for (CompletableFuture<SentimentResult> future : futures) {
                assertTrue(future.isDone());
                assertNotNull(future.get());
            }
        }
    }

    // Вспомогательные методы

    private List<TextDocument> createTrainData() {
        return List.of(
                createDoc("excellent great amazing wonderful fantastic", Sentiment.POSITIVE),
                createDoc("good nice pleasant lovely beautiful", Sentiment.POSITIVE),
                createDoc("love happy joy superb outstanding", Sentiment.POSITIVE),
                createDoc("perfect brilliant exceptional marvelous", Sentiment.POSITIVE),
                createDoc("excellent wonderful product recommend", Sentiment.POSITIVE),
                createDoc("terrible awful horrible bad worst", Sentiment.NEGATIVE),
                createDoc("hate sad angry disgusting poor", Sentiment.NEGATIVE),
                createDoc("waste garbage trash useless broken", Sentiment.NEGATIVE),
                createDoc("disappointing frustrating annoying", Sentiment.NEGATIVE),
                createDoc("terrible awful experience never again", Sentiment.NEGATIVE),
                createDoc("okay average normal fine decent", Sentiment.NEUTRAL),
                createDoc("neither good bad ordinary standard", Sentiment.NEUTRAL)
        );
    }

    private TextDocument createDoc(String text, Sentiment label) {
        List<String> tokens = Arrays.asList(text.toLowerCase().split("\\s+"));
        return new TextDocument(text, tokens, label);
    }
}
