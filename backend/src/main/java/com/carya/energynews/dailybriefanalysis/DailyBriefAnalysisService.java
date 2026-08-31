package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBriefResponse;
import com.carya.energynews.dailybrief.DailyBriefService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class DailyBriefAnalysisService {

    private final DailyBriefService dailyBriefService;
    private final DailyBriefAiInputFactory inputFactory;
    private final DailyBriefAiResultValidator resultValidator;
    private final DailyBriefAnalysisStore analysisStore;
    private final ObjectProvider<DailyBriefAiProvider> providerSource;
    private final Clock clock;

    public DailyBriefAnalysisService(
            DailyBriefService dailyBriefService,
            DailyBriefAiInputFactory inputFactory,
            DailyBriefAiResultValidator resultValidator,
            DailyBriefAnalysisStore analysisStore,
            ObjectProvider<DailyBriefAiProvider> providerSource,
            Clock clock
    ) {
        this.dailyBriefService = dailyBriefService;
        this.inputFactory = inputFactory;
        this.resultValidator = resultValidator;
        this.analysisStore = analysisStore;
        this.providerSource = providerSource;
        this.clock = clock;
    }

    public DailyBriefAnalysisResponse generate(Long dailyBriefId) {
        DailyBriefResponse brief = dailyBriefService.getById(dailyBriefId);
        if (brief.items().isEmpty()) {
            throw new DailyBriefEmptyAnalysisException(dailyBriefId);
        }

        DailyBriefAiProvider provider = providerSource.getIfAvailable();
        if (provider == null) {
            throw new DailyBriefAiProviderUnavailableException();
        }

        DailyBriefAiRequest request = inputFactory.create(brief);
        List<Long> articleIds = request.articles().stream()
                .map(DailyBriefAiArticle::articleId)
                .toList();
        DailyBriefAiSnapshot snapshot = new DailyBriefAiSnapshot(
                brief.id(),
                brief.updatedAt(),
                articleIds,
                request
        );

        DailyBriefAiResult providerResult = provider.analyze(request);
        DailyBriefAiResult validated = resultValidator.validate(providerResult, request.articles());
        return analysisStore.save(
                snapshot,
                validated,
                provider.providerName(),
                provider.model(),
                clock.instant()
        );
    }

    public DailyBriefAnalysisResponse get(Long dailyBriefId) {
        return analysisStore.getByDailyBriefId(dailyBriefId);
    }
}
