package com.carya.energynews.watchlistdiscovery;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist-discovery")
public class WatchlistDiscoveryController {

    private final WatchlistDiscoveryService discoveryService;

    public WatchlistDiscoveryController(WatchlistDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @PostMapping("/run")
    public WatchlistDiscoveryRunResponse run(
            @Valid @RequestBody WatchlistDiscoveryRunRequest request
    ) {
        return discoveryService.run(request);
    }
}
