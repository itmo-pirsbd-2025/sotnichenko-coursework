package ru.sotnichenko.sentiment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Утилита для нагрузочного тестирования сервера анализа тональности.
 */
public class LoadTester {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_THREADS = 10;
    private static final int DEFAULT_REQUESTS = 1000;
    private static final int DEFAULT_WARMUP = 100;

    // Тестовые тексты для нагрузки
    private static final String[] POSITIVE_TEXTS = {
            "This product is absolutely amazing and wonderful!",
            "Excellent quality, highly recommend to everyone!",
            "Best purchase I've ever made, love it!",
            "Fantastic service, very satisfied customer!",
            "Отличный товар, всем рекомендую!",
            "Превосходное качество, очень доволен!",
            "Замечательный сервис, буду заказывать ещё!"
    };

    private static final String[] NEGATIVE_TEXTS = {
            "Terrible quality, complete waste of money!",
            "Worst experience ever, never buying again!",
            "Horrible product, totally disappointed!",
            "Awful service, do not recommend at all!",
            "Ужасное качество, деньги на ветер!",
            "Худшая покупка в моей жизни!",
            "Отвратительный сервис, никому не советую!"
    };

    private static final String[] NEUTRAL_TEXTS = {
            "Average product, nothing special about it.",
            "It's okay, does what it's supposed to do.",
            "Normal quality for this price range.",
            "Обычный товар, ничего особенного.",
            "Нормально, соответствует цене.",
            "Средне, есть и плюсы и минусы."
    };

    public static void main(String[] args) throws Exception {
        int port = getArg(args, "--port", DEFAULT_PORT);
        int threads = getArg(args, "--threads", DEFAULT_THREADS);
        int requests = getArg(args, "--requests", DEFAULT_REQUESTS);
        int warmup = getArg(args, "--warmup", DEFAULT_WARMUP);
        String endpoint = getArg(args, "--endpoint", "/api/analyze");
        boolean batchMode = hasArg(args, "--batch");

        System.out.println("=== Sentiment Analysis Server Load Tester ===");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Target: http://localhost:" + port + endpoint);
        System.out.println("  Threads: " + threads);
        System.out.println("  Requests: " + requests);
        System.out.println("  Warmup: " + warmup);
        System.out.println("  Mode: " + (batchMode ? "batch" : "single"));
        System.out.println();

        String baseUrl = "http://localhost:" + port;

        // Warmup
        System.out.println("--- Warmup Phase ---");
        if (batchMode) {
            runBatchRequests(baseUrl + "/api/analyze/batch", 2, warmup, false);
        } else {
            runSingleRequests(baseUrl + endpoint, 2, warmup, false);
        }

        // Main test
        System.out.println("\n--- Main Test Phase ---");
        if (batchMode) {
            runBatchRequests(baseUrl + "/api/analyze/batch", threads, requests, true);
        } else {
            runSingleRequests(baseUrl + endpoint, threads, requests, true);
        }

        // Test different endpoints
        System.out.println("\n--- Additional Tests ---");

        System.out.println("\nGET /api/analyze with query param:");
        runGetRequests(baseUrl + "/api/analyze", threads, requests / 2, true);

        System.out.println("\nGET /api/stats:");
        runGetRequests(baseUrl + "/api/stats", 2, 100, true);

        System.out.println("\n=== Load Test Complete ===");
    }

