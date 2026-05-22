package com.trekkfin.dto;

import java.math.BigDecimal;

public record TransferRequest(
    String from,
    String to,
    BigDecimal amount
) {}

