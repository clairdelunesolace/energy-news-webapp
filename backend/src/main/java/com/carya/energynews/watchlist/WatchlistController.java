package com.carya.energynews.watchlist;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<WatchlistResponse> getAll() {
        return watchlistService.getAll();
    }

    @GetMapping("/{id}")
    public WatchlistResponse getById(@PathVariable Long id) {
        return watchlistService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistResponse create(@Valid @RequestBody CreateWatchlistRequest request) {
        return watchlistService.create(request);
    }

    @PatchMapping("/{id}")
    public WatchlistResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWatchlistRequest request
    ) {
        return watchlistService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        watchlistService.delete(id);
    }

    @PostMapping("/{watchlistId}/keywords")
    @ResponseStatus(HttpStatus.CREATED)
    public KeywordResponse addKeyword(
            @PathVariable Long watchlistId,
            @Valid @RequestBody CreateKeywordRequest request
    ) {
        return watchlistService.addKeyword(watchlistId, request);
    }
}
