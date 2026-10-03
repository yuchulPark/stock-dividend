package com.stock.external.alphavantage;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.*;
import java.util.Locale;
import com.fasterxml.jackson.databind.*;
import com.stock.config.AlphaVantageProperties;
import com.stock.exception.ApiException;
import com.stock.service.ProviderCallBudgetService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.*;
@Component
public class AlphaVantageClient {
    private final RestClient rest;
    private final ObjectMapper mapper;
    private final AlphaVantageProperties properties;
    private final ProviderCallBudgetService budget;
    private final Clock clock;
    private Instant blockedUntil = Instant.MIN;
    private ApiException blockedError;
    public AlphaVantageClient(@Qualifier("alphaVantageRestClient") RestClient alphaVantageRestClient, ObjectMapper mapper,
            AlphaVantageProperties properties, ProviderCallBudgetService budget, Clock clock) {
        rest = alphaVantageRestClient; this.mapper = mapper; this.properties = properties; this.budget = budget; this.clock = clock;
    }
    public <T> T get(String function, String parameter, String value, Class<T> type) {
        authorize();
        try {
            String body = rest.get().uri(uri -> uri.path("/query").queryParam("function", function)
                    .queryParam(parameter, value).queryParam("datatype", "json")
                    .queryParam("apikey", properties.getApiKey()).build()).retrieve().body(String.class);
            if (body == null) throw ApiException.invalidResponse();
            JsonNode json = mapper.readTree(body);
            if (json == null || !json.isObject()) throw ApiException.invalidResponse();
            checkError(json);
            return mapper.treeToValue(json, type);
        } catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) block(ex);
            throw ex;
        } catch (RestClientResponseException ex) {
            ApiException error = ex.getStatusCode().value() == 429 ? ApiException.rateLimit()
                    : ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403 ? invalidKey()
                    : new ApiException("EXTERNAL_API_UNAVAILABLE", "외부 금융 데이터 API를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
            block(error); throw error;
        } catch (ResourceAccessException ex) {
            boolean timeout = false;
            for (Throwable cause = ex; cause != null; cause = cause.getCause())
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) timeout = true;
            ApiException error = new ApiException(timeout ? "EXTERNAL_API_TIMEOUT" : "EXTERNAL_API_UNAVAILABLE",
                    timeout ? "외부 금융 데이터 API 응답 시간이 초과되었습니다." : "외부 금융 데이터 API에 연결할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
            block(error); throw error;
        } catch (Exception ex) {
            ApiException error = ApiException.invalidResponse(); block(error); throw error;
        }
    }
    public <T, R> R get(String function, String parameter, String value, Class<T> type, java.util.function.Function<T, R> transform) {
        try { return transform.apply(get(function, parameter, value, type)); }
        catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) block(ex);
            throw ex;
        }
    }
    private synchronized void authorize() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw new ApiException("EXTERNAL_API_KEY_MISSING", "Alpha Vantage API Key가 설정되어 있지 않습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        if (clock.instant().isBefore(blockedUntil)) throw blockedError;
        budget.reserve();
    }
    private synchronized void block(ApiException error) {
        blockedError = error; blockedUntil = clock.instant().plus(properties.getFailureCooldown());
    }
    private static ApiException invalidKey() {
        return new ApiException("EXTERNAL_API_KEY_INVALID", "Alpha Vantage API Key의 유효성 또는 접근 권한을 확인해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
    }
    private static void checkError(JsonNode json) {
        for (String field : new String[]{"Information", "Note", "Error Message"}) {
            if (!json.has(field)) continue;
            String text = json.path(field).asText("").toLowerCase(Locale.ROOT);
            if ((text.contains("api key") || text.contains("apikey")) && (text.contains("invalid") || text.contains("missing") || text.contains("not valid"))) throw invalidKey();
            if (text.contains("rate") || text.contains("limit") || text.contains("frequency") || text.contains("25 requests")) throw ApiException.rateLimit();
            if (text.contains("premium")) throw new ApiException("EXTERNAL_API_ACCESS_DENIED", "해당 금융 데이터의 API 이용 권한을 확인해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
            // An 'Invalid API call' does not prove that a key or ticker is invalid.
            throw new ApiException("EXTERNAL_API_REQUEST_REJECTED", "외부 금융 데이터 API가 요청을 처리하지 못했습니다.", HttpStatus.BAD_GATEWAY);
        }
    }
}
