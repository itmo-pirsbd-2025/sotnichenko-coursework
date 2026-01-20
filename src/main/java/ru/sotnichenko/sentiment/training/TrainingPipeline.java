package ru.sotnichenko.sentiment.training;

import ru.sotnichenko.sentiment.classifier.*;
import ru.sotnichenko.sentiment.dictionary.EnglishSentimentDictionary;
import ru.sotnichenko.sentiment.dictionary.RussianSentimentDictionary;
import ru.sotnichenko.sentiment.model.TextDocument;
import ru.sotnichenko.sentiment.preprocessing.TextPreprocessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Пайплайн обучения моделей.
 * Объединяет загрузку данных, обучение и оценку.
 */
public class TrainingPipeline {

    private final TextPreprocessor preprocessor;
    private final DatasetLoader datasetLoader;
    private final ModelEvaluator evaluator;

    private List<TextDocument> trainData;
    private List<TextDocument> testData;
    private final Map<String, Classifier> trainedClassifiers;

    public TrainingPipeline() {
        this(TextPreprocessor.defaultPreprocessor());
    }

    public TrainingPipeline(TextPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
        this.datasetLoader = new DatasetLoader(preprocessor);
        this.evaluator = new ModelEvaluator();
        this.trainedClassifiers = new LinkedHashMap<>();
    }

    /**
     * Загружает датасет и разделяет на train/test.
     */
    public TrainingPipeline loadDataset(String path, double trainRatio) throws IOException {
        List<TextDocument> allData;

        Path dataPath = Path.of(path);
        if (Files.isDirectory(dataPath)) {
            allData = datasetLoader.loadFromDirectory(dataPath);
        } else if (path.endsWith(".csv") || path.endsWith(".tsv")) {
            allData = datasetLoader.loadFromCsv(dataPath);
        } else if (path.endsWith(".jsonl") || path.endsWith(".json")) {
            allData = datasetLoader.loadFromJsonLines(dataPath);
        } else {
            throw new IllegalArgumentException("Unsupported file format: " + path);
        }

        DatasetLoader.DatasetSplit split = datasetLoader.split(allData, trainRatio);
        this.trainData = split.train();
        this.testData = split.test();

        System.out.println("Dataset loaded:");
        System.out.println("  Train: " + trainData.size() + " documents");
        System.out.println("  Test: " + testData.size() + " documents");
        System.out.println("  Statistics: " + datasetLoader.getStatistics(allData));

        return this;
    }

    /**
     * Устанавливает обучающие данные напрямую.
     */
    public TrainingPipeline setTrainData(List<TextDocument> trainData) {
        this.trainData = trainData;
        return this;
    }

    /**
     * Устанавливает тестовые данные напрямую.
     */
    public TrainingPipeline setTestData(List<TextDocument> testData) {
        this.testData = testData;
        return this;
    }

    /**
     * Обучает Naive Bayes классификатор.
     */
    public TrainingPipeline trainNaiveBayes() {
        return trainNaiveBayes(1.0);
    }

    public TrainingPipeline trainNaiveBayes(double alpha) {
        checkTrainData();

        System.out.println("\nTraining Naive Bayes classifier (alpha=" + alpha + ")...");
        long start = System.currentTimeMillis();

        NaiveBayesClassifier classifier = new NaiveBayesClassifier(alpha);
        classifier.train(trainData);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  Training completed in " + elapsed + " ms");
        System.out.println("  Vocabulary size: " + classifier.getVocabularySize());

        trainedClassifiers.put("NaiveBayes", classifier);
        return this;
    }

    /**
     * Обучает Logistic Regression классификатор.
     */
    public TrainingPipeline trainLogisticRegression() {
        return trainLogisticRegression(0.1, 0.01, 100);
    }

    public TrainingPipeline trainLogisticRegression(double learningRate,
                                                    double regularization,
                                                    int epochs) {
        checkTrainData();

        System.out.println("\nTraining Logistic Regression classifier...");
        System.out.println("  Learning rate: " + learningRate);
        System.out.println("  Regularization: " + regularization);
        System.out.println("  Epochs: " + epochs);

        long start = System.currentTimeMillis();

        LogisticRegressionClassifier classifier = LogisticRegressionClassifier.builder()
                .learningRate(learningRate)
                .regularization(regularization)
                .epochs(epochs)
                .build();
        classifier.train(trainData);

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  Training completed in " + elapsed + " ms");
        System.out.println("  Features: " + classifier.getNumFeatures());

        trainedClassifiers.put("LogisticRegression", classifier);
        return this;
    }

    /**
     * Добавляет словарный классификатор (русский).
     */
    public TrainingPipeline addRussianDictionaryClassifier() {
        System.out.println("\nAdding Russian Dictionary classifier...");

        DictionaryClassifier classifier = new DictionaryClassifier(
                RussianSentimentDictionary.extended()
        );

        trainedClassifiers.put("DictionaryRu", classifier);
        return this;
    }

