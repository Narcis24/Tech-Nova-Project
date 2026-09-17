package com.neueda.app.service;

import com.neueda.app.dto.PlaceOrderRequest;
import com.neueda.app.entity.Account;

@Component
public class OrderValidator {

    private final AccountRespository account_repository;
    private final InstrumentRepository instrument_repository;

    public OrderValidator(AccountRespository account_repository,
        InstrumentRepository instrument_repository) {

        this.account_repository = account_repository;
        this.instrument_repository = instrument_repository;
    }

    public Account validateAccountID(String accountID) {
        return account_repository.findByID(accountID)
            .orElseThrow(() ->
                new AccountNotFoundException(
                    "Account with ID " + accountID + "was not found")
        );
    }

    public void validateAccountStatus(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException("Account must be active to place an order");
        }
    }

    public void validateInstrumentSymbol(String symbol) {
        return InstrumentRepository.findBySymbol(symbol)
            .orElseThrow(() ->
                new InstrumentNotFoundException(
                    "Instrument Symbol" + symbol + "cannot be found")
        );
    }

    public void validateSidePattern(String side) {
        if (side == null) {
            throw new IllegalArgumentException("Side must be either BUY or SELL");
        }
    }

    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }

    public void validatePrice(int price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0");
        }
    }
}