package com.carya.energynews.dailybriefanalysis;

public class DailyBriefEmptyAnalysisException extends RuntimeException {

    public DailyBriefEmptyAnalysisException(Long dailyBriefId) {
        super("DailyBrief " + dailyBriefId + " has no Articles to analyze");
    }
}
