package com.trekkfin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trekkfin.dto.TransferRequest;
import com.trekkfin.entity.Account;
import com.trekkfin.repository.AccountRepository;
import com.trekkfin.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class WalletIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RedisService redisService;

    @Autowired
    private AccountRepository accountRepository;

    private final String senderPhone = "1234567890";
    private final String receiverPhone = "0987654321";

    @BeforeEach
    public void setup() {
        if (accountRepository.findByPhone(senderPhone).isEmpty()) {
            accountRepository.save(Account.builder()
                    .name("Sender")
                    .email("sender@example.com")
                    .phone(senderPhone)
                    .balance(new BigDecimal("100.00"))
                    .build());
        } else {
            accountRepository.updateBalanceByPhone(senderPhone, new BigDecimal("100.00"));
        }

        if (accountRepository.findByPhone(receiverPhone).isEmpty()) {
            accountRepository.save(Account.builder()
                    .name("Receiver")
                    .email("receiver@example.com")
                    .phone(receiverPhone)
                    .balance(new BigDecimal("0.00"))
                    .build());
        } else {
            accountRepository.updateBalanceByPhone(receiverPhone, new BigDecimal("0.00"));
        }
    }

    @Test
    public void testTransferRequiresIdempotencyKeyHeader() throws Exception {
        TransferRequest request = new TransferRequest(senderPhone, receiverPhone, new BigDecimal("10.00"));

        mockMvc.perform(post("/wallet/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.msg").value("Idempotency-Key header is required"));
    }

    @Test
    public void testFirstRequestSucceedsAndCachesResponse() throws Exception {
        TransferRequest request = new TransferRequest(senderPhone, receiverPhone, new BigDecimal("10.00"));
        String idempotencyKey = "key-success-123";

        when(redisService.getCachedResponse(idempotencyKey)).thenReturn(Optional.empty());
        when(redisService.startRequest(idempotencyKey)).thenReturn(true);

        mockMvc.perform(post("/wallet/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.msg").value("Transfer completed successfully"));

        verify(redisService, times(1)).saveResponse(eq(idempotencyKey), any());
    }

    @Test
    public void testSecondRequestReturnsCachedResponse() throws Exception {
        TransferRequest request = new TransferRequest(senderPhone, receiverPhone, new BigDecimal("10.00"));
        String idempotencyKey = "key-cached-123";

        com.trekkfin.dto.TransferResponse cachedResponse = new com.trekkfin.dto.TransferResponse(
                com.trekkfin.entity.TransactionStatus.SUCCESS, "Transfer completed successfully (CACHED)"
        );

        when(redisService.getCachedResponse(idempotencyKey)).thenReturn(Optional.of(cachedResponse));

        mockMvc.perform(post("/wallet/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.msg").value("Transfer completed successfully (CACHED)"));

        verify(redisService, never()).startRequest(any());
    }

    @Test
    public void testConcurrentDuplicateRequestReturnsConflict() throws Exception {
        TransferRequest request = new TransferRequest(senderPhone, receiverPhone, new BigDecimal("10.00"));
        String idempotencyKey = "key-conflict-123";

        when(redisService.getCachedResponse(idempotencyKey)).thenReturn(Optional.empty());
        when(redisService.startRequest(idempotencyKey)).thenReturn(false);

        mockMvc.perform(post("/wallet/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.msg").value("A duplicate request is already in progress"));
    }
}
