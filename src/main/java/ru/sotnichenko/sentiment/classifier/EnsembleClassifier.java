package ru.sotnichenko.sentiment.classifier;

import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.*;

/**
 * Ансамблевый классификатор, объединяющий несколько классификаторов.
 * Использует взвешенное голосование для финального решения.
 */
public class EnsembleClassifier implements Classifier {

    private static final String NAME = "Ensemble";

    private final List<ClassifierWithWeight> classifiers;
    private final VotingStrategy votingStrategy;

    public enum VotingStrategy {
        HARD,    // Голосование по классам
        SOFT,    // Усреднение вероятностей
        WEIGHTED // Взвешенное усреднение
    }

    public EnsembleClassifier() {
        this(VotingStrategy.SOFT);
    }

    public EnsembleClassifier(VotingStrategy strategy) {
        this.classifiers = new ArrayList<>();
        this.votingStrategy = strategy;
    }

    /**
     * Добавляет классификатор в ансамбль с весом по умолчанию (1.0).
     */
    public void addClassifier(Classifier classifier) {
        addClassifier(classifier, 1.0);
    }

    /**
     * Добавляет классификатор в ансамбль с заданным весом.
     */
    public void addClassifier(Classifier classifier, double weight) {
        classifiers.add(new ClassifierWithWeight(classifier, weight));
    }

