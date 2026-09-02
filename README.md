<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
  <img src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" />
  <img src="https://img.shields.io/badge/H2-In--Memory-0000BB?style=for-the-badge&logo=databricks&logoColor=white" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" />
</p>

# 💳 Wallet Service — Idempotent Payment Event Processor

> A production-grade Spring Boot microservice that ingests payment webhook events and applies them to user wallet balances — with built-in **idempotency**, **concurrency safety**, and **pessimistic locking** to ensure duplicate/retried webhooks are never double-processed and concurrent debits can never drive a balance negative.

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Concurrency & Idempotency Strategy](#-concurrency--idempotency-strategy)
- [Running Tests](#-running-tests)
- [Design Decisions](#-design-decisions)

---

## 🔍 Overview

Payment gateways (Razorpay, Stripe, etc.) routinely **retry webhook events** on network timeouts. Without protection, the same debit could be applied multiple times, corrupting a user's balance.

This service solves three critical problems:

| Problem | Solution |
|---|---|
| **Duplicate webhooks** | Idempotency via `transactionId` — same event processed at most once |
| **Concurrent debits** | `SELECT ... FOR UPDATE` pessimistic row lock on the wallet |
| **Negative balance** | Balance check happens *inside* the locked section |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Wallet Service                           │
│                                                                 │
│   ┌──────────────┐    ┌───────────────────┐    ┌─────────────┐  │
│   │  Controller  │──▶│TransactionService │───▶│  Repository │  │
│   │  (REST API)  │    │ (Business Logic)  │    │  (JPA/H2)   │  │
│   └──────────────┘    └───────────────────┘    └─────────────┘  │
│                              │                       │          │
│                     ┌────────▼────────┐     ┌────────▼───────┐  │
│                     │ Idempotency     │     │ Pessimistic    │  │
│                     │ Guard (2-phase) │     │ Row Lock       │  │
│                     └─────────────────┘     │ (FOR UPDATE)   │  │
│                                             └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🛠 Tech Stack

| Technology | Purpose |
|---|---|
| **Java 17** | Language runtime |
| **Spring Boot 3.3.x** | Web framework, DI, auto-configuration |
| **Spring Data JPA** | ORM & repository abstraction |
| **Hibernate** | JPA implementation, `@Lock` support |
| **H2 Database** | In-memory DB (zero config, resets on restart) |
| **Bean Validation** | `@NotNull`, `@NotBlank`, `@DecimalMin` on DTOs |
| **JUnit 5** | Integration & concurrency testing |
| **Maven** | Build & dependency management |

---

## 📂 Project Structure

```
wallet-service/
├── pom.xml                          # Maven build configuration
├── DECISIONS.md                     # Design decisions & AI correction log
├── README.md                        # Module-level documentation
│
└── src/
    ├── main/
    │   ├── java/com/example/wallet/
    │   │   ├── WalletApplication.java            # Spring Boot entry point
    │   │   ├── controller/
    │   │   │   └── TransactionController.java    # REST endpoint
    │   │   ├── dto/
    │   │   │   ├── TransactionRequest.java       # Inbound request DTO
    │   │   │   └── TransactionResponse.java      # Outbound response DTO
    │   │   ├── model/
    │   │   │   ├── Wallet.java                   # Wallet entity (balance)
    │   │   │   ├── TransactionRecord.java        # Processed txn entity
    │   │   │   └── TransactionType.java          # DEBIT / CREDIT enum
    │   │   ├── repository/
    │   │   │   ├── WalletRepository.java         # Wallet DAO + FOR UPDATE
    │   │   │   └── TransactionRepository.java    # Transaction DAO
    │   │   ├── service/
    │   │   │   └── TransactionService.java       # Core business logic
    │   │   └── exception/
    │   │       ├── GlobalExceptionHandler.java   # @ControllerAdvice
    │   │       ├── DuplicateTransactionException.java
    │   │       ├── InsufficientFundsException.java
    │   │       └── WalletNotFoundException.java
    │   └── resources/
    │       └── application.properties            # H2 & JPA config
    │
    └── test/
        └── java/com/example/wallet/
            └── TransactionProcessorIntegrationTest.java
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** 
- **Maven 3.8+** 

### Clone & Run

```bash
# Clone the repository
git clone https://github.com/Danish-git123/vvp-healthcare.git


# Start the application
mvn spring-boot:run
```

The API will be available at **`http://localhost:8080`**
or you can basically go in tests folder and run test cases 

> **Note:** The H2 database is in-memory — it resets on every restart. No external DB setup required.

---

## 🧪 Running Tests

No Postman or external DB setup needed. The test suite covers:

```bash
# Run all tests
cd wallet-service
mvn test
```

| # | Test | What it verifies |
|---|---|---|
| 1 | **Happy Path** | Single valid debit succeeds, balance updates correctly |
| 2 | **Idempotency** | 3 concurrent threads send same `transactionId` → exactly 1 succeeds (200), 2 get `409 Conflict` |
| 3 | **Race Condition** | 10 concurrent ₹100 debits on ₹500 wallet → exactly 5 succeed, 5 get `422`, balance = ₹0.00 |

Tests use `CountDownLatch` to synchronize concurrent thread starts, ensuring the race conditions are reliably reproduced.

---


for the api testing->

## 📡 API Reference
NOTE->You Can check the test case which i have built in the tests folder

### Process Transaction

```
POST /api/v1/transactions/process
Content-Type: application/json
```

#### Request Body

```json
{
  "transactionId": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "userId": "e2a1d7b0-1234-5678-9abc-def012345678",
  "amount": 250.00,
  "type": "DEBIT"
}
```

#### Response Codes

| Status | Description |
|---|---|
| `200 OK` | Transaction processed — returns updated balance |
| `400 Bad Request` | Validation failure (missing or invalid fields) |
| `404 Not Found` | No wallet found for the given `userId` |
| `409 Conflict` | Duplicate `transactionId` — echoes original result |
| `422 Unprocessable Entity` | Insufficient funds for DEBIT |

#### Success Response Example

```json
{
  "transactionId": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "status": "SUCCESS",
  "balanceAfter": 250.00,
  "message": "Transaction processed successfully"
}
```

---

## 🔒 Concurrency & Idempotency Strategy

The service employs a **two-phase idempotency guard** combined with **database-level pessimistic locking**:

```
Request arrives
    │
    ▼
┌─────────────────────────────┐
│ Phase 1: Quick duplicate    │  ◀── Non-locking check (fast path)
│ check (existsByTxnId)       │
└──────────────┬──────────────┘
               │ Not a duplicate
               ▼
┌─────────────────────────────┐
│ Acquire row lock            │  ◀── SELECT ... FOR UPDATE
│ (findByUserIdForUpdate)     │      Serializes all concurrent
└──────────────┬──────────────┘      requests for this wallet
               │
               ▼
┌─────────────────────────────┐
│ Phase 2: Authoritative      │  ◀── Re-check inside lock
│ duplicate check             │      (closes race window)
└──────────────┬──────────────┘
               │
               ▼
┌─────────────────────────────┐
│ Balance check + debit       │  ◀── Safe: no other txn can read
│ within locked section       │      this wallet concurrently
└─────────────────────────────┘
```

**Why pessimistic over optimistic locking?**
- Optimistic locking requires retry logic on version conflicts
- In-memory locks (`ConcurrentHashMap`) break when horizontally scaled
- DB row lock works correctly regardless of number of app instances

---



## 📝 Design Decisions

Detailed reasoning behind the architectural choices is documented in [`DECISIONS.md`](./wallet-service/DECISIONS.md), including:

- Why pessimistic locking was chosen over optimistic locking
- The two-phase duplicate detection strategy
- Where AI assistance needed correcting during development

---

<p align="center">
  <sub>Built with ☕ Java & Spring Boot</sub>
</p>
