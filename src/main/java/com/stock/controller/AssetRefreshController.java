package com.stock.controller;
import com.stock.dto.response.RefreshResponse;
import com.stock.entity.Market;
import com.stock.service.AssetSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@ConditionalOnProperty(name = "stock.development.refresh-enabled", havingValue = "true")
public class AssetRefreshController {
    private final AssetSyncService service;
    public AssetRefreshController(AssetSyncService service) { this.service = service; }
    // 운영 환경에서는 관리자 기능으로 제한 필요. Default disabled, explicitly enable only during development.
    @PostMapping("/api/assets/{market}/{ticker}/refresh")
    public RefreshResponse refresh(@PathVariable Market market, @PathVariable String ticker) {
        return service.refresh(market, ticker);
    }
}
