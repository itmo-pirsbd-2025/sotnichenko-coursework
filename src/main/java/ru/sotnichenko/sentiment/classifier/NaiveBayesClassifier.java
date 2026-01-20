package ru.sotnichenko.sentiment.classifier;

import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Наивный Байесовский классификатор для анализа тональности.
 *
 * Использует модель мультиномиального Naive Bayes с аддитивным сглаживанием (Лапласа).
 *
 * P(class|document) ∝ P(class) * ∏ P(word|class)
 *
 * где P(word|class) = (count(word, class) + alpha) / (totalWords(class) + alpha * vocabularySize)
 */
public class NaiveBayesClassifier implements Classifier {

    private static final String NAME = "NaiveBayes";
    private static final double DEFAULT_ALPHA = 1.0; // Laplace smoothing

    // Параметры модели
    private final double alpha;
    private final Map<Sentiment, Map<String, Integer>> wordCounts; // class -> word -> count
    private final Map<Sentiment, Integer> classCounts; // class -> document count
    private final Map<Sentiment, Integer> totalWordCounts; // class -> total words in class
    private final Set<String> vocabulary;

    private int totalDocuments;
    private boolean trained;

    public NaiveBayesClassifier() {
        this(DEFAULT_ALPHA);
    }

    public NaiveBayesClassifier(double alpha) {
        this.alpha = alpha;
        this.wordCounts = new ConcurrentHashMap<>();
        this.classCounts = new ConcurrentHashMap<>();
        this.totalWordCounts = new ConcurrentHashMap<>();
        this.vocabulary = ConcurrentHashMap.newKeySet();
        this.totalDocuments = 0;
        this.trained = false;

        // Инициализируем для всех классов
        for (Sentiment sentiment : Sentiment.values()) {
            wordCounts.put(sentiment, new ConcurrentHashMap<>());
            classCounts.put(sentiment, 0);
            totalWordCounts.put(sentiment, 0);
        }
    }

    @Override
    public void train(List<TextDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Training documents cannot be empty");
        }

        // Очищаем предыдущие данные
        reset();

        // Подсчитываем статистику
        for (TextDocument doc : documents) {
            if (!doc.hasLabel()) {
                continue; // Пропускаем документы без метки
            }

            Sentiment label = doc.getLabel();
            List<String> tokens = doc.getTokens();

            // Увеличиваем счётчик документов для класса
            classCounts.merge(label, 1, Integer::sum);
            totalDocuments++;

            // Добавляем слова в словарь и подсчитываем
            Map<String, Integer> classWordCounts = wordCounts.get(label);
            for (String token : tokens) {
                vocabulary.add(token);
                classWordCounts.merge(token, 1, Integer::sum);
                totalWordCounts.merge(label, 1, Integer::sum);
            }
        }

