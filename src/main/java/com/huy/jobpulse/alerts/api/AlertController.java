package com.huy.jobpulse.alerts.api;

import com.huy.jobpulse.alerts.application.AlertService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertResponse> findAll(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(defaultValue = "false") boolean unread
    ) {
        return service.findAll(user.getSubject(), unread).stream()
                .map(AlertResponse::from)
                .toList();
    }

    @PatchMapping("/{id}/read")
    public AlertResponse markRead(@AuthenticationPrincipal OidcUser user,
            @PathVariable UUID id) {
        return AlertResponse.from(service.markRead(user.getSubject(), id));
    }
}
