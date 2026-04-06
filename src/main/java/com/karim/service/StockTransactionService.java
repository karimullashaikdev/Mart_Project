package com.karim.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.karim.entity.StockTransaction;

public interface StockTransactionService {

    Page<StockTransaction> getTransactionsByProduct(UUID productId, Pageable pageable);

    List<StockTransaction> getTransactionsByOrder(UUID orderId);

    List<StockTransaction> getTransactionsByReturn(UUID returnRequestId);
}
