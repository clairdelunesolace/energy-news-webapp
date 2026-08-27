package com.carya.energynews.watchlist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-26T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-26T02:00:00Z");

    @Mock
    private WatchlistRepository watchlistRepository;

    @Mock
    private KeywordRepository keywordRepository;

    @InjectMocks
    private WatchlistService watchlistService;

    @Test
    void listsEnabledWatchlistsFirstAndSortsNamesAndKeywords() {
        Watchlist disabled = watchlist(2L, "储能", false);
        disabled.addKeyword("LDES");
        disabled.addKeyword("BESS");
        Watchlist enabled = watchlist(1L, "Data Center", true);
        when(watchlistRepository.findAll()).thenReturn(List.of(disabled, enabled));

        List<WatchlistResponse> result = watchlistService.getAll();

        assertThat(result).extracting(WatchlistResponse::name)
                .containsExactly("Data Center", "储能");
        assertThat(result.get(1).keywords()).extracting(KeywordResponse::keyword)
                .containsExactly("BESS", "LDES");
    }

    @Test
    void getsWatchlistWithOwnedKeywords() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        watchlist.addKeyword("GB200");
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));

        WatchlistResponse result = watchlistService.getById(1L);

        assertThat(result.name()).isEqualTo("NVIDIA");
        assertThat(result.keywords()).extracting(KeywordResponse::keyword)
                .containsExactly("GB200");
    }

    @Test
    void rejectsUnknownWatchlist() {
        when(watchlistRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.getById(99L))
                .isInstanceOf(WatchlistNotFoundException.class)
                .hasMessage("Watchlist with id 99 was not found");
    }

    @Test
    void createsTrimmedEnabledWatchlist() {
        when(watchlistRepository.existsByNameIgnoreCase("Data Center")).thenReturn(false);
        when(watchlistRepository.saveAndFlush(any(Watchlist.class))).thenAnswer(invocation -> {
            Watchlist watchlist = invocation.getArgument(0);
            setPersistedFields(watchlist, 1L);
            return watchlist;
        });

        WatchlistResponse result = watchlistService.create(
                new CreateWatchlistRequest("  Data Center  ", null)
        );

        assertThat(result.name()).isEqualTo("Data Center");
        assertThat(result.enabled()).isTrue();
        verify(watchlistRepository).existsByNameIgnoreCase("Data Center");
    }

    @Test
    void createsExplicitlyDisabledWatchlist() {
        when(watchlistRepository.saveAndFlush(any(Watchlist.class))).thenAnswer(invocation -> {
            Watchlist watchlist = invocation.getArgument(0);
            setPersistedFields(watchlist, 1L);
            return watchlist;
        });

        WatchlistResponse result = watchlistService.create(
                new CreateWatchlistRequest("储能", false)
        );

        assertThat(result.enabled()).isFalse();
    }

    @Test
    void rejectsDuplicateWatchlistNameIgnoringCaseAndWhitespace() {
        when(watchlistRepository.existsByNameIgnoreCase("data center")).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.create(
                new CreateWatchlistRequest(" data center ", null)
        )).isInstanceOf(DuplicateWatchlistNameException.class);

        verify(watchlistRepository, never()).saveAndFlush(any());
    }

    @Test
    void renamesAndDisablesWatchlistWithoutNullingFields() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(watchlistRepository.saveAndFlush(watchlist)).thenReturn(watchlist);

        WatchlistResponse result = watchlistService.update(
                1L,
                new UpdateWatchlistRequest("  AI Infrastructure  ", false)
        );

        assertThat(result.name()).isEqualTo("AI Infrastructure");
        assertThat(result.enabled()).isFalse();
        verify(watchlistRepository)
                .existsByNameIgnoreCaseAndIdNot("AI Infrastructure", 1L);
    }

    @Test
    void partialWatchlistUpdatePreservesName() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(watchlistRepository.saveAndFlush(watchlist)).thenReturn(watchlist);

        WatchlistResponse result = watchlistService.update(
                1L,
                new UpdateWatchlistRequest(null, false)
        );

        assertThat(result.name()).isEqualTo("NVIDIA");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void rejectsDuplicateWatchlistRename() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(watchlistRepository.existsByNameIgnoreCaseAndIdNot("储能", 1L))
                .thenReturn(true);

        assertThatThrownBy(() -> watchlistService.update(
                1L,
                new UpdateWatchlistRequest(" 储能 ", null)
        )).isInstanceOf(DuplicateWatchlistNameException.class);
    }

    @Test
    void deletesWatchlist() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));

        watchlistService.delete(1L);

        verify(watchlistRepository).delete(watchlist);
        verify(watchlistRepository).flush();
    }

    @Test
    void addsTrimmedEnabledKeyword() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(keywordRepository.saveAndFlush(any(Keyword.class))).thenAnswer(invocation -> {
            Keyword keyword = invocation.getArgument(0);
            setPersistedFields(keyword, 10L);
            return keyword;
        });

        KeywordResponse result = watchlistService.addKeyword(
                1L,
                new CreateKeywordRequest("  GB200  ", null)
        );

        assertThat(result.keyword()).isEqualTo("GB200");
        assertThat(result.enabled()).isTrue();
        verify(keywordRepository)
                .existsByWatchlistIdAndKeywordIgnoreCase(1L, "GB200");
    }

    @Test
    void rejectsDuplicateKeywordWithinWatchlistIgnoringCase() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(watchlist));
        when(keywordRepository.existsByWatchlistIdAndKeywordIgnoreCase(1L, "nvidia"))
                .thenReturn(true);

        assertThatThrownBy(() -> watchlistService.addKeyword(
                1L,
                new CreateKeywordRequest(" nvidia ", null)
        )).isInstanceOf(DuplicateKeywordException.class);

        verify(keywordRepository, never()).saveAndFlush(any());
    }

    @Test
    void allowsSameKeywordInDifferentWatchlists() {
        Watchlist first = watchlist(1L, "A", true);
        Watchlist second = watchlist(2L, "B", true);
        when(watchlistRepository.findById(1L)).thenReturn(Optional.of(first));
        when(watchlistRepository.findById(2L)).thenReturn(Optional.of(second));
        when(keywordRepository.saveAndFlush(any(Keyword.class))).thenAnswer(invocation -> {
            Keyword keyword = invocation.getArgument(0);
            setPersistedFields(keyword, keyword.getWatchlist().getId() * 10);
            return keyword;
        });

        watchlistService.addKeyword(1L, new CreateKeywordRequest("battery", null));
        watchlistService.addKeyword(2L, new CreateKeywordRequest("battery", null));

        verify(keywordRepository).existsByWatchlistIdAndKeywordIgnoreCase(1L, "battery");
        verify(keywordRepository).existsByWatchlistIdAndKeywordIgnoreCase(2L, "battery");
    }

    @Test
    void rejectsKeywordForUnknownWatchlist() {
        when(watchlistRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.addKeyword(
                99L,
                new CreateKeywordRequest("battery", null)
        )).isInstanceOf(WatchlistNotFoundException.class);
    }

    @Test
    void editsAndDisablesKeyword() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        Keyword keyword = keyword(watchlist, 10L, "GB200", true);
        when(keywordRepository.findById(10L)).thenReturn(Optional.of(keyword));
        when(keywordRepository.saveAndFlush(keyword)).thenReturn(keyword);

        KeywordResponse result = watchlistService.updateKeyword(
                10L,
                new UpdateKeywordRequest("  Rubin  ", false)
        );

        assertThat(result.keyword()).isEqualTo("Rubin");
        assertThat(result.enabled()).isFalse();
        verify(keywordRepository)
                .existsByWatchlistIdAndKeywordIgnoreCaseAndIdNot(1L, "Rubin", 10L);
    }

    @Test
    void partialKeywordUpdatePreservesText() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        Keyword keyword = keyword(watchlist, 10L, "GB200", true);
        when(keywordRepository.findById(10L)).thenReturn(Optional.of(keyword));
        when(keywordRepository.saveAndFlush(keyword)).thenReturn(keyword);

        KeywordResponse result = watchlistService.updateKeyword(
                10L,
                new UpdateKeywordRequest(null, false)
        );

        assertThat(result.keyword()).isEqualTo("GB200");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void rejectsDuplicateKeywordEdit() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        Keyword keyword = keyword(watchlist, 10L, "GB200", true);
        when(keywordRepository.findById(10L)).thenReturn(Optional.of(keyword));
        when(keywordRepository.existsByWatchlistIdAndKeywordIgnoreCaseAndIdNot(
                1L,
                "nvidia",
                10L
        )).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.updateKeyword(
                10L,
                new UpdateKeywordRequest(" nvidia ", null)
        )).isInstanceOf(DuplicateKeywordException.class);
    }

    @Test
    void deletesKeywordAndRejectsUnknownKeyword() {
        Watchlist watchlist = watchlist(1L, "NVIDIA", true);
        Keyword keyword = keyword(watchlist, 10L, "GB200", true);
        when(keywordRepository.findById(10L)).thenReturn(Optional.of(keyword));
        when(keywordRepository.findById(99L)).thenReturn(Optional.empty());

        watchlistService.deleteKeyword(10L);

        verify(keywordRepository).delete(keyword);
        verify(keywordRepository).flush();
        assertThatThrownBy(() -> watchlistService.deleteKeyword(99L))
                .isInstanceOf(KeywordNotFoundException.class)
                .hasMessage("Keyword with id 99 was not found");
    }

    private static Watchlist watchlist(Long id, String name, boolean enabled) {
        Watchlist watchlist = new Watchlist(name);
        watchlist.setEnabled(enabled);
        setPersistedFields(watchlist, id);
        return watchlist;
    }

    private static Keyword keyword(
            Watchlist watchlist,
            Long id,
            String text,
            boolean enabled
    ) {
        Keyword keyword = new Keyword(watchlist, text);
        keyword.setEnabled(enabled);
        setPersistedFields(keyword, id);
        return keyword;
    }

    private static void setPersistedFields(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(entity, "updatedAt", UPDATED_AT);
    }
}
