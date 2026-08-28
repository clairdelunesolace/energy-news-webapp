package com.carya.energynews.dailybrief;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/daily-briefs")
public class DailyBriefController {

    private final DailyBriefService dailyBriefService;

    public DailyBriefController(DailyBriefService dailyBriefService) {
        this.dailyBriefService = dailyBriefService;
    }

    @PostMapping("/generate")
    public DailyBriefResponse generate(
            @Valid @RequestBody GenerateDailyBriefRequest request
    ) {
        return dailyBriefService.generate(request);
    }

    @GetMapping("/{id}")
    public DailyBriefResponse getById(@PathVariable Long id) {
        return dailyBriefService.getById(id);
    }

    @GetMapping
    public DailyBriefResponse getByWatchlistAndDate(
            @RequestParam Long watchlistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return dailyBriefService.getByWatchlistAndDate(watchlistId, date);
    }
}