    /**
     * Выполняет POST запросы с одним текстом.
     */
    private static void runSingleRequests(String url, int threads, int totalRequests,
                                          boolean printStats) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Random random = new Random();
        String[] allTexts = concat(POSITIVE_TEXTS, NEGATIVE_TEXTS, NEUTRAL_TEXTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final String text = allTexts[random.nextInt(allTexts.length)];

            executor.submit(() -> {
                long reqStart = System.nanoTime();
                try {
                    String body = "{\"text\":\"" + escapeJson(text) + "\"}";
                    int code = postJson(url, body);

                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    totalTime.addAndGet(System.nanoTime() - reqStart);
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;

        if (printStats) {
            printStats("POST /api/analyze", totalRequests, successCount.get(),
                    errorCount.get(), elapsed, totalTime.get());
        }
    }

    /**
     * Выполняет batch POST запросы.
     */
    private static void runBatchRequests(String url, int threads, int totalRequests,
                                         boolean printStats) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Random random = new Random();
        String[] allTexts = concat(POSITIVE_TEXTS, NEGATIVE_TEXTS, NEUTRAL_TEXTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            // Генерируем batch из 5-10 текстов
            int batchSize = 5 + random.nextInt(6);
            StringBuilder jsonArray = new StringBuilder("[");
            for (int j = 0; j < batchSize; j++) {
                if (j > 0) jsonArray.append(",");
                jsonArray.append("\"").append(escapeJson(allTexts[random.nextInt(allTexts.length)])).append("\"");
            }
            jsonArray.append("]");

            final String body = jsonArray.toString();

            executor.submit(() -> {
                long reqStart = System.nanoTime();
                try {
                    int code = postJson(url, body);

                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    totalTime.addAndGet(System.nanoTime() - reqStart);
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;

        if (printStats) {
            printStats("POST /api/analyze/batch", totalRequests, successCount.get(),
                    errorCount.get(), elapsed, totalTime.get());
        }
    }

    /**
     * Выполняет GET запросы.
     */
    private static void runGetRequests(String baseUrl, int threads, int totalRequests,
                                       boolean printStats) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        Random random = new Random();
        String[] allTexts = concat(POSITIVE_TEXTS, NEGATIVE_TEXTS, NEUTRAL_TEXTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final String text = allTexts[random.nextInt(allTexts.length)];
            final String url = baseUrl.contains("?") ? baseUrl :
                    baseUrl + "?text=" + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);

            executor.submit(() -> {
                long reqStart = System.nanoTime();
                try {
                    int code = getRequest(url);

                    if (code == 200) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    totalTime.addAndGet(System.nanoTime() - reqStart);
                    latch.countDown();
                }
            });
        }

        latch.await(120, TimeUnit.SECONDS);
        executor.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;

        if (printStats) {
            printStats("GET " + baseUrl.substring(baseUrl.lastIndexOf('/')),
                    totalRequests, successCount.get(), errorCount.get(), elapsed, totalTime.get());
        }
    }

    /**
     * Выполняет POST запрос с JSON.
     */
    private static int postJson(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();

        // Читаем ответ
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(code >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
            while (reader.readLine() != null) {
                // consume
            }
        }

        conn.disconnect();
        return code;
    }

    /**
     * Выполняет GET запрос.
     */
    private static int getRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(code >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
            while (reader.readLine() != null) {
                // consume
            }
        }

        conn.disconnect();
        return code;
    }

    /**
     * Печатает статистику.
     */
    private static void printStats(String testName, int total, int success, int errors,
                                   long elapsedMs, long totalTimeNanos) {
        double rps = total * 1000.0 / elapsedMs;
        double avgLatencyMs = totalTimeNanos / 1_000_000.0 / total;
        double successRate = success * 100.0 / total;

        System.out.println("\n" + testName + ":");
        System.out.printf("  Requests:     %d total, %d success, %d errors%n", total, success, errors);
        System.out.printf("  Success rate: %.2f%%%n", successRate);
        System.out.printf("  Duration:     %d ms%n", elapsedMs);
        System.out.printf("  Throughput:   %.2f req/sec%n", rps);
        System.out.printf("  Avg latency:  %.3f ms%n", avgLatencyMs);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String[] concat(String[]... arrays) {
        List<String> result = new ArrayList<>();
        for (String[] arr : arrays) {
            for (String s : arr) {
                result.add(s);
            }
        }
        return result.toArray(new String[0]);
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
