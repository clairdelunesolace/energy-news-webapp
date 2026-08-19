package com.carya.energynews.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    boolean existsByUrl(String url);

    List<Source> findAllByEnabledTrue();
}
