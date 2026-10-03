package com.stock.config;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("external.krx")
public class KrxProperties {
    private String authKey = "";
    private int lookbackDays = 14;
    private Duration connectTimeout = Duration.ofSeconds(5), readTimeout = Duration.ofSeconds(10);
    public String getAuthKey() { return authKey; }
    public void setAuthKey(String value) { authKey = value; }
    public int getLookbackDays() { return lookbackDays; }
    public void setLookbackDays(int value) { lookbackDays = value; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = value; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration value) { readTimeout = value; }
    @jakarta.annotation.PostConstruct void validate() {
        if (lookbackDays < 1 || lookbackDays > 31) throw new IllegalArgumentException("KRX lookback must be 1..31 days");
    }
}
