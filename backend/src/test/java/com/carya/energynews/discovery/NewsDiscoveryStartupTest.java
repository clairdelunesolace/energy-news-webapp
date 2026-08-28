package com.carya.energynews.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.security.admin.password=test-password",
        "app.discovery.provider=none",
        "spring.datasource.url=jdbc:h2:mem:discovery-startup;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NewsDiscoveryStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationStartsWithoutDiscoveryProviderOrService() {
        assertThat(applicationContext.getBeansOfType(NewsDiscoveryProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(NewsDiscoveryService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(
                com.carya.energynews.watchlistdiscovery.WatchlistDiscoveryScheduler.class
        )).isEmpty();
    }
}
