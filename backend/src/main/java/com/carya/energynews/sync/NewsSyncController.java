package com.carya.energynews.sync;

import com.carya.energynews.source.Source;
import com.carya.energynews.source.SourceNotFoundException;
import com.carya.energynews.source.SourceRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news-sync")
public class NewsSyncController {

    private final NewsSyncService newsSyncService;
    private final SourceRepository sourceRepository;

    public NewsSyncController(NewsSyncService newsSyncService, SourceRepository sourceRepository) {
        this.newsSyncService = newsSyncService;
        this.sourceRepository = sourceRepository;
    }

    @PostMapping
    public NewsSyncResult syncAllEnabledSources() {
        return newsSyncService.syncAllEnabledSources();
    }

    @PostMapping("/sources/{sourceId}")
    public NewsSyncResult syncSource(@PathVariable Long sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new SourceNotFoundException(sourceId));
        return newsSyncService.sync(source);
    }
}
