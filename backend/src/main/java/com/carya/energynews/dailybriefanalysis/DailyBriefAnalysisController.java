package com.carya.energynews.dailybriefanalysis;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-briefs/{dailyBriefId}/analysis")
public class DailyBriefAnalysisController {

    private final DailyBriefAnalysisService analysisService;

    public DailyBriefAnalysisController(DailyBriefAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/generate")
    public DailyBriefAnalysisResponse generate(@PathVariable Long dailyBriefId) {
        return analysisService.generate(dailyBriefId);
    }

    @GetMapping
    public DailyBriefAnalysisResponse get(@PathVariable Long dailyBriefId) {
        return analysisService.get(dailyBriefId);
    }
}
