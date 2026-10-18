package com.jobtracker.application;

import com.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.jobtracker.application.ApplicationDtos.HistoryEntry;
import com.jobtracker.common.ApiExceptions.NotFoundException;
import com.jobtracker.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    /** When an application is marked as applied without a follow-up date, we suggest one a week later. */
    static final int DEFAULT_FOLLOW_UP_DAYS = 7;

    private final JobApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final UserRepository users;
    private final Clock clock;

    public ApplicationService(JobApplicationRepository applications, StatusHistoryRepository history,
            UserRepository users, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(Long userId, ApplicationStatus status, String query) {
        return applications.findAll(filter(userId, status, query), Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream().map(ApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long userId, Long id) {
        return ApplicationResponse.from(find(userId, id));
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Long userId, Long id) {
        find(userId, id);
        return history.findByApplicationIdOrderByChangedAtAsc(id).stream().map(HistoryEntry::from).toList();
    }

    @Transactional
    public ApplicationResponse create(Long userId, ApplicationRequest req) {
        JobApplication app = new JobApplication();
        app.setUser(users.getReferenceById(userId));
        copyFields(app, req);
        app.setStatus(req.status());
        applyStatusDefaults(app);
        applications.save(app);
        history.save(new StatusHistory(app, null, app.getStatus(), clock.instant()));
        return ApplicationResponse.from(app);
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long id, ApplicationRequest req) {
        JobApplication app = find(userId, id);
        ApplicationStatus previous = app.getStatus();
        LocalDate previousFollowUp = app.getFollowUpDate();

        copyFields(app, req);
        app.setStatus(req.status());
        if (previous != app.getStatus()) {
            applyStatusDefaults(app);
        }
        if (!Objects.equals(previousFollowUp, app.getFollowUpDate())) {
            app.setReminderSentAt(null); // a new follow-up date deserves a new reminder
        }
        recordTransition(app, previous);
        return ApplicationResponse.from(app);
    }

    @Transactional
    public ApplicationResponse changeStatus(Long userId, Long id, ApplicationStatus newStatus) {
        JobApplication app = find(userId, id);
        ApplicationStatus previous = app.getStatus();
        LocalDate previousFollowUp = app.getFollowUpDate();

        app.setStatus(newStatus);
        applyStatusDefaults(app);
        if (!Objects.equals(previousFollowUp, app.getFollowUpDate())) {
            app.setReminderSentAt(null);
        }
        recordTransition(app, previous);
        return ApplicationResponse.from(app);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        applications.delete(find(userId, id));
    }

    /** Loads an application owned by the caller; someone else's application looks exactly like a missing one. */
    JobApplication find(Long userId, Long id) {
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
    }

    private void recordTransition(JobApplication app, ApplicationStatus previous) {
        if (previous != app.getStatus()) {
            history.save(new StatusHistory(app, previous, app.getStatus(), clock.instant()));
        }
    }

    private void applyStatusDefaults(JobApplication app) {
        if (app.getStatus() == ApplicationStatus.APPLIED) {
            if (app.getAppliedDate() == null) {
                app.setAppliedDate(LocalDate.now(clock));
            }
            if (app.getFollowUpDate() == null) {
                app.setFollowUpDate(app.getAppliedDate().plusDays(DEFAULT_FOLLOW_UP_DAYS));
            }
        }
    }

    private static void copyFields(JobApplication app, ApplicationRequest req) {
        app.setCompany(req.company().trim());
        app.setRole(req.role().trim());
        app.setLocation(blankToNull(req.location()));
        app.setJobUrl(blankToNull(req.jobUrl()));
        app.setSalaryRange(blankToNull(req.salaryRange()));
        app.setAppliedDate(req.appliedDate());
        app.setFollowUpDate(req.followUpDate());
        app.replaceSkills(req.skills());
        app.setNotes(blankToNull(req.notes()));
        app.setJobDescription(blankToNull(req.jobDescription()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Specification<JobApplication> filter(Long userId, ApplicationStatus status, String query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("company")), like),
                        cb.like(cb.lower(root.get("role")), like),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("location"), "")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
