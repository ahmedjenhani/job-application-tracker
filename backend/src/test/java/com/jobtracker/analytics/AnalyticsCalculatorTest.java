package com.jobtracker.analytics;

import static com.jobtracker.application.ApplicationStatus.APPLIED;
import static com.jobtracker.application.ApplicationStatus.INTERVIEW;
import static com.jobtracker.application.ApplicationStatus.OFFER;
import static com.jobtracker.application.ApplicationStatus.REJECTED;
import static com.jobtracker.application.ApplicationStatus.WISHLIST;
import static org.assertj.core.api.Assertions.assertThat;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.JobApplication;
import com.jobtracker.application.StatusHistory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14); // a Wednesday

    private long nextId = 1;
    private final List<JobApplication> apps = new ArrayList<>();
    private final List<StatusHistory> history = new ArrayList<>();

    @Test
    void emptyAccountHasNoData() {
        AnalyticsResponse r = AnalyticsCalculator.compute(List.of(), List.of(), TODAY);

        assertThat(r.total()).isZero();
        assertThat(r.responseRate()).isZero();
        assertThat(r.interviewRate()).isZero();
        assertThat(r.avgDaysToResponse()).isNull();
        assertThat(r.weekly()).hasSize(12);
        assertThat(r.weekly()).allMatch(w -> w.count() == 0);
        assertThat(r.byStatus().values()).allMatch(c -> c == 0);
    }

    @Test
    void wishlistItemsAreNotCountedAsSubmitted() {
        app(WISHLIST, null, null, step(null, WISHLIST, "2026-10-01T10:00:00Z"));
        app(APPLIED, LocalDate.of(2026, 10, 1), null, step(null, APPLIED, "2026-10-01T10:00:00Z"));

        AnalyticsResponse r = AnalyticsCalculator.compute(apps, history, TODAY);

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.funnel().get(0).count()).isEqualTo(1);
        assertThat(r.responseRate()).isZero();
    }

    @Test
    void ratesAreComputedOverSubmittedApplications() {
        // 4 submitted: one rejected, one interview, one offer (via interview), one still waiting
        app(REJECTED, LocalDate.of(2026, 9, 1), null,
                step(null, APPLIED, "2026-09-01T12:00:00Z"), step(APPLIED, REJECTED, "2026-09-11T12:00:00Z"));
        app(INTERVIEW, LocalDate.of(2026, 9, 2), null,
                step(null, APPLIED, "2026-09-02T12:00:00Z"), step(APPLIED, INTERVIEW, "2026-09-06T12:00:00Z"));
        app(OFFER, LocalDate.of(2026, 9, 3), null,
                step(null, APPLIED, "2026-09-03T12:00:00Z"), step(APPLIED, INTERVIEW, "2026-09-05T12:00:00Z"),
                step(INTERVIEW, OFFER, "2026-09-20T12:00:00Z"));
        app(APPLIED, LocalDate.of(2026, 9, 4), null, step(null, APPLIED, "2026-09-04T12:00:00Z"));

        AnalyticsResponse r = AnalyticsCalculator.compute(apps, history, TODAY);

        assertThat(r.responseRate()).isEqualTo(75.0);   // 3 of 4 answered
        assertThat(r.interviewRate()).isEqualTo(50.0);  // 2 of 4 reached an interview
        assertThat(r.funnel()).extracting(f -> f.label() + ":" + f.count())
                .containsExactly("Applied:4", "Interview:2", "Offer:1");
        // days to first response: 10, 4 and 2 -> average 5.3
        assertThat(r.avgDaysToResponse()).isEqualTo(5.3);
    }

    @Test
    void rejectedAfterAnInterviewStillCountsAsInterviewed() {
        app(REJECTED, LocalDate.of(2026, 9, 1), null,
                step(null, APPLIED, "2026-09-01T12:00:00Z"), step(APPLIED, INTERVIEW, "2026-09-05T12:00:00Z"),
                step(INTERVIEW, REJECTED, "2026-09-12T12:00:00Z"));

        AnalyticsResponse r = AnalyticsCalculator.compute(apps, history, TODAY);

        assertThat(r.interviewRate()).isEqualTo(100.0);
        assertThat(r.avgDaysToResponse()).isEqualTo(4.0); // first response, not the final one
    }

    @Test
    void followUpsDueOnlyCountApplicationsStillWaiting() {
        app(APPLIED, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), step(null, APPLIED, "2026-10-01T12:00:00Z"));
        app(INTERVIEW, LocalDate.of(2026, 10, 1), TODAY, step(null, INTERVIEW, "2026-10-01T12:00:00Z"));
        app(APPLIED, LocalDate.of(2026, 10, 1), TODAY.plusDays(1), step(null, APPLIED, "2026-10-01T12:00:00Z"));
        app(REJECTED, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), step(null, REJECTED, "2026-10-01T12:00:00Z"));

        assertThat(AnalyticsCalculator.compute(apps, history, TODAY).followUpsDue()).isEqualTo(2);
    }

    @Test
    void weeklyCountsGroupByMondayAndCoverTwelveWeeks() {
        app(APPLIED, LocalDate.of(2026, 10, 12), null, step(null, APPLIED, "2026-10-12T12:00:00Z")); // Mon, this week
        app(APPLIED, LocalDate.of(2026, 10, 14), null, step(null, APPLIED, "2026-10-14T12:00:00Z")); // Wed, this week
        app(APPLIED, LocalDate.of(2026, 10, 11), null, step(null, APPLIED, "2026-10-11T12:00:00Z")); // Sun, last week
        app(APPLIED, LocalDate.of(2025, 1, 1), null, step(null, APPLIED, "2025-01-01T12:00:00Z"));   // outside window

        List<AnalyticsResponse.WeekCount> weekly = AnalyticsCalculator.compute(apps, history, TODAY).weekly();

        assertThat(weekly).hasSize(12);
        assertThat(weekly.get(11).weekStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(weekly.get(11).count()).isEqualTo(2);
        assertThat(weekly.get(10).weekStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(weekly.get(10).count()).isEqualTo(1);
        assertThat(weekly.stream().mapToLong(AnalyticsResponse.WeekCount::count).sum()).isEqualTo(3);
    }

    // ---- helpers ----

    private record Step(ApplicationStatus from, ApplicationStatus to, Instant at) {}

    private static Step step(ApplicationStatus from, ApplicationStatus to, String isoInstant) {
        return new Step(from, to, Instant.parse(isoInstant));
    }

    private void app(ApplicationStatus status, LocalDate applied, LocalDate followUp, Step... steps) {
        JobApplication a = new JobApplication();
        a.setId(nextId++);
        a.setStatus(status);
        a.setAppliedDate(applied);
        a.setFollowUpDate(followUp);
        apps.add(a);
        for (Step s : steps) {
            history.add(new StatusHistory(a, s.from(), s.to(), s.at()));
        }
    }
}
