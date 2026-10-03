package com.stock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class StockApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext context;

	@Test
	void contextLoads() {
	    org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(org.springframework.web.client.RestClient.class))
	            .containsKeys("alphaVantageRestClient", "krxRestClient", "openDartRestClient");
	    var registry = context.getBean(com.stock.external.market.MarketDataProviderRegistry.class);
	    org.assertj.core.api.Assertions.assertThat(registry.market(com.stock.entity.Market.US))
	            .isInstanceOf(com.stock.external.market.AlphaVantageMarketDataProvider.class);
	    org.assertj.core.api.Assertions.assertThat(registry.market(com.stock.entity.Market.KR))
	            .isInstanceOf(com.stock.external.market.KrxMarketDataProvider.class);
	    org.assertj.core.api.Assertions.assertThat(registry.requireDividends(com.stock.entity.Market.KR, com.stock.entity.AssetType.STOCK))
	            .isInstanceOf(com.stock.external.dividend.OpenDartDividendProvider.class);
	}

}
