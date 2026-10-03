package com.stock.external.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record QuoteData(String ticker, BigDecimal price, LocalDate asOfDate) {}
