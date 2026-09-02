package com.example.wallet;

import com.example.wallet.dto.TransactionRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.model.Wallet;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full end-to-end tests against a real embedded servlet container + real H2
 * database, using genuine concurrent HTTP calls (not mocked). This is what
 * actually proves the pessimistic locking and idempotency logic hold up
 * under contention - a single-threaded MockMvc test would not catch a race
 * condition.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionProcessorIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();

        userId = UUID.randomUUID();
        Wallet wallet = new Wallet(userId, new BigDecimal("500.00"));
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully.")
    void processesSingleValidDebitTransactionSuccessfully() {
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), userId, new BigDecimal("100.00"), "DEBIT");

        ResponseEntity<TransactionResponse> response =
                restTemplate.postForEntity(processUrl(), request, TransactionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().getStatus());
        assertEquals(0, new BigDecimal("400.00").compareTo(response.getBody().getResultingBalance()));

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        assertEquals(0, new BigDecimal("400.00").compareTo(updated.getBalance()));

        System.out.println("[HAPPY PATH] Debited 100.00 -> resulting balance = " + updated.getBalance());
    }

    @Test
    @DisplayName("Sends 3 identical transactionIDs simultaneously. Ensures the balance is only deducted once.")
    void sendsThreeIdenticalTransactionIdsSimultaneously_deductsBalanceOnlyOnce() throws Exception {
        UUID sharedTransactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50.00");
        int concurrentRequests = 3;

        List<ResponseEntity<TransactionResponse>> results = fireConcurrently(concurrentRequests, i ->
                new TransactionRequest(sharedTransactionId, userId, amount, "DEBIT"));

        long successCount = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.OK)
                .count();
        long conflictCount = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .count();

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();
        long recordCount = transactionRepository.count();

        System.out.println("[IDEMPOTENCY] successes=" + successCount
                + " conflicts=" + conflictCount
                + " finalBalance=" + updated.getBalance()
                + " persistedRecords=" + recordCount);

        assertEquals(1, successCount, "Exactly one of the three identical requests should succeed");
        assertEquals(2, conflictCount, "The other two identical requests should be rejected as duplicates");
        assertEquals(1, recordCount, "Only one TransactionRecord should ever be persisted for this transactionId");
        assertEquals(0, new BigDecimal("450.00").compareTo(updated.getBalance()),
                "Balance should be deducted exactly once (500.00 - 50.00 = 450.00)");
    }

    @Test
    @DisplayName("Sends 10 concurrent debit requests of Rs.100 for a wallet with a Rs.500 balance. "
            + "Ensures the final balance is exactly Rs.0 and 5 requests fail with insufficient funds.")
    void sendsTenConcurrentDebitsAgainstFiveHundredBalance_exactlyFiveSucceedAndFiveFail() throws Exception {
        BigDecimal amount = new BigDecimal("100.00");
        int concurrentRequests = 10;

        List<ResponseEntity<TransactionResponse>> results = fireConcurrently(concurrentRequests, i ->
                new TransactionRequest(UUID.randomUUID(), userId, amount, "DEBIT"));

        long successCount = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.OK)
                .count();
        long insufficientFundsCount = results.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY)
                .count();

        Wallet updated = walletRepository.findByUserId(userId).orElseThrow();

        System.out.println("[RACE CONDITION] successes=" + successCount
                + " insufficientFunds=" + insufficientFundsCount
                + " finalBalance=" + updated.getBalance());

        assertEquals(5, successCount, "Exactly 5 of the 10 concurrent debits should succeed (500 / 100 = 5)");
        assertEquals(5, insufficientFundsCount, "The remaining 5 should fail with insufficient funds");
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getBalance()), "Final balance must be exactly 0.00");
        assertTrue(updated.getBalance().signum() >= 0, "Balance must never go negative");
    }

    /**
     * Fires {@code count} requests from separate threads, releasing them all
     * at once via a CountDownLatch so they hit the server as close to
     * simultaneously as possible - this is what actually exercises the
     * locking logic instead of just testing sequential calls.
     */
    private List<ResponseEntity<TransactionResponse>> fireConcurrently(
            int count, java.util.function.IntFunction<TransactionRequest> requestFactory) throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch allThreadsReady = new CountDownLatch(count);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<ResponseEntity<TransactionResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                TransactionRequest request = requestFactory.apply(index);
                allThreadsReady.countDown();
                startSignal.await();
                return restTemplate.postForEntity(processUrl(), request, TransactionResponse.class);
            }));
        }

        allThreadsReady.await(5, TimeUnit.SECONDS);
        startSignal.countDown();

        List<ResponseEntity<TransactionResponse>> results = new ArrayList<>();
        for (Future<ResponseEntity<TransactionResponse>> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }

        pool.shutdown();
        return results;
    }

    private String processUrl() {
        return "http://localhost:" + port + "/api/v1/transactions/process";
    }
}
