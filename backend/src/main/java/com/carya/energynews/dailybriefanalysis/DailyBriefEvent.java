package com.carya.energynews.dailybriefanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(
        name = "daily_brief_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_brief_events_analysis_rank",
                columnNames = {"analysis_id", "event_rank"}
        )
)
public class DailyBriefEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "analysis_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_brief_events_analysis")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DailyBriefAnalysis analysis;

    @Column(name = "event_rank", nullable = false)
    private int eventRank;

    @NotNull
    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @NotNull
    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @NotNull
    @Column(name = "why_it_matters", nullable = false, columnDefinition = "text")
    private String whyItMatters;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DailyBriefEvent() {
    }

    public DailyBriefEvent(
            DailyBriefAnalysis analysis,
            int eventRank,
            String title,
            String summary,
            String whyItMatters
    ) {
        this.analysis = analysis;
        this.eventRank = eventRank;
        this.title = title;
        this.summary = summary;
        this.whyItMatters = whyItMatters;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public DailyBriefAnalysis getAnalysis() {
        return analysis;
    }

    public int getEventRank() {
        return eventRank;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getWhyItMatters() {
        return whyItMatters;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
