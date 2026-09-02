package com.example.wallet.exception;

import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.model.TransactionRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A retried/duplicate transactionId. We return 409 Conflict, and include
     * the ORIGINAL cached result in the body (status, resulting balance,
     * original timestamp) so a caller can reconcile without re-processing.
     */
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<TransactionResponse> handleDuplicate(DuplicateTransactionException ex) {
        TransactionRecord existing = ex.getExisting();
        TransactionResponse body = new TransactionResponse(
                existing.getTransactionId(),
                "DUPLICATE_IGNORED",
                existing.getResultingBalance(),
                existing.getProcessedAt(),
                "Transaction already processed. Returning original result; balance was not changed again."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<TransactionResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        TransactionResponse body = new TransactionResponse(
                null, "FAILED", null, Instant.now(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<TransactionResponse> handleWalletNotFound(WalletNotFoundException ex) {
        TransactionResponse body = new TransactionResponse(
                null, "FAILED", null, Instant.now(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TransactionResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        TransactionResponse body = new TransactionResponse(
                null, "FAILED", null, Instant.now(), message);
        return ResponseEntity.badRequest().body(body);
    }
}
