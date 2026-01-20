package ru.sotnichenko.sentiment.dictionary;

import ru.sotnichenko.sentiment.model.Sentiment;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Словарь тональности слов.
 * Содержит слова с их эмоциональными оценками.
 */
public class SentimentDictionary {

    private final Map<String, Double> wordScores;
    private final Map<String, Double> phraseScores;
    private final String name;

    public SentimentDictionary(String name) {
        this.name = name;
        this.wordScores = new HashMap<>();
        this.phraseScores = new HashMap<>();
    }

    /**
     * Загружает словарь из файла ресурсов.
     * Формат: слово\tоценка (от -1 до 1)
     */
    public void loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            loadFromStream(is);
        }
    }

    /**
     * Загружает словарь из потока.
     */
    public void loadFromStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\t");
                if (parts.length >= 2) {
                    String word = parts[0].toLowerCase().trim();
                    try {
                        double score = Double.parseDouble(parts[1].trim());
                        score = Math.max(-1.0, Math.min(1.0, score)); // Ограничиваем [-1, 1]

                        if (word.contains(" ")) {
                            phraseScores.put(word, score);
                        } else {
                            wordScores.put(word, score);
                        }
                    } catch (NumberFormatException ignored) {
                        // Пропускаем некорректные строки
                    }
                }
            }
        }
    }

    /**
     * Добавляет слово в словарь.
     */
    public void addWord(String word, double score) {
        wordScores.put(word.toLowerCase(), Math.max(-1.0, Math.min(1.0, score)));
    }

    /**
     * Добавляет фразу в словарь.
     */
    public void addPhrase(String phrase, double score) {
        phraseScores.put(phrase.toLowerCase(), Math.max(-1.0, Math.min(1.0, score)));
    }

    /**
     * Получает оценку слова.
     */
    public Double getWordScore(String word) {
        return wordScores.get(word.toLowerCase());
    }

    /**
     * Получает оценку фразы.
     */
    public Double getPhraseScore(String phrase) {
        return phraseScores.get(phrase.toLowerCase());
    }

    /**
     * Проверяет наличие слова в словаре.
     */
    public boolean containsWord(String word) {
        return wordScores.containsKey(word.toLowerCase());
    }

    /**
     * Вычисляет общую оценку тональности списка токенов.
     */
    public double calculateScore(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int matchedCount = 0;

        // Проверяем биграммы (фразы из 2 слов)
        Set<Integer> usedIndices = new HashSet<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + " " + tokens.get(i + 1);
            Double phraseScore = phraseScores.get(bigram.toLowerCase());
            if (phraseScore != null) {
                totalScore += phraseScore;
                matchedCount++;
                usedIndices.add(i);
                usedIndices.add(i + 1);
            }
        }

        // Проверяем отдельные слова
        for (int i = 0; i < tokens.size(); i++) {
            if (usedIndices.contains(i)) {
                continue;
            }
            Double wordScore = wordScores.get(tokens.get(i).toLowerCase());
            if (wordScore != null) {
                totalScore += wordScore;
                matchedCount++;
            }
        }

        return matchedCount > 0 ? totalScore / matchedCount : 0.0;
    }

    /**
     * Определяет тональность по оценке.
     */
    public Sentiment getSentiment(double score) {
        return Sentiment.fromScore(score);
    }

    /**
     * Анализирует тональность текста.
     */
    public DictionaryAnalysisResult analyze(List<String> tokens) {
        double score = calculateScore(tokens);
        Sentiment sentiment = getSentiment(score);

        // Находим позитивные и негативные слова
        List<String> positiveWords = new ArrayList<>();
        List<String> negativeWords = new ArrayList<>();

        for (String token : tokens) {
            Double wordScore = wordScores.get(token.toLowerCase());
            if (wordScore != null) {
                if (wordScore > 0.1) {
                    positiveWords.add(token);
                } else if (wordScore < -0.1) {
                    negativeWords.add(token);
                }
            }
        }

        return new DictionaryAnalysisResult(score, sentiment, positiveWords, negativeWords);
    }

    /**
     * Возвращает имя словаря.
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает количество слов в словаре.
     */
    public int wordCount() {
        return wordScores.size();
    }

    /**
     * Возвращает количество фраз в словаре.
     */
    public int phraseCount() {
        return phraseScores.size();
    }

    /**
     * Возвращает общее количество записей.
     */
    public int size() {
        return wordScores.size() + phraseScores.size();
    }

    /**
     * Результат анализа по словарю.
     */
    public static class DictionaryAnalysisResult {
        private final double score;
        private final Sentiment sentiment;
        private final List<String> positiveWords;
        private final List<String> negativeWords;

        public DictionaryAnalysisResult(double score, Sentiment sentiment,
                                        List<String> positiveWords, List<String> negativeWords) {
            this.score = score;
            this.sentiment = sentiment;
            this.positiveWords = List.copyOf(positiveWords);
            this.negativeWords = List.copyOf(negativeWords);
        }

        public double getScore() {
            return score;
        }

        public Sentiment getSentiment() {
            return sentiment;
        }

        public List<String> getPositiveWords() {
            return positiveWords;
        }

        public List<String> getNegativeWords() {
            return negativeWords;
        }
    }
}
