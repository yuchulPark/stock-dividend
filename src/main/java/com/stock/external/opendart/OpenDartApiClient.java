package com.stock.external.opendart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.config.OpenDartProperties;
import com.stock.exception.ApiException;
import com.stock.external.OfficialApiErrors;
import com.stock.external.opendart.dto.OpenDartResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class OpenDartApiClient {
    private final RestClient rest;
    private final ObjectMapper mapper;
    private final OpenDartProperties properties;
    public OpenDartApiClient(@Qualifier("openDartRestClient") RestClient rest, ObjectMapper mapper, OpenDartProperties properties) {
        this.rest = rest; this.mapper = mapper; this.properties = properties;
    }
    private void authorize() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
            throw OfficialApiErrors.error("EXTERNAL_API_KEY_MISSING", "OpenDART API 인증키가 설정되어 있지 않습니다.");
    }
    public byte[] corporations() {
        authorize();
        try {
            return rest.get().uri(u -> u.path("/api/corpCode.xml").queryParam("crtfc_key", properties.getApiKey()).build())
                    .exchange((req, res) -> {
                        if (!res.getStatusCode().is2xxSuccessful()) {
                            if (res.getStatusCode().value() == 429) throw ApiException.rateLimit();
                            if (res.getStatusCode().value() == 401 || res.getStatusCode().value() == 403)
                                throw OfficialApiErrors.error("EXTERNAL_API_AUTH_FAILED", "OpenDART 인증에 실패했습니다.");
                            throw OfficialApiErrors.error("EXTERNAL_API_UNAVAILABLE", "OpenDART 기업정보를 조회할 수 없습니다.");
                        }
                        byte[] bytes = res.getBody().readNBytes(10 * 1024 * 1024 + 1);
                        if (bytes.length > 10 * 1024 * 1024) throw ApiException.invalidResponse();
                        return bytes;
                    });
        } catch (ApiException ex) { throw ex; }
        catch (RestClientException ex) { throw OfficialApiErrors.transport(ex); }
    }
    public OpenDartResponse.Data dividends(String corporation, int year, String code) {
        authorize();
        try {
            String body = rest.get().uri(u -> u.path("/api/alotMatter.json").queryParam("crtfc_key", properties.getApiKey())
                    .queryParam("corp_code", corporation).queryParam("bsns_year", year).queryParam("reprt_code", code).build())
                    .retrieve().body(String.class);
            if (body == null) throw ApiException.invalidResponse();
            var response = mapper.readValue(body, OpenDartResponse.Data.class);
            if (response == null) throw ApiException.invalidResponse();
            OpenDartMapper.checkStatus(response.status()); return response;
        } catch (ApiException ex) { throw ex; }
        catch (RestClientException ex) { throw OfficialApiErrors.transport(ex); }
        catch (Exception ex) { throw ApiException.invalidResponse(); }
    }
}
