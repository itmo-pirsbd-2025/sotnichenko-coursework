package ru.sotnichenko.sentiment.training;

import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.TextDocument;
import ru.sotnichenko.sentiment.preprocessing.TextPreprocessor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Загрузчик датасетов для обучения классификаторов.
 * Поддерживает различные форматы: CSV, TSV, JSON Lines.
 */
public class DatasetLoader {

    private final TextPreprocessor preprocessor;

    public DatasetLoader() {
        this(TextPreprocessor.defaultPreprocessor());
    }

    public DatasetLoader(TextPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    /**
     * Загружает датасет из CSV файла.
     * Формат: text,label (или text;label)
     */
    public List<TextDocument> loadFromCsv(String path) throws IOException {
        return loadFromCsv(Path.of(path));
    }

    public List<TextDocument> loadFromCsv(Path path) throws IOException {
        List<TextDocument> documents = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;
            String delimiter = ",";

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Определяем разделитель из первой строки
                if (firstLine) {
                    if (line.contains("\t")) {
                        delimiter = "\t";
                    } else if (line.contains(";")) {
                        delimiter = ";";
                    }
                    // Пропускаем заголовок, если он есть
                    if (line.toLowerCase().contains("text") || line.toLowerCase().contains("label")) {
                        firstLine = false;
                        continue;
                    }
                    firstLine = false;
                }

                TextDocument doc = parseCsvLine(line, delimiter);
                if (doc != null) {
                    documents.add(doc);
                }
            }
        }

