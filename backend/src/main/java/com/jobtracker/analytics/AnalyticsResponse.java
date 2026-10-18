package com.jobtracker.analytics;

import com.jobtracker.application.ApplicationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AnalyticsResponse(
        long total,
        Map<ApplicationStatus, Long> byStatus,
        /** Percentage (0-100) of submitted applications that got any answer. */
        double responseRate,
        /** Percentage (0-100) of submitted applications that reached the interview stage. */
        double interviewRate,
        /** Average days between applying and the first answer; null until there is data. */
        Double avgDaysToResponse,
        /** Applications still waiting for a reply whose follow-up date has arrived. */
        long followUpsDue,
        List<WeekCount> weekly,
        List<FunnelStep> funnel) {

    public record WeekCount(LocalDate weekStart, long count) {}

    public record FunnelStep(String label, long count) {}
}
