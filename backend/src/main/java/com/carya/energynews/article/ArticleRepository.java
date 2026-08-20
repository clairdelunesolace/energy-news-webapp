package com.carya.energynews.article;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByUrl(String url);

    @EntityGraph(attributePaths = "source")
    Optional<Article> findByUrl(String url);
}
