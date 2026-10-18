package com.jobtracker.config;

import static com.jobtracker.application.ApplicationStatus.APPLIED;
import static com.jobtracker.application.ApplicationStatus.INTERVIEW;
import static com.jobtracker.application.ApplicationStatus.OFFER;
import static com.jobtracker.application.ApplicationStatus.REJECTED;
import static com.jobtracker.application.ApplicationStatus.WISHLIST;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.JobApplication;
import com.jobtracker.application.JobApplicationRepository;
import com.jobtracker.application.StatusHistory;
import com.jobtracker.application.StatusHistoryRepository;
import com.jobtracker.notification.Notification;
import com.jobtracker.notification.NotificationRepository;
import com.jobtracker.user.User;
import com.jobtracker.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a demo account with realistic data so a recruiter can log in and see the app populated.
 * Dates are relative to "today", so the dashboard always looks alive. Runs once (skipped if the user exists).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@example.com";
    public static final String DEMO_PASSWORD = "Demo@1234";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /**
     * @param path       statuses the application went through, last one is the current status
     * @param dayOffsets days after applying at which each status of {@code path} was reached
     * @param followUpIn days from today for the follow-up date (negative = overdue), null = none
     * @param reminded   whether the reminder for an overdue follow-up was already sent (notification exists)
     */
    private record Seed(String company, String role, String location, String skills, List<ApplicationStatus> path,
            List<Integer> dayOffsets, int appliedDaysAgo, Integer followUpIn, boolean reminded, String notes,
            String description) {}

    private final UserRepository users;
    private final JobApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final NotificationRepository notifications;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, JobApplicationRepository applications,
            StatusHistoryRepository history, NotificationRepository notifications, PasswordEncoder encoder,
            Clock clock) {
        this.users = users;
        this.applications = applications;
        this.history = history;
        this.notifications = notifications;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(DEMO_EMAIL)) {
            return;
        }
        User demo = new User();
        demo.setEmail(DEMO_EMAIL);
        demo.setFullName("Demo Candidate");
        demo.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        users.save(demo);

        LocalDate today = LocalDate.now(clock);
        List<Notification> pending = new ArrayList<>();
        for (Seed s : seeds()) {
            JobApplication app = new JobApplication();
            app.setUser(demo);
            app.setCompany(s.company());
            app.setRole(s.role());
            app.setLocation(s.location());
            app.setNotes(s.notes());
            app.setJobDescription(s.description());
            app.setSkills(s.skills());
            app.setStatus(s.path().get(s.path().size() - 1));

            LocalDate applied = today.minusDays(s.appliedDaysAgo());
            if (s.path().get(0) != WISHLIST) {
                app.setAppliedDate(applied);
            }
            if (s.followUpIn() != null) {
                app.setFollowUpDate(today.plusDays(s.followUpIn()));
                if (s.reminded()) {
                    app.setReminderSentAt(atNoon(app.getFollowUpDate()));
                }
            }
            app.setCreatedAt(atNoon(applied));
            applications.save(app);

            ApplicationStatus previous = null;
            for (int i = 0; i < s.path().size(); i++) {
                Instant when = atNoon(applied.plusDays(s.dayOffsets().get(i)));
                history.save(new StatusHistory(app, previous, s.path().get(i), when));
                previous = s.path().get(i);
            }
            if (app.getReminderSentAt() != null) {
                pending.add(new Notification(demo.getId(), app.getId(),
                        "Time to follow up with %s about the %s role.".formatted(s.company(), s.role()),
                        app.getReminderSentAt()));
            }
        }
        notifications.saveAll(pending);
        log.info("Seeded demo account {} with {} applications", DEMO_EMAIL, seeds().size());
    }

    private static Instant atNoon(LocalDate date) {
        return date.atTime(12, 0).toInstant(ZoneOffset.UTC);
    }

    private static List<Seed> seeds() {
        return List.of(
                new Seed("Sofrecom", "Junior Java Developer", "Tunis", "Java,Spring Boot,PostgreSQL,Docker",
                        List.of(APPLIED, INTERVIEW, OFFER), List.of(0, 6, 15), 52, null, false,
                        "Offer received - deciding by end of month.",
                        "We are looking for a junior Java developer to join our telecom platform team. "
                                + "You will build REST APIs with Spring Boot, work with PostgreSQL and ship with Docker."),
                new Seed("Vermeg", "Full Stack Engineer (Angular/Spring)", "Tunis",
                        "Angular,TypeScript,Spring Boot,REST",
                        List.of(APPLIED, INTERVIEW), List.of(0, 9), 21, 3, false,
                        "Technical interview done, waiting for the HR round.",
                        "Join our banking software team to build Angular front-ends backed by Spring Boot services. "
                                + "Experience with TypeScript, RxJS and REST design is expected."),
                new Seed("Talan", "Software Engineer Intern", "Tunis", "Java,Angular,Git",
                        List.of(APPLIED, INTERVIEW), List.of(0, 5), 14, 7, false,
                        "Sent a thank-you email after the first call.", null),
                new Seed("Ooredoo", "Backend Developer", "Tunis", "Spring Boot,Kafka,SQL",
                        List.of(APPLIED), List.of(0), 12, -2, true,
                        "Applied via the careers page, no answer yet.", null),
                new Seed("Proxym Group", "Java/Angular Developer", "Sousse", "Java,Angular,Docker",
                        List.of(APPLIED), List.of(0), 9, -1, false,
                        "Applied through a referral from a classmate.", null),
                new Seed("Instadeep", "Software Engineer - Web", "Tunis", "TypeScript,Angular,Python",
                        List.of(APPLIED, REJECTED), List.of(0, 11), 58, null, false,
                        "Not selected after the screening.", null),
                new Seed("Sopra Steria", "Junior Software Developer", "Remote", "Java,Spring,Microservices",
                        List.of(APPLIED, REJECTED), List.of(0, 18), 74, null, false, null, null),
                new Seed("Focus Corporation", "Application Developer", "Tunis", "Java,Oracle,SQL",
                        List.of(APPLIED, REJECTED), List.of(0, 7), 44, null, false, null, null),
                new Seed("Wiremind", "Backend Engineer", "Remote", "Java,PostgreSQL,Kubernetes",
                        List.of(APPLIED, INTERVIEW, REJECTED), List.of(0, 8, 20), 66, null, false,
                        "Good conversation, but they wanted more experience.", null),
                new Seed("Expensya", "Junior Full Stack Developer", "Tunis", "Angular,.NET,SQL Server",
                        List.of(APPLIED), List.of(0), 5, 2, false, null, null),
                new Seed("Sagemcom", "Embedded Software Intern", "Ariana", "C,Linux,Git",
                        List.of(APPLIED), List.of(0), 3, 4, false, null, null),
                new Seed("Mobelite", "Java Developer", "Tunis", "Java,Spring Boot,Docker",
                        List.of(WISHLIST), List.of(0), 2, null, false, "Waiting for the new job posting to open.",
                        null),
                new Seed("Dynamics Ltd", "Frontend Developer", "Remote", "Angular,RxJS,SCSS",
                        List.of(WISHLIST), List.of(0), 1, null, false, null, null),
                new Seed("Lynx Software", "DevOps Intern", "Tunis", "Docker,CI/CD,Linux",
                        List.of(WISHLIST), List.of(0), 0, null, false, null, null));
    }
}
