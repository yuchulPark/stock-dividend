package com.stock.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("external.opendart")
public class OpenDartProperties {
    private String apiKey = "", reportCode = "11011";
    private int years = 5;
    private Duration connectTimeout = Duration.ofSeconds(5), readTimeout = Duration.ofSeconds(10);
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getReportCode() { return reportCode; }
    public void setReportCode(String value) { reportCode = value; }
    public int getYears() { return years; }
    public void setYears(int value) { years = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = value; }
    @jakarta.annotation.PostConstruct void validate() {
        if (years < 1 || years > 10 || !java.util.Set.of("11011", "11012", "11013", "11014").contains(reportCode))
            throw new IllegalArgumentException("Invalid OpenDART report scope");
    }
}