    @Override
    public SentimentResult classify(TextDocument document) {
        long startTime = System.currentTimeMillis();

        if (classifiers.isEmpty()) {
            return SentimentResult.builder()
                    .text(document.getOriginalText())
                    .sentiment(Sentiment.NEUTRAL)
                    .confidence(0.0)
                    .classifier(NAME)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        Map<Sentiment, Double> aggregatedProbabilities;

        switch (votingStrategy) {
            case HARD -> aggregatedProbabilities = hardVoting(document);
            case SOFT -> aggregatedProbabilities = softVoting(document);
            case WEIGHTED -> aggregatedProbabilities = weightedVoting(document);
            default -> aggregatedProbabilities = softVoting(document);
        }

        // Находим класс с максимальной вероятностью
        Sentiment bestClass = Sentiment.NEUTRAL;
        double maxProb = 0.0;

        for (Map.Entry<Sentiment, Double> entry : aggregatedProbabilities.entrySet()) {
            if (entry.getValue() > maxProb) {
                maxProb = entry.getValue();
                bestClass = entry.getKey();
            }
        }

        // Вычисляем confidence
        final Sentiment finalBestClass = bestClass;
        double secondBest = aggregatedProbabilities.entrySet().stream()
                .filter(e -> e.getKey() != finalBestClass)
                .mapToDouble(Map.Entry::getValue)
                .max()
                .orElse(0.0);

        double confidence = Math.min(1.0, maxProb - secondBest + maxProb * 0.3);

        return SentimentResult.builder()
                .text(document.getOriginalText())
                .sentiment(bestClass)
                .confidence(confidence)
                .probabilities(aggregatedProbabilities)
                .classifier(NAME + "[" + classifiers.size() + "]")
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * Жёсткое голосование - каждый классификатор голосует за один класс.
     */
    private Map<Sentiment, Double> hardVoting(TextDocument document) {
        Map<Sentiment, Double> votes = new HashMap<>();
        for (Sentiment s : Sentiment.values()) {
            votes.put(s, 0.0);
        }

        double totalWeight = 0.0;

        for (ClassifierWithWeight cw : classifiers) {
            if (cw.classifier.isTrained()) {
                SentimentResult result = cw.classifier.classify(document);
                votes.merge(result.getSentiment(), cw.weight, Double::sum);
                totalWeight += cw.weight;
            }
        }

        // Нормализуем
        if (totalWeight > 0) {
            for (Sentiment s : votes.keySet()) {
                votes.put(s, votes.get(s) / totalWeight);
            }
        }

        return votes;
    }

    /**
     * Мягкое голосование - усреднение вероятностей.
     */
    private Map<Sentiment, Double> softVoting(TextDocument document) {
        Map<Sentiment, Double> avgProbabilities = new HashMap<>();
        for (Sentiment s : Sentiment.values()) {
            avgProbabilities.put(s, 0.0);
        }

        int count = 0;

        for (ClassifierWithWeight cw : classifiers) {
            if (cw.classifier.isTrained()) {
                Map<Sentiment, Double> probs = cw.classifier.predictProbabilities(document);
                for (Map.Entry<Sentiment, Double> entry : probs.entrySet()) {
                    avgProbabilities.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
                count++;
            }
        }

        // Усредняем
        if (count > 0) {
            for (Sentiment s : avgProbabilities.keySet()) {
                avgProbabilities.put(s, avgProbabilities.get(s) / count);
            }
        }

        return avgProbabilities;
    }

    /**
     * Взвешенное голосование - вероятности взвешиваются весами классификаторов.
     */
    private Map<Sentiment, Double> weightedVoting(TextDocument document) {
        Map<Sentiment, Double> weightedProbabilities = new HashMap<>();
        for (Sentiment s : Sentiment.values()) {
            weightedProbabilities.put(s, 0.0);
        }

        double totalWeight = 0.0;

        for (ClassifierWithWeight cw : classifiers) {
            if (cw.classifier.isTrained()) {
                Map<Sentiment, Double> probs = cw.classifier.predictProbabilities(document);
                for (Map.Entry<Sentiment, Double> entry : probs.entrySet()) {
                    weightedProbabilities.merge(
                            entry.getKey(),
                            entry.getValue() * cw.weight,
                            Double::sum
                    );
                }
                totalWeight += cw.weight;
            }
        }

        // Нормализуем
        if (totalWeight > 0) {
            for (Sentiment s : weightedProbabilities.keySet()) {
                weightedProbabilities.put(s, weightedProbabilities.get(s) / totalWeight);
            }
        }

        return weightedProbabilities;
    }

    @Override
    public void train(List<TextDocument> documents) {
        // Обучаем все классификаторы в ансамбле
        for (ClassifierWithWeight cw : classifiers) {
            if (!cw.classifier.isTrained()) {
                cw.classifier.train(documents);
            }
        }
    }

    @Override
    public Map<Sentiment, Double> predictProbabilities(TextDocument document) {
        return switch (votingStrategy) {
            case HARD -> hardVoting(document);
            case SOFT -> softVoting(document);
            case WEIGHTED -> weightedVoting(document);
        };
    }

    @Override
    public boolean isTrained() {
        // Ансамбль обучен, если хотя бы один классификатор обучен
        return classifiers.stream().anyMatch(cw -> cw.classifier.isTrained());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void save(OutputStream outputStream) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
            oos.writeObject(votingStrategy);
            oos.writeInt(classifiers.size());
            for (ClassifierWithWeight cw : classifiers) {
                oos.writeUTF(cw.classifier.getName());
                oos.writeDouble(cw.weight);
                cw.classifier.save(oos);
            }
        }
    }

    @Override
    public void load(InputStream inputStream) throws IOException {
        // Загрузка ансамбля требует предварительного добавления классификаторов
        try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
            VotingStrategy strategy = (VotingStrategy) ois.readObject();
            int count = ois.readInt();

            for (int i = 0; i < count && i < classifiers.size(); i++) {
                String name = ois.readUTF();
                double weight = ois.readDouble();
                classifiers.get(i).weight = weight;
                classifiers.get(i).classifier.load(ois);
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load ensemble", e);
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("classifier", NAME);
        stats.put("votingStrategy", votingStrategy.name());
        stats.put("classifierCount", classifiers.size());

        List<Map<String, Object>> classifierStats = new ArrayList<>();
        for (ClassifierWithWeight cw : classifiers) {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("name", cw.classifier.getName());
            cs.put("weight", cw.weight);
            cs.put("trained", cw.classifier.isTrained());
            classifierStats.add(cs);
        }
        stats.put("classifiers", classifierStats);

        return stats;
    }

    /**
     * Возвращает количество классификаторов в ансамбле.
     */
    public int getClassifierCount() {
        return classifiers.size();
    }

    /**
     * Возвращает список классификаторов.
     */
    public List<Classifier> getClassifiers() {
        return classifiers.stream()
                .map(cw -> cw.classifier)
                .toList();
    }

    /**
     * Внутренний класс для хранения классификатора с весом.
     */
    private static class ClassifierWithWeight {
        Classifier classifier;
        double weight;

        ClassifierWithWeight(Classifier classifier, double weight) {
            this.classifier = classifier;
            this.weight = weight;
        }
    }

    /**
     * Создаёт билдер.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<ClassifierWithWeight> classifiers = new ArrayList<>();
        private VotingStrategy strategy = VotingStrategy.SOFT;

        public Builder add(Classifier classifier) {
            return add(classifier, 1.0);
        }

        public Builder add(Classifier classifier, double weight) {
            classifiers.add(new ClassifierWithWeight(classifier, weight));
            return this;
        }

        public Builder votingStrategy(VotingStrategy strategy) {
            this.strategy = strategy;
            return this;
        }

        public EnsembleClassifier build() {
            EnsembleClassifier ensemble = new EnsembleClassifier(strategy);
            for (ClassifierWithWeight cw : classifiers) {
                ensemble.addClassifier(cw.classifier, cw.weight);
            }
            return ensemble;
        }
    }
}
