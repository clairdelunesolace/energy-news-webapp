package com.carya.energynews.source;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    boolean existsByUrl(String url);
}
