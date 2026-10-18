package com.jobtracker.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApplicationDtos {

    private ApplicationDtos() {}

    public record ApplicationRequest(
            @NotBlank @Size(max = 150) String company,
            @NotBlank @Size(max = 150) String role,
            @Size(max = 150) String location,
            @Size(max = 500) String jobUrl,
            @Size(max = 100) String salaryRange,
            @NotNull ApplicationStatus status,
            LocalDate appliedDate,
            LocalDate followUpDate,
            @Size(max = 20) List<@Size(max = 50) String> skills,
            @Size(max = 10000) String notes,
            @Size(max = 20000) String jobDescription) {}

    public record StatusUpdateRequest(@NotNull ApplicationStatus status) {}

    public record ApplicationResponse(
            Long id,
            String company,
            String role,
            String location,
            String jobUrl,
            String salaryRange,
            ApplicationStatus status,
            LocalDate appliedDate,
            LocalDate followUpDate,
            List<String> skills,
            String notes,
            String jobDescription,
            Instant createdAt,
            Instant updatedAt) {

        public static ApplicationResponse from(JobApplication a) {
            return new ApplicationResponse(a.getId(), a.getCompany(), a.getRole(), a.getLocation(), a.getJobUrl(),
                    a.getSalaryRange(), a.getStatus(), a.getAppliedDate(), a.getFollowUpDate(), a.skillList(),
                    a.getNotes(), a.getJobDescription(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    public record HistoryEntry(ApplicationStatus from, ApplicationStatus to, Instant changedAt) {
        public static HistoryEntry from(StatusHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getChangedAt());
        }
    }
}
