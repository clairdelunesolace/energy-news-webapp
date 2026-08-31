package com.carya.energynews.dailybriefanalysis;

public interface DailyBriefAiProvider {

    String providerName();

    String model();

    DailyBriefAiResult analyze(DailyBriefAiRequest request);
}