    /**
     * Добавляет словарный классификатор (английский).
     */
    public TrainingPipeline addEnglishDictionaryClassifier() {
        System.out.println("\nAdding English Dictionary classifier...");

        DictionaryClassifier classifier = new DictionaryClassifier(
                EnglishSentimentDictionary.extended()
        );

        trainedClassifiers.put("DictionaryEn", classifier);
        return this;
    }

    /**
     * Создаёт ансамблевый классификатор из обученных моделей.
     */
    public TrainingPipeline createEnsemble(EnsembleClassifier.VotingStrategy strategy) {
        if (trainedClassifiers.isEmpty()) {
            throw new IllegalStateException("No classifiers trained yet");
        }

        System.out.println("\nCreating Ensemble classifier with " + strategy + " voting...");

        EnsembleClassifier.Builder builder = EnsembleClassifier.builder()
                .votingStrategy(strategy);

        for (Classifier classifier : trainedClassifiers.values()) {
            builder.add(classifier);
        }

        trainedClassifiers.put("Ensemble", builder.build());
        return this;
    }

    /**
     * Оценивает все обученные классификаторы.
     */
    public Map<String, ModelEvaluator.EvaluationResult> evaluate() {
        checkTestData();

        System.out.println("\n=== Evaluation Results ===\n");

        Map<String, ModelEvaluator.EvaluationResult> results = new LinkedHashMap<>();

        for (Map.Entry<String, Classifier> entry : trainedClassifiers.entrySet()) {
            String name = entry.getKey();
            Classifier classifier = entry.getValue();

            if (!classifier.isTrained()) {
                System.out.println(name + ": Not trained, skipping...");
                continue;
            }

            ModelEvaluator.EvaluationResult result = evaluator.evaluate(classifier, testData);
            results.put(name, result);

            System.out.println(result.toFormattedString());
            System.out.println();
        }

        return results;
    }

    /**
     * Выполняет кросс-валидацию для классификатора.
     */
    public ModelEvaluator.CrossValidationResult crossValidate(String classifierName, int folds) {
        Classifier classifier = trainedClassifiers.get(classifierName);
        if (classifier == null) {
            throw new IllegalArgumentException("Classifier not found: " + classifierName);
        }

        List<TextDocument> allData = new ArrayList<>();
        if (trainData != null) allData.addAll(trainData);
        if (testData != null) allData.addAll(testData);

        if (allData.isEmpty()) {
            throw new IllegalStateException("No data loaded");
        }

        return evaluator.crossValidate(classifier, allData, folds);
    }

    /**
     * Сохраняет модель в файл.
     */
    public TrainingPipeline saveModel(String classifierName, String path) throws IOException {
        Classifier classifier = trainedClassifiers.get(classifierName);
        if (classifier == null) {
            throw new IllegalArgumentException("Classifier not found: " + classifierName);
        }

        classifier.saveToFile(path);
        System.out.println("Model saved: " + classifierName + " -> " + path);

        return this;
    }

    /**
     * Загружает модель из файла.
     */
    public TrainingPipeline loadModel(String classifierName, String path) throws IOException {
        Classifier classifier = trainedClassifiers.get(classifierName);
        if (classifier == null) {
            throw new IllegalArgumentException("Classifier not found: " + classifierName);
        }

        classifier.loadFromFile(path);
        System.out.println("Model loaded: " + classifierName + " <- " + path);

        return this;
    }

    /**
     * Возвращает обученный классификатор по имени.
     */
    public Classifier getClassifier(String name) {
        return trainedClassifiers.get(name);
    }

    /**
     * Возвращает все обученные классификаторы.
     */
    public Map<String, Classifier> getClassifiers() {
        return Collections.unmodifiableMap(trainedClassifiers);
    }

    /**
     * Возвращает лучший классификатор по F1-score.
     */
    public Classifier getBestClassifier() {
        checkTestData();

        Classifier best = null;
        double bestF1 = -1;

        for (Classifier classifier : trainedClassifiers.values()) {
            if (classifier.isTrained()) {
                ModelEvaluator.EvaluationResult result = evaluator.evaluate(classifier, testData);
                if (result.macroF1() > bestF1) {
                    bestF1 = result.macroF1();
                    best = classifier;
                }
            }
        }

        return best;
    }

    /**
     * Возвращает препроцессор.
     */
    public TextPreprocessor getPreprocessor() {
        return preprocessor;
    }

    private void checkTrainData() {
        if (trainData == null || trainData.isEmpty()) {
            throw new IllegalStateException("Training data not loaded. Call loadDataset() first.");
        }
    }

    private void checkTestData() {
        if (testData == null || testData.isEmpty()) {
            throw new IllegalStateException("Test data not loaded. Call loadDataset() first.");
        }
    }

    /**
     * Создаёт пайплайн с настройками по умолчанию.
     */
    public static TrainingPipeline create() {
        return new TrainingPipeline();
    }

    /**
     * Создаёт пайплайн с пользовательским препроцессором.
     */
    public static TrainingPipeline create(TextPreprocessor preprocessor) {
        return new TrainingPipeline(preprocessor);
    }
}
