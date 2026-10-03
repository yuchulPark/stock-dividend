package com.stock.external.krx.dto;
import java.util.List;
import com.fasterxml.jackson.annotation.*;
public final class KrxResponse {
    private KrxResponse() {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("OutBlock_1") List<Row> rows) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(@JsonProperty("ISU_CD") String code, @JsonProperty("ISU_SRT_CD") String shortCode,
            @JsonProperty("ISU_NM") String name, @JsonProperty("MKT_TP_NM") String market,
            @JsonProperty("KIND_STKCERT_TP_NM") String shareClass,
            @JsonProperty("BAS_DD") String date, @JsonProperty("TDD_CLSPRC") String close) {}
}
