package com.carya.energynews.sync;

import com.carya.energynews.article.Article;
import com.carya.energynews.article.ArticleIngestionResult;
import com.carya.energynews.article.ArticleIngestionService;
import com.carya.energynews.collection.CollectedArticle;
import com.carya.energynews.collection.NewsCollectionException;
import com.carya.energynews.collection.NewsCollectionService;
import com.carya.energynews.content.ArticleContentFetchException;
import com.carya.energynews.content.ArticleContentService;
import com.carya.energynews.filter.ArticleFilter;
import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceLanguage;
import com.carya.energynews.source.SourceRepository;
import com.carya.energynews.source.SourceType;
import com.carya.energynews.translation.ArticleContentTranslationService;
import com.carya.energynews.translation.ContentTranslationStatus;
import com.carya.energynews.translation.TranslationException;
import com.carya.energynews.translation.TranslationLanguage;
import com.carya.energynews.translation.TranslationService;
import com.carya.energynews.translation.TranslationStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class NewsSyncService {

    private final SourceRepository sourceRepository;
    private final NewsCollectionService newsCollectionService;
    private final ArticleFilter articleFilter;
    private final ArticleIngestionService articleIngestionService;
    private final TranslationService translationService;
    private final ArticleContentService articleContentService;
    private final ArticleContentTranslationService articleContentTranslationService;

    public NewsSyncService(
            SourceRepository sourceRepository,
            NewsCollectionService newsCollectionService,
            ArticleFilter articleFilter,
            ArticleIngestionService articleIngestionService,
            TranslationService translationService,
            ArticleContentService articleContentService,
            ArticleContentTranslationService articleContentTranslationService
    ) {
        this.sourceRepository = sourceRepository;
        this.newsCollectionService = newsCollectionService;
        this.articleFilter = articleFilter;
        this.articleIngestionService = articleIngestionService;
        this.translationService = translationService;
        this.articleContentService = articleContentService;
        this.articleContentTranslationService = articleContentTranslationService;
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
        int translated = 0;
        int translationFailed = 0;
        int contentFetched = 0;
        int contentFetchFailed = 0;
        int contentTranslated = 0;
        int contentTranslationFailed = 0;
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
                translated += sourceResult.translated();
                translationFailed += sourceResult.translationFailed();
                contentFetched += sourceResult.contentFetched();
                contentFetchFailed += sourceResult.contentFetchFailed();
                contentTranslated += sourceResult.contentTranslated();
                contentTranslationFailed += sourceResult.contentTranslationFailed();
            } catch (NewsCollectionException exception) {
                failedSources++;
            }
        }

        return new NewsSyncResult(
                collected,
                filteredOut,
                saved,
                duplicates,
                translated,
                translationFailed,
                contentFetched,
                contentFetchFailed,
                contentTranslated,
                contentTranslationFailed,
                failedSources
        );
    }

    private NewsSyncResult summarize(
            int collected,
            int filteredOut,
            List<ArticleIngestionResult> ingestionResults,
            int failedSources
    ) {
        int saved = 0;
        int duplicates = 0;
        int translated = 0;
        int translationFailed = 0;
        int contentFetched = 0;
        int contentFetchFailed = 0;
        int contentTranslated = 0;
        int contentTranslationFailed = 0;

        for (ArticleIngestionResult result : ingestionResults) {
            switch (result.status()) {
                case SAVED -> saved++;
                case DUPLICATE -> duplicates++;
            }

            Article article = result.article();
            boolean titleTranslationSuccessful = false;

            if (article.getSource().getLanguage() == SourceLanguage.EN) {
                try {
                    if (translationService.translate(article, TranslationLanguage.ZH_CN).getStatus()
                            == TranslationStatus.SUCCESS) {
                        translated++;
                        titleTranslationSuccessful = true;
                    }
                } catch (TranslationException exception) {
                    translationFailed++;
                }
            }

            try {
                article = articleContentService.enrichContent(article);
            } catch (ArticleContentFetchException exception) {
                contentFetchFailed++;
                continue;
            }

            boolean contentAvailable = article.getContent() != null
                    && !article.getContent().isBlank();
            if (contentAvailable) {
                contentFetched++;
            }

            if (!titleTranslationSuccessful
                    || article.getSource().getLanguage() != SourceLanguage.EN
                    || !contentAvailable) {
                continue;
            }

            try {
                if (articleContentTranslationService.translateContent(
                        article,
                        TranslationLanguage.ZH_CN
                ).getContentStatus() == ContentTranslationStatus.SUCCESS) {
                    contentTranslated++;
                }
            } catch (TranslationException exception) {
                contentTranslationFailed++;
            }
        }

        return new NewsSyncResult(
                collected,
                filteredOut,
                saved,
                duplicates,
                translated,
                translationFailed,
                contentFetched,
                contentFetchFailed,
                contentTranslated,
                contentTranslationFailed,
                failedSources
        );
    }
}
