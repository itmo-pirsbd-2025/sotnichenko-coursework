package ru.sotnichenko.sentiment.model;

/**
 * Перечисление типов тональности текста.
 */
public enum Sentiment {
    POSITIVE("positive", 1.0),
    NEGATIVE("negative", -1.0),
    NEUTRAL("neutral", 0.0);

    private final String label;
    private final double numericValue;

    Sentiment(String label, double numericValue) {
        this.label = label;
        this.numericValue = numericValue;
    }

    public String getLabel() {
        return label;
    }

    public double getNumericValue() {
        return numericValue;
    }

    /**
     * Получает тональность по метке.
     */
    public static Sentiment fromLabel(String label) {
        for (Sentiment s : values()) {
            if (s.label.equalsIgnoreCase(label)) {
                return s;
            }
        }
        return NEUTRAL;
    }

    /**
     * Получает тональность по числовому значению.
     */
    public static Sentiment fromScore(double score) {
        if (score > 0.1) return POSITIVE;
        if (score < -0.1) return NEGATIVE;
        return NEUTRAL;
    }
}
