package com.carya.energynews.dailybriefanalysis;

public class DailyBriefAnalysisNotFoundException extends RuntimeException {

    public DailyBriefAnalysisNotFoundException(Long dailyBriefId) {
        super("AI analysis has not been generated for DailyBrief " + dailyBriefId);
    }
}
