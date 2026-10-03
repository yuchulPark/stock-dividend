package com.stock.external;
import com.stock.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.*;
public final class OfficialApiErrors {
    private OfficialApiErrors() {}
    public static ApiException error(String code, String message) { return new ApiException(code, message, HttpStatus.SERVICE_UNAVAILABLE); }
    public static ApiException transport(RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            if (status == 429) return ApiException.rateLimit();
            if (status == 401 || status == 403) return error("EXTERNAL_API_AUTH_FAILED", "공식 데이터 API 인증 또는 서비스 이용승인을 확인해 주세요.");
        }
        for (Throwable cause = ex; cause != null; cause = cause.getCause())
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException)
                return error("EXTERNAL_API_TIMEOUT", "공식 데이터 API 응답 시간이 초과되었습니다.");
        return error("EXTERNAL_API_UNAVAILABLE", "공식 데이터 API에 연결할 수 없습니다.");
    }
}
