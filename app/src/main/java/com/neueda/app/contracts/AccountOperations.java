package com.neueda.app.contracts;
import java.math.BigDecimal;

public interface AccountOperations {
    void validateStatus();

    void debitCash(java.math.BigDecimal amount);

    void creditCash(java.math.BigDecimal amount);
    
    BigDecimal getCashBalance();
}