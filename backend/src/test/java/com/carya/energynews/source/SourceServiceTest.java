package com.carya.energynews.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @InjectMocks
    private SourceService sourceService;

    @Test
    void returnsAllSourcesAsDtos() {
        Source source = new Source(
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH
        );
        source.onCreate();
        when(sourceRepository.findAll()).thenReturn(List.of(source));

        List<SourceResponse> responses = sourceService.getAll();

        assertThat(responses).containsExactly(new SourceResponse(
                null,
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH,
                SourceLanguage.EN,
                true,
                false,
                source.getCreatedAt(),
                source.getUpdatedAt()
        ));
    }

    @Test
    void throwsWhenSourceDoesNotExist() {
        when(sourceRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sourceService.getById(42L))
                .isInstanceOf(SourceNotFoundException.class)
                .hasMessage("Source with id 42 was not found");
    }

    @Test
    void createsEnabledSource() {
        CreateSourceRequest request = new CreateSourceRequest(
                "API source",
                "https://example.com/api",
                SourceType.API,
                SourcePriority.MEDIUM
        );
        when(sourceRepository.existsByUrl(request.url())).thenReturn(false);
        when(sourceRepository.saveAndFlush(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            source.onCreate();
            return source;
        });

        SourceResponse response = sourceService.create(request);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).saveAndFlush(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().isEnabled()).isTrue();
        assertThat(sourceCaptor.getValue().getLanguage()).isEqualTo(SourceLanguage.EN);
        assertThat(sourceCaptor.getValue().isContentEnrichmentEnabled()).isFalse();
        assertThat(response.enabled()).isTrue();
        assertThat(response.language()).isEqualTo(SourceLanguage.EN);
        assertThat(response.contentEnrichmentEnabled()).isFalse();
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.url()).isEqualTo(request.url());
    }

    @Test
    void createsSourceWithContentEnrichmentEnabled() {
        CreateSourceRequest request = new CreateSourceRequest(
                "Qualified full-content source",
                "https://example.com/qualified-feed",
                SourceType.RSS,
                SourcePriority.HIGH,
                SourceLanguage.EN,
                true
        );
        when(sourceRepository.existsByUrl(request.url())).thenReturn(false);
        when(sourceRepository.saveAndFlush(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            source.onCreate();
            return source;
        });

        SourceResponse response = sourceService.create(request);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).saveAndFlush(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().isContentEnrichmentEnabled()).isTrue();
        assertThat(response.contentEnrichmentEnabled()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(SourceLanguage.class)
    void createsSourceWithExplicitLanguage(SourceLanguage language) {
        CreateSourceRequest request = new CreateSourceRequest(
                "Language source",
                "https://example.com/" + language.name().toLowerCase(),
                SourceType.RSS,
                SourcePriority.MEDIUM,
                language
        );
        when(sourceRepository.existsByUrl(request.url())).thenReturn(false);
        when(sourceRepository.saveAndFlush(any(Source.class))).thenAnswer(invocation -> {
            Source source = invocation.getArgument(0);
            source.onCreate();
            return source;
        });

        SourceResponse response = sourceService.create(request);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).saveAndFlush(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getLanguage()).isEqualTo(language);
        assertThat(response.language()).isEqualTo(language);
    }

    @Test
    void translatesDatabaseConstraintViolationIntoDuplicateUrlError() {
        CreateSourceRequest request = new CreateSourceRequest(
                "Duplicate source",
                "https://example.com/duplicate",
                SourceType.WEBSITE,
                SourcePriority.LOW
        );
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("unique constraint");
        when(sourceRepository.existsByUrl(request.url())).thenReturn(false);
        when(sourceRepository.saveAndFlush(any(Source.class))).thenThrow(databaseException);

        assertThatThrownBy(() -> sourceService.create(request))
                .isInstanceOf(DuplicateSourceUrlException.class)
                .hasMessage("A source with URL 'https://example.com/duplicate' already exists")
                .hasCause(databaseException);
    }
}
