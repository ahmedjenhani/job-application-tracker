package com.jobtracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.common.ApiExceptions.AiUnavailableException;
import java.util.Map;

/** Used when no API key is configured: AI endpoints answer 503 and the UI falls back to manual entry. */
public class DisabledLlmClient implements LlmClient {

    @Override
    public JsonNode generateJson(String systemInstruction, String userContent, Map<String, Object> responseSchema) {
        throw new AiUnavailableException("AI features are not configured on this server");
    }
}
