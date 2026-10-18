package com.jobtracker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtracker.AbstractIntegrationTest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class ReminderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReminderService reminders;

    @Autowired
    private JdbcTemplate jdbc;

    private long createApplication(String token, String status, LocalDate followUp) throws Exception {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("company", "Acme");
        p.put("role", "Dev");
        p.put("status", status);
        p.put("appliedDate", LocalDate.now(ZoneOffset.UTC).minusDays(10).toString());
        if (followUp != null) {
            p.put("followUpDate", followUp.toString());
        }
        String res = mvc.perform(post("/api/applications").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).path("id").asLong();
    }

    @Test
    void overdueFollowUpsProduceExactlyOneNotificationEach() throws Exception {
        String token = registerUser();
        createApplication(token, "APPLIED", LocalDate.now(ZoneOffset.UTC).minusDays(1));      // overdue -> reminder
        createApplication(token, "INTERVIEW", LocalDate.now(ZoneOffset.UTC));                  // due today -> reminder
        createApplication(token, "APPLIED", LocalDate.now(ZoneOffset.UTC).plusDays(3));        // future -> nothing
        createApplication(token, "REJECTED", LocalDate.now(ZoneOffset.UTC).minusDays(5));      // already answered -> nothing
        createApplication(token, "WISHLIST", null);                              // not applied -> nothing

        reminders.sendDueReminders(LocalDate.now(ZoneOffset.UTC));
        reminders.sendDueReminders(LocalDate.now(ZoneOffset.UTC)); // running again must not duplicate

        mvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("follow up")));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void remindersAreMarkedAsSentSoTheyFireOncePerFollowUpDate() throws Exception {
        String token = registerUser();
        long id = createApplication(token, "APPLIED", LocalDate.now(ZoneOffset.UTC).minusDays(1));

        reminders.sendDueReminders(LocalDate.now(ZoneOffset.UTC));

        assertThat(jdbc.queryForObject("select reminder_sent_at is not null from job_applications where id = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void notificationsCanBeMarkedReadAndAreIsolatedPerUser() throws Exception {
        String owner = registerUser();
        String other = registerUser();
        createApplication(owner, "APPLIED", LocalDate.now(ZoneOffset.UTC).minusDays(1));
        createApplication(owner, "APPLIED", LocalDate.now(ZoneOffset.UTC).minusDays(2));
        reminders.sendDueReminders(LocalDate.now(ZoneOffset.UTC));

        String list = mvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                .andReturn().getResponse().getContentAsString();
        long firstId = json.readTree(list).get(0).path("id").asLong();

        // someone else cannot touch it
        mvc.perform(post("/api/notifications/{id}/read", firstId).header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/notifications/{id}/read", firstId).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.count").value(0));
    }
}
