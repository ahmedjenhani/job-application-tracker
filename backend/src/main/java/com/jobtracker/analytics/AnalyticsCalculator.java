package com.jobtracker.analytics;

import com.jobtracker.analytics.AnalyticsResponse.FunnelStep;
import com.jobtracker.analytics.AnalyticsResponse.WeekCount;
import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.JobApplication;
import com.jobtracker.application.StatusHistory;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure computation of the dashboard numbers, kept free of I/O so it is trivial to unit test.
 *
 * <p>Definitions: an application is "submitted" once it has left the wishlist; it "got a response" once it
 * is (or was) in INTERVIEW, OFFER or REJECTED.
 */
public final class AnalyticsCalculator {

    static final int WEEKS_SHOWN = 12;

    private AnalyticsCalculator() {}

    public static AnalyticsResponse compute(List<JobApplication> apps, List<StatusHistory> history, LocalDate today) {
        Map<Long, Set<ApplicationStatus>> reached = reachedStatuses(history);

        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.put(s, 0L);
        }
        apps.forEach(a -> byStatus.merge(a.getStatus(), 1L, Long::sum));

        long submitted = 0;
        long responded = 0;
        long interviewed = 0;
        long offers = 0;
        long followUpsDue = 0;
        for (JobApplication a : apps) {
            Set<ApplicationStatus> seen = reached.getOrDefault(a.getId(), EnumSet.noneOf(ApplicationStatus.class));
            seen = withCurrent(seen, a.getStatus());

            if (seen.contains(ApplicationStatus.APPLIED) || a.getStatus() != ApplicationStatus.WISHLIST) {
                submitted++;
            }
            if (seen.stream().anyMatch(ApplicationStatus::isResponse)) {
                responded++;
            }
            if (seen.contains(ApplicationStatus.INTERVIEW) || seen.contains(ApplicationStatus.OFFER)) {
                interviewed++;
            }
            if (seen.contains(ApplicationStatus.OFFER)) {
                offers++;
            }
            if (a.getStatus().isAwaitingReply() && a.getFollowUpDate() != null
                    && !a.getFollowUpDate().isAfter(today)) {
                followUpsDue++;
            }
        }

        return new AnalyticsResponse(
                apps.size(),
                byStatus,
                percentage(responded, submitted),
                percentage(interviewed, submitted),
                averageDaysToResponse(history),
                followUpsDue,
                weekly(apps, today),
                List.of(new FunnelStep("Applied", submitted),
                        new FunnelStep("Interview", interviewed),
                        new FunnelStep("Offer", offers)));
    }

    private static Map<Long, Set<ApplicationStatus>> reachedStatuses(List<StatusHistory> history) {
        Map<Long, Set<ApplicationStatus>> reached = new HashMap<>();
        for (StatusHistory h : history) {
            reached.computeIfAbsent(h.getApplication().getId(), k -> EnumSet.noneOf(ApplicationStatus.class))
                    .add(h.getToStatus());
        }
        return reached;
    }

    private static Set<ApplicationStatus> withCurrent(Set<ApplicationStatus> seen, ApplicationStatus current) {
        Set<ApplicationStatus> copy = EnumSet.noneOf(ApplicationStatus.class);
        copy.addAll(seen);
        copy.add(current);
        return copy;
    }

    private static double percentage(long part, long whole) {
        if (whole == 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static Double averageDaysToResponse(List<StatusHistory> history) {
        Map<Long, Instant> appliedAt = new HashMap<>();
        Map<Long, Instant> respondedAt = new HashMap<>();
        for (StatusHistory h : history) { // already ordered by changedAt
            Long id = h.getApplication().getId();
            if (h.getToStatus() == ApplicationStatus.APPLIED) {
                appliedAt.putIfAbsent(id, h.getChangedAt());
            } else if (h.getToStatus().isResponse() && appliedAt.containsKey(id)) {
                respondedAt.putIfAbsent(id, h.getChangedAt());
            }
        }
        List<Double> days = new ArrayList<>();
        respondedAt.forEach((id, end) -> {
            double d = Duration.between(appliedAt.get(id), end).toMinutes() / (60.0 * 24.0);
            days.add(Math.max(d, 0));
        });
        if (days.isEmpty()) {
            return null;
        }
        double avg = days.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.round(avg * 10.0) / 10.0;
    }

    private static List<WeekCount> weekly(List<JobApplication> apps, LocalDate today) {
        LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Long> counts = new HashMap<>();
        for (JobApplication a : apps) {
            if (a.getAppliedDate() != null) {
                counts.merge(a.getAppliedDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), 1L,
                        Long::sum);
            }
        }
        List<WeekCount> result = new ArrayList<>();
        for (int i = WEEKS_SHOWN - 1; i >= 0; i--) {
            LocalDate week = currentWeek.minus(i, ChronoUnit.WEEKS);
            result.add(new WeekCount(week, counts.getOrDefault(week, 0L)));
        }
        return result;
    }
}
