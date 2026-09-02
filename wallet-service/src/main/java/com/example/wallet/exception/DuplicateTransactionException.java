package com.example.wallet.exception;

import com.example.wallet.model.TransactionRecord;

public class DuplicateTransactionException extends RuntimeException {

    private final TransactionRecord existing;

    public DuplicateTransactionException(TransactionRecord existing) {
        super("Duplicate transactionId: " + existing.getTransactionId());
        this.existing = existing;
    }

    public TransactionRecord getExisting() {
        return existing;
    }
}
