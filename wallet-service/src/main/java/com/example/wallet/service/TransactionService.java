package com.example.wallet.service;

import com.example.wallet.dto.TransactionRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.exception.DuplicateTransactionException;
import com.example.wallet.exception.InsufficientFundsException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.model.TransactionRecord;
import com.example.wallet.model.TransactionType;
import com.example.wallet.model.Wallet;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse process(TransactionRequest request) {
//      as per the assignment multiple request(txn req) are coming ,
//      so here think like multiple request objects are recieved

        // 1) Cheap pre-check. This alone does NOT guarantee correctness under
        //    concurrency (two threads can both pass it before either commits) -
        //    it just lets an obvious, already-committed duplicate fail fast
        //    without paying for a row lock.
        transactionRepository.findByTransactionId(request.getTransactionId())
                .ifPresent(existing -> {
                    throw new DuplicateTransactionException(existing);
                });

        // 2) Acquire a DB-level pessimistic write lock (SELECT ... FOR UPDATE)
        //    on the wallet row. Every request touching this wallet - whether
        //    it's a true duplicate or a legitimate concurrent debit - now
        //    serializes on this line. Nobody else can read-modify-write this
        //    wallet's balance until this transaction commits or rolls back.
        Wallet wallet = walletRepository.findByUserIdForUpdate(request.getUserId())
                .orElseThrow(() -> new WalletNotFoundException("No wallet found for user " + request.getUserId()));

        // 3) Authoritative duplicate check, now that we hold the lock. If a
        //    sibling request for the SAME transactionId got here first, it has
        //    already inserted its TransactionRecord and committed by the time
        //    we acquire the lock - so this check is now guaranteed accurate.
        transactionRepository.findByTransactionId(request.getTransactionId())
                .ifPresent(existing -> {
                    throw new DuplicateTransactionException(existing);
                });

        TransactionType type = TransactionType.valueOf(request.getType().trim().toUpperCase());
        BigDecimal newBalance;

        if (type == TransactionType.DEBIT) {
            if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(
                        "Insufficient funds for user " + request.getUserId()
                                + ": balance=" + wallet.getBalance()
                                + ", requested=" + request.getAmount());
            }
            newBalance = wallet.getBalance().subtract(request.getAmount());
        } else {
            newBalance = wallet.getBalance().add(request.getAmount());
        }

        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        TransactionRecord record = new TransactionRecord(
                request.getTransactionId(), request.getUserId(), request.getAmount(), type, newBalance);

        // 4) Defense in depth: even though (1)+(3) should make this
        //    unreachable, the unique constraint on transactionId is the last
        //    line of defense. If it ever fires, we convert it into the same
        //    DuplicateTransactionException so the caller sees a consistent
        //    response, and @Transactional rolls back the balance update we
        //    made above in this same transaction.
        try {
            transactionRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            TransactionRecord existing = transactionRepository.findByTransactionId(request.getTransactionId())
                    .orElseThrow(() -> e);
            throw new DuplicateTransactionException(existing);
        }

        return new TransactionResponse(
                record.getTransactionId(),
                "SUCCESS",
                newBalance,
                record.getProcessedAt(),
                "Transaction processed successfully.");
    }
}
