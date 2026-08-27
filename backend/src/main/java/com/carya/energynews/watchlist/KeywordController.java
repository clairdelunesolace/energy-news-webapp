package com.carya.energynews.watchlist;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/keywords")
public class KeywordController {

    private final WatchlistService watchlistService;

    public KeywordController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @PatchMapping("/{id}")
    public KeywordResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKeywordRequest request
    ) {
        return watchlistService.updateKeyword(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        watchlistService.deleteKeyword(id);
    }
}