        trained = totalDocuments > 0;
    }

    /**
     * Дообучает модель на новых данных (инкрементальное обучение).
     */
    public void trainIncremental(List<TextDocument> documents) {
        for (TextDocument doc : documents) {
            if (!doc.hasLabel()) {
                continue;
            }

            Sentiment label = doc.getLabel();
            List<String> tokens = doc.getTokens();

            classCounts.merge(label, 1, Integer::sum);
            totalDocuments++;

            Map<String, Integer> classWordCounts = wordCounts.get(label);
            for (String token : tokens) {
                vocabulary.add(token);
                classWordCounts.merge(token, 1, Integer::sum);
                totalWordCounts.merge(label, 1, Integer::sum);
            }
        }

        trained = totalDocuments > 0;
    }

    @Override
    public SentimentResult classify(TextDocument document) {
        long startTime = System.currentTimeMillis();

        if (!trained) {
            // Если модель не обучена, возвращаем нейтральный результат
            return SentimentResult.builder()
                    .text(document.getOriginalText())
                    .sentiment(Sentiment.NEUTRAL)
                    .confidence(0.0)
                    .classifier(NAME)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        Map<Sentiment, Double> probabilities = predictProbabilities(document);

        // Находим класс с максимальной вероятностью
        Sentiment bestClass = Sentiment.NEUTRAL;
        double maxProb = Double.NEGATIVE_INFINITY;

        for (Map.Entry<Sentiment, Double> entry : probabilities.entrySet()) {
            if (entry.getValue() > maxProb) {
                maxProb = entry.getValue();
                bestClass = entry.getKey();
            }
        }

        // Вычисляем confidence как разницу между лучшим и вторым результатом
        double confidence = calculateConfidence(probabilities, bestClass);

        return SentimentResult.builder()
                .text(document.getOriginalText())
                .sentiment(bestClass)
                .confidence(confidence)
                .probabilities(probabilities)
                .classifier(NAME)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    @Override
    public Map<Sentiment, Double> predictProbabilities(TextDocument document) {
        Map<Sentiment, Double> logProbabilities = new HashMap<>();
        List<String> tokens = document.getTokens();
        int vocabSize = vocabulary.size();

        for (Sentiment sentiment : Sentiment.values()) {
            // P(class) - априорная вероятность класса (в логарифмической шкале)
            double logPrior = Math.log((double) classCounts.get(sentiment) / totalDocuments);

            // ∑ log P(word|class) - сумма логарифмов вероятностей слов
            double logLikelihood = 0.0;
            int totalWordsInClass = totalWordCounts.get(sentiment);
            Map<String, Integer> classWordCounts = wordCounts.get(sentiment);

            for (String token : tokens) {
                int wordCount = classWordCounts.getOrDefault(token, 0);
                // Аддитивное сглаживание (Лапласа)
                double wordProb = (wordCount + alpha) / (totalWordsInClass + alpha * vocabSize);
                logLikelihood += Math.log(wordProb);
            }

            logProbabilities.put(sentiment, logPrior + logLikelihood);
        }

        // Нормализуем через log-sum-exp для получения вероятностей
        return normalizeLogProbabilities(logProbabilities);
    }

    /**
     * Нормализует логарифмические вероятности.
     */
    private Map<Sentiment, Double> normalizeLogProbabilities(Map<Sentiment, Double> logProbs) {
        // Находим максимум для численной стабильности
        double maxLogProb = logProbs.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        // Вычисляем exp(logProb - max) для каждого класса
        Map<Sentiment, Double> expProbs = new HashMap<>();
        double sum = 0.0;

        for (Map.Entry<Sentiment, Double> entry : logProbs.entrySet()) {
            double expProb = Math.exp(entry.getValue() - maxLogProb);
            expProbs.put(entry.getKey(), expProb);
            sum += expProb;
        }

        // Нормализуем
        Map<Sentiment, Double> result = new HashMap<>();
        for (Map.Entry<Sentiment, Double> entry : expProbs.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / sum);
        }

        return result;
    }

    /**
     * Вычисляет уверенность классификатора.
     */
    private double calculateConfidence(Map<Sentiment, Double> probabilities, Sentiment bestClass) {
        double bestProb = probabilities.get(bestClass);

        // Находим вторую по величине вероятность
        double secondBest = probabilities.entrySet().stream()
                .filter(e -> e.getKey() != bestClass)
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0.0);

        // Confidence как разница между лучшим и вторым результатом
        return Math.min(1.0, Math.max(0.0, bestProb - secondBest + bestProb));
    }

    /**
     * Сбрасывает модель.
     */
    public void reset() {
        for (Sentiment sentiment : Sentiment.values()) {
            wordCounts.get(sentiment).clear();
            classCounts.put(sentiment, 0);
            totalWordCounts.put(sentiment, 0);
        }
        vocabulary.clear();
        totalDocuments = 0;
        trained = false;
    }

    @Override
    public boolean isTrained() {
        return trained;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void save(OutputStream outputStream) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
            // Сохраняем параметры
            oos.writeDouble(alpha);
            oos.writeInt(totalDocuments);
            oos.writeBoolean(trained);

            // Сохраняем словарь
            oos.writeInt(vocabulary.size());
            for (String word : vocabulary) {
                oos.writeUTF(word);
            }

            // Сохраняем счётчики для каждого класса
            for (Sentiment sentiment : Sentiment.values()) {
                oos.writeInt(classCounts.get(sentiment));
                oos.writeInt(totalWordCounts.get(sentiment));

                Map<String, Integer> classWords = wordCounts.get(sentiment);
                oos.writeInt(classWords.size());
                for (Map.Entry<String, Integer> entry : classWords.entrySet()) {
                    oos.writeUTF(entry.getKey());
                    oos.writeInt(entry.getValue());
                }
            }
        }
    }

    @Override
    public void load(InputStream inputStream) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
            // Загружаем параметры
            double loadedAlpha = ois.readDouble();
            if (Math.abs(loadedAlpha - this.alpha) > 0.001) {
                // Предупреждение о несоответствии alpha
            }

            totalDocuments = ois.readInt();
            trained = ois.readBoolean();

            // Загружаем словарь
            vocabulary.clear();
            int vocabSize = ois.readInt();
            for (int i = 0; i < vocabSize; i++) {
                vocabulary.add(ois.readUTF());
            }

            // Загружаем счётчики для каждого класса
            for (Sentiment sentiment : Sentiment.values()) {
                classCounts.put(sentiment, ois.readInt());
                totalWordCounts.put(sentiment, ois.readInt());

                Map<String, Integer> classWords = wordCounts.get(sentiment);
                classWords.clear();

                int wordMapSize = ois.readInt();
                for (int i = 0; i < wordMapSize; i++) {
                    String word = ois.readUTF();
                    int count = ois.readInt();
                    classWords.put(word, count);
                }
            }
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("classifier", NAME);
        stats.put("trained", trained);
        stats.put("alpha", alpha);
        stats.put("totalDocuments", totalDocuments);
        stats.put("vocabularySize", vocabulary.size());

        Map<String, Integer> classDistribution = new LinkedHashMap<>();
        for (Sentiment sentiment : Sentiment.values()) {
            classDistribution.put(sentiment.getLabel(), classCounts.get(sentiment));
        }
        stats.put("classDistribution", classDistribution);

        return stats;
    }

    /**
     * Возвращает наиболее информативные признаки для класса.
     */
    public List<String> getMostInformativeFeatures(Sentiment sentiment, int topN) {
        if (!trained) {
            return List.of();
        }

        Map<String, Integer> classWords = wordCounts.get(sentiment);
        int totalInClass = totalWordCounts.get(sentiment);

        // Вычисляем информативность каждого слова
        List<Map.Entry<String, Double>> scored = new ArrayList<>();

        for (String word : vocabulary) {
            int countInClass = classWords.getOrDefault(word, 0);

            // Подсчитываем общее количество слова во всех классах
            int countTotal = 0;
            int totalAll = 0;
            for (Sentiment s : Sentiment.values()) {
                countTotal += wordCounts.get(s).getOrDefault(word, 0);
                totalAll += totalWordCounts.get(s);
            }

            if (countTotal == 0) continue;

            // Информативность: P(word|class) / P(word)
            double probInClass = (double) countInClass / totalInClass;
            double probOverall = (double) countTotal / totalAll;

            if (probOverall > 0) {
                double informativeness = probInClass / probOverall;
                scored.add(Map.entry(word, informativeness));
            }
        }

        // Сортируем по убыванию информативности
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        return scored.stream()
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Возвращает размер словаря.
     */
    public int getVocabularySize() {
        return vocabulary.size();
    }

    /**
     * Возвращает общее количество документов.
     */
    public int getTotalDocuments() {
        return totalDocuments;
    }
}
