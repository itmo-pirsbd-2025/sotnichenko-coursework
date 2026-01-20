package ru.sotnichenko.sentiment.classifier;

import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Логистическая регрессия для анализа тональности.
 *
 * Реализует мультиклассовую логистическую регрессию (softmax regression)
 * с оптимизацией методом стохастического градиентного спуска (SGD).
 *
 * P(class|document) = softmax(W * features + b)
 */
public class LogisticRegressionClassifier implements Classifier {

    private static final String NAME = "LogisticRegression";
    private static final double DEFAULT_LEARNING_RATE = 0.1;
    private static final double DEFAULT_REGULARIZATION = 0.01;
    private static final int DEFAULT_EPOCHS = 100;
    private static final int DEFAULT_BATCH_SIZE = 32;

    // Параметры обучения
    private final double learningRate;
    private final double regularization; // L2 регуляризация
    private final int epochs;
    private final int batchSize;

    // Параметры модели
    private final Map<String, Integer> featureIndex; // слово -> индекс
    private double[][] weights; // [numClasses][numFeatures + 1] (+1 для bias)
    private final Map<Sentiment, Integer> classIndex; // класс -> индекс
    private final Sentiment[] indexToClass; // индекс -> класс

    private boolean trained;
    private int numFeatures;

    public LogisticRegressionClassifier() {
        this(DEFAULT_LEARNING_RATE, DEFAULT_REGULARIZATION, DEFAULT_EPOCHS, DEFAULT_BATCH_SIZE);
    }

    public LogisticRegressionClassifier(double learningRate, double regularization,
                                        int epochs, int batchSize) {
        this.learningRate = learningRate;
        this.regularization = regularization;
        this.epochs = epochs;
        this.batchSize = batchSize;

        this.featureIndex = new ConcurrentHashMap<>();
        this.classIndex = new LinkedHashMap<>();
        this.indexToClass = Sentiment.values();

        // Инициализируем индексы классов
        int idx = 0;
        for (Sentiment sentiment : Sentiment.values()) {
            classIndex.put(sentiment, idx++);
        }

        this.trained = false;
        this.numFeatures = 0;
    }

    @Override
    public void train(List<TextDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Training documents cannot be empty");
        }

        // Фильтруем документы с метками
        List<TextDocument> labeledDocs = documents.stream()
                .filter(TextDocument::hasLabel)
                .toList();

        if (labeledDocs.isEmpty()) {
            throw new IllegalArgumentException("No labeled documents for training");
        }

        // Строим словарь признаков
        buildVocabulary(labeledDocs);

        // Инициализируем веса
        initializeWeights();

        // Обучаем методом SGD
        trainSGD(labeledDocs);

