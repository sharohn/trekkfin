package com.trekkfin.controller;

import com.trekkfin.dto.TransferRequest;
import com.trekkfin.dto.TransferResponse;
import com.trekkfin.entity.TransactionStatus;
import com.trekkfin.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

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
    public ResponseEntity<TransferResponse> transfer(@RequestBody TransferRequest request) {
        try {
            TransferResponse response = walletService.transfer(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new TransferResponse(TransactionStatus.FAILED, "An error occurred: " + e.getMessage()));
        }
    }
}





