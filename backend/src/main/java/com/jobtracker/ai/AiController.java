package com.jobtracker.ai;

import com.jobtracker.ai.AiDtos.InterviewPrepResponse;
import com.jobtracker.ai.AiDtos.JobPostingExtraction;
import com.jobtracker.ai.AiDtos.ParseJobRequest;
import com.jobtracker.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) {
        this.ai = ai;
    }

    /** Turns a pasted job posting into form fields. */
    @PostMapping("/parse-job")
    public JobPostingExtraction parseJob(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ParseJobRequest req) {
        return ai.parseJobPosting(user.id(), req.text());
    }

    /** Generates interview questions tailored to one of the user's applications. */
    @PostMapping("/applications/{id}/interview-prep")
    public InterviewPrepResponse interviewPrep(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ai.interviewPrep(user.id(), id);
    }
}
