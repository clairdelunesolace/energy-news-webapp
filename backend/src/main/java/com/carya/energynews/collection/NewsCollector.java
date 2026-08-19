package com.carya.energynews.collection;

import com.carya.energynews.source.Source;

import java.util.List;

public interface NewsCollector {

    List<CollectedArticle> collect(Source source);
}
