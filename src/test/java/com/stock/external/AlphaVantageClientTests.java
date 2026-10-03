package com.stock.external;
import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.AlphaVantageProperties;
import com.stock.exception.ApiException;
import com.stock.external.alphavantage.AlphaVantageClient;
import com.stock.external.alphavantage.dto.AlphaVantageResponse;
import com.stock.service.ProviderCallBudgetService;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class AlphaVantageClientTests {
    @Test void missingKeyDoesNotCallNetworkOrConsumeBudget() {
        var budget = mock(ProviderCallBudgetService.class);
        var client = new AlphaVantageClient(RestClient.create(), new ObjectMapper(), new AlphaVantageProperties(), budget, Clock.systemUTC());
        assertThatThrownBy(() -> client.get("GLOBAL_QUOTE", "symbol", "IBM", AlphaVantageResponse.Quote.class))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("EXTERNAL_API_KEY_MISSING");
        verifyNoInteractions(budget);
    }
    @Test void bodyRateLimitIsSanitizedAndSubsequentCallsAreBlocked() {
        var builder = RestClient.builder().baseUrl("https://www.alphavantage.co");
        var server = MockRestServiceServer.bindTo(builder).build();
        var props = new AlphaVantageProperties(); props.setApiKey("mock-key");
        var budget = mock(ProviderCallBudgetService.class);
        var client = new AlphaVantageClient(builder.build(), new ObjectMapper(), props, budget, Clock.systemUTC());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("function=GLOBAL_QUOTE")))
                .andRespond(withSuccess("{\"Information\":\"API rate limit reached; mock-key\"}", MediaType.APPLICATION_JSON));
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> client.get("GLOBAL_QUOTE", "symbol", "IBM", AlphaVantageResponse.Quote.class))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("mock-key").extracting("code").isEqualTo("EXTERNAL_API_RATE_LIMIT");
        verify(budget, times(1)).reserve(); server.verify();
    }
    @Test void invalidKeyAndHttp429AreDistinctFromMalformedJson() {
        assertCode("{\"Error Message\":\"Invalid API key\"}", HttpStatus.OK, "EXTERNAL_API_KEY_INVALID");
        assertCode("{}", HttpStatus.TOO_MANY_REQUESTS, "EXTERNAL_API_RATE_LIMIT");
        assertCode("not json", HttpStatus.OK, "EXTERNAL_API_INVALID_RESPONSE");
    }
    @Test void timeoutIsSanitized() {
        var builder = RestClient.builder().baseUrl("https://www.alphavantage.co");
        var server = MockRestServiceServer.bindTo(builder).build();
        var props = new AlphaVantageProperties(); props.setApiKey("mock-key");
        var client = new AlphaVantageClient(builder.build(), new ObjectMapper(), props, mock(ProviderCallBudgetService.class), Clock.systemUTC());
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("secret URL")));
        assertThatThrownBy(() -> client.get("GLOBAL_QUOTE", "symbol", "IBM", AlphaVantageResponse.Quote.class))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("secret").extracting("code").isEqualTo("EXTERNAL_API_TIMEOUT");
        server.verify();
    }
    private void assertCode(String body, HttpStatus status, String code) {
        var builder = RestClient.builder().baseUrl("https://www.alphavantage.co");
        var server = MockRestServiceServer.bindTo(builder).build();
        var props = new AlphaVantageProperties(); props.setApiKey("mock-key");
        var client = new AlphaVantageClient(builder.build(), new ObjectMapper(), props, mock(ProviderCallBudgetService.class), Clock.systemUTC());
        server.expect(anything()).andRespond(withStatus(status).body(body).contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.get("GLOBAL_QUOTE", "symbol", "IBM", AlphaVantageResponse.Quote.class))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
        server.verify();
    }
}
