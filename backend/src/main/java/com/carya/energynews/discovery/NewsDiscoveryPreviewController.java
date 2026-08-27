package com.carya.energynews.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/discovery")
public class NewsDiscoveryPreviewController {

    private final Optional<NewsDiscoveryService> discoveryService;
    private final Clock clock;

    public NewsDiscoveryPreviewController(
            Optional<NewsDiscoveryService> discoveryService,
            Clock clock
    ) {
        this.discoveryService = discoveryService;
        this.clock = Objects.requireNonNull(clock, "Discovery clock is required");
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
        NewsDiscoveryQuery query = new NewsDiscoveryQuery(
                keyword,
                startOfDate(from),
                endOfDate(to),
                limit
        );
        List<DiscoveredArticle> results = service.discover(query);
        return new NewsDiscoveryPreviewResponse(
                service.providerName(),
                query.keyword(),
                results.size(),
                results
        );
    }

    private Instant startOfDate(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant endOfDate(LocalDate date) {
        if (date == null) {
            return null;
        }

        Instant now = clock.instant();
        LocalDate currentUtcDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!date.isBefore(currentUtcDate)) {
            return now;
        }
        return date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }
}
