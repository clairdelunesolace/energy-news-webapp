package com.carya.energynews.sync;

import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.collection.NewsCollectionService;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NewsSyncService {

    private final SourceRepository sourceRepository;
    private final NewsCollectionService newsCollectionService;
    private final ArticleIngestionService articleIngestionService;

    public NewsSyncService(
            SourceRepository sourceRepository,
            NewsCollectionService newsCollectionService,
            ArticleIngestionService articleIngestionService
    ) {
        this.sourceRepository = sourceRepository;
        this.newsCollectionService = newsCollectionService;
        this.articleIngestionService = articleIngestionService;
    }

    public NewsSyncResult sync(Source source) {
        List<CollectedArticle> collectedArticles = newsCollectionService.collect(source);
        List<ArticleIngestionResult> ingestionResults = articleIngestionService.ingestAll(collectedArticles);
        return summarize(collectedArticles.size(), ingestionResults, 0);
    }

    public NewsSyncResult syncAllEnabledSources() {
        int collected = 0;
        int saved = 0;
        int duplicates = 0;
        int failedSources = 0;

        for (Source source : sourceRepository.findAllByEnabledTrue()) {
            if (source.getType() != SourceType.RSS) {
                continue;
            }

            try {
                NewsSyncResult sourceResult = sync(source);
                collected += sourceResult.collected();
                saved += sourceResult.saved();
                duplicates += sourceResult.duplicates();
            } catch (NewsCollectionException exception) {
                failedSources++;
            }
        }

        return new NewsSyncResult(collected, saved, duplicates, failedSources);
    }

    private NewsSyncResult summarize(
            int collected,
            List<ArticleIngestionResult> ingestionResults,
            int failedSources
    ) {
        int saved = 0;
        int duplicates = 0;

        for (ArticleIngestionResult result : ingestionResults) {
            switch (result) {
                case SAVED -> saved++;
                case DUPLICATE -> duplicates++;
            }
        }

        return new NewsSyncResult(collected, saved, duplicates, failedSources);
    }
}
