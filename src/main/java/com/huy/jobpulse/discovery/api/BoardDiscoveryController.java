package com.huy.jobpulse.discovery.api;

import com.huy.jobpulse.discovery.application.BoardDiscoveryService;
import com.huy.jobpulse.discovery.application.CompanySeedService;
import com.huy.jobpulse.discovery.application.DiscoveryRunResult;
import com.huy.jobpulse.discovery.application.SeedImportResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/discovery")
public class BoardDiscoveryController {

    private final CompanySeedService seedService;
    private final BoardDiscoveryService discoveryService;
    private final LocalAdminGuard localAdminGuard;

    public BoardDiscoveryController(
            CompanySeedService seedService,
            BoardDiscoveryService discoveryService,
            LocalAdminGuard localAdminGuard
    ) {
        this.seedService = seedService;
        this.discoveryService = discoveryService;
        this.localAdminGuard = localAdminGuard;
    }

    @PostMapping(
            path = "/seeds",
            consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE}
    )
    public SeedImportResult importSeeds(
            @RequestBody String csv,
            HttpServletRequest request
    ) {
        localAdminGuard.requireLocal(request);
        return seedService.importCsv(csv);
    }

    @GetMapping("/seeds")
    public List<CompanySeedResponse> listSeeds(HttpServletRequest request) {
        localAdminGuard.requireLocal(request);
        return seedService.findAll().stream()
                .map(CompanySeedResponse::from)
                .toList();
    }

    @PostMapping("/runs")
    public DiscoveryRunResult run(HttpServletRequest request) {
        localAdminGuard.requireLocal(request);
        return discoveryService.runAll();
    }
}
