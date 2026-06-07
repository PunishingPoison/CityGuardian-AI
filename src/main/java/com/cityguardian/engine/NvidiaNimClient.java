package com.cityguardian.engine;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class NvidiaNimClient {
    private static final String API_URL = "https://integrate.api.nvidia.com/v1/chat/completions";

    public static OptimizationResult optimizeResources(String apiKey, String promptText) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return new OptimizationResult(3, 3, 3, "No NVIDIA_API_KEY provided. Using default 3 of each.");
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            Gson gson = new Gson();

            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", promptText);

            JsonArray messages = new JsonArray();
            messages.add(message);

            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", "meta/llama-3.1-8b-instruct");
            requestBody.add("messages", messages);
            requestBody.addProperty("temperature", 0.2);
            requestBody.addProperty("top_p", 0.7);
            requestBody.addProperty("max_tokens", 1024);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new OptimizationResult(3, 3, 3, "API Error: " + response.statusCode() + " - " + response.body());
            }

            JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            String aiContent = jsonResponse.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            return parseLLMResponse(aiContent);

        } catch (Exception e) {
            e.printStackTrace();
            return new OptimizationResult(3, 3, 3, "Error communicating with AI: " + e.getMessage());
        }
    }

    private static OptimizationResult parseLLMResponse(String content) {
        int ft = 3;
        int amb = 3;
        int heli = 3;

        // Very basic parsing expecting something like "Firetrucks: 5", "Ambulances: 10"
        String[] lines = content.split("\n");
        for (String line : lines) {
            String lower = line.toLowerCase();
            try {
                if (lower.contains("firetruck")) {
                    String val = line.replaceAll("[^0-9]", "");
                    if (!val.isEmpty()) ft = Integer.parseInt(val);
                } else if (lower.contains("ambulance")) {
                    String val = line.replaceAll("[^0-9]", "");
                    if (!val.isEmpty()) amb = Integer.parseInt(val);
                } else if (lower.contains("helicopter")) {
                    String val = line.replaceAll("[^0-9]", "");
                    if (!val.isEmpty()) heli = Integer.parseInt(val);
                }
            } catch (Exception e) {}
        }
        
        // Prevent huge numbers just in case parser gets confused by explanations
        if (ft > 30) ft = 15;
        if (amb > 30) amb = 15;
        if (heli > 30) heli = 15;

        return new OptimizationResult(ft, amb, heli, content);
    }
}
