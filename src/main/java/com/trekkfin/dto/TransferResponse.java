package com.trekkfin.dto;

import com.trekkfin.entity.TransactionStatus;

public record TransferResponse(
    TransactionStatus status,
    String msg
) {}

