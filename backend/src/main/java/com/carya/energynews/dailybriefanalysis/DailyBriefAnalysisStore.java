package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBrief;
import com.carya.energynews.dailybrief.DailyBriefItem;
import com.carya.energynews.dailybrief.DailyBriefItemRepository;
import com.carya.energynews.dailybrief.DailyBriefNotFoundException;
import com.carya.energynews.dailybrief.DailyBriefRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DailyBriefAnalysisStore {

    private final DailyBriefRepository dailyBriefRepository;
    private final DailyBriefItemRepository dailyBriefItemRepository;
    private final DailyBriefAnalysisRepository analysisRepository;
    private final DailyBriefEventRepository eventRepository;
    private final DailyBriefEventItemRepository eventItemRepository;

    public DailyBriefAnalysisStore(
            DailyBriefRepository dailyBriefRepository,
            DailyBriefItemRepository dailyBriefItemRepository,
            DailyBriefAnalysisRepository analysisRepository,
            DailyBriefEventRepository eventRepository,
            DailyBriefEventItemRepository eventItemRepository
    ) {
        this.dailyBriefRepository = dailyBriefRepository;
        this.dailyBriefItemRepository = dailyBriefItemRepository;
        this.analysisRepository = analysisRepository;
        this.eventRepository = eventRepository;
        this.eventItemRepository = eventItemRepository;
    }

    @Transactional
    public DailyBriefAnalysisResponse save(
            DailyBriefAiSnapshot snapshot,
            DailyBriefAiResult result,
            String provider,
            String model,
            Instant generatedAt
    ) {
        DailyBrief brief = dailyBriefRepository.findById(snapshot.dailyBriefId())
                .orElseThrow(() -> new DailyBriefNotFoundException(snapshot.dailyBriefId()));
        List<DailyBriefItem> currentItems = dailyBriefItemRepository
                .findAllByDailyBriefIdOrderByRankAsc(brief.getId());
        List<Long> currentArticleIds = currentItems.stream()
                .map(item -> item.getArticle().getId())
                .toList();
        if (!Objects.equals(brief.getUpdatedAt(), snapshot.dailyBriefUpdatedAt())
                || !currentArticleIds.equals(snapshot.articleIds())) {
            throw new DailyBriefAnalysisStaleSnapshotException(brief.getId());
        }

        Map<Long, DailyBriefItem> itemsByArticleId = currentItems.stream()
                .collect(Collectors.toMap(
                        item -> item.getArticle().getId(),
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        DailyBriefAnalysis analysis = analysisRepository.findByDailyBriefId(brief.getId())
                .orElseGet(() -> new DailyBriefAnalysis(brief));
        analysis.update(
                provider,
                model,
                result.headline(),
                result.overview(),
                generatedAt
        );
        analysis = analysisRepository.saveAndFlush(analysis);

        eventRepository.deleteAllByAnalysisId(analysis.getId());

        List<DailyBriefEvent> events = new ArrayList<>(result.events().size());
        for (int index = 0; index < result.events().size(); index++) {
            DailyBriefAiEvent event = result.events().get(index);
            events.add(new DailyBriefEvent(
                    analysis,
                    index + 1,
                    event.title(),
                    event.summary(),
                    event.whyItMatters()
            ));
        }
        events = eventRepository.saveAllAndFlush(events);

        List<DailyBriefEventItem> eventItems = new ArrayList<>();
        for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {
            DailyBriefEvent event = events.get(eventIndex);
            List<Long> supportingIds = result.events().get(eventIndex).supportingArticleIds();
            for (int supportIndex = 0; supportIndex < supportingIds.size(); supportIndex++) {
                Long articleId = supportingIds.get(supportIndex);
                DailyBriefItem item = itemsByArticleId.get(articleId);
                if (item == null) {
                    throw new DailyBriefAnalysisStaleSnapshotException(brief.getId());
                }
                eventItems.add(new DailyBriefEventItem(event, item, supportIndex + 1));
            }
        }
        eventItemRepository.saveAllAndFlush(eventItems);

        return toResponse(analysis, events, result.events());
    }

    @Transactional(readOnly = true)
    public DailyBriefAnalysisResponse getByDailyBriefId(Long dailyBriefId) {
        if (!dailyBriefRepository.existsById(dailyBriefId)) {
            throw new DailyBriefNotFoundException(dailyBriefId);
        }
        DailyBriefAnalysis analysis = analysisRepository.findByDailyBriefId(dailyBriefId)
                .orElseThrow(() -> new DailyBriefAnalysisNotFoundException(dailyBriefId));
        List<DailyBriefEvent> events = eventRepository
                .findAllByAnalysisIdOrderByEventRankAsc(analysis.getId());
        List<Long> eventIds = events.stream().map(DailyBriefEvent::getId).toList();

        Map<Long, List<Long>> supportIdsByEventId = new LinkedHashMap<>();
        if (!eventIds.isEmpty()) {
            eventItemRepository.findAllByEventIdInOrderByEventIdAscSupportRankAsc(eventIds)
                    .forEach(link -> supportIdsByEventId
                            .computeIfAbsent(link.getEvent().getId(), ignored -> new ArrayList<>())
                            .add(link.getDailyBriefItem().getArticle().getId()));
        }

        List<DailyBriefEventResponse> eventResponses = events.stream()
                .map(event -> new DailyBriefEventResponse(
                        event.getEventRank(),
                        event.getTitle(),
                        event.getSummary(),
                        event.getWhyItMatters(),
                        List.copyOf(supportIdsByEventId.getOrDefault(event.getId(), List.of()))
                ))
                .toList();
        return toResponse(analysis, eventResponses);
    }

    private static DailyBriefAnalysisResponse toResponse(
            DailyBriefAnalysis analysis,
            List<DailyBriefEvent> events,
            List<DailyBriefAiEvent> aiEvents
    ) {
        List<DailyBriefEventResponse> eventResponses = new ArrayList<>(events.size());
        for (int index = 0; index < events.size(); index++) {
            DailyBriefEvent event = events.get(index);
            eventResponses.add(new DailyBriefEventResponse(
                    event.getEventRank(),
                    event.getTitle(),
                    event.getSummary(),
                    event.getWhyItMatters(),
                    aiEvents.get(index).supportingArticleIds()
            ));
        }
        return toResponse(analysis, List.copyOf(eventResponses));
    }

    private static DailyBriefAnalysisResponse toResponse(
            DailyBriefAnalysis analysis,
            List<DailyBriefEventResponse> events
    ) {
        return new DailyBriefAnalysisResponse(
                analysis.getId(),
                analysis.getDailyBrief().getId(),
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getHeadline(),
                analysis.getOverview(),
                analysis.getGeneratedAt(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt(),
                events
        );
    }
}
