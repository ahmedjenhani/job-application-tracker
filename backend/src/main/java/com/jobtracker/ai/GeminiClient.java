package com.jobtracker.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.common.ApiExceptions.AiUnavailableException;
import com.jobtracker.common.ApiExceptions.RateLimitException;
import com.jobtracker.config.AppProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google Gemini (free tier) implementation. Tries the primary model first and falls back to a second
 * model when the first one is rate limited, retired or failing.
 */
public class GeminiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient http;
    private final ObjectMapper mapper;
    private final List<String> models = new ArrayList<>();

    public GeminiClient(AppProperties.Gemini config, ObjectMapper mapper) {
        this.mapper = mapper;
        models.add(config.model());
        if (config.fallbackModel() != null && !config.fallbackModel().isBlank()
                && !config.fallbackModel().equals(config.model())) {
            models.add(config.fallbackModel());
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        this.http = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .defaultHeader("x-goog-api-key", config.apiKey())
                .build();
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userContent, Map<String, Object> responseSchema) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userContent)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema,
                        "temperature", 0.2));

        boolean quotaExhausted = false;
        for (String model : models) {
            try {
                return callModel(model, body);
            } catch (RestClientResponseException e) {
                quotaExhausted |= e.getStatusCode().value() == 429;
                log.warn("Gemini model {} failed with HTTP {}", model, e.getStatusCode().value());
            } catch (RestClientException | JsonProcessingException | IllegalStateException e) {
                log.warn("Gemini model {} failed: {}", model, e.getMessage());
            }
        }
        if (quotaExhausted) {
            throw new RateLimitException("The free AI quota is used up for now. Please try again in a minute.", 60);
        }
        throw new AiUnavailableException("The AI service is not reachable right now. Please fill the form manually.");
    }

    private JsonNode callModel(String model, Map<String, Object> body) throws JsonProcessingException {
        JsonNode response = http.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        JsonNode text = response == null ? null : response.at("/candidates/0/content/parts/0/text");
        if (text == null || text.isMissingNode() || text.asText().isBlank()) {
            throw new IllegalStateException("empty response (possibly blocked by safety filters)");
        }
        return mapper.readTree(text.asText());
    }
}
