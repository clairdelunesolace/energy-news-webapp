package com.carya.energynews.watchlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class WatchlistRepositoryTest {

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Test
    void persistsDefaultsTrimmedValuesAndTimestamps() {
        Watchlist watchlist = new Watchlist("  NVIDIA  ");
        Keyword keyword = watchlist.addKeyword("  GB200  ");

        Watchlist saved = watchlistRepository.saveAndFlush(watchlist);

        assertThat(saved.getName()).isEqualTo("NVIDIA");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(keyword.getKeyword()).isEqualTo("GB200");
        assertThat(keyword.isEnabled()).isTrue();
        assertThat(keyword.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateLookupsIgnoreCase() {
        Watchlist watchlist = watchlistRepository.saveAndFlush(new Watchlist("Data Center"));
        watchlist.addKeyword("NVIDIA");
        watchlistRepository.saveAndFlush(watchlist);

        assertThat(watchlistRepository.existsByNameIgnoreCase("data center")).isTrue();
        assertThat(keywordRepository.existsByWatchlistIdAndKeywordIgnoreCase(
                watchlist.getId(),
                "nvidia"
        )).isTrue();
    }

    @Test
    void databaseRejectsExactDuplicateWatchlistNames() {
        watchlistRepository.saveAndFlush(new Watchlist("NVIDIA"));

        assertThatThrownBy(() -> watchlistRepository.saveAndFlush(new Watchlist("NVIDIA")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsExactDuplicateKeywordWithinWatchlist() {
        Watchlist watchlist = new Watchlist("NVIDIA");
        watchlist.addKeyword("GB200");
        watchlist.addKeyword("GB200");

        assertThatThrownBy(() -> watchlistRepository.saveAndFlush(watchlist))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameKeywordIsAllowedInDifferentWatchlists() {
        Watchlist first = new Watchlist("First");
        first.addKeyword("battery");
        Watchlist second = new Watchlist("Second");
        second.addKeyword("battery");

        watchlistRepository.saveAndFlush(first);
        watchlistRepository.saveAndFlush(second);

        assertThat(keywordRepository.count()).isEqualTo(2);
    }

    @Test
    void deletingWatchlistDeletesOwnedKeywords() {
        Watchlist watchlist = new Watchlist("NVIDIA");
        Keyword keyword = watchlist.addKeyword("GB200");
        watchlistRepository.saveAndFlush(watchlist);
        Long keywordId = keyword.getId();

        watchlistRepository.delete(watchlist);
        watchlistRepository.flush();

        assertThat(keywordRepository.existsById(keywordId)).isFalse();
    }
}
