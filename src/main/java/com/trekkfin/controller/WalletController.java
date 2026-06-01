package com.trekkfin.controller;

import com.trekkfin.dto.TransferRequest;
import com.trekkfin.dto.TransferResponse;
import com.trekkfin.entity.TransactionStatus;
import com.trekkfin.service.RedisService;
import com.trekkfin.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private RedisService redisService;

    @GetMapping("/balance/{contactNumber}")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String contactNumber) {
        try {
            BigDecimal balance = walletService.getBalance(contactNumber);
            return ResponseEntity.ok(balance);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        // 1. Check if a completed response is already cached in Redis
        Optional<TransferResponse> cached = redisService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        // 2. Try to lock the key as IN_PROGRESS
        boolean acquired = redisService.startRequest(idempotencyKey);
        if (!acquired) {
            // Double check if a cached response finished in the fraction of time since we last checked
            cached = redisService.getCachedResponse(idempotencyKey);
            if (cached.isPresent()) {
                return ResponseEntity.ok(cached.get());
            }
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new TransferResponse(TransactionStatus.FAILED, "A duplicate request is already in progress"));
        }

        try {
            // 3. Process the transfer
            TransferResponse response = walletService.transfer(request);

            // 4. Save response in Redis (cached for 24 hours)
            redisService.saveResponse(idempotencyKey, response);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 5. On system/db exceptions, clear the key so the request can be retried
            redisService.removeKey(idempotencyKey);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TransferResponse(TransactionStatus.FAILED, "An error occurred: " + e.getMessage()));
        }
    }
}





