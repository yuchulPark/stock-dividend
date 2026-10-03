package com.stock.repository;
import java.time.LocalDate;
import java.util.*;
import com.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    @org.springframework.data.jpa.repository.Query("select d from Dividend d where d.stockAsset.id = :assetId order by d.exDividendDate desc nulls last, d.businessYear desc nulls last, d.id desc")
    List<Dividend> findByStockAssetIdOrderByExDividendDateDesc(@org.springframework.data.repository.query.Param("assetId") Long assetId);
    Optional<Dividend> findByStockAssetIdAndSourceAndBusinessYearAndReportCodeAndShareClass(Long assetId, DataSourceType source, Integer year, String code, String kind);
    Optional<Dividend> findFirstByStockAssetIdOrderByExDividendDateDesc(Long assetId);
    Optional<Dividend> findByStockAssetIdAndSourceAndExDividendDate(Long assetId, DataSourceType source, LocalDate date);
    boolean existsByStockAssetIdAndSourceAndExDividendDate(Long assetId, DataSourceType source, LocalDate date);
}
