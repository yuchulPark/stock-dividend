package com.stock.repository;
import java.time.LocalDate;
import com.stock.entity.ProviderCallBudget;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProviderCallBudgetRepository extends JpaRepository<ProviderCallBudget, LocalDate> {}
