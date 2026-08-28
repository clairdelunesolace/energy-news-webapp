package com.carya.energynews.dailybrief;

import com.carya.energynews.watchlist.Watchlist;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "daily_briefs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_briefs_watchlist_date",
                columnNames = {"watchlist_id", "brief_date"}
        )
)
public class DailyBrief {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "watchlist_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_briefs_watchlist")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Watchlist watchlist;

    @NotNull
    @Column(name = "brief_date", nullable = false)
    private LocalDate briefDate;

    @NotNull
    @Column(nullable = false, length = 64)
    private String zone;

    @NotNull
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @NotNull
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyBrief() {
    }

    public DailyBrief(
            Watchlist watchlist,
            LocalDate briefDate,
            String zone,
            Instant windowStart,
            Instant windowEnd,
            int candidateCount
    ) {
        this.watchlist = watchlist;
        this.briefDate = briefDate;
        updateSnapshot(zone, windowStart, windowEnd, candidateCount);
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

    public void updateSnapshot(
            String zone,
            Instant windowStart,
            Instant windowEnd,
            int candidateCount
    ) {
        this.zone = zone;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.candidateCount = candidateCount;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Watchlist getWatchlist() {
        return watchlist;
    }

    public LocalDate getBriefDate() {
        return briefDate;
    }

    public String getZone() {
        return zone;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public int getCandidateCount() {
        return candidateCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
