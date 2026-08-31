package com.carya.energynews.dailybriefanalysis;

import com.carya.energynews.dailybrief.DailyBrief;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "daily_brief_analyses")
public class DailyBriefAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "daily_brief_id",
            nullable = false,
            unique = true,
            foreignKey = @ForeignKey(name = "fk_daily_brief_analyses_brief")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DailyBrief dailyBrief;

    @NotNull
    @Column(nullable = false, length = 50)
    private String provider;

    @NotNull
    @Column(nullable = false, length = 200)
    private String model;

    @NotNull
    @Column(nullable = false, columnDefinition = "text")
    private String headline;

    @NotNull
    @Column(nullable = false, columnDefinition = "text")
    private String overview;

    @NotNull
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyBriefAnalysis() {
    }

    public DailyBriefAnalysis(DailyBrief dailyBrief) {
        this.dailyBrief = dailyBrief;
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

    public void update(
            String provider,
            String model,
            String headline,
            String overview,
            Instant generatedAt
    ) {
        this.provider = provider;
        this.model = model;
        this.headline = headline;
        this.overview = overview;
        this.generatedAt = generatedAt;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public DailyBrief getDailyBrief() {
        return dailyBrief;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getHeadline() {
        return headline;
    }

    public String getOverview() {
        return overview;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
