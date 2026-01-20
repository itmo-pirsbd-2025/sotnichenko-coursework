package ru.sotnichenko.sentiment;

import ru.sotnichenko.lab4.config.ServerConfig;
import ru.sotnichenko.lab4.handler.HealthHandler;
import ru.sotnichenko.lab4.handler.MetricsHandler;
import ru.sotnichenko.lab4.routing.Router;
import ru.sotnichenko.lab4.server.AsyncHttpServer;
import ru.sotnichenko.lab4.server.HttpResponse;
import ru.sotnichenko.lab4.routing.RequestHandler;
import ru.sotnichenko.sentiment.api.*;
import ru.sotnichenko.sentiment.classifier.*;
import ru.sotnichenko.sentiment.dictionary.EnglishSentimentDictionary;
import ru.sotnichenko.sentiment.dictionary.RussianSentimentDictionary;
import ru.sotnichenko.sentiment.model.Sentiment;
import ru.sotnichenko.sentiment.model.TextDocument;
import ru.sotnichenko.sentiment.preprocessing.TextPreprocessor;
import ru.sotnichenko.sentiment.training.DatasetLoader;
import ru.sotnichenko.sentiment.training.TrainingPipeline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Главный класс сервера анализа тональности.
 *
 * Использует асинхронный HTTP сервер из лабораторной работы 4
 * и ML классификаторы для анализа тональности текста.
 */
