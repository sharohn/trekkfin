package com.trekkfin.service;

import com.trekkfin.dto.TransferRequest;
import com.trekkfin.dto.TransferResponse;
import com.trekkfin.entity.Account;
import com.trekkfin.entity.Transaction;
import com.trekkfin.entity.TransactionStatus;
import com.trekkfin.repository.AccountRepository;
import com.trekkfin.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class WalletService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public BigDecimal getBalance(String contactNumber) {
        Account account = accountRepository.findByPhone(contactNumber)
                .orElseThrow(() -> new RuntimeException("Account not found with contact: " + contactNumber));
        return account.getBalance();
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        String fromContact = request.from();
        String toContact = request.to();
        BigDecimal amount = request.amount();

        // Validate amount
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new TransferResponse(TransactionStatus.FAILED, "Amount must be greater than zero");
        }

        // Check if same contact
        if (fromContact.equals(toContact)) {
            return new TransferResponse(TransactionStatus.FAILED, "Cannot transfer to the same account");
        }

        // Find accounts by contact number
        Account fromAccount = accountRepository.findByPhoneWithLock(fromContact)
                .orElse(null);
        if (fromAccount == null) {
            return new TransferResponse(TransactionStatus.FAILED, "From account not found");
        }

        Account toAccount = accountRepository.findByPhoneWithLock(toContact)
                .orElse(null);
        if (toAccount == null) {
            return new TransferResponse(TransactionStatus.FAILED, "To account not found");
        }

        // Check balance - must be greater than amount
        if (fromAccount.getBalance().compareTo(amount) <= 0) {
            return new TransferResponse(TransactionStatus.FAILED, "Insufficient balance");
        }

        // Perform transfer
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Record transaction using builder pattern
        Transaction transaction = Transaction.builder()
                .from(fromAccount)
                .to(toAccount)
                .amount(amount)
                .status(TransactionStatus.SUCCESS)
                .build();

        transactionRepository.save(transaction);

        return new TransferResponse(TransactionStatus.SUCCESS, "Transfer completed successfully");
    }
}
