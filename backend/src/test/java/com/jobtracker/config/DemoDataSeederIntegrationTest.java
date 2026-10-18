package com.jobtracker.config;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtracker.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** The demo account is what recruiters will log into, so it must be there and look alive. */
class DemoDataSeederIntegrationTest extends AbstractIntegrationTest {

    private String demoToken() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", DemoDataSeeder.DEMO_EMAIL,
                                "password", DemoDataSeeder.DEMO_PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("token").asText();
    }

    @Test
    void demoAccountCanLogInAndHasData() throws Exception {
        String token = demoToken();

        mvc.perform(get("/api/applications").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(14)));
        mvc.perform(get("/api/analytics").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.total").value(14))
                .andExpect(jsonPath("$.responseRate", greaterThanOrEqualTo(30.0)))
                .andExpect(jsonPath("$.avgDaysToResponse").exists())
                .andExpect(jsonPath("$.followUpsDue", greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)));
    }
}
