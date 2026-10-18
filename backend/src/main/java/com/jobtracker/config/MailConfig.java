package com.jobtracker.config;

import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Only creates a mail sender when MAIL_HOST is set, so local runs and tests need no SMTP server. */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnExpression("!'${app.mail.host:}'.isBlank()")
    JavaMailSender javaMailSender(AppProperties props) {
        AppProperties.Mail mail = props.mail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        sender.setUsername(mail.username());
        sender.setPassword(mail.password());
        Properties smtp = sender.getJavaMailProperties();
        smtp.put("mail.smtp.auth", "true");
        smtp.put("mail.smtp.starttls.enable", "true");
        smtp.put("mail.smtp.connectiontimeout", "5000");
        smtp.put("mail.smtp.timeout", "10000");
        smtp.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
