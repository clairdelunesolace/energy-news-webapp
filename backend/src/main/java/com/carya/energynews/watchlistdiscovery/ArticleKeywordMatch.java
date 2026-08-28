package com.carya.energynews.watchlistdiscovery;

import com.carya.energynews.article.Article;
import com.carya.energynews.watchlist.Keyword;
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

import java.time.Instant;

@Entity
@Table(
        name = "article_keyword_matches",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_article_keyword_matches_article_keyword",
                columnNames = {"article_id", "keyword_id"}
        )
)
public class ArticleKeywordMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "article_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_article_keyword_matches_article")
    )
    private Article article;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "keyword_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_article_keyword_matches_keyword")
    )
    private Keyword keyword;

    @Column(name = "matched_at", nullable = false, updatable = false)
    private Instant matchedAt;

    protected ArticleKeywordMatch() {
    }

    public ArticleKeywordMatch(Article article, Keyword keyword) {
        this.article = article;
        this.keyword = keyword;
    }

    @PrePersist
    void onCreate() {
        matchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Article getArticle() {
        return article;
    }

    public Keyword getKeyword() {
        return keyword;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }
}
