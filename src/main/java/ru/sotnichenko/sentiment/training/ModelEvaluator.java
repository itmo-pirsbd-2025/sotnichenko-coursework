package ru.sotnichenko.sentiment.training;

import ru.sotnichenko.sentiment.classifier.Classifier;
import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.util.*;

/**
 * Оценщик качества моделей классификации.
 * Вычисляет метрики: accuracy, precision, recall, F1-score.
 */
public class ModelEvaluator {

    /**
     * Оценивает классификатор на тестовом наборе данных.
     */
    public EvaluationResult evaluate(Classifier classifier, List<TextDocument> testData) {
        if (testData == null || testData.isEmpty()) {
            throw new IllegalArgumentException("Test data cannot be empty");
        }

        List<TextDocument> labeledData = testData.stream()
                .filter(TextDocument::hasLabel)
                .toList();

        if (labeledData.isEmpty()) {
            throw new IllegalArgumentException("No labeled documents in test data");
        }

        // Матрица ошибок [actual][predicted]
        Map<Sentiment, Map<Sentiment, Integer>> confusionMatrix = new EnumMap<>(Sentiment.class);
        for (Sentiment s : Sentiment.values()) {
            confusionMatrix.put(s, new EnumMap<>(Sentiment.class));
            for (Sentiment p : Sentiment.values()) {
                confusionMatrix.get(s).put(p, 0);
            }
        }

        // Классифицируем и подсчитываем
        int correct = 0;
        long totalTime = 0;

        for (TextDocument doc : labeledData) {
            long start = System.nanoTime();
            SentimentResult result = classifier.classify(doc);
            totalTime += System.nanoTime() - start;

            Sentiment actual = doc.getLabel();
            Sentiment predicted = result.getSentiment();

            confusionMatrix.get(actual).merge(predicted, 1, Integer::sum);

            if (actual == predicted) {
                correct++;
            }
        }

        // Вычисляем метрики
        double accuracy = (double) correct / labeledData.size();
        double avgLatencyMs = totalTime / 1_000_000.0 / labeledData.size();

        Map<Sentiment, ClassMetrics> perClassMetrics = new EnumMap<>(Sentiment.class);

        for (Sentiment sentiment : Sentiment.values()) {
            int tp = confusionMatrix.get(sentiment).get(sentiment);
            int fp = 0;
            int fn = 0;

            for (Sentiment other : Sentiment.values()) {
                if (other != sentiment) {
                    fp += confusionMatrix.get(other).get(sentiment);
                    fn += confusionMatrix.get(sentiment).get(other);
                }
            }

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;

            perClassMetrics.put(sentiment, new ClassMetrics(precision, recall, f1, tp, fp, fn));
        }

        // Макро-усреднённые метрики
        double macroPrecision = perClassMetrics.values().stream()
                .mapToDouble(ClassMetrics::precision)
                .average()
                .orElse(0.0);

        double macroRecall = perClassMetrics.values().stream()
                .mapToDouble(ClassMetrics::recall)
                .average()
                .orElse(0.0);

        double macroF1 = perClassMetrics.values().stream()
                .mapToDouble(ClassMetrics::f1)
                .average()
                .orElse(0.0);

        return new EvaluationResult(
                classifier.getName(),
                labeledData.size(),
                accuracy,
                macroPrecision,
                macroRecall,
                macroF1,
                perClassMetrics,
                confusionMatrix,
                avgLatencyMs
        );
    }

    /**
     * Выполняет кросс-валидацию.
     */
    public CrossValidationResult crossValidate(Classifier classifier,
                                               List<TextDocument> data,
                                               int folds) {
        return crossValidate(classifier, data, folds, 42L);
    }

    public CrossValidationResult crossValidate(Classifier classifier,
                                               List<TextDocument> data,
                                               int folds,
                                               long seed) {
        List<TextDocument> labeled = data.stream()
                .filter(TextDocument::hasLabel)
                .toList();

        if (labeled.size() < folds) {
            throw new IllegalArgumentException("Not enough data for " + folds + "-fold cross-validation");
        }

        // Перемешиваем данные
        List<TextDocument> shuffled = new ArrayList<>(labeled);
        Collections.shuffle(shuffled, new Random(seed));

        // Разбиваем на фолды
        int foldSize = shuffled.size() / folds;
        List<List<TextDocument>> foldsList = new ArrayList<>();

        for (int i = 0; i < folds; i++) {
            int start = i * foldSize;
            int end = (i == folds - 1) ? shuffled.size() : (i + 1) * foldSize;
            foldsList.add(new ArrayList<>(shuffled.subList(start, end)));
        }

        // Выполняем кросс-валидацию
        List<EvaluationResult> results = new ArrayList<>();

        for (int i = 0; i < folds; i++) {
            // Формируем обучающую и тестовую выборки
            List<TextDocument> train = new ArrayList<>();
            List<TextDocument> test = foldsList.get(i);

            for (int j = 0; j < folds; j++) {
                if (j != i) {
                    train.addAll(foldsList.get(j));
                }
            }

            // Обучаем и оцениваем
            classifier.train(train);
            EvaluationResult result = evaluate(classifier, test);
            results.add(result);
        }

        // Усредняем результаты
        double avgAccuracy = results.stream()
                .mapToDouble(EvaluationResult::accuracy)
                .average()
                .orElse(0.0);

        double stdAccuracy = calculateStd(
                results.stream().mapToDouble(EvaluationResult::accuracy).toArray()
        );

        double avgF1 = results.stream()
                .mapToDouble(EvaluationResult::macroF1)
                .average()
                .orElse(0.0);

        double stdF1 = calculateStd(
                results.stream().mapToDouble(EvaluationResult::macroF1).toArray()
        );

        return new CrossValidationResult(
                classifier.getName(),
                folds,
                avgAccuracy,
                stdAccuracy,
                avgF1,
                stdF1,
                results
        );
    }

