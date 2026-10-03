package com.stock.repository;
import java.util.*;
import com.stock.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface StockAssetRepository extends JpaRepository<StockAsset, Long> {
    Optional<StockAsset> findByMarketAndTicker(Market market, String ticker);
    boolean existsByMarketAndTicker(Market market, String ticker);
    List<StockAsset> findByMarket(Market market);
    @Query("select a from StockAsset a where a.market = :market and (locate(lower(:keyword), lower(a.ticker)) > 0 or locate(lower(:keyword), lower(a.name)) > 0) order by a.ticker")
    List<StockAsset> search(@Param("market") Market market, @Param("keyword") String keyword);
}
