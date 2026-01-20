package ru.sotnichenko.sentiment.model;

import java.util.List;
import java.util.Objects;

/**
 * Представление текстового документа после предобработки.
 */
public class TextDocument {
    private final String originalText;
    private final List<String> tokens;
    private final Sentiment label; // null если метка неизвестна

    public TextDocument(String originalText, List<String> tokens) {
        this(originalText, tokens, null);
    }

    public TextDocument(String originalText, List<String> tokens, Sentiment label) {
        this.originalText = originalText;
        this.tokens = List.copyOf(tokens);
        this.label = label;
    }

    public String getOriginalText() {
        return originalText;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public Sentiment getLabel() {
        return label;
    }

    public boolean hasLabel() {
        return label != null;
    }

    /**
     * Создаёт документ с указанной меткой.
     */
    public TextDocument withLabel(Sentiment label) {
        return new TextDocument(this.originalText, this.tokens, label);
    }

    /**
     * Возвращает количество токенов.
     */
    public int tokenCount() {
        return tokens.size();
    }

    /**
     * Проверяет наличие токена.
     */
    public boolean containsToken(String token) {
        return tokens.contains(token);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextDocument that = (TextDocument) o;
        return Objects.equals(originalText, that.originalText) &&
               Objects.equals(tokens, that.tokens) &&
               label == that.label;
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalText, tokens, label);
    }

    @Override
    public String toString() {
        return "TextDocument{" +
               "tokens=" + tokens.size() +
               ", label=" + label +
               '}';
    }
}
