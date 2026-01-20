package ru.sotnichenko.sentiment.api;

import ru.sotnichenko.lab4.json.JsonParser;
import ru.sotnichenko.lab4.routing.RequestHandler;
import ru.sotnichenko.lab4.server.HttpRequest;
import ru.sotnichenko.lab4.server.HttpResponse;
import ru.sotnichenko.sentiment.model.SentimentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Обработчик эндпоинта /api/analyze/batch.
 * Анализирует тональность нескольких текстов.
 */
public class BatchAnalyzeHandler implements RequestHandler {

    private static final int MAX_BATCH_SIZE = 100;

    private final SentimentService service;

    public BatchAnalyzeHandler(SentimentService service) {
        this.service = service;
    }

    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        return service.analyzeBatchAsync(extractTexts(request))
                .thenApply(this::buildResponse)
                .exceptionally(e -> HttpResponse.internalError(
                        "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"
                ));
    }

    /**
     * Извлекает список текстов из запроса.
     */
    private List<String> extractTexts(HttpRequest request) {
        String body = request.getBodyAsString();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        JsonParser parser = new JsonParser();
        try {
            // Пробуем парсить как массив
            List<Object> array = parser.parseArray(body);
            List<String> texts = new ArrayList<>();

            for (Object item : array) {
                if (item instanceof String str) {
                    texts.add(str);
                } else if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) item;
                    Object textObj = map.get("text");
                    if (textObj != null) {
                        texts.add(textObj.toString());
                    }
                }
            }

            // Ограничиваем размер батча
            if (texts.size() > MAX_BATCH_SIZE) {
                texts = texts.subList(0, MAX_BATCH_SIZE);
            }

            return texts;

        } catch (Exception e) {
            // Пробуем парсить как объект с полем "texts"
            try {
                Map<String, Object> json = parser.parseObject(body);
                Object textsObj = json.get("texts");

                if (textsObj instanceof List<?> list) {
                    List<String> texts = new ArrayList<>();
                    for (Object item : list) {
                        if (item != null) {
                            texts.add(item.toString());
                        }
                    }

                    if (texts.size() > MAX_BATCH_SIZE) {
                        texts = texts.subList(0, MAX_BATCH_SIZE);
                    }

                    return texts;
                }
            } catch (Exception ignored) {
            }
        }

        return List.of();
    }

    /**
     * Формирует ответ.
     */
    private HttpResponse buildResponse(List<SentimentResult> results) {
        StringBuilder resultsJson = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) resultsJson.append(",");
            resultsJson.append(results.get(i).toJson());
        }
        resultsJson.append("]");

        String json = "{\"count\":" + results.size() + ",\"results\":" + resultsJson + "}";

        return HttpResponse.ok(json);
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
                List<String> texts = extractTextsSync(request);

                if (texts.isEmpty()) {
                    return HttpResponse.badRequest(
                            "{\"error\":\"Texts array is required. Send JSON: [\\\"text1\\\", \\\"text2\\\"] or {\\\"texts\\\":[...]}\"}"
                    );
                }

                List<SentimentResult> results = service.analyzeBatch(texts);

                StringBuilder resultsJson = new StringBuilder("[");
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) resultsJson.append(",");
                    resultsJson.append(results.get(i).toJson());
                }
                resultsJson.append("]");

                String json = "{\"count\":" + results.size() + ",\"results\":" + resultsJson + "}";

                return HttpResponse.ok(json);

            } catch (Exception e) {
                return HttpResponse.internalError(
                        "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}"
                );
            }
        };
    }

    private static List<String> extractTextsSync(HttpRequest request) {
        String body = request.getBodyAsString();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        JsonParser parser = new JsonParser();
        try {
            List<Object> array = parser.parseArray(body);
            List<String> texts = new ArrayList<>();

            for (Object item : array) {
                if (item instanceof String str) {
                    texts.add(str);
                } else if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) item;
                    Object textObj = map.get("text");
                    if (textObj != null) {
                        texts.add(textObj.toString());
                    }
                }
            }

            if (texts.size() > MAX_BATCH_SIZE) {
                texts = texts.subList(0, MAX_BATCH_SIZE);
            }

            return texts;

        } catch (Exception e) {
            try {
                Map<String, Object> json = parser.parseObject(body);
                Object textsObj = json.get("texts");

                if (textsObj instanceof List<?> list) {
                    List<String> texts = new ArrayList<>();
                    for (Object item : list) {
                        if (item != null) {
                            texts.add(item.toString());
                        }
                    }

                    if (texts.size() > MAX_BATCH_SIZE) {
                        texts = texts.subList(0, MAX_BATCH_SIZE);
                    }

                    return texts;
                }
            } catch (Exception ignored) {
            }
        }

        return List.of();
    }
}
