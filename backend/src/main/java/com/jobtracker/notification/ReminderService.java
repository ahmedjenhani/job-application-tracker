package com.jobtracker.notification;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.JobApplication;
import com.jobtracker.application.JobApplicationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one in-app notification (and one email) for every application that is still waiting for a reply
 * and whose follow-up date has arrived. Each application is reminded once per follow-up date.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final JobApplicationRepository applications;
    private final NotificationRepository notifications;
    private final MailService mail;
    private final Clock clock;

    public ReminderService(JobApplicationRepository applications, NotificationRepository notifications,
            MailService mail, Clock clock) {
        this.applications = applications;
        this.notifications = notifications;
        this.mail = mail;
        this.clock = clock;
    }

    /** @return how many reminders were sent */
    @Transactional
    public int sendDueReminders(LocalDate today) {
        List<JobApplication> due = applications.findDueForReminder(awaitingReplyStatuses(), today);
        for (JobApplication app : due) {
            String message = "Time to follow up with %s about the %s role.".formatted(app.getCompany(), app.getRole());
            notifications.save(new Notification(app.getUser().getId(), app.getId(), message, clock.instant()));
            mail.sendFollowUpReminder(app.getUser(), app);
            app.setReminderSentAt(clock.instant());
        }
        if (!due.isEmpty()) {
            log.info("Sent {} follow-up reminder(s)", due.size());
        }
        return due.size();
    }

    private static EnumSet<ApplicationStatus> awaitingReplyStatuses() {
        EnumSet<ApplicationStatus> set = EnumSet.noneOf(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            if (s.isAwaitingReply()) {
                set.add(s);
            }
        }
        return set;
    }
}
