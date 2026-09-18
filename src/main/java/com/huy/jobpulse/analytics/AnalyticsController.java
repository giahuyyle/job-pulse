package com.huy.jobpulse.analytics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics/jobs")
public class AnalyticsController {

    private final DailyJobStatsRepository repository;

    public AnalyticsController(DailyJobStatsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/daily")
    public List<DailyJobStat> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        return repository.findBetween(from, to);
    }
}
