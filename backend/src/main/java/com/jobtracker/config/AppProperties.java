package com.jobtracker.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code app.*} block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Cors cors, Jwt jwt, Seed seed, Reminders reminders, Mail mail, Ai ai) {

    public record Cors(List<String> allowedOrigins) {}

    public record Jwt(String secret, int expirationHours) {}

    public record Seed(boolean enabled) {}

    public record Reminders(boolean enabled, String cron, String zone) {}

    public record Mail(String host, int port, String username, String password, String from) {}

    public record Ai(Gemini gemini, RateLimit rateLimit) {}

    public record Gemini(String apiKey, String model, String fallbackModel, String baseUrl, int timeoutSeconds) {}

    public record RateLimit(int perMinute, int perDay) {}
}
