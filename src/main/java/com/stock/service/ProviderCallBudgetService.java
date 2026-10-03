package com.stock.service;
import java.time.*;
import com.stock.config.AlphaVantageProperties;
import com.stock.entity.ProviderCallBudget;
import com.stock.exception.ApiException;
import com.stock.repository.ProviderCallBudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
@Service
public class ProviderCallBudgetService {
    private final ProviderCallBudgetRepository repository;
    private final AlphaVantageProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public ProviderCallBudgetService(ProviderCallBudgetRepository repository, AlphaVantageProperties properties,
            Clock clock, PlatformTransactionManager manager) {
        this.repository = repository; this.properties = properties; this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }
    // Single-server reservation, including transaction commit, is serialized. Failed attempts consume budget too.
    public synchronized void reserve() {
        transaction.executeWithoutResult(status -> {
            LocalDate day = LocalDate.now(clock);
            ProviderCallBudget budget = repository.findById(day).orElseGet(() -> new ProviderCallBudget(day));
            if (budget.getUsedCalls() >= properties.getDailyLimit()) throw ApiException.rateLimit();
            budget.reserve(); repository.saveAndFlush(budget);
        });
    }
}
