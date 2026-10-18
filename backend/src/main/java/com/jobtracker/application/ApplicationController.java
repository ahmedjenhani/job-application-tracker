package com.jobtracker.application;

import com.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.jobtracker.application.ApplicationDtos.HistoryEntry;
import com.jobtracker.application.ApplicationDtos.StatusUpdateRequest;
import com.jobtracker.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false, name = "q") String query) {
        return service.list(user.id(), status, query);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.get(user.id(), id);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.history(user.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody ApplicationRequest req) {
        return service.create(user.id(), req);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody ApplicationRequest req) {
        return service.update(user.id(), id, req);
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse changeStatus(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest req) {
        return service.changeStatus(user.id(), id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        service.delete(user.id(), id);
    }
}
