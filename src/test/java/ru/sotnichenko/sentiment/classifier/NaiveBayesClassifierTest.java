package ru.sotnichenko.sentiment.classifier;

import org.junit.jupiter.api.*;
import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для Naive Bayes классификатора.
 */
@DisplayName("NaiveBayes Classifier Tests")
class NaiveBayesClassifierTest {

    private NaiveBayesClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new NaiveBayesClassifier();
    }

    @Nested
    @DisplayName("Обучение")
    class TrainingTests {

        @Test
        @DisplayName("Обучение на данных")
        void trainOnData() {
            List<TextDocument> trainData = createTrainData();

            classifier.train(trainData);

            assertTrue(classifier.isTrained());
            assertTrue(classifier.getVocabularySize() > 0);
        }

        @Test
        @DisplayName("Исключение при пустых данных")
        void throwsOnEmptyData() {
            assertThrows(IllegalArgumentException.class, () ->
                    classifier.train(List.of()));
        }

        @Test
        @DisplayName("Инкрементальное обучение")
        void incrementalTraining() {
            List<TextDocument> batch1 = List.of(
                    createDoc("great product", Sentiment.POSITIVE),
                    createDoc("terrible quality", Sentiment.NEGATIVE)
            );

            List<TextDocument> batch2 = List.of(
                    createDoc("amazing service", Sentiment.POSITIVE),
                    createDoc("awful experience", Sentiment.NEGATIVE)
            );

            classifier.train(batch1);
            int vocabSize1 = classifier.getVocabularySize();

            classifier.trainIncremental(batch2);
            int vocabSize2 = classifier.getVocabularySize();

            assertTrue(vocabSize2 >= vocabSize1);
        }
    }

    @Nested
    @DisplayName("Классификация")
    class ClassificationTests {

        @BeforeEach
        void trainClassifier() {
            classifier.train(createTrainData());
        }

        @Test
        @DisplayName("Классификация позитивного текста")
        void classifyPositive() {
            TextDocument doc = createDoc("excellent wonderful amazing");

            SentimentResult result = classifier.classify(doc);

            assertEquals(Sentiment.POSITIVE, result.getSentiment());
            assertTrue(result.getConfidence() > 0);
        }

        @Test
        @DisplayName("Классификация негативного текста")
        void classifyNegative() {
            TextDocument doc = createDoc("terrible awful horrible");

            SentimentResult result = classifier.classify(doc);

            assertEquals(Sentiment.NEGATIVE, result.getSentiment());
        }

        @Test
        @DisplayName("Вероятности нормализованы")
        void probabilitiesNormalized() {
            TextDocument doc = createDoc("good product");

            Map<Sentiment, Double> probs = classifier.predictProbabilities(doc);

            double sum = probs.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001);
        }

        @Test
        @DisplayName("Классификация пустого документа")
        void classifyEmptyDocument() {
            TextDocument doc = new TextDocument("", List.of());

            SentimentResult result = classifier.classify(doc);

            assertNotNull(result);
            assertNotNull(result.getSentiment());
        }
    }

    @Nested
    @DisplayName("Сериализация")
    class SerializationTests {

        @Test
        @DisplayName("Сохранение и загрузка модели")
        void saveAndLoad() throws IOException {
            classifier.train(createTrainData());

            // Сохраняем
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            classifier.save(baos);
            byte[] modelBytes = baos.toByteArray();

            // Создаём новый классификатор и загружаем
            NaiveBayesClassifier loaded = new NaiveBayesClassifier();
            loaded.load(new ByteArrayInputStream(modelBytes));

            // Проверяем
            assertTrue(loaded.isTrained());
            assertEquals(classifier.getVocabularySize(), loaded.getVocabularySize());
            assertEquals(classifier.getTotalDocuments(), loaded.getTotalDocuments());
        }

        @Test
        @DisplayName("Загруженная модель даёт те же результаты")
        void loadedModelSameResults() throws IOException {
            classifier.train(createTrainData());
            TextDocument testDoc = createDoc("excellent product");

            SentimentResult original = classifier.classify(testDoc);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            classifier.save(baos);

            NaiveBayesClassifier loaded = new NaiveBayesClassifier();
            loaded.load(new ByteArrayInputStream(baos.toByteArray()));

            SentimentResult loaded_result = loaded.classify(testDoc);

            assertEquals(original.getSentiment(), loaded_result.getSentiment());
        }
    }

    @Nested
    @DisplayName("Метрики и статистика")
    class StatsTests {

        @Test
        @DisplayName("Статистика модели")
        void modelStats() {
            classifier.train(createTrainData());

            Map<String, Object> stats = classifier.getStats();

            assertEquals("NaiveBayes", stats.get("classifier"));
            assertTrue((Boolean) stats.get("trained"));
            assertTrue((Integer) stats.get("vocabularySize") > 0);
        }

        @Test
        @DisplayName("Наиболее информативные признаки")
        void mostInformativeFeatures() {
            classifier.train(createTrainData());

            List<String> positiveFeatures = classifier.getMostInformativeFeatures(Sentiment.POSITIVE, 5);
            List<String> negativeFeatures = classifier.getMostInformativeFeatures(Sentiment.NEGATIVE, 5);

            assertFalse(positiveFeatures.isEmpty());
            assertFalse(negativeFeatures.isEmpty());
        }
    }

    @Nested
    @DisplayName("Сглаживание")
    class SmoothingTests {

        @Test
        @DisplayName("Разные значения alpha")
        void differentAlphaValues() {
            NaiveBayesClassifier clf1 = new NaiveBayesClassifier(0.1);
            NaiveBayesClassifier clf2 = new NaiveBayesClassifier(1.0);
            NaiveBayesClassifier clf3 = new NaiveBayesClassifier(10.0);

            List<TextDocument> trainData = createTrainData();
            clf1.train(trainData);
            clf2.train(trainData);
            clf3.train(trainData);

            TextDocument testDoc = createDoc("unknown word test");

            // Все должны работать без ошибок
            assertNotNull(clf1.classify(testDoc));
            assertNotNull(clf2.classify(testDoc));
            assertNotNull(clf3.classify(testDoc));
        }

        @Test
        @DisplayName("Обработка неизвестных слов")
        void handleUnknownWords() {
            classifier.train(createTrainData());

            TextDocument doc = createDoc("xyz123 qwerty asdf");

            SentimentResult result = classifier.classify(doc);

            assertNotNull(result);
            // Должен вернуть что-то, даже если все слова неизвестны
        }
    }

    @Nested
    @DisplayName("Необученный классификатор")
    class UntrainedTests {

        @Test
        @DisplayName("isTrained возвращает false")
        void isTrainedReturnsFalse() {
            assertFalse(classifier.isTrained());
        }

        @Test
        @DisplayName("Классификация возвращает нейтральный результат")
        void classifyReturnsNeutral() {
            TextDocument doc = createDoc("some text");

            SentimentResult result = classifier.classify(doc);

            assertEquals(Sentiment.NEUTRAL, result.getSentiment());
            assertEquals(0.0, result.getConfidence());
        }
    }

    // Вспомогательные методы

    private List<TextDocument> createTrainData() {
        return List.of(
                createDoc("excellent great amazing wonderful", Sentiment.POSITIVE),
                createDoc("good nice pleasant lovely", Sentiment.POSITIVE),
                createDoc("love happy joy fantastic", Sentiment.POSITIVE),
                createDoc("terrible awful horrible bad", Sentiment.NEGATIVE),
                createDoc("hate sad angry worst", Sentiment.NEGATIVE),
                createDoc("disgusting poor terrible", Sentiment.NEGATIVE),
                createDoc("okay average normal", Sentiment.NEUTRAL),
                createDoc("fine neither good bad", Sentiment.NEUTRAL)
        );
    }

    private TextDocument createDoc(String text) {
        List<String> tokens = Arrays.asList(text.toLowerCase().split("\\s+"));
        return new TextDocument(text, tokens);
    }

    private TextDocument createDoc(String text, Sentiment label) {
        return createDoc(text).withLabel(label);
    }
}
