package ru.sotnichenko.sentiment.preprocessing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Токенизатор текста.
 * Разбивает текст на отдельные слова с учётом особенностей языка.
 */
public class Tokenizer {

    // Паттерн для слов (кириллица и латиница)
    private static final Pattern WORD_PATTERN = Pattern.compile(
            "[a-zA-Zа-яА-ЯёЁ]+(?:'[a-zA-Z]+)?|\\d+(?:[.,]\\d+)?"
    );

    // Паттерн для эмодзи и эмотиконов
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[:;=][-']?[)D(P/\\\\|oO]|[)D(]+|<3|\\^_\\^|>_<"
    );

    private final boolean lowercase;
    private final boolean preserveEmoticons;
    private final int minTokenLength;
    private final int maxTokenLength;

    public Tokenizer() {
        this(true, true, 1, 50);
    }

    public Tokenizer(boolean lowercase, boolean preserveEmoticons, int minTokenLength, int maxTokenLength) {
        this.lowercase = lowercase;
        this.preserveEmoticons = preserveEmoticons;
        this.minTokenLength = minTokenLength;
        this.maxTokenLength = maxTokenLength;
    }

    /**
     * Токенизирует текст.
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> tokens = new ArrayList<>();

        // Сначала извлекаем эмотиконы, если нужно
        if (preserveEmoticons) {
            Matcher emojiMatcher = EMOJI_PATTERN.matcher(text);
            while (emojiMatcher.find()) {
                tokens.add(emojiMatcher.group());
            }
        }

        // Извлекаем слова
        Matcher wordMatcher = WORD_PATTERN.matcher(text);
        while (wordMatcher.find()) {
            String token = wordMatcher.group();

            // Применяем фильтры
            if (token.length() < minTokenLength || token.length() > maxTokenLength) {
                continue;
            }

            // Приводим к нижнему регистру
            if (lowercase) {
                token = token.toLowerCase();
            }

            tokens.add(token);
        }

        return tokens;
    }

    /**
     * Токенизирует с нормализацией повторяющихся символов.
     * Например: "ооооочень" -> "очень"
     */
    public List<String> tokenizeWithNormalization(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        // Нормализуем повторяющиеся символы (более 2 подряд)
        String normalized = normalizeRepeatedChars(text);

        return tokenize(normalized);
    }

    /**
     * Нормализует повторяющиеся символы.
     */
    private String normalizeRepeatedChars(String text) {
        StringBuilder sb = new StringBuilder();
        char prevChar = 0;
        int repeatCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == prevChar) {
                repeatCount++;
                if (repeatCount <= 2) {
                    sb.append(c);
                }
            } else {
                sb.append(c);
                prevChar = c;
                repeatCount = 1;
            }
        }

        return sb.toString();
    }

    /**
     * Создаёт билдер для настройки токенизатора.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean lowercase = true;
        private boolean preserveEmoticons = true;
        private int minTokenLength = 1;
        private int maxTokenLength = 50;

        public Builder lowercase(boolean lowercase) {
            this.lowercase = lowercase;
            return this;
        }

        public Builder preserveEmoticons(boolean preserve) {
            this.preserveEmoticons = preserve;
            return this;
        }

        public Builder minTokenLength(int length) {
            this.minTokenLength = length;
            return this;
        }

        public Builder maxTokenLength(int length) {
            this.maxTokenLength = length;
            return this;
        }

        public Tokenizer build() {
            return new Tokenizer(lowercase, preserveEmoticons, minTokenLength, maxTokenLength);
        }
    }
}
