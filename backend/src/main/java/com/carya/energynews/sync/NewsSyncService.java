package com.carya.energynews.sync;

import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.collection.NewsCollectionService;
import com.carya.energynews.filter.ArticleFilter;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsSyncService {

    private final SourceRepository sourceRepository;
    private final NewsCollectionService newsCollectionService;
    private final ArticleFilter articleFilter;
    private final ArticleIngestionService articleIngestionService;

    public NewsSyncService(
            SourceRepository sourceRepository,
            NewsCollectionService newsCollectionService,
            ArticleFilter articleFilter,
            ArticleIngestionService articleIngestionService
    ) {
        this.sourceRepository = sourceRepository;
        this.newsCollectionService = newsCollectionService;
        this.articleFilter = articleFilter;
        this.articleIngestionService = articleIngestionService;
    }

    public NewsSyncResult sync(Source source) {
        List<CollectedArticle> collectedArticles = newsCollectionService.collect(source);
        List<CollectedArticle> acceptedArticles = new ArrayList<>();

        for (CollectedArticle article : collectedArticles) {
            if (articleFilter.evaluate(article).accepted()) {
                acceptedArticles.add(article);
            }
        }

        List<ArticleIngestionResult> ingestionResults = articleIngestionService.ingestAll(acceptedArticles);
        int filteredOut = collectedArticles.size() - acceptedArticles.size();
        return summarize(collectedArticles.size(), filteredOut, ingestionResults, 0);
    }

    public NewsSyncResult syncAllEnabledSources() {
        int collected = 0;
        int filteredOut = 0;
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
                filteredOut += sourceResult.filteredOut();
                saved += sourceResult.saved();
                duplicates += sourceResult.duplicates();
            } catch (NewsCollectionException exception) {
                failedSources++;
            }
        }

        return new NewsSyncResult(collected, filteredOut, saved, duplicates, failedSources);
    }

    private NewsSyncResult summarize(
            int collected,
            int filteredOut,
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

        return new NewsSyncResult(collected, filteredOut, saved, duplicates, failedSources);
    }
}
