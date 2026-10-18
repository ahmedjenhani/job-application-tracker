package com.jobtracker.notification;

import com.jobtracker.config.AppProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the reminder check on the cron schedule configured in {@code app.reminders.cron}. */
@Component
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderService reminders;
    private final ZoneId zone;

    public ReminderScheduler(ReminderService reminders, AppProperties props) {
        this.reminders = reminders;
        this.zone = ZoneId.of(props.reminders().zone());
    }

    @Scheduled(cron = "${app.reminders.cron}", zone = "${app.reminders.zone}")
    public void run() {
        reminders.sendDueReminders(LocalDate.now(zone));
    }
}
