package ru.sotnichenko.sentiment.classifier;

import ru.sotnichenko.sentiment.dictionary.SentimentDictionary;
import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.*;

/**
 * Классификатор на основе словаря тональности.
 * Использует предопределённый словарь слов с эмоциональными оценками.
 * Не требует обучения на данных.
 */
public class DictionaryClassifier implements Classifier {

    private static final String NAME = "Dictionary";

    private final SentimentDictionary dictionary;
    private final boolean handleNegation;
    private final Set<String> negationWords;

    public DictionaryClassifier(SentimentDictionary dictionary) {
        this(dictionary, true);
    }

    public DictionaryClassifier(SentimentDictionary dictionary, boolean handleNegation) {
        this.dictionary = dictionary;
        this.handleNegation = handleNegation;
        this.negationWords = new HashSet<>(Arrays.asList(
                // Русские
                "не", "нет", "ни", "без", "никак", "никогда", "нигде", "ничто", "никто",
                // Английские
                "not", "no", "never", "neither", "nothing", "nobody", "nowhere",
                "don't", "doesn't", "didn't", "won't", "wouldn't", "couldn't", "shouldn't"
        ));
    }

    @Override
    public SentimentResult classify(TextDocument document) {
        long startTime = System.currentTimeMillis();

        List<String> tokens = document.getTokens();
        double score = calculateScoreWithNegation(tokens);

        Sentiment sentiment = Sentiment.fromScore(score);
        double confidence = calculateConfidence(score);

        Map<Sentiment, Double> probabilities = new HashMap<>();
        probabilities.put(Sentiment.POSITIVE, score > 0 ? Math.abs(score) : 0.0);
        probabilities.put(Sentiment.NEGATIVE, score < 0 ? Math.abs(score) : 0.0);
        probabilities.put(Sentiment.NEUTRAL, 1.0 - Math.abs(score));

        // Нормализуем вероятности
        double sum = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            for (Sentiment s : probabilities.keySet()) {
                probabilities.put(s, probabilities.get(s) / sum);
            }
        }

        return SentimentResult.builder()
                .text(document.getOriginalText())
                .sentiment(sentiment)
                .confidence(confidence)
                .probabilities(probabilities)
                .classifier(NAME + "(" + dictionary.getName() + ")")
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
    }

    /**
     * Вычисляет оценку с учётом отрицаний.
     */
    private double calculateScoreWithNegation(List<String> tokens) {
        if (!handleNegation) {
            return dictionary.calculateScore(tokens);
        }

        double totalScore = 0.0;
        int matchedCount = 0;
        boolean negated = false;
        int negationWindow = 0; // Окно действия отрицания

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).toLowerCase();

            // Проверяем, является ли слово отрицанием
            if (negationWords.contains(token)) {
                negated = true;
                negationWindow = 3; // Отрицание действует на следующие 3 слова
                continue;
            }

            // Получаем оценку слова
            Double wordScore = dictionary.getWordScore(token);
            if (wordScore != null && wordScore != 0.0) {
                // Инвертируем оценку, если есть активное отрицание
                if (negated && negationWindow > 0) {
                    wordScore = -wordScore * 0.8; // Немного ослабляем инверсию
                }
                totalScore += wordScore;
                matchedCount++;
            }

            // Уменьшаем окно отрицания
            if (negationWindow > 0) {
                negationWindow--;
                if (negationWindow == 0) {
                    negated = false;
                }
            }
        }

        return matchedCount > 0 ? totalScore / matchedCount : 0.0;
    }

    /**
     * Вычисляет уверенность на основе оценки.
     */
    private double calculateConfidence(double score) {
        // Чем дальше от нуля, тем выше уверенность
        double absScore = Math.abs(score);
        return Math.min(1.0, absScore * 1.5);
    }

    @Override
    public void train(List<TextDocument> documents) {
        // Словарный классификатор не требует обучения
        // Но можно использовать данные для расширения словаря
    }

    @Override
    public Map<Sentiment, Double> predictProbabilities(TextDocument document) {
        SentimentResult result = classify(document);
        return result.getProbabilities();
    }

    @Override
    public boolean isTrained() {
        // Словарный классификатор всегда готов к работе
        return dictionary.size() > 0;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void save(OutputStream outputStream) throws IOException {
        // Для словарного классификатора сохраняем только конфигурацию
        try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
            oos.writeBoolean(handleNegation);
            oos.writeInt(negationWords.size());
            for (String word : negationWords) {
                oos.writeUTF(word);
            }
        }
    }

    @Override
    public void load(InputStream inputStream) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
            boolean loadedHandleNegation = ois.readBoolean();
            int negWordsCount = ois.readInt();
            Set<String> loadedNegWords = new HashSet<>();
            for (int i = 0; i < negWordsCount; i++) {
                loadedNegWords.add(ois.readUTF());
            }
            negationWords.clear();
            negationWords.addAll(loadedNegWords);
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("classifier", NAME);
        stats.put("dictionaryName", dictionary.getName());
        stats.put("dictionarySize", dictionary.size());
        stats.put("wordCount", dictionary.wordCount());
        stats.put("phraseCount", dictionary.phraseCount());
        stats.put("handleNegation", handleNegation);
        stats.put("negationWordsCount", negationWords.size());
        return stats;
    }

    /**
     * Добавляет слова отрицания.
     */
    public void addNegationWords(Collection<String> words) {
        negationWords.addAll(words);
    }

    /**
     * Возвращает словарь.
     */
    public SentimentDictionary getDictionary() {
        return dictionary;
    }
}
