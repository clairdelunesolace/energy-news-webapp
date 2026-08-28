package com.carya.energynews.dailybrief;

import com.carya.energynews.article.Article;
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
        name = "daily_brief_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_brief_items_brief_article",
                columnNames = {"daily_brief_id", "article_id"}
        )
)
public class DailyBriefItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "daily_brief_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_brief_items_brief")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DailyBrief dailyBrief;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "article_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_brief_items_article")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Article article;

    @Column(name = "item_rank", nullable = false)
    private int rank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DailyBriefItem() {
    }

    public DailyBriefItem(DailyBrief dailyBrief, Article article, int rank) {
        this.dailyBrief = dailyBrief;
        this.article = article;
        this.rank = rank;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public DailyBrief getDailyBrief() {
        return dailyBrief;
    }

    public Article getArticle() {
        return article;
    }

    public int getRank() {
        return rank;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
