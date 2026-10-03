package com.stock.external.krx;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.KrxProperties;
import com.stock.exception.ApiException;
import com.stock.external.OfficialApiErrors;
import com.stock.external.krx.dto.KrxResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class KrxApiClient {
    public enum Api {
        KOSPI_INFO("sto/stk_isu_base_info"), KOSDAQ_INFO("sto/ksq_isu_base_info"),
        KOSPI_PRICE("sto/stk_bydd_trd"), KOSDAQ_PRICE("sto/ksq_bydd_trd"), ETF_PRICE("etp/etf_bydd_trd");
        private final String path;
        Api(String path) { this.path = path; }
        public String path() { return path; }
    }
    private final RestClient rest;
    private final ObjectMapper mapper;
    private final KrxProperties properties;
    public KrxApiClient(@Qualifier("krxRestClient") RestClient rest, ObjectMapper mapper, KrxProperties properties) {
        this.rest = rest; this.mapper = mapper; this.properties = properties;
    }
    public KrxResponse.Data get(Api api, LocalDate date) {
        if (properties.getAuthKey() == null || properties.getAuthKey().isBlank())
            throw OfficialApiErrors.error("EXTERNAL_API_KEY_MISSING", "KRX OPEN API 인증키가 설정되어 있지 않습니다.");
        try {
            String body = rest.get().uri(u -> u.path("/svc/apis/" + api.path())
                    .queryParam("basDd", date.format(DateTimeFormatter.BASIC_ISO_DATE)).build())
                    .header("AUTH_KEY", properties.getAuthKey()).retrieve().body(String.class);
            if (body == null) throw ApiException.invalidResponse();
            KrxResponse.Data response = mapper.readValue(body, KrxResponse.Data.class);
            if (response == null || response.rows() == null) throw ApiException.invalidResponse();
            return response;
        } catch (ApiException ex) { throw ex; }
        catch (RestClientException ex) { throw OfficialApiErrors.transport(ex); }
        catch (Exception ex) { throw ApiException.invalidResponse(); }
    }
}
