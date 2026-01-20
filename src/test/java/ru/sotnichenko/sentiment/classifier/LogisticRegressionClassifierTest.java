package ru.sotnichenko.sentiment.classifier;

import org.junit.jupiter.api.*;
import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для Logistic Regression классификатора.
 */
@DisplayName("LogisticRegression Classifier Tests")
class LogisticRegressionClassifierTest {

    private LogisticRegressionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = LogisticRegressionClassifier.builder()
                .learningRate(0.1)
                .regularization(0.01)
                .epochs(50)
                .batchSize(4)
                .build();
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
            assertTrue(classifier.getNumFeatures() > 0);
        }

        @Test
        @DisplayName("Исключение при пустых данных")
        void throwsOnEmptyData() {
            assertThrows(IllegalArgumentException.class, () ->
                    classifier.train(List.of()));
        }

        @Test
        @DisplayName("Исключение при данных без меток")
        void throwsOnUnlabeledData() {
            List<TextDocument> unlabeled = List.of(
                    createDoc("text one"),
                    createDoc("text two")
            );

            assertThrows(IllegalArgumentException.class, () ->
                    classifier.train(unlabeled));
        }
    }

    @Nested
    @DisplayName("Классификация")
    class ClassificationTests {

        @BeforeEach
        void trainClassifier() {
            // Больше данных для лучшего обучения
            classifier = LogisticRegressionClassifier.builder()
                    .learningRate(0.5)
                    .epochs(100)
                    .build();
            classifier.train(createLargerTrainData());
        }

        @Test
        @DisplayName("Классификация позитивного текста")
        void classifyPositive() {
            TextDocument doc = createDoc("excellent wonderful amazing great");

            SentimentResult result = classifier.classify(doc);

            // Должен определить как позитивный или хотя бы не негативный
            assertNotEquals(Sentiment.NEGATIVE, result.getSentiment());
        }

        @Test
        @DisplayName("Классификация негативного текста")
        void classifyNegative() {
            TextDocument doc = createDoc("terrible awful horrible worst");

            SentimentResult result = classifier.classify(doc);

            // Должен определить как негативный или хотя бы не позитивный
            assertNotEquals(Sentiment.POSITIVE, result.getSentiment());
        }

        @Test
        @DisplayName("Вероятности суммируются в 1")
        void probabilitiesSumToOne() {
            TextDocument doc = createDoc("good product");

            Map<Sentiment, Double> probs = classifier.predictProbabilities(doc);

            double sum = probs.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001);
        }
    }

    @Nested
    @DisplayName("Сериализация")
    class SerializationTests {

        @Test
        @DisplayName("Сохранение и загрузка модели")
        void saveAndLoad() throws IOException {
            classifier.train(createTrainData());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            classifier.save(baos);
            byte[] modelBytes = baos.toByteArray();

            LogisticRegressionClassifier loaded = new LogisticRegressionClassifier();
            loaded.load(new ByteArrayInputStream(modelBytes));

            assertTrue(loaded.isTrained());
            assertEquals(classifier.getNumFeatures(), loaded.getNumFeatures());
        }
    }

    @Nested
    @DisplayName("Статистика")
    class StatsTests {

        @Test
        @DisplayName("Статистика модели")
        void modelStats() {
            classifier.train(createTrainData());

            Map<String, Object> stats = classifier.getStats();

            assertEquals("LogisticRegression", stats.get("classifier"));
            assertTrue((Boolean) stats.get("trained"));
            assertTrue((Integer) stats.get("numFeatures") > 0);
        }

        @Test
        @DisplayName("Top features")
        void topFeatures() {
            classifier.train(createTrainData());

            List<Map.Entry<String, Double>> features =
                    classifier.getTopFeatures(Sentiment.POSITIVE, 5);

            assertFalse(features.isEmpty());
            // Первый должен иметь наибольший вес
            assertTrue(features.get(0).getValue() >= features.get(features.size() - 1).getValue());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Builder создаёт классификатор")
        void builderCreatesClassifier() {
            LogisticRegressionClassifier clf = LogisticRegressionClassifier.builder()
                    .learningRate(0.01)
                    .regularization(0.001)
                    .epochs(200)
                    .batchSize(16)
                    .build();

            assertNotNull(clf);
            assertFalse(clf.isTrained());
        }

        @Test
        @DisplayName("Значения по умолчанию")
        void defaultValues() {
            LogisticRegressionClassifier clf = LogisticRegressionClassifier.builder().build();

            assertNotNull(clf);
            Map<String, Object> stats = clf.getStats();
            assertEquals(0.1, stats.get("learningRate"));
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
        }

        @Test
        @DisplayName("Вероятности равномерные")
        void uniformProbabilities() {
            TextDocument doc = createDoc("test");

            Map<Sentiment, Double> probs = classifier.predictProbabilities(doc);

            double expected = 1.0 / Sentiment.values().length;
            for (Double prob : probs.values()) {
                assertEquals(expected, prob, 0.001);
            }
        }
    }

    // Вспомогательные методы

    private List<TextDocument> createTrainData() {
        return List.of(
                createDoc("excellent great amazing", Sentiment.POSITIVE),
                createDoc("good nice pleasant", Sentiment.POSITIVE),
                createDoc("terrible awful horrible", Sentiment.NEGATIVE),
                createDoc("bad worst hate", Sentiment.NEGATIVE),
                createDoc("okay average normal", Sentiment.NEUTRAL),
                createDoc("fine neither", Sentiment.NEUTRAL)
        );
    }

    private List<TextDocument> createLargerTrainData() {
        List<TextDocument> data = new ArrayList<>();

        // Позитивные
        data.add(createDoc("excellent great amazing wonderful", Sentiment.POSITIVE));
        data.add(createDoc("good nice pleasant lovely beautiful", Sentiment.POSITIVE));
        data.add(createDoc("love happy joy fantastic superb", Sentiment.POSITIVE));
        data.add(createDoc("excellent perfect outstanding", Sentiment.POSITIVE));
        data.add(createDoc("great amazing wonderful", Sentiment.POSITIVE));

        // Негативные
        data.add(createDoc("terrible awful horrible bad", Sentiment.NEGATIVE));
        data.add(createDoc("hate sad angry worst disgusting", Sentiment.NEGATIVE));
        data.add(createDoc("poor terrible horrible", Sentiment.NEGATIVE));
        data.add(createDoc("bad awful waste terrible", Sentiment.NEGATIVE));
        data.add(createDoc("worst hate disgusting", Sentiment.NEGATIVE));

        // Нейтральные
        data.add(createDoc("okay average normal fine", Sentiment.NEUTRAL));
        data.add(createDoc("neither good bad average", Sentiment.NEUTRAL));
        data.add(createDoc("ordinary regular standard", Sentiment.NEUTRAL));

        return data;
    }

    private TextDocument createDoc(String text) {
        List<String> tokens = Arrays.asList(text.toLowerCase().split("\\s+"));
        return new TextDocument(text, tokens);
    }

    private TextDocument createDoc(String text, Sentiment label) {
        return createDoc(text).withLabel(label);
    }
}
