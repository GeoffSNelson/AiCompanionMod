package com.nelson.aicompanion.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("AiCompanionMod");
    private static final Duration ACTION_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(5);
    private final HttpClient httpClient;
    private final Gson gson;
    private String apiBaseUrl;

    public AiClient(int port) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
        this.apiBaseUrl = "http://127.0.0.1:" + port;
    }

    /**
     * Sends the current state of the NPC/Player to the AI engine to get the next action.
     */
    public CompletableFuture<JsonObject> sendContextAndGetAction(JsonObject contextData) {
        String jsonPayload = gson.toJson(contextData);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/npc/action"))
                .header("Content-Type", "application/json")
                .timeout(ACTION_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), JsonObject.class);
                    } else {
                        LOGGER.error("Failed to get action from AI. Status Code: " + response.statusCode());
                        return new JsonObject();
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error communicating with AI engine: ", ex);
                    return new JsonObject();
                });
    }

    /**
     * A helper method to send chat messages spoken by the player to the AI,
     * and optionally receive a conversational response.
     */
    public CompletableFuture<JsonObject> sendChatMessage(String playerName, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player", playerName);
        payload.addProperty("message", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/npc/chat"))
                .header("Content-Type", "application/json")
                .timeout(CHAT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), JsonObject.class);
                    } else {
                        LOGGER.error("Failed to send chat to AI Engine. Status: " + response.statusCode());
                        return new JsonObject();
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error communicating chat to AI engine: ", ex);
                    return new JsonObject();
                });
    }

    /**
     * Sends an in-game event (found diamonds, build complete, low health, etc.)
     * to the AI engine and receives a broadcast reply.
     */
    public CompletableFuture<JsonObject> sendEvent(JsonObject payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl + "/api/npc/event"))
                .header("Content-Type", "application/json")
                .timeout(EVENT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return gson.fromJson(response.body(), JsonObject.class);
                    }
                    return new JsonObject();
                })
                .exceptionally(ex -> new JsonObject());
    }
}
