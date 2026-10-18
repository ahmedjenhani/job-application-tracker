package com.jobtracker.analytics;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtracker.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private long create(String token, String status) throws Exception {
        String res = mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("company", "C", "role", "R", "status", status))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("id").asLong();
    }

    @Test
    void brandNewAccountHasEmptyButValidAnalytics() throws Exception {
        String token = registerUser();

        mvc.perform(get("/api/analytics").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.responseRate").value(0.0))
                .andExpect(jsonPath("$.weekly", hasSize(12)))
                .andExpect(jsonPath("$.funnel", hasSize(3)));
    }

    @Test
    void numbersFollowTheUsersApplications() throws Exception {
        String token = registerUser();
        long a = create(token, "APPLIED");
        create(token, "APPLIED");
        create(token, "WISHLIST");
        long d = create(token, "APPLIED");
        mvc.perform(patch("/api/applications/{id}/status", a).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"));
        mvc.perform(patch("/api/applications/{id}/status", d).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"));

        mvc.perform(get("/api/analytics").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.byStatus.APPLIED").value(1))
                .andExpect(jsonPath("$.byStatus.INTERVIEW").value(1))
                .andExpect(jsonPath("$.byStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.byStatus.WISHLIST").value(1))
                .andExpect(jsonPath("$.responseRate").value(66.7))   // 2 of 3 submitted
                .andExpect(jsonPath("$.interviewRate").value(33.3))
                .andExpect(jsonPath("$.weekly[11].count").value(3)); // 3 applied this week
    }

    @Test
    void analyticsOnlyCoverTheCallersData() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        create(alice, "APPLIED");
        create(alice, "APPLIED");

        mvc.perform(get("/api/analytics").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.total").value(0));
    }
}
