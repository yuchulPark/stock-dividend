package com.stock.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;

@Entity
@org.hibernate.annotations.Check(constraints = "dividend_per_share > 0")
@org.hibernate.annotations.Check(name = "ck_dividend_event_or_report", constraints = "(source <> 'OPEN_DART' and ex_dividend_date is not null and business_year is null and report_code is null and filing_number is null and share_class is null) or (source = 'OPEN_DART' and ex_dividend_date is null and record_date is null and payment_date is null and business_year >= 2015 and business_year is not null and report_code in ('11011','11012','11013','11014') and report_code is not null and filing_number is not null and share_class is not null)")
@Table(name = "dividend", uniqueConstraints = {
    @UniqueConstraint(name = "uk_dividend_asset_source_ex_date", columnNames = {"stock_asset_id", "source", "ex_dividend_date"}),
    @UniqueConstraint(name = "uk_dividend_asset_source_report", columnNames = {"stock_asset_id", "source", "business_year", "report_code", "share_class"})})
public class Dividend {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_dividend_stock_asset"))
    private StockAsset stockAsset;
    @DecimalMin(value = "0", inclusive = false)
    @Column(nullable = false, precision = 24, scale = 8) private BigDecimal dividendPerShare;
    private LocalDate exDividendDate;
    private Integer businessYear;
    @Column(length = 5) private String reportCode;
    @Column(length = 14) private String filingNumber;
    @Column(length = 40) private String shareClass;
    public Integer getBusinessYear() { return businessYear; }
    public String getReportCode() { return reportCode; }
    public String getFilingNumber() { return filingNumber; }
    public String getShareClass() { return shareClass; }
    public void report(Integer year, String code, String filing, String kind) {
        if (source != DataSourceType.OPEN_DART || exDividendDate != null || year == null
                || year < 2015 || code == null || !code.matches("110(11|12|13|14)")
                || filing == null || !filing.matches("[0-9]{14}") || kind == null || kind.isBlank())
            throw new IllegalArgumentException("Invalid dividend report identity");
        businessYear = year; reportCode = code; filingNumber = filing; shareClass = kind;
    }
    private LocalDate recordDate;
    private LocalDate paymentDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Currency currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private DataSourceType source;
    private LocalDateTime externalUpdatedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;

    protected Dividend() {}
    public Long getId() { return id; }
    public StockAsset getStockAsset() { return stockAsset; }
    public BigDecimal getDividendPerShare() { return dividendPerShare; }
    public LocalDate getExDividendDate() { return exDividendDate; }
    public LocalDate getRecordDate() { return recordDate; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public Currency getCurrency() { return currency; }
    public DataSourceType getSource() { return source; }
    public LocalDateTime getExternalUpdatedAt() { return externalUpdatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public Dividend(StockAsset asset, LocalDate exDate, DataSourceType source) {
        this.stockAsset = asset; this.exDividendDate = exDate; this.source = source;
    }
    public void update(BigDecimal amount, LocalDate record, LocalDate payment, Currency currency) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Dividend must be positive");
        dividendPerShare = amount; recordDate = record; paymentDate = payment; this.currency = currency;
        // Alpha Vantage does not supply an external modification timestamp.
    }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); }
}
