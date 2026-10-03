package com.stock.exception;
import org.springframework.http.HttpStatus;
public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public ApiException(String code, String message, HttpStatus status) {
        super(message); this.code = code; this.status = status;
    }
    public static ApiException invalidResponse() {
        return new ApiException("EXTERNAL_API_INVALID_RESPONSE", "외부 금융 데이터 응답 형식이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
    }
    public static ApiException rateLimit() {
        return new ApiException("EXTERNAL_API_RATE_LIMIT", "외부 금융 데이터 API 호출 한도를 초과했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }
    public static ApiException notFound() {
        return new ApiException("ASSET_NOT_FOUND", "해당 주식 또는 ETF를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
    }
}
