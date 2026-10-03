package com.stock.service;
import java.time.LocalDateTime;
public record CacheResult<T>(T data, boolean dataFresh, LocalDateTime syncedAt, String warningCode) {}
