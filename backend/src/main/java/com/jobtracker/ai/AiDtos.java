package com.jobtracker.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    public record ParseJobRequest(
            @NotBlank @Size(min = 40, max = 12000, message = "paste the full job posting (40 to 12000 characters)")
            String text) {}

    public record JobPostingExtraction(
            String company, String role, String location, String salaryRange, List<String> skills, String summary) {}

    public record InterviewQuestion(String question, String category, String tip) {}

    public record InterviewPrepResponse(List<InterviewQuestion> questions) {}
}
