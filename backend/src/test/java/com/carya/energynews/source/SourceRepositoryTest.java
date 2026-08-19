package com.carya.energynews.source;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class SourceRepositoryTest {

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsSourceWithDefaultsEnumsAndTimestamps() {
        Source source = new Source(
                "Energy Storage News",
                "https://example.com/feed",
                SourceType.RSS,
                SourcePriority.HIGH
        );

        Source saved = sourceRepository.saveAndFlush(source);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(jdbcTemplate.queryForObject(
                "select type from sources where id = ?",
                String.class,
                saved.getId()
        )).isEqualTo("RSS");
        assertThat(jdbcTemplate.queryForObject(
                "select priority from sources where id = ?",
                String.class,
                saved.getId()
        )).isEqualTo("HIGH");
    }

    @Test
    void updatesUpdatedAtWithoutChangingCreatedAt() {
        Source source = sourceRepository.saveAndFlush(new Source(
                "Original name",
                "https://example.com/original",
                SourceType.WEBSITE,
                SourcePriority.MEDIUM
        ));
        Instant createdAt = source.getCreatedAt();
        Instant updatedAt = source.getUpdatedAt();

        source.setName("Updated name");
        sourceRepository.flush();

        assertThat(source.getCreatedAt()).isEqualTo(createdAt);
        assertThat(source.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void rejectsDuplicateUrl() {
        sourceRepository.saveAndFlush(new Source(
                "First source",
                "https://example.com/duplicate",
                SourceType.API,
                SourcePriority.LOW
        ));

        Source duplicate = new Source(
                "Second source",
                "https://example.com/duplicate",
                SourceType.RSS,
                SourcePriority.HIGH
        );

        assertThatThrownBy(() -> sourceRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNullName() {
        Source source = new Source(
                null,
                "https://example.com/no-name",
                SourceType.API,
                SourcePriority.MEDIUM
        );

        assertThatThrownBy(() -> sourceRepository.saveAndFlush(source))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsNullUrl() {
        Source source = new Source(
                "Missing URL",
                null,
                SourceType.WEBSITE,
                SourcePriority.LOW
        );

        assertThatThrownBy(() -> sourceRepository.saveAndFlush(source))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
