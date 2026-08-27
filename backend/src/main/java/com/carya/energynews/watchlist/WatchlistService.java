package com.carya.energynews.watchlist;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class WatchlistService {

    private static final Comparator<Watchlist> WATCHLIST_ORDER = Comparator
            .comparing(Watchlist::isEnabled)
            .reversed()
            .thenComparing(Watchlist::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Watchlist::getId, Comparator.nullsLast(Long::compareTo));

    private static final Comparator<Keyword> KEYWORD_ORDER = Comparator
            .comparing(Keyword::getKeyword, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Keyword::getId, Comparator.nullsLast(Long::compareTo));

    private final WatchlistRepository watchlistRepository;
    private final KeywordRepository keywordRepository;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            KeywordRepository keywordRepository
    ) {
        this.watchlistRepository = watchlistRepository;
        this.keywordRepository = keywordRepository;
    }

    @Transactional(readOnly = true)
    public List<WatchlistResponse> getAll() {
        return watchlistRepository.findAll().stream()
                .sorted(WATCHLIST_ORDER)
                .map(WatchlistService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WatchlistResponse getById(Long id) {
        return toResponse(findWatchlist(id));
    }

    public WatchlistResponse create(CreateWatchlistRequest request) {
        String name = request.name().trim();
        ensureUniqueWatchlistName(name, null);

        Watchlist watchlist = new Watchlist(name);
        watchlist.setEnabled(request.enabled() == null || request.enabled());

        try {
            return toResponse(watchlistRepository.saveAndFlush(watchlist));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateWatchlistNameException(name, exception);
        }
    }

    public WatchlistResponse update(Long id, UpdateWatchlistRequest request) {
        Watchlist watchlist = findWatchlist(id);

        if (request.name() != null) {
            String name = request.name().trim();
            ensureUniqueWatchlistName(name, id);
            watchlist.setName(name);
        }
        if (request.enabled() != null) {
            watchlist.setEnabled(request.enabled());
        }

        try {
            return toResponse(watchlistRepository.saveAndFlush(watchlist));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateWatchlistNameException(watchlist.getName(), exception);
        }
    }

    public void delete(Long id) {
        Watchlist watchlist = findWatchlist(id);
        watchlistRepository.delete(watchlist);
        watchlistRepository.flush();
    }

    public KeywordResponse addKeyword(Long watchlistId, CreateKeywordRequest request) {
        Watchlist watchlist = findWatchlist(watchlistId);
        String text = request.keyword().trim();
        ensureUniqueKeyword(watchlistId, text, null);

        Keyword keyword = watchlist.addKeyword(text);
        keyword.setEnabled(request.enabled() == null || request.enabled());

        try {
            return toResponse(keywordRepository.saveAndFlush(keyword));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateKeywordException(text, exception);
        }
    }

    public KeywordResponse updateKeyword(Long id, UpdateKeywordRequest request) {
        Keyword keyword = findKeyword(id);

        if (request.keyword() != null) {
            String text = request.keyword().trim();
            ensureUniqueKeyword(keyword.getWatchlist().getId(), text, id);
            keyword.setKeyword(text);
        }
        if (request.enabled() != null) {
            keyword.setEnabled(request.enabled());
        }

        try {
            return toResponse(keywordRepository.saveAndFlush(keyword));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateKeywordException(keyword.getKeyword(), exception);
        }
    }

    public void deleteKeyword(Long id) {
        Keyword keyword = findKeyword(id);
        keywordRepository.delete(keyword);
        keywordRepository.flush();
    }

    private Watchlist findWatchlist(Long id) {
        return watchlistRepository.findById(id)
                .orElseThrow(() -> new WatchlistNotFoundException(id));
    }

    private Keyword findKeyword(Long id) {
        return keywordRepository.findById(id)
                .orElseThrow(() -> new KeywordNotFoundException(id));
    }

    private void ensureUniqueWatchlistName(String name, Long excludedId) {
        boolean exists = excludedId == null
                ? watchlistRepository.existsByNameIgnoreCase(name)
                : watchlistRepository.existsByNameIgnoreCaseAndIdNot(name, excludedId);
        if (exists) {
            throw new DuplicateWatchlistNameException(name);
        }
    }

    private void ensureUniqueKeyword(Long watchlistId, String keyword, Long excludedId) {
        boolean exists = excludedId == null
                ? keywordRepository.existsByWatchlistIdAndKeywordIgnoreCase(watchlistId, keyword)
                : keywordRepository.existsByWatchlistIdAndKeywordIgnoreCaseAndIdNot(
                        watchlistId,
                        keyword,
                        excludedId
                );
        if (exists) {
            throw new DuplicateKeywordException(keyword);
        }
    }

    private static WatchlistResponse toResponse(Watchlist watchlist) {
        List<KeywordResponse> keywords = watchlist.getKeywords().stream()
                .sorted(KEYWORD_ORDER)
                .map(WatchlistService::toResponse)
                .toList();
        return new WatchlistResponse(
                watchlist.getId(),
                watchlist.getName(),
                watchlist.isEnabled(),
                watchlist.getCreatedAt(),
                watchlist.getUpdatedAt(),
                keywords
        );
    }

    private static KeywordResponse toResponse(Keyword keyword) {
        return new KeywordResponse(
                keyword.getId(),
                keyword.getKeyword(),
                keyword.isEnabled(),
                keyword.getCreatedAt(),
                keyword.getUpdatedAt()
        );
    }
}
