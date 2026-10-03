package com.stock.external.opendart.dto;
import java.util.List;
import com.fasterxml.jackson.annotation.*;
public final class OpenDartResponse {
    private OpenDartResponse() {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String status, String message, List<Row> list) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(@JsonProperty("rcept_no") String filingNumber, @JsonProperty("corp_code") String corporation,
            String se, @JsonProperty("stock_knd") String shareClass, String thstrm,
            @JsonProperty("stlm_dt") String settlementDate) {}
}
