package com.carya.energynews.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/discovery")
public class NewsDiscoveryPreviewController {

    private final Optional<NewsDiscoveryService> discoveryService;
    private final NewsDiscoveryQueryFactory queryFactory;

    public NewsDiscoveryPreviewController(
            Optional<NewsDiscoveryService> discoveryService,
            NewsDiscoveryQueryFactory queryFactory
    ) {
        this.discoveryService = discoveryService;
        this.queryFactory = queryFactory;
    }

    @GetMapping("/preview")
    public NewsDiscoveryPreviewResponse preview(
            @RequestParam String keyword,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "20") int limit
    ) {
        NewsDiscoveryService service = discoveryService.orElseThrow(() ->
                new NewsDiscoveryProviderUnavailableException(
                        "News discovery provider is not configured."
                )
        );
        NewsDiscoveryQuery query = queryFactory.create(keyword, from, to, limit);
        List<DiscoveredArticle> results = service.discover(query);
        return new NewsDiscoveryPreviewResponse(
                service.providerName(),
                query.keyword(),
                results.size(),
                results
        );
    }
}
