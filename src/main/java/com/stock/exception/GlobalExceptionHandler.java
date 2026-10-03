package com.stock.exception;
import com.stock.dto.response.ErrorResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> missingRoute(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("API_NOT_FOUND", "요청한 API를 찾을 수 없습니다."));
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> api(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_REQUEST", "시장, 종목코드 또는 검색어를 확인해 주세요."));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("DATA_CONFLICT", "데이터 제약조건과 충돌했습니다."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다."));
    }
}
