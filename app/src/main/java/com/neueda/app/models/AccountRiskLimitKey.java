package com.neueda.app.models;

import com.neueda.app.enums.RiskRule;
import java.io.Serializable;
import java.util.Objects;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AccountRiskLimitKey implements Serializable {
    private String accountId;
    private RiskRule rule;

    public AccountRiskLimitKey(String accountId, RiskRule rule) {
        this.accountId = accountId;
        this.rule = rule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountRiskLimitKey that = (AccountRiskLimitKey) o;
        return Objects.equals(accountId, that.accountId) && rule == that.rule;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, rule);
    }
}
