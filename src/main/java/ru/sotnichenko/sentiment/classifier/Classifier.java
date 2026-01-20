package ru.sotnichenko.sentiment.classifier;

import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.SentimentResult;
import ru.sotnichenko.sentiment.model.TextDocument;

import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * Базовый интерфейс для классификаторов тональности.
 */
public interface Classifier {

    /**
     * Классифицирует документ.
     *
     * @param document предобработанный документ
     * @return результат классификации
     */
    SentimentResult classify(TextDocument document);

    /**
     * Классифицирует несколько документов.
     *
     * @param documents список документов
     * @return список результатов
     */
    default List<SentimentResult> classifyBatch(List<TextDocument> documents) {
        return documents.stream()
                .map(this::classify)
                .toList();
    }

    /**
     * Обучает классификатор на наборе данных.
     *
     * @param documents обучающие документы с метками
     */
    void train(List<TextDocument> documents);

    /**
     * Возвращает вероятности для каждого класса.
     *
     * @param document документ
     * @return карта класс -> вероятность
     */
    Map<Sentiment, Double> predictProbabilities(TextDocument document);

    /**
     * Проверяет, обучен ли классификатор.
     */
    boolean isTrained();

    /**
     * Возвращает имя классификатора.
     */
    String getName();

    /**
     * Сохраняет модель в поток.
     */
    void save(OutputStream outputStream) throws IOException;

    /**
     * Загружает модель из потока.
     */
    void load(InputStream inputStream) throws IOException;

    /**
     * Сохраняет модель в файл.
     */
    default void saveToFile(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            save(bos);
        }
    }

    /**
     * Загружает модель из файла.
     */
    default void loadFromFile(String path) throws IOException {
        try (FileInputStream fis = new FileInputStream(path);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            load(bis);
        }
    }

    /**
     * Возвращает статистику модели.
     */
    Map<String, Object> getStats();
}
