package com.stock.dto.response;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.stock.entity.*;
public record DividendResponse(BigDecimal dividendPerShare, LocalDate exDividendDate, LocalDate recordDate,
        LocalDate paymentDate, Currency currency, DataSourceType source, String dataType,
        Integer businessYear, String reportCode, String filingNumber, String shareClass) {
    public DividendResponse(BigDecimal amount, LocalDate exDate, LocalDate recordDate, LocalDate paymentDate, Currency currency, DataSourceType source) {
        this(amount, exDate, recordDate, paymentDate, currency, source, "EVENT", null, null, null, null);
    }
    public static DividendResponse from(Dividend data) {
        return new DividendResponse(data.getDividendPerShare(), data.getExDividendDate(), data.getRecordDate(),
                data.getPaymentDate(), data.getCurrency(), data.getSource(), data.getBusinessYear() == null ? "EVENT" : "REPORT",
                data.getBusinessYear(), data.getReportCode(), data.getFilingNumber(), data.getShareClass());
    }
}
