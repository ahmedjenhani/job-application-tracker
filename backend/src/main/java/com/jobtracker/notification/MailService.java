package com.jobtracker.notification;

import com.jobtracker.application.JobApplication;
import com.jobtracker.config.AppProperties;
import com.jobtracker.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends follow-up emails when SMTP is configured (MAIL_HOST). Without SMTP the email is only logged, so the
 * app runs fine locally and in tests. A mail failure never blocks the in-app notification.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.from = props.mail().from();
    }

    public void sendFollowUpReminder(User user, JobApplication app) {
        String subject = "Follow up with " + app.getCompany();
        String body = """
                Hi %s,

                It's time to follow up on your application for "%s" at %s.
                A short, polite message to the recruiter a week after applying often makes the difference.

                Open your Job Tracker board to update the status or set a new reminder.
                """.formatted(user.getFullName(), app.getRole(), app.getCompany());

        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            log.info("[mail disabled] would send '{}' to user {}", subject, user.getId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Could not send reminder email for application {}: {}", app.getId(), e.getMessage());
        }
    }
}