        trained = true;
    }

    /**
     * Строит словарь признаков из обучающих данных.
     */
    private void buildVocabulary(List<TextDocument> documents) {
        featureIndex.clear();
        Set<String> vocabulary = new LinkedHashSet<>();

        for (TextDocument doc : documents) {
            vocabulary.addAll(doc.getTokens());
        }

        int idx = 0;
        for (String word : vocabulary) {
            featureIndex.put(word, idx++);
        }

        numFeatures = featureIndex.size();
    }

    /**
     * Инициализирует веса случайными малыми значениями.
     */
    private void initializeWeights() {
        int numClasses = Sentiment.values().length;
        // +1 для bias терма
        weights = new double[numClasses][numFeatures + 1];

        Random random = new Random(42); // Фиксированный seed для воспроизводимости

        for (int c = 0; c < numClasses; c++) {
            for (int f = 0; f < numFeatures + 1; f++) {
                // Xavier initialization
                double scale = Math.sqrt(2.0 / (numFeatures + numClasses));
                weights[c][f] = (random.nextDouble() - 0.5) * 2 * scale;
            }
        }
    }

    /**
     * Обучает модель методом стохастического градиентного спуска.
     */
    private void trainSGD(List<TextDocument> documents) {
        List<TextDocument> shuffled = new ArrayList<>(documents);
        Random random = new Random(42);

        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(shuffled, random);

            // Мини-батчи
            for (int i = 0; i < shuffled.size(); i += batchSize) {
                int end = Math.min(i + batchSize, shuffled.size());
                List<TextDocument> batch = shuffled.subList(i, end);
                updateWeightsBatch(batch);
            }
        }
    }

    /**
     * Обновляет веса на основе мини-батча.
     */
    private void updateWeightsBatch(List<TextDocument> batch) {
        int numClasses = Sentiment.values().length;

        // Накапливаем градиенты
        double[][] gradients = new double[numClasses][numFeatures + 1];

        for (TextDocument doc : batch) {
            double[] features = extractFeatures(doc);
            double[] predictions = softmax(forward(features));

            int trueClass = classIndex.get(doc.getLabel());

            // Вычисляем градиент: dL/dW = (prediction - true) * features
            for (int c = 0; c < numClasses; c++) {
                double error = predictions[c] - (c == trueClass ? 1.0 : 0.0);

                for (int f = 0; f < numFeatures + 1; f++) {
                    double featureValue = (f < numFeatures) ? features[f] : 1.0; // bias
                    gradients[c][f] += error * featureValue;
                }
            }
        }

        // Обновляем веса
        double lr = learningRate / batch.size();

        for (int c = 0; c < numClasses; c++) {
            for (int f = 0; f < numFeatures + 1; f++) {
                // Градиент + L2 регуляризация
                double gradient = gradients[c][f] + regularization * weights[c][f];
                weights[c][f] -= lr * gradient;
            }
        }
    }

    /**
     * Извлекает признаки из документа (bag of words).
     */
    private double[] extractFeatures(TextDocument document) {
        double[] features = new double[numFeatures];

        for (String token : document.getTokens()) {
            Integer idx = featureIndex.get(token);
            if (idx != null) {
                features[idx] += 1.0;
            }
        }

        // Нормализация (TF)
        double norm = 0.0;
        for (double f : features) {
            norm += f * f;
        }
        if (norm > 0) {
            norm = Math.sqrt(norm);
            for (int i = 0; i < features.length; i++) {
                features[i] /= norm;
            }
        }

        return features;
    }

    /**
     * Прямой проход: W * x + b.
     */
    private double[] forward(double[] features) {
        int numClasses = Sentiment.values().length;
        double[] logits = new double[numClasses];

        for (int c = 0; c < numClasses; c++) {
            double sum = weights[c][numFeatures]; // bias
            for (int f = 0; f < numFeatures; f++) {
                sum += weights[c][f] * features[f];
            }
            logits[c] = sum;
        }

        return logits;
    }

    /**
     * Применяет softmax к логитам.
     */
    private double[] softmax(double[] logits) {
        double maxLogit = Arrays.stream(logits).max().orElse(0.0);

        double[] exps = new double[logits.length];
        double sum = 0.0;

        for (int i = 0; i < logits.length; i++) {
            exps[i] = Math.exp(logits[i] - maxLogit);
            sum += exps[i];
        }

        for (int i = 0; i < exps.length; i++) {
            exps[i] /= sum;
        }

        return exps;
    }

    @Override
    public SentimentResult classify(TextDocument document) {
        long startTime = System.currentTimeMillis();

        if (!trained) {
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
        double maxProb = 0.0;

        for (Map.Entry<Sentiment, Double> entry : probabilities.entrySet()) {
            if (entry.getValue() > maxProb) {
                maxProb = entry.getValue();
                bestClass = entry.getKey();
            }
        }

        // Confidence на основе разницы вероятностей
        final Sentiment finalBestClass = bestClass;
        double secondBest = probabilities.entrySet().stream()
                .filter(e -> e.getKey() != finalBestClass)
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0.0);

        double confidence = Math.min(1.0, maxProb - secondBest + maxProb * 0.5);

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
        if (!trained) {
            Map<Sentiment, Double> uniform = new HashMap<>();
            double prob = 1.0 / Sentiment.values().length;
            for (Sentiment s : Sentiment.values()) {
                uniform.put(s, prob);
            }
            return uniform;
        }

        double[] features = extractFeatures(document);
        double[] probabilities = softmax(forward(features));

        Map<Sentiment, Double> result = new HashMap<>();
        for (int i = 0; i < indexToClass.length; i++) {
            result.put(indexToClass[i], probabilities[i]);
        }

        return result;
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
            oos.writeDouble(learningRate);
            oos.writeDouble(regularization);
            oos.writeInt(epochs);
            oos.writeInt(batchSize);
            oos.writeBoolean(trained);
            oos.writeInt(numFeatures);

            // Сохраняем словарь признаков
            oos.writeInt(featureIndex.size());
            for (Map.Entry<String, Integer> entry : featureIndex.entrySet()) {
                oos.writeUTF(entry.getKey());
                oos.writeInt(entry.getValue());
            }

            // Сохраняем веса
            if (weights != null) {
                oos.writeInt(weights.length);
                oos.writeInt(weights[0].length);
                for (double[] row : weights) {
                    for (double w : row) {
                        oos.writeDouble(w);
                    }
                }
            } else {
                oos.writeInt(0);
            }
        }
    }

    @Override
    public void load(InputStream inputStream) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
            // Пропускаем параметры обучения (используем текущие)
            ois.readDouble(); // learningRate
            ois.readDouble(); // regularization
            ois.readInt(); // epochs
            ois.readInt(); // batchSize

            trained = ois.readBoolean();
            numFeatures = ois.readInt();

            // Загружаем словарь признаков
            featureIndex.clear();
            int vocabSize = ois.readInt();
            for (int i = 0; i < vocabSize; i++) {
                String word = ois.readUTF();
                int idx = ois.readInt();
                featureIndex.put(word, idx);
            }

            // Загружаем веса
            int rows = ois.readInt();
            if (rows > 0) {
                int cols = ois.readInt();
                weights = new double[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        weights[r][c] = ois.readDouble();
                    }
                }
            }
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("classifier", NAME);
        stats.put("trained", trained);
        stats.put("learningRate", learningRate);
        stats.put("regularization", regularization);
        stats.put("epochs", epochs);
        stats.put("batchSize", batchSize);
        stats.put("numFeatures", numFeatures);
        stats.put("numClasses", Sentiment.values().length);
        return stats;
    }

    /**
     * Возвращает наиболее важные признаки для класса.
     */
    public List<Map.Entry<String, Double>> getTopFeatures(Sentiment sentiment, int topN) {
        if (!trained || weights == null) {
            return List.of();
        }

        int classIdx = classIndex.get(sentiment);
        List<Map.Entry<String, Double>> features = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : featureIndex.entrySet()) {
            String word = entry.getKey();
            int featureIdx = entry.getValue();
            double weight = weights[classIdx][featureIdx];
            features.add(Map.entry(word, weight));
        }

        // Сортируем по весу (по убыванию)
        features.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        return features.stream().limit(topN).toList();
    }

    /**
     * Возвращает количество признаков.
     */
    public int getNumFeatures() {
        return numFeatures;
    }

    /**
     * Создаёт билдер для настройки классификатора.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double learningRate = DEFAULT_LEARNING_RATE;
        private double regularization = DEFAULT_REGULARIZATION;
        private int epochs = DEFAULT_EPOCHS;
        private int batchSize = DEFAULT_BATCH_SIZE;

        public Builder learningRate(double lr) {
            this.learningRate = lr;
            return this;
        }

        public Builder regularization(double reg) {
            this.regularization = reg;
            return this;
        }

        public Builder epochs(int epochs) {
            this.epochs = epochs;
            return this;
        }

        public Builder batchSize(int size) {
            this.batchSize = size;
            return this;
        }

        public LogisticRegressionClassifier build() {
            return new LogisticRegressionClassifier(learningRate, regularization, epochs, batchSize);
        }
    }
}
