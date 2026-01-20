package ru.sotnichenko.sentiment.api;

import ru.sotnichenko.lab4.json.JsonParser;
import ru.sotnichenko.lab4.routing.RequestHandler;
import ru.sotnichenko.lab4.server.HttpRequest;
import ru.sotnichenko.lab4.server.HttpResponse;
import ru.sotnichenko.sentiment.model.SentimentResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Обработчик эндпоинта /api/analyze.
 * Анализирует тональность текста.
 */
public class AnalyzeHandler implements RequestHandler {

    private final SentimentService service;

    public AnalyzeHandler(SentimentService service) {
        this.service = service;
    }

    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Получаем текст из запроса
                String text = extractText(request);

                if (text == null || text.isBlank()) {
                    return HttpResponse.badRequest(
                            "{\"error\":\"Text is required. Use query param 'text' or JSON body {\\\"text\\\":\\\"...\\\"}\"}"
                    );
                }

                // Анализируем
                SentimentResult result = service.analyze(text);

                // Формируем ответ
                return HttpResponse.ok(result.toJson());

            } catch (Exception e) {
                return HttpResponse.internalError(
                        "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
                );
            }
        });
    }

    /**
     * Извлекает текст из запроса.
     */
    private String extractText(HttpRequest request) {
        // Сначала проверяем query параметр
        String text = request.getQueryParam("text");
        if (text != null && !text.isBlank()) {
            return text;
        }

        // Затем проверяем тело запроса
        String body = request.getBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                Map<String, Object> json = new JsonParser().parseObject(body);
                Object textObj = json.get("text");
                if (textObj != null) {
                    return textObj.toString();
                }
            } catch (Exception ignored) {
                // Если не JSON, используем тело как текст
                return body;
            }
        }

        return null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Создаёт синхронный обработчик.
     */
    public static RequestHandler.SyncHandler sync(SentimentService service) {
        return request -> {
            try {
                String text = extractTextSync(request);

                if (text == null || text.isBlank()) {
                    return HttpResponse.badRequest(
                            "{\"error\":\"Text is required. Use query param 'text' or JSON body {\\\"text\\\":\\\"...\\\"}\"}"
                    );
                }

                SentimentResult result = service.analyze(text);
                return HttpResponse.ok(result.toJson());

            } catch (Exception e) {
                return HttpResponse.internalError(
                        "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}"
                );
            }
        };
    }

    private static String extractTextSync(HttpRequest request) {
        String text = request.getQueryParam("text");
        if (text != null && !text.isBlank()) {
            return text;
        }

        String body = request.getBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                Map<String, Object> json = new JsonParser().parseObject(body);
                Object textObj = json.get("text");
                if (textObj != null) {
                    return textObj.toString();
                }
            } catch (Exception ignored) {
                return body;
            }
        }

        return null;
    }
}
