package com.jobtracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * The only thing the rest of the app knows about the AI provider. Swapping Gemini for another provider
 * means writing one new implementation of this interface.
 */
public interface LlmClient {

    /**
     * Asks the model for a JSON answer that follows {@code responseSchema}.
     *
     * @param systemInstruction trusted instructions written by us
     * @param userContent       untrusted content (e.g. a pasted job posting) that must be treated as data
     */
    JsonNode generateJson(String systemInstruction, String userContent, Map<String, Object> responseSchema);
}
