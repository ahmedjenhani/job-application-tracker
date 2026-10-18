package com.jobtracker;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.ai.LlmClient;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * One Spring context and one real PostgreSQL container shared by all integration tests.
 * Each test registers its own fresh user, so tests never see each other's data.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=integration-test-secret-with-more-than-32-chars",
        "app.reminders.enabled=false",
        "app.mail.host=",
        "app.ai.gemini.api-key=",
        "app.ai.rate-limit.per-minute=1000",
        "app.ai.rate-limit.per-day=1000"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    private static final AtomicInteger USER_SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** The real Gemini client is never called from tests. */
    @MockitoBean
    protected LlmClient llm;

    /** Registers a new user and returns its bearer token. */
    protected String registerUser() throws Exception {
        return registerUser("user" + USER_SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.dev");
    }

    protected String registerUser(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", "Test User", "email", email, "password", "Password1!"));
        MockHttpServletRequestBuilder req = post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body);
        String response = mvc.perform(req).andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return node.path("token").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
