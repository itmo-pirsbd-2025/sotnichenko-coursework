package ru.sotnichenko.sentiment.preprocessing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Фильтр стоп-слов.
 * Удаляет частые слова, не несущие смысловой нагрузки.
 */
public class StopWordsFilter {

    private final Set<String> stopWords;

    public StopWordsFilter() {
        this.stopWords = new HashSet<>();
        loadDefaultStopWords();
    }

    public StopWordsFilter(Set<String> stopWords) {
        this.stopWords = new HashSet<>(stopWords);
    }

    /**
     * Загружает стоп-слова по умолчанию (русские и английские).
     */
    private void loadDefaultStopWords() {
        // Русские стоп-слова
        stopWords.addAll(Arrays.asList(
                "а", "без", "более", "больше", "будет", "будто", "бы", "был", "была", "были", "было",
                "быть", "в", "вам", "вас", "ведь", "весь", "во", "вот", "все", "всего", "всей",
                "всех", "всю", "вы", "где", "да", "даже", "для", "до", "его", "ее", "её", "ей",
                "ему", "если", "есть", "еще", "ещё", "же", "за", "здесь", "и", "из", "или", "им",
                "их", "к", "как", "какая", "какой", "когда", "кто", "ли", "либо", "мне", "может",
                "мой", "моя", "мы", "на", "над", "надо", "наш", "не", "него", "нее", "неё", "ней",
                "нет", "ни", "них", "но", "ну", "о", "об", "один", "он", "она", "они", "оно", "от",
                "очень", "по", "под", "при", "с", "сам", "свой", "себя", "так", "такой", "там",
                "те", "тем", "то", "того", "тоже", "той", "только", "том", "тот", "ту", "ты", "у",
                "уже", "хотя", "чего", "чей", "чем", "что", "чтобы", "чья", "эта", "эти", "это",
                "этот", "я"
        ));

        // Английские стоп-слова
        stopWords.addAll(Arrays.asList(
                "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by", "can",
                "could", "did", "do", "does", "doing", "done", "for", "from", "had", "has", "have",
                "having", "he", "her", "here", "him", "his", "how", "i", "if", "in", "into", "is",
                "it", "its", "just", "me", "my", "no", "not", "now", "of", "on", "or", "other",
                "our", "out", "own", "same", "she", "should", "so", "some", "such", "than", "that",
                "the", "their", "them", "then", "there", "these", "they", "this", "those", "through",
                "to", "too", "under", "up", "very", "was", "we", "were", "what", "when", "where",
                "which", "while", "who", "whom", "why", "will", "with", "would", "you", "your"
        ));
    }

    /**
     * Загружает стоп-слова из файла ресурсов.
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
     * Загружает стоп-слова из потока.
     */
    public void loadFromStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    stopWords.add(line);
                }
            }
        }
    }

    /**
     * Проверяет, является ли слово стоп-словом.
     */
    public boolean isStopWord(String word) {
        return stopWords.contains(word.toLowerCase());
    }

    /**
     * Фильтрует список токенов, удаляя стоп-слова.
     */
    public List<String> filter(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !isStopWord(token))
                .collect(Collectors.toList());
    }

    /**
     * Добавляет стоп-слово.
     */
    public void addStopWord(String word) {
        stopWords.add(word.toLowerCase());
    }

    /**
     * Добавляет несколько стоп-слов.
     */
    public void addStopWords(Collection<String> words) {
        words.forEach(w -> stopWords.add(w.toLowerCase()));
    }

    /**
     * Удаляет стоп-слово.
     */
    public void removeStopWord(String word) {
        stopWords.remove(word.toLowerCase());
    }

    /**
     * Возвращает количество стоп-слов.
     */
    public int size() {
        return stopWords.size();
    }

    /**
     * Возвращает неизменяемое множество стоп-слов.
     */
    public Set<String> getStopWords() {
        return Collections.unmodifiableSet(stopWords);
    }

    /**
     * Создаёт фильтр только для русского языка.
     */
    public static StopWordsFilter russian() {
        StopWordsFilter filter = new StopWordsFilter(Collections.emptySet());
        filter.stopWords.addAll(Arrays.asList(
                "а", "без", "более", "больше", "будет", "будто", "бы", "был", "была", "были", "было",
                "быть", "в", "вам", "вас", "ведь", "весь", "во", "вот", "все", "всего", "всей",
                "всех", "всю", "вы", "где", "да", "даже", "для", "до", "его", "ее", "её", "ей",
                "ему", "если", "есть", "еще", "ещё", "же", "за", "здесь", "и", "из", "или", "им",
                "их", "к", "как", "какая", "какой", "когда", "кто", "ли", "либо", "мне", "может",
                "мой", "моя", "мы", "на", "над", "надо", "наш", "не", "него", "нее", "неё", "ней",
                "нет", "ни", "них", "но", "ну", "о", "об", "один", "он", "она", "они", "оно", "от",
                "очень", "по", "под", "при", "с", "сам", "свой", "себя", "так", "такой", "там",
                "те", "тем", "то", "того", "тоже", "той", "только", "том", "тот", "ту", "ты", "у",
                "уже", "хотя", "чего", "чей", "чем", "что", "чтобы", "чья", "эта", "эти", "это",
                "этот", "я"
        ));
        return filter;
    }

    /**
     * Создаёт фильтр только для английского языка.
     */
    public static StopWordsFilter english() {
        StopWordsFilter filter = new StopWordsFilter(Collections.emptySet());
        filter.stopWords.addAll(Arrays.asList(
                "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by", "can",
                "could", "did", "do", "does", "doing", "done", "for", "from", "had", "has", "have",
                "having", "he", "her", "here", "him", "his", "how", "i", "if", "in", "into", "is",
                "it", "its", "just", "me", "my", "no", "not", "now", "of", "on", "or", "other",
                "our", "out", "own", "same", "she", "should", "so", "some", "such", "than", "that",
                "the", "their", "them", "then", "there", "these", "they", "this", "those", "through",
                "to", "too", "under", "up", "very", "was", "we", "were", "what", "when", "where",
                "which", "while", "who", "whom", "why", "will", "with", "would", "you", "your"
        ));
        return filter;
    }
}
