package com.trekkfin.service;

import com.trekkfin.dto.TransferRequest;
import com.trekkfin.dto.TransferResponse;
import com.trekkfin.entity.Account;
import com.trekkfin.entity.TransactionStatus;
import com.trekkfin.repository.AccountRepository;
import com.trekkfin.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class WalletServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private final String senderPhone = "1234567890";
    private final String receiverPhone = "0987654321";

    @BeforeEach
    public void setup() {
        BigDecimal senderInitialBalance = new BigDecimal("100.00");
        BigDecimal receiverInitialBalance = new BigDecimal("0.00");

        // Try to update existing sender balance
        int senderUpdated = accountRepository.updateBalanceByPhone(senderPhone, senderInitialBalance);
        if (senderUpdated == 0) {
            Account sender = Account.builder()
                    .name("Sender")
                    .email("sender@example.com")
                    .phone(senderPhone)
                    .balance(senderInitialBalance)
                    .build();
            accountRepository.save(sender);
        }

        // Try to update existing receiver balance
        int receiverUpdated = accountRepository.updateBalanceByPhone(receiverPhone, receiverInitialBalance);
        if (receiverUpdated == 0) {
            Account receiver = Account.builder()
                    .name("Receiver")
                    .email("receiver@example.com")
                    .phone(receiverPhone)
                    .balance(receiverInitialBalance)
                    .build();
            accountRepository.save(receiver);
        }
    }

    @Test
    public void testConcurrentTransfers() throws InterruptedException {
        int concurrencyLevel = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrencyLevel);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(concurrencyLevel);

        BigDecimal amountToTransfer = new BigDecimal("1.00");
        TransferRequest request = new TransferRequest(senderPhone, receiverPhone, amountToTransfer);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < concurrencyLevel; i++) {
            executorService.submit(() -> {
                try {
                    startGate.await(); // Hold all threads here until released
                    TransferResponse response = walletService.transfer(request);
                    if (response.status() == TransactionStatus.SUCCESS) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Release all threads simultaneously
        long startTime = System.nanoTime();
        startGate.countDown();

        // Wait for all threads to complete
        endGate.await();
        executorService.shutdown();
        long endTime = System.nanoTime();

        System.out.println("====== CONCURRENCY TEST RESULTS ======");
        System.out.println("Total Requests: " + concurrencyLevel);
        System.out.println("Successful transfers: " + successCount.get());
        System.out.println("Failed transfers: " + failureCount.get());
        System.out.println("Total Execution Time: " + (endTime - startTime) / 1_000_000 + " ms");

        // Verify balance updates are consistent
        Account senderAfter = accountRepository.findByPhone(senderPhone).orElseThrow();
        Account receiverAfter = accountRepository.findByPhone(receiverPhone).orElseThrow();

        BigDecimal actualTotalBalance = senderAfter.getBalance().add(receiverAfter.getBalance());
        System.out.println("Final Sender Balance: " + senderAfter.getBalance());
        System.out.println("Final Receiver Balance: " + receiverAfter.getBalance());
        System.out.println("Total system money in DB: $" + actualTotalBalance);

        // Crucial Assertions to verify no double spends or money loss occurred
        BigDecimal expectedSenderBalance = new BigDecimal("100.00")
                .subtract(amountToTransfer.multiply(new BigDecimal(successCount.get())));
        BigDecimal expectedReceiverBalance = amountToTransfer.multiply(new BigDecimal(successCount.get()));

        assertEquals(0, expectedSenderBalance.compareTo(senderAfter.getBalance()),
                "Sender balance is inconsistent! Lost updates detected.");
        assertEquals(0, expectedReceiverBalance.compareTo(receiverAfter.getBalance()),
                "Receiver balance is inconsistent! Lost updates detected.");
        assertEquals(0, new BigDecimal("100.00").compareTo(actualTotalBalance),
                "Money was leaked or created out of thin air!");
    }
}
