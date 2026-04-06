package com.karim.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.karim.dto.StockTransactionFilter;
import com.karim.entity.Stock;
import com.karim.entity.StockTransaction;

public interface StockService {

	Stock getStock(UUID productId);

	void addStock(UUID productId, int quantity, String reason, UUID actorId);

	void reserveStock(UUID productId, int quantity, UUID orderId, UUID actorId);

	void releaseReservedStock(UUID productId, int quantity, UUID orderId, UUID actorId);

	void confirmStockSale(UUID productId, int quantity, UUID orderId, UUID actorId);

	void restockFromReturn(UUID productId, int quantity, UUID returnRequestId, UUID actorId);

	void adjustStock(UUID productId, int delta, String reason, UUID actorId);

	List<Stock> getLowStockProducts();

	Page<StockTransaction> getStockTransactions(UUID productId, StockTransactionFilter filter, Pageable pageable);
}
