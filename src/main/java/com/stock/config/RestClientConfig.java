package com.stock.config;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
@EnableConfigurationProperties({AlphaVantageProperties.class, CacheProperties.class, KrxProperties.class, OpenDartProperties.class})
public class RestClientConfig {
    @Bean public Clock stockClock() { return Clock.systemUTC(); }
    @Bean public RestClient alphaVantageRestClient(RestClient.Builder builder, AlphaVantageProperties properties) {
        if (properties.getConnectTimeout().isNegative() || properties.getConnectTimeout().isZero()
                || properties.getReadTimeout().isNegative() || properties.getReadTimeout().isZero())
            throw new IllegalArgumentException("HTTP timeouts must be positive");
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl("https://www.alphavantage.co").requestFactory(factory).build();
    }
    @Bean public RestClient krxRestClient(RestClient.Builder builder, KrxProperties properties) {
        return officialClient(builder, "https://data-dbg.krx.co.kr", properties.getConnectTimeout(), properties.getReadTimeout());
    }
    @Bean public RestClient openDartRestClient(RestClient.Builder builder, OpenDartProperties properties) {
        return officialClient(builder, "https://opendart.fss.or.kr", properties.getConnectTimeout(), properties.getReadTimeout());
    }
    private RestClient officialClient(RestClient.Builder builder, String baseUrl, java.time.Duration connect, java.time.Duration read) {
        if (connect == null || read == null || connect.isNegative() || connect.isZero() || read.isNegative() || read.isZero())
            throw new IllegalArgumentException("HTTP timeouts must be positive");
        HttpClient http = HttpClient.newBuilder().connectTimeout(connect).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(read);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
