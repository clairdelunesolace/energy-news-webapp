package com.carya.energynews.dailybriefanalysis;

public class DailyBriefAiProviderUnavailableException extends RuntimeException {

    public DailyBriefAiProviderUnavailableException() {
        super("Daily brief AI provider is not configured");
    }
}
