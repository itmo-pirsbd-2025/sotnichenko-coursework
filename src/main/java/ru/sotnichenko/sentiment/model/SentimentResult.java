package ru.sotnichenko.sentiment.model;

import ru.sotnichenko.lab4.json.JsonBuilder;

import java.util.Map;

/**
 * Результат анализа тональности текста.
 */
public class SentimentResult {
    private final String text;
    private final Sentiment sentiment;
    private final double confidence;
    private final Map<Sentiment, Double> probabilities;
    private final String classifier;
    private final long processingTimeMs;

    private SentimentResult(Builder builder) {
        this.text = builder.text;
        this.sentiment = builder.sentiment;
        this.confidence = builder.confidence;
        this.probabilities = builder.probabilities;
        this.classifier = builder.classifier;
        this.processingTimeMs = builder.processingTimeMs;
    }

    public String getText() {
        return text;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public double getConfidence() {
        return confidence;
    }

    public Map<Sentiment, Double> getProbabilities() {
        return probabilities;
    }

    public String getClassifier() {
        return classifier;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    /**
     * Преобразует результат в JSON.
     */
    public String toJson() {
        JsonBuilder builder = JsonBuilder.object()
                .put("text", truncateText(text, 100))
                .put("sentiment", sentiment.getLabel())
                .put("confidence", Math.round(confidence * 10000) / 10000.0)
                .put("classifier", classifier)
                .put("processingTimeMs", processingTimeMs);

        if (probabilities != null && !probabilities.isEmpty()) {
            JsonBuilder probBuilder = JsonBuilder.object();
            for (Map.Entry<Sentiment, Double> entry : probabilities.entrySet()) {
                probBuilder.put(entry.getKey().getLabel(),
                        Math.round(entry.getValue() * 10000) / 10000.0);
            }
            builder.put("probabilities", probBuilder);
        }

        return builder.build();
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private Sentiment sentiment = Sentiment.NEUTRAL;
        private double confidence = 0.0;
        private Map<Sentiment, Double> probabilities;
        private String classifier = "unknown";
        private long processingTimeMs = 0;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder sentiment(Sentiment sentiment) {
            this.sentiment = sentiment;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder probabilities(Map<Sentiment, Double> probabilities) {
            this.probabilities = probabilities;
            return this;
        }

        public Builder classifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        public Builder processingTimeMs(long processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
            return this;
        }

        public SentimentResult build() {
            return new SentimentResult(this);
        }
    }
}
