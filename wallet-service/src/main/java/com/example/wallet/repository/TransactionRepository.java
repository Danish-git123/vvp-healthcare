package com.example.wallet.repository;

import com.example.wallet.model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionRecord, UUID> {

    boolean existsByTransactionId(UUID transactionId);

    Optional<TransactionRecord> findByTransactionId(UUID transactionId);
}
