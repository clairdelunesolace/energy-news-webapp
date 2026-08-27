package com.carya.energynews.discovery;

import java.util.List;

public interface NewsDiscoveryProvider {

    String providerName();

    List<DiscoveredArticle> discover(NewsDiscoveryQuery query);
}
