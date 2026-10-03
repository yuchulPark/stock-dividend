package com.stock.entity;

import java.time.LocalDate;
import jakarta.persistence.*;

@Entity
@Table(name = "provider_call_budget")
public class ProviderCallBudget {
    @Id private LocalDate budgetDate;
    @Column(nullable = false) private int usedCalls;
    protected ProviderCallBudget() {}
    public LocalDate getBudgetDate() { return budgetDate; }
    public int getUsedCalls() { return usedCalls; }
    public ProviderCallBudget(LocalDate day) { budgetDate = day; }
    public void reserve() { usedCalls++; }
}
