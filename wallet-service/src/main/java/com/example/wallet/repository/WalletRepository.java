package com.example.wallet.repository;

import com.example.wallet.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Acquires a database-level "SELECT ... FOR UPDATE" row lock on the wallet.
     * Any other transaction trying to lock the same row (e.g. a concurrent
     * debit for the same user) will block here until this transaction commits
     * or rolls back. This is what serializes concurrent debits and prevents
     * negative balances / lost updates.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