    /**
     * Вычисляет стандартное отклонение.
     */
    private double calculateStd(double[] values) {
        if (values.length == 0) return 0.0;

        double mean = Arrays.stream(values).average().orElse(0.0);
        double sumSquaredDiff = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .sum();

        return Math.sqrt(sumSquaredDiff / values.length);
    }

    /**
     * Сравнивает несколько классификаторов.
     */
    public List<EvaluationResult> compareClassifiers(List<Classifier> classifiers,
                                                     List<TextDocument> trainData,
                                                     List<TextDocument> testData) {
        List<EvaluationResult> results = new ArrayList<>();

        for (Classifier classifier : classifiers) {
            classifier.train(trainData);
            EvaluationResult result = evaluate(classifier, testData);
            results.add(result);
        }

        // Сортируем по F1-score
        results.sort((a, b) -> Double.compare(b.macroF1(), a.macroF1()));

        return results;
    }

    /**
     * Метрики для одного класса.
     */
    public record ClassMetrics(
            double precision,
            double recall,
            double f1,
            int truePositives,
            int falsePositives,
            int falseNegatives
    ) {
        public String toFormattedString() {
            return String.format("P=%.3f R=%.3f F1=%.3f (TP=%d FP=%d FN=%d)",
                    precision, recall, f1, truePositives, falsePositives, falseNegatives);
        }
    }

    /**
     * Результат оценки модели.
     */
    public record EvaluationResult(
            String classifierName,
            int testSize,
            double accuracy,
            double macroPrecision,
            double macroRecall,
            double macroF1,
            Map<Sentiment, ClassMetrics> perClassMetrics,
            Map<Sentiment, Map<Sentiment, Integer>> confusionMatrix,
            double avgLatencyMs
    ) {
        /**
         * Форматирует результат для вывода.
         */
        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Evaluation Results: ").append(classifierName).append(" ===\n");
            sb.append(String.format("Test size: %d documents%n", testSize));
            sb.append(String.format("Accuracy: %.4f (%.2f%%)%n", accuracy, accuracy * 100));
            sb.append(String.format("Macro Precision: %.4f%n", macroPrecision));
            sb.append(String.format("Macro Recall: %.4f%n", macroRecall));
            sb.append(String.format("Macro F1: %.4f%n", macroF1));
            sb.append(String.format("Avg Latency: %.3f ms%n", avgLatencyMs));

            sb.append("\nPer-class metrics:\n");
            for (Map.Entry<Sentiment, ClassMetrics> entry : perClassMetrics.entrySet()) {
                sb.append(String.format("  %s: %s%n",
                        entry.getKey().getLabel(), entry.getValue().toFormattedString()));
            }

            sb.append("\nConfusion Matrix:\n");
            sb.append("            ");
            for (Sentiment s : Sentiment.values()) {
                sb.append(String.format("%10s", s.getLabel()));
            }
            sb.append("\n");

            for (Sentiment actual : Sentiment.values()) {
                sb.append(String.format("%10s: ", actual.getLabel()));
                for (Sentiment predicted : Sentiment.values()) {
                    sb.append(String.format("%10d", confusionMatrix.get(actual).get(predicted)));
                }
                sb.append("\n");
            }

            return sb.toString();
        }

        /**
         * Преобразует в JSON.
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"classifier\":\"").append(classifierName).append("\",");
            sb.append("\"testSize\":").append(testSize).append(",");
            sb.append("\"accuracy\":").append(String.format("%.4f", accuracy)).append(",");
            sb.append("\"macroPrecision\":").append(String.format("%.4f", macroPrecision)).append(",");
            sb.append("\"macroRecall\":").append(String.format("%.4f", macroRecall)).append(",");
            sb.append("\"macroF1\":").append(String.format("%.4f", macroF1)).append(",");
            sb.append("\"avgLatencyMs\":").append(String.format("%.3f", avgLatencyMs));
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * Результат кросс-валидации.
     */
    public record CrossValidationResult(
            String classifierName,
            int folds,
            double avgAccuracy,
            double stdAccuracy,
            double avgF1,
            double stdF1,
            List<EvaluationResult> foldResults
    ) {
        public String toFormattedString() {
            return String.format(
                    "Cross-validation (%d folds) for %s:%n" +
                    "  Accuracy: %.4f (+/- %.4f)%n" +
                    "  F1-score: %.4f (+/- %.4f)",
                    folds, classifierName,
                    avgAccuracy, stdAccuracy,
                    avgF1, stdF1
            );
        }
    }
}
