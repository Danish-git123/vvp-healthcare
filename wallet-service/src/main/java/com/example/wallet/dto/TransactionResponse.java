package com.example.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionResponse {

    private UUID transactionId;
    private String status; // SUCCESS, DUPLICATE_IGNORED, FAILED
    private BigDecimal resultingBalance;
    private Instant processedAt;
    private String message;

    public TransactionResponse() {
    }

    public TransactionResponse(UUID transactionId, String status, BigDecimal resultingBalance,
                                Instant processedAt, String message) {
        this.transactionId = transactionId;
        this.status = status;
        this.resultingBalance = resultingBalance;
        this.processedAt = processedAt;
        this.message = message;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(UUID transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public void setResultingBalance(BigDecimal resultingBalance) {
        this.resultingBalance = resultingBalance;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