        return documents;
    }

    /**
     * Парсит строку CSV.
     */
    private TextDocument parseCsvLine(String line, String delimiter) {
        String[] parts;

        // Обработка кавычек
        if (line.contains("\"")) {
            parts = parseQuotedCsv(line, delimiter);
        } else {
            parts = line.split(delimiter, 2);
        }

        if (parts.length < 2) {
            return null;
        }

        String text = parts[0].trim();
        String labelStr = parts[1].trim().toLowerCase();

        // Удаляем кавычки
        text = text.replaceAll("^\"|\"$", "");
        labelStr = labelStr.replaceAll("^\"|\"$", "");

        Sentiment label = parseLabel(labelStr);
        if (label == null || text.isEmpty()) {
            return null;
        }

        TextDocument doc = preprocessor.process(text);
        return doc.withLabel(label);
    }

    /**
     * Парсит CSV с кавычками.
     */
    private String[] parseQuotedCsv(String line, String delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                parts.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());

        return parts.toArray(new String[0]);
    }

    /**
     * Парсит метку тональности.
     */
    private Sentiment parseLabel(String labelStr) {
        return switch (labelStr.toLowerCase()) {
            case "positive", "pos", "1", "позитивный", "позитив", "положительный" -> Sentiment.POSITIVE;
            case "negative", "neg", "-1", "0", "негативный", "негатив", "отрицательный" -> Sentiment.NEGATIVE;
            case "neutral", "neu", "2", "нейтральный", "нейтрал" -> Sentiment.NEUTRAL;
            default -> null;
        };
    }

    /**
     * Загружает датасет из JSON Lines файла.
     * Формат: {"text": "...", "label": "..."}
     */
    public List<TextDocument> loadFromJsonLines(String path) throws IOException {
        return loadFromJsonLines(Path.of(path));
    }

    public List<TextDocument> loadFromJsonLines(Path path) throws IOException {
        List<TextDocument> documents = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                TextDocument doc = parseJsonLine(line);
                if (doc != null) {
                    documents.add(doc);
                }
            }
        }

        return documents;
    }

    /**
     * Парсит JSON строку.
     */
    private TextDocument parseJsonLine(String json) {
        // Простой парсинг без внешних зависимостей
        String text = extractJsonField(json, "text");
        String labelStr = extractJsonField(json, "label");

        if (text == null || labelStr == null) {
            return null;
        }

        Sentiment label = parseLabel(labelStr);
        if (label == null) {
            return null;
        }

        TextDocument doc = preprocessor.process(text);
        return doc.withLabel(label);
    }

    /**
     * Извлекает поле из JSON.
     */
    private String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        idx = json.indexOf(":", idx);
        if (idx < 0) return null;

        // Пропускаем пробелы
        idx++;
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) {
            idx++;
        }

        if (idx >= json.length()) return null;

        // Определяем тип значения
        char c = json.charAt(idx);
        if (c == '"') {
            // Строка
            int start = idx + 1;
            int end = json.indexOf("\"", start);
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf("\"", end + 1);
            }
            if (end < 0) return null;
            return json.substring(start, end).replace("\\\"", "\"");
        } else {
            // Число или другое значение
            int end = idx;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(idx, end).trim();
        }
    }

    /**
     * Загружает датасет из директории с файлами по классам.
     * Структура: dir/positive/*.txt, dir/negative/*.txt, dir/neutral/*.txt
     */
    public List<TextDocument> loadFromDirectory(String path) throws IOException {
        return loadFromDirectory(Path.of(path));
    }

    public List<TextDocument> loadFromDirectory(Path basePath) throws IOException {
        List<TextDocument> documents = new ArrayList<>();

        for (Sentiment sentiment : Sentiment.values()) {
            Path classDir = basePath.resolve(sentiment.getLabel());
            if (Files.exists(classDir) && Files.isDirectory(classDir)) {
                try (var files = Files.list(classDir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".txt")).toList()) {
                        String text = Files.readString(file, StandardCharsets.UTF_8);
                        TextDocument doc = preprocessor.process(text);
                        documents.add(doc.withLabel(sentiment));
                    }
                }
            }
        }

        return documents;
    }

    /**
     * Разделяет датасет на обучающую и тестовую выборки.
     */
    public DatasetSplit split(List<TextDocument> documents, double trainRatio) {
        return split(documents, trainRatio, 42L);
    }

    public DatasetSplit split(List<TextDocument> documents, double trainRatio, long seed) {
        List<TextDocument> shuffled = new ArrayList<>(documents);
        Collections.shuffle(shuffled, new Random(seed));

        int trainSize = (int) (shuffled.size() * trainRatio);

        List<TextDocument> train = shuffled.subList(0, trainSize);
        List<TextDocument> test = shuffled.subList(trainSize, shuffled.size());

        return new DatasetSplit(new ArrayList<>(train), new ArrayList<>(test));
    }

    /**
     * Разделяет датасет на обучающую, валидационную и тестовую выборки.
     */
    public DatasetSplit3 split3(List<TextDocument> documents,
                                double trainRatio, double validRatio) {
        return split3(documents, trainRatio, validRatio, 42L);
    }

    public DatasetSplit3 split3(List<TextDocument> documents,
                                double trainRatio, double validRatio, long seed) {
        List<TextDocument> shuffled = new ArrayList<>(documents);
        Collections.shuffle(shuffled, new Random(seed));

        int trainSize = (int) (shuffled.size() * trainRatio);
        int validSize = (int) (shuffled.size() * validRatio);

        List<TextDocument> train = shuffled.subList(0, trainSize);
        List<TextDocument> valid = shuffled.subList(trainSize, trainSize + validSize);
        List<TextDocument> test = shuffled.subList(trainSize + validSize, shuffled.size());

        return new DatasetSplit3(
                new ArrayList<>(train),
                new ArrayList<>(valid),
                new ArrayList<>(test)
        );
    }

    /**
     * Балансирует датасет путём undersampling.
     */
    public List<TextDocument> balanceByUndersampling(List<TextDocument> documents) {
        return balanceByUndersampling(documents, 42L);
    }

    public List<TextDocument> balanceByUndersampling(List<TextDocument> documents, long seed) {
        // Группируем по классам
        Map<Sentiment, List<TextDocument>> byClass = documents.stream()
                .filter(TextDocument::hasLabel)
                .collect(Collectors.groupingBy(TextDocument::getLabel));

        // Находим минимальный размер класса
        int minSize = byClass.values().stream()
                .mapToInt(List::size)
                .min()
                .orElse(0);

        // Семплируем из каждого класса
        Random random = new Random(seed);
        List<TextDocument> balanced = new ArrayList<>();

        for (List<TextDocument> classDocs : byClass.values()) {
            List<TextDocument> shuffled = new ArrayList<>(classDocs);
            Collections.shuffle(shuffled, random);
            balanced.addAll(shuffled.subList(0, Math.min(minSize, shuffled.size())));
        }

        Collections.shuffle(balanced, random);
        return balanced;
    }

    /**
     * Возвращает статистику датасета.
     */
    public Map<String, Object> getStatistics(List<TextDocument> documents) {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalDocuments", documents.size());

        // Распределение по классам
        Map<String, Long> classDistribution = documents.stream()
                .filter(TextDocument::hasLabel)
                .collect(Collectors.groupingBy(
                        d -> d.getLabel().getLabel(),
                        Collectors.counting()
                ));
        stats.put("classDistribution", classDistribution);

        // Статистика токенов
        DoubleSummaryStatistics tokenStats = documents.stream()
                .mapToDouble(TextDocument::tokenCount)
                .summaryStatistics();

        Map<String, Object> tokenStatistics = new LinkedHashMap<>();
        tokenStatistics.put("min", (int) tokenStats.getMin());
        tokenStatistics.put("max", (int) tokenStats.getMax());
        tokenStatistics.put("avg", Math.round(tokenStats.getAverage() * 100) / 100.0);
        stats.put("tokenStatistics", tokenStatistics);

        return stats;
    }

    /**
     * Результат разделения датасета на 2 части.
     */
    public record DatasetSplit(List<TextDocument> train, List<TextDocument> test) {
    }

    /**
     * Результат разделения датасета на 3 части.
     */
    public record DatasetSplit3(List<TextDocument> train,
                                List<TextDocument> validation,
                                List<TextDocument> test) {
    }
}
