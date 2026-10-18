package com.jobtracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    LlmClient llmClient(AppProperties props, ObjectMapper mapper) {
        AppProperties.Gemini gemini = props.ai().gemini();
        if (gemini.apiKey() == null || gemini.apiKey().isBlank()) {
            log.warn("GEMINI_API_KEY is not set: AI features are disabled");
            return new DisabledLlmClient();
        }
        log.info("AI enabled with Gemini model '{}' (fallback '{}')", gemini.model(), gemini.fallbackModel());
        return new GeminiClient(gemini, mapper);
    }
}