public class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Sentiment Analysis Server ===");
        System.out.println("Initializing...\n");

        // Парсим аргументы
        int port = getArg(args, "--port", DEFAULT_PORT);
        String modelPath = getArg(args, "--model", null);
        String dataPath = getArg(args, "--data", null);
        boolean useEnsemble = hasArg(args, "--ensemble");

        // Создаём препроцессор
        TextPreprocessor preprocessor = TextPreprocessor.builder()
                .removeStopWords(true)
                .applyStemming(true)
                .normalizeRepeatedChars(true)
                .build();

        // Создаём или загружаем классификатор
        Classifier classifier = createClassifier(preprocessor, modelPath, dataPath, useEnsemble);

        // Создаём сервис
        SentimentService service = SentimentService.builder(classifier)
                .preprocessor(preprocessor)
                .cacheSize(10000)
                .cacheTtlMs(300000) // 5 минут
                .build();

        // Настраиваем сервер
        ServerConfig config = new ServerConfig()
                .port(port)
                .workerThreads(Runtime.getRuntime().availableProcessors());

        // Создаём роутер
        Router router = new Router();

        // Эндпоинты анализа тональности
        router.get("/api/analyze", AnalyzeHandler.sync(service));
        router.post("/api/analyze", AnalyzeHandler.sync(service));
        router.post("/api/analyze/batch", BatchAnalyzeHandler.sync(service));
        router.get("/api/stats", StatsHandler.sync(service));

        // Стандартные эндпоинты
        router.get("/health", HealthHandler.sync());
        router.get("/", (RequestHandler.SyncHandler) req ->
                HttpResponse.ok(buildWelcomeJson()));

        // Запускаем сервер
        AsyncHttpServer server = new AsyncHttpServer(config, router);
        router.get("/metrics", MetricsHandler.sync(server.getMetrics()));

        server.start();

        System.out.println("\nServer started on port " + port);
        System.out.println("\nAvailable endpoints:");
        System.out.println("  GET  /                    - Welcome message");
        System.out.println("  GET  /health              - Health check");
        System.out.println("  GET  /metrics             - Server metrics");
        System.out.println("  GET  /api/analyze?text=   - Analyze text (query param)");
        System.out.println("  POST /api/analyze         - Analyze text (JSON body)");
        System.out.println("  POST /api/analyze/batch   - Analyze multiple texts");
        System.out.println("  GET  /api/stats           - Service statistics");
        System.out.println("\nPress Enter to stop...");

        // Ждём ввода для остановки
        new BufferedReader(new InputStreamReader(System.in)).readLine();

        service.shutdown();
        server.stop();
        System.out.println("Server stopped.");
    }

    /**
     * Создаёт классификатор.
     */
    private static Classifier createClassifier(TextPreprocessor preprocessor,
                                               String modelPath,
                                               String dataPath,
                                               boolean useEnsemble) throws Exception {
        // Если указан путь к модели, загружаем
        if (modelPath != null) {
            System.out.println("Loading model from: " + modelPath);
            NaiveBayesClassifier classifier = new NaiveBayesClassifier();
            classifier.loadFromFile(modelPath);
            System.out.println("Model loaded successfully.");
            return classifier;
        }

        // Если указан путь к данным, обучаем
        if (dataPath != null) {
            System.out.println("Training model from data: " + dataPath);
            TrainingPipeline pipeline = TrainingPipeline.create(preprocessor);
            pipeline.loadDataset(dataPath, 0.8);

            if (useEnsemble) {
                pipeline.trainNaiveBayes()
                        .trainLogisticRegression()
                        .addRussianDictionaryClassifier()
                        .addEnglishDictionaryClassifier()
                        .createEnsemble(EnsembleClassifier.VotingStrategy.SOFT);

                pipeline.evaluate();
                return pipeline.getClassifier("Ensemble");
            } else {
                pipeline.trainNaiveBayes();
                pipeline.evaluate();
                return pipeline.getClassifier("NaiveBayes");
            }
        }

        // По умолчанию создаём словарный классификатор + обучаем на демо-данных
        System.out.println("No model or data specified. Creating default classifier...");

        // Создаём демо-данные для обучения
        List<TextDocument> demoData = createDemoTrainingData(preprocessor);

        // Обучаем Naive Bayes
        NaiveBayesClassifier nbClassifier = new NaiveBayesClassifier();
        nbClassifier.train(demoData);

        if (useEnsemble) {
            // Создаём ансамбль
            EnsembleClassifier ensemble = EnsembleClassifier.builder()
                    .add(nbClassifier, 1.0)
                    .add(new DictionaryClassifier(RussianSentimentDictionary.extended()), 0.5)
                    .add(new DictionaryClassifier(EnglishSentimentDictionary.extended()), 0.5)
                    .votingStrategy(EnsembleClassifier.VotingStrategy.SOFT)
                    .build();

            System.out.println("Ensemble classifier created with " + ensemble.getClassifierCount() + " models.");
            return ensemble;
        }

        System.out.println("Naive Bayes classifier trained on " + demoData.size() + " examples.");
        return nbClassifier;
    }

    /**
     * Создаёт демонстрационные данные для обучения.
     */
    private static List<TextDocument> createDemoTrainingData(TextPreprocessor preprocessor) {
        List<TextDocument> data = new ArrayList<>();

        // Позитивные примеры (русский)
        addExample(data, preprocessor, "Отличный продукт, очень доволен покупкой!", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Замечательный сервис, рекомендую всем!", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Прекрасное качество, превзошло ожидания", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Всё супер, буду заказывать ещё", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Великолепно! Лучшее что я пробовал", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Очень понравилось, спасибо за отличную работу", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Быстрая доставка, качественный товар", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Идеально подошло, счастлив покупкой", Sentiment.POSITIVE);

        // Негативные примеры (русский)
        addExample(data, preprocessor, "Ужасное качество, полное разочарование", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Не работает, деньги на ветер", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Очень плохо, не рекомендую никому", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Кошмар, худшая покупка в моей жизни", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Отвратительный сервис, никогда больше", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Сломалось через день, полный брак", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Обман, не соответствует описанию", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Жалею о покупке, выброшенные деньги", Sentiment.NEGATIVE);

        // Нейтральные примеры (русский)
        addExample(data, preprocessor, "Обычный товар, ничего особенного", Sentiment.NEUTRAL);
        addExample(data, preprocessor, "Нормально, соответствует цене", Sentiment.NEUTRAL);
        addExample(data, preprocessor, "Средне, есть и плюсы и минусы", Sentiment.NEUTRAL);
        addExample(data, preprocessor, "Ожидал большего, но в целом сойдёт", Sentiment.NEUTRAL);

        // Позитивные примеры (английский)
        addExample(data, preprocessor, "Excellent product, highly recommend!", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Amazing quality, exceeded expectations", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Love it! Best purchase ever", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Great service, very satisfied", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Wonderful experience, will buy again", Sentiment.POSITIVE);
        addExample(data, preprocessor, "Perfect! Exactly what I needed", Sentiment.POSITIVE);

        // Негативные примеры (английский)
        addExample(data, preprocessor, "Terrible quality, complete waste of money", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Horrible experience, never again", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Doesn't work at all, very disappointed", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Worst purchase ever, total scam", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Broken on arrival, awful product", Sentiment.NEGATIVE);
        addExample(data, preprocessor, "Do not buy, complete garbage", Sentiment.NEGATIVE);

        // Нейтральные примеры (английский)
        addExample(data, preprocessor, "Average product, nothing special", Sentiment.NEUTRAL);
        addExample(data, preprocessor, "It's okay, gets the job done", Sentiment.NEUTRAL);
        addExample(data, preprocessor, "Fair quality for the price", Sentiment.NEUTRAL);
        addExample(data, preprocessor, "Neither good nor bad, just meh", Sentiment.NEUTRAL);

        return data;
    }

    private static void addExample(List<TextDocument> data, TextPreprocessor preprocessor,
                                   String text, Sentiment label) {
        TextDocument doc = preprocessor.process(text);
        data.add(doc.withLabel(label));
    }

    private static String buildWelcomeJson() {
        return "{" +
               "\"service\":\"Sentiment Analysis Server\"," +
               "\"version\":\"1.0.0\"," +
               "\"status\":\"running\"," +
               "\"endpoints\":{" +
               "\"analyze\":\"/api/analyze\"," +
               "\"batch\":\"/api/analyze/batch\"," +
               "\"stats\":\"/api/stats\"," +
               "\"health\":\"/health\"," +
               "\"metrics\":\"/metrics\"" +
               "}" +
               "}";
    }

    private static int getArg(String[] args, String name, int defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private static String getArg(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static boolean hasArg(String[] args, String name) {
        for (String arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
