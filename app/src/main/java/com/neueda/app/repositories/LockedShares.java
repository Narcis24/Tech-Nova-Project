package com.neueda.app.repositories;

/** Shares of one symbol held back by an account's open sell orders. */
public record LockedShares(String symbol, Long quantity) {
}
