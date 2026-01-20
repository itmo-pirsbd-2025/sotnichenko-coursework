package ru.sotnichenko.sentiment.api;

import ru.sotnichenko.lab4.routing.RequestHandler;
import ru.sotnichenko.lab4.server.HttpRequest;
import ru.sotnichenko.lab4.server.HttpResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Обработчик эндпоинта /api/stats.
 * Возвращает статистику сервиса анализа тональности.
 */
public class StatsHandler implements RequestHandler {

    private final SentimentService service;

    public StatsHandler(SentimentService service) {
        this.service = service;
    }

    @Override
    public CompletableFuture<HttpResponse> handle(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SentimentService.ServiceStats stats = service.getStats();
                return HttpResponse.ok(stats.toJson());
            } catch (Exception e) {
                return HttpResponse.internalError(
                        "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}"
                );
            }
        });
    }

    /**
     * Создаёт синхронный обработчик.
     */
    public static RequestHandler.SyncHandler sync(SentimentService service) {
        return request -> {
            try {
                SentimentService.ServiceStats stats = service.getStats();
                return HttpResponse.ok(stats.toJson());
            } catch (Exception e) {
                return HttpResponse.internalError(
                        "{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}"
                );
            }
        };
    }
}
