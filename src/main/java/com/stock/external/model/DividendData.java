package com.stock.external.model;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.stock.entity.Currency;
public record DividendData(BigDecimal amount, LocalDate exDate, LocalDate recordDate, LocalDate paymentDate,
        Currency currency, Integer businessYear, String reportCode, String filingNumber, String shareClass) {
    public DividendData(BigDecimal amount, LocalDate exDate, LocalDate recordDate, LocalDate paymentDate, Currency currency) {
        this(amount, exDate, recordDate, paymentDate, currency, null, null, null, null);
    }
}
