package com.example.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per successfully processed transactionId. The unique constraint on
 * transactionId is the last line of defense for idempotency: even if the
 * application-level checks in TransactionService were ever bypassed, the
 * database itself will refuse a second insert of the same transactionId.
 */
@Entity
@Table(name = "transaction_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaction_id", columnNames = "transactionId"))
public class TransactionRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID transactionId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal resultingBalance;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected TransactionRecord() {
        // required by JPA
    }

    public TransactionRecord(UUID transactionId, UUID userId, BigDecimal amount,
                              TransactionType type, BigDecimal resultingBalance) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.resultingBalance = resultingBalance;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
