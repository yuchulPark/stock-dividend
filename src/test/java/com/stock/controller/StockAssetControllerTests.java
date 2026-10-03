package com.stock.controller;
import java.util.List;
import com.stock.entity.Market;
import com.stock.exception.*;
import com.stock.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class StockAssetControllerTests {
    private final AssetSyncService service = mock(AssetSyncService.class);
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new StockAssetController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void emptyDividendFallbackStillCarriesFreshnessHeader() throws Exception {
        when(service.getDividends(Market.US, "SCHD")).thenReturn(new CacheResult<>(List.of(), false, null, "EXTERNAL_API_RATE_LIMIT"));
        mvc().perform(get("/api/assets/US/SCHD/dividends")).andExpect(status().isOk())
                .andExpect(content().json("[]")).andExpect(header().string("X-Data-Fresh", "false"))
                .andExpect(header().string("X-Data-Warning", "EXTERNAL_API_RATE_LIMIT"));
    }
    @Test void errorsUseOurOwnContract() throws Exception {
        when(service.getAsset(Market.US, "SCHD")).thenThrow(ApiException.rateLimit());
        mvc().perform(get("/api/assets/US/SCHD")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_RATE_LIMIT"));
    }
    @Test void invalidMarketAndMissingKeywordAreBadRequests() throws Exception {
        mvc().perform(get("/api/assets/XX/SCHD")).andExpect(status().isBadRequest());
        mvc().perform(get("/api/assets/search")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void koreanSearchKeepsExistingArrayContractAndFreshnessHeader() throws Exception {
        var data = new com.stock.dto.response.AssetSearchResponse("005930", "삼성전자", Market.KR,
                com.stock.entity.AssetType.STOCK, com.stock.entity.Currency.KRW);
        when(service.search(Market.KR, "삼성전자")).thenReturn(new CacheResult<>(List.of(data), true, null, null));
        mvc().perform(get("/api/assets/search").param("market", "KR").param("keyword", "삼성전자"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ticker").value("005930"))
                .andExpect(jsonPath("$[0].currency").value("KRW")).andExpect(header().string("X-Data-Fresh", "true"));
    }
    @Test void koreanEtfDividendApiClearlyReportsUnsupported() throws Exception {
        when(service.getDividends(Market.KR, "490590")).thenThrow(new ApiException("DIVIDEND_DATA_NOT_SUPPORTED",
                "국내 ETF 분배금 데이터 연동은 현재 지원하지 않습니다.", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY));
        mvc().perform(get("/api/assets/KR/490590/dividends")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DIVIDEND_DATA_NOT_SUPPORTED"));
    }
}
