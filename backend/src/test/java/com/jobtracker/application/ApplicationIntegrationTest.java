package com.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtracker.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, Object> payload(String company, String role, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("company", company);
        m.put("role", role);
        m.put("status", status);
        return m;
    }

    private long create(String token, Map<String, Object> payload) throws Exception {
        String res = mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("id").asLong();
    }

    @Test
    void creatingAnAppliedApplicationFillsInSensibleDates() throws Exception {
        String token = registerUser();

        mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload("Acme", "Java Dev", "APPLIED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedDate").value(LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.followUpDate").value(LocalDate.now(ZoneOffset.UTC).plusDays(7).toString()));
    }

    @Test
    void wishlistItemsGetNoDates() throws Exception {
        String token = registerUser();

        mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload("Acme", "Java Dev", "WISHLIST"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedDate").doesNotExist())
                .andExpect(jsonPath("$.followUpDate").doesNotExist());
    }

    @Test
    void listSupportsStatusFilterAndTextSearch() throws Exception {
        String token = registerUser();
        create(token, payload("Alpha Corp", "Java Developer", "APPLIED"));
        create(token, payload("Beta Labs", "Angular Developer", "INTERVIEW"));
        create(token, payload("Gamma", "DevOps", "WISHLIST"));

        mvc.perform(get("/api/applications").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/applications?status=INTERVIEW").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].company").value("Beta Labs"));
        mvc.perform(get("/api/applications?q=angular").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].company").value("Beta Labs"));
        mvc.perform(get("/api/applications?q=ALPHA&status=APPLIED").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/applications?q=nothing-matches").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void changingStatusRecordsHistory() throws Exception {
        String token = registerUser();
        long id = create(token, payload("Acme", "Dev", "APPLIED"));

        mvc.perform(patch("/api/applications/{id}/status", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERVIEW"));
        // same status again must not create a duplicate history row
        mvc.perform(patch("/api/applications/{id}/status", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/applications/{id}/history", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].to").value("APPLIED"))
                .andExpect(jsonPath("$[1].from").value("APPLIED"))
                .andExpect(jsonPath("$[1].to").value("INTERVIEW"));
    }

    @Test
    void updateReplacesFieldsAndResetsReminderWhenFollowUpChanges() throws Exception {
        String token = registerUser();
        Map<String, Object> p = payload("Acme", "Dev", "APPLIED");
        p.put("appliedDate", LocalDate.now(ZoneOffset.UTC).minusDays(10).toString());
        p.put("followUpDate", LocalDate.now(ZoneOffset.UTC).minusDays(3).toString());
        long id = create(token, p);
        jdbc.update("update job_applications set reminder_sent_at = now() where id = ?", id);

        // same follow-up date: reminder state is kept
        p.put("notes", "called HR");
        mvc.perform(put("/api/applications/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes").value("called HR"));
        assertThat(jdbc.queryForObject("select reminder_sent_at is not null from job_applications where id = ?",
                Boolean.class, id)).isTrue();

        // new follow-up date: a new reminder will be sent
        p.put("followUpDate", LocalDate.now(ZoneOffset.UTC).plusDays(5).toString());
        mvc.perform(put("/api/applications/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select reminder_sent_at is null from job_applications where id = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void skillsAreStoredAsAList() throws Exception {
        String token = registerUser();
        Map<String, Object> p = payload("Acme", "Dev", "WISHLIST");
        p.put("skills", List.of("Java", "Spring Boot", "Java", " Docker "));
        long id = create(token, p);

        mvc.perform(get("/api/applications/{id}", id).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.skills", hasSize(3)))
                .andExpect(jsonPath("$.skills[2]").value("Docker"));
    }

    @Test
    void usersCannotSeeOrModifyEachOthersApplications() throws Exception {
        String alice = registerUser();
        String bob = registerUser();
        long id = create(alice, payload("Secret Co", "Dev", "APPLIED"));

        mvc.perform(get("/api/applications/{id}", id).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/applications").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(put("/api/applications/{id}", id).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload("Hacked", "Dev", "REJECTED"))))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/applications/{id}/status", id).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/applications/{id}", id).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/{id}/history", id).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/applications/{id}", id).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.company").value("Secret Co"));
    }

    @Test
    void deleteRemovesTheApplicationAndItsHistory() throws Exception {
        String token = registerUser();
        long id = create(token, payload("Acme", "Dev", "APPLIED"));

        mvc.perform(delete("/api/applications/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/applications/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from status_history where application_id = ?",
                Long.class, id)).isZero();
    }

    @Test
    void validationRejectsBadInput() throws Exception {
        String token = registerUser();
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("company", " ");
        bad.put("status", "APPLIED");

        mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.company").exists())
                .andExpect(jsonPath("$.errors.role").exists());
        mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"A\",\"role\":\"B\",\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest());
    }
}
