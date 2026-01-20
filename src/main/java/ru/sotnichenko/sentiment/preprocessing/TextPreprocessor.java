package ru.sotnichenko.sentiment.preprocessing;

import ru.sotnichenko.sentiment.model.TextDocument;

import java.util.List;

/**
 * Конвейер предобработки текста.
 * Объединяет токенизацию, фильтрацию стоп-слов и стемминг.
 */
public class TextPreprocessor {

    private final Tokenizer tokenizer;
    private final StopWordsFilter stopWordsFilter;
    private final Stemmer stemmer;
    private final boolean removeStopWords;
    private final boolean applyStemming;
    private final boolean normalizeRepeatedChars;

    public TextPreprocessor() {
        this(new Builder());
    }

    private TextPreprocessor(Builder builder) {
        this.tokenizer = builder.tokenizer;
        this.stopWordsFilter = builder.stopWordsFilter;
        this.stemmer = builder.stemmer;
        this.removeStopWords = builder.removeStopWords;
        this.applyStemming = builder.applyStemming;
        this.normalizeRepeatedChars = builder.normalizeRepeatedChars;
    }

    /**
     * Предобрабатывает текст и возвращает TextDocument.
     */
    public TextDocument process(String text) {
        if (text == null || text.isEmpty()) {
            return new TextDocument("", List.of());
        }

        // Токенизация
        List<String> tokens = normalizeRepeatedChars
                ? tokenizer.tokenizeWithNormalization(text)
                : tokenizer.tokenize(text);

        // Фильтрация стоп-слов
        if (removeStopWords && stopWordsFilter != null) {
            tokens = stopWordsFilter.filter(tokens);
        }

        // Стемминг
        if (applyStemming && stemmer != null) {
            tokens = stemmer.stem(tokens);
        }

        return new TextDocument(text, tokens);
    }

    /**
     * Только токенизация без дополнительной обработки.
     */
    public List<String> tokenize(String text) {
        if (normalizeRepeatedChars) {
            return tokenizer.tokenizeWithNormalization(text);
        }
        return tokenizer.tokenize(text);
    }

    /**
     * Токенизация с фильтрацией стоп-слов.
     */
    public List<String> tokenizeAndFilter(String text) {
        List<String> tokens = tokenize(text);
        if (removeStopWords && stopWordsFilter != null) {
            return stopWordsFilter.filter(tokens);
        }
        return tokens;
    }

    /**
     * Возвращает токенизатор.
     */
    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    /**
     * Возвращает фильтр стоп-слов.
     */
    public StopWordsFilter getStopWordsFilter() {
        return stopWordsFilter;
    }

    /**
     * Возвращает стеммер.
     */
    public Stemmer getStemmer() {
        return stemmer;
    }

    /**
     * Создаёт билдер.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Создаёт препроцессор по умолчанию.
     */
    public static TextPreprocessor defaultPreprocessor() {
        return new Builder().build();
    }

    /**
     * Создаёт минимальный препроцессор (только токенизация).
     */
    public static TextPreprocessor minimal() {
        return new Builder()
                .removeStopWords(false)
                .applyStemming(false)
                .build();
    }

    public static class Builder {
        private Tokenizer tokenizer = Tokenizer.builder()
                .lowercase(true)
                .preserveEmoticons(true)
                .minTokenLength(2)
                .build();
        private StopWordsFilter stopWordsFilter = new StopWordsFilter();
        private Stemmer stemmer = new Stemmer();
        private boolean removeStopWords = true;
        private boolean applyStemming = true;
        private boolean normalizeRepeatedChars = true;

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder stopWordsFilter(StopWordsFilter filter) {
            this.stopWordsFilter = filter;
            return this;
        }

        public Builder stemmer(Stemmer stemmer) {
            this.stemmer = stemmer;
            return this;
        }

        public Builder removeStopWords(boolean remove) {
            this.removeStopWords = remove;
            return this;
        }

        public Builder applyStemming(boolean apply) {
            this.applyStemming = apply;
            return this;
        }

        public Builder normalizeRepeatedChars(boolean normalize) {
            this.normalizeRepeatedChars = normalize;
            return this;
        }

        public TextPreprocessor build() {
            return new TextPreprocessor(this);
        }
    }
}
