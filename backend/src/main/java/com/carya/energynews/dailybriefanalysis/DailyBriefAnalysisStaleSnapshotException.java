package com.carya.energynews.dailybriefanalysis;

public class DailyBriefAnalysisStaleSnapshotException extends RuntimeException {

    public DailyBriefAnalysisStaleSnapshotException(Long dailyBriefId) {
        super("DailyBrief " + dailyBriefId + " changed while AI analysis was being generated");
    }
}
