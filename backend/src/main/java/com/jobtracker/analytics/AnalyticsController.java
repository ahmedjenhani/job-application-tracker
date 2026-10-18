package com.jobtracker.analytics;

import com.jobtracker.application.JobApplicationRepository;
import com.jobtracker.application.StatusHistoryRepository;
import com.jobtracker.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final JobApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final Clock clock;

    public AnalyticsController(JobApplicationRepository applications, StatusHistoryRepository history, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AnalyticsResponse summary(@AuthenticationPrincipal CurrentUser user) {
        return AnalyticsCalculator.compute(
                applications.findByUserId(user.id()), history.findAllForUser(user.id()), LocalDate.now(clock));
    }
}
