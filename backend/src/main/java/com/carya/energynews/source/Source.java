package com.carya.energynews.source;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(
        name = "sources",
        uniqueConstraints = @UniqueConstraint(name = "uk_sources_url", columnNames = "url")
)
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    private SourceType type;

    @Enumerated(EnumType.STRING)
    private SourcePriority priority;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'EN'")
    private SourceLanguage language = SourceLanguage.EN;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Source() {
    }

    public Source(String name, String url, SourceType type, SourcePriority priority) {
        this(name, url, type, priority, SourceLanguage.EN);
    }

    public Source(
            String name,
            String url,
            SourceType type,
            SourcePriority priority,
            SourceLanguage language
    ) {
        this.name = name;
        this.url = url;
        this.type = type;
        this.priority = priority;
        this.language = language == null ? SourceLanguage.EN : language;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public SourceType getType() {
        return type;
    }

    public void setType(SourceType type) {
        this.type = type;
    }

    public SourcePriority getPriority() {
        return priority;
    }

    public void setPriority(SourcePriority priority) {
        this.priority = priority;
    }

    public SourceLanguage getLanguage() {
        return language;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
