package com.stock.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("external.alpha-vantage")
public class AlphaVantageProperties {
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
    private int dailyLimit = 25;
    private Duration failureCooldown = Duration.ofMinutes(5);
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }
    public Duration getFailureCooldown() { return failureCooldown; }
    public void setFailureCooldown(Duration failureCooldown) { this.failureCooldown = failureCooldown; }
    @jakarta.annotation.PostConstruct void validate() {
        if (dailyLimit < 1 || failureCooldown == null || failureCooldown.isNegative() || failureCooldown.isZero())
            throw new IllegalArgumentException("Daily limit and failure cooldown must be positive");
    }
}
