package com.karim.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.StockResponseDto;
import com.karim.dto.StockTransactionFilter;
import com.karim.entity.Stock;
import com.karim.entity.StockTransaction;
import com.karim.enums.StockTransactionType;
import com.karim.repository.StockRepository;
import com.karim.repository.StockTransactionRepository;
import com.karim.service.StockService;

@Service
public class StockServiceImpl implements StockService {

	private final StockRepository stockRepository;
	private final StockTransactionRepository transactionRepository;

	public StockServiceImpl(StockRepository stockRepository, StockTransactionRepository transactionRepository) {
		this.stockRepository = stockRepository;
		this.transactionRepository = transactionRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public StockResponseDto getStock(UUID productId) {
		Stock stock = findActiveStockOrThrow(productId);
		return toStockResponseDto(stock);
	}

	@Override
	@Transactional
	public void addStock(UUID productId, int quantity, String reason, UUID actorId) {

		if (quantity <= 0) {
			throw new RuntimeException("Quantity must be greater than 0");
		}

		Stock stock = findActiveStockOrThrow(productId);

		int quantityBefore = getAvailableQty(stock);
		int quantityAfter = quantityBefore + quantity;

		stock.setQuantityAvailable(quantityAfter);
		stock.setUpdatedBy(actorId);
		stockRepository.save(stock);

		StockTransaction tx = new StockTransaction();
		tx.setProductId(productId);
		tx.setType(StockTransactionType.PURCHASE);
		tx.setQuantityDelta(quantity);
		tx.setQuantityBefore(quantityBefore);
		tx.setQuantityAfter(quantityAfter);
		tx.setReason(reason);
		tx.setCreatedBy(actorId);

		transactionRepository.save(tx);
	}

	@Override
	@Transactional
	public void reserveStock(UUID productId, int quantity, UUID orderId, UUID actorId) {

		if (quantity <= 0) {
			throw new RuntimeException("Quantity must be greater than 0");
		}

		Stock stock = findActiveStockOrThrow(productId);

		int availableBefore = getAvailableQty(stock);
		int reservedBefore = getReservedQty(stock);

		if (availableBefore < quantity) {
			throw new RuntimeException("Insufficient stock available");
		}

		int availableAfter = availableBefore - quantity;
		int reservedAfter = reservedBefore + quantity;

		stock.setQuantityAvailable(availableAfter);
		stock.setQuantityReserved(reservedAfter);
		stock.setUpdatedBy(actorId);
		stockRepository.save(stock);

		StockTransaction tx = new StockTransaction();
		tx.setProductId(productId);
		tx.setOrderId(orderId);
		tx.setType(StockTransactionType.SALE);
		tx.setQuantityDelta(-quantity);
		tx.setQuantityBefore(availableBefore);
		tx.setQuantityAfter(availableAfter);
		tx.setReason("Order Reservation");
		tx.setCreatedBy(actorId);

		transactionRepository.save(tx);
	}

	@Override
	@Transactional
	public void releaseReservedStock(UUID productId, int quantity, UUID orderId, UUID actorId) {

		if (quantity <= 0) {
			throw new RuntimeException("Quantity must be greater than 0");
		}

		Stock stock = findActiveStockOrThrow(productId);

		int availableBefore = getAvailableQty(stock);
		int reservedBefore = getReservedQty(stock);

		if (reservedBefore < quantity) {
			throw new RuntimeException("Insufficient reserved stock to release");
		}

		int availableAfter = availableBefore + quantity;
		int reservedAfter = reservedBefore - quantity;

		stock.setQuantityAvailable(availableAfter);
		stock.setQuantityReserved(reservedAfter);
		stock.setUpdatedBy(actorId);
		stockRepository.save(stock);

		StockTransaction tx = new StockTransaction();
		tx.setProductId(productId);
		tx.setOrderId(orderId);
		tx.setType(StockTransactionType.ADJUSTMENT);
		tx.setQuantityDelta(quantity);
		tx.setQuantityBefore(availableBefore);
		tx.setQuantityAfter(availableAfter);
		tx.setReason("Order Cancel - Release Reserved Stock");
		tx.setCreatedBy(actorId);

		transactionRepository.save(tx);
	}

	@Override
	@Transactional
	public void confirmStockSale(UUID productId, int quantity, UUID orderId, UUID actorId) {

		if (quantity <= 0) {
			throw new RuntimeException("Quantity must be greater than 0");
		}

		Stock stock = findActiveStockOrThrow(productId);

		int reservedBefore = getReservedQty(stock);

		if (reservedBefore < quantity) {
			throw new RuntimeException("Insufficient reserved stock to confirm sale");
		}

		int reservedAfter = reservedBefore - quantity;
		stock.setQuantityReserved(reservedAfter);
		stock.setUpdatedBy(actorId);
		stockRepository.save(stock);

		// available quantity already reduced during reserveStock()
	}

	@Override
	@Transactional
	public void restockFromReturn(UUID productId, int quantity, UUID returnRequestId, UUID actorId) {

		if (quantity <= 0) {
			throw new RuntimeException("Quantity must be greater than 0");
		}

		Stock stock = findActiveStockOrThrow(productId);

		int beforeQty = getAvailableQty(stock);
		int afterQty = beforeQty + quantity;

		stock.setQuantityAvailable(afterQty);
		stock.setUpdatedBy(actorId);
		stockRepository.save(stock);

		StockTransaction transaction = new StockTransaction();
		transaction.setProductId(productId);
		transaction.setReturnRequestId(returnRequestId);
		transaction.setType(StockTransactionType.RETURN);
		transaction.setQuantityDelta(quantity);
		transaction.setQuantityBefore(beforeQty);
		transaction.setQuantityAfter(afterQty);
		transaction.setReason("Restocked from approved return");
		transaction.setCreatedBy(actorId);

		transactionRepository.save(transaction);
	}

	@Override
	@Transactional
	public void adjustStock(UUID productId, int delta, String reason, UUID actorId) {

		Stock stock = findActiveStockOrThrow(productId);

		int beforeQty = getAvailableQty(stock);
		int afterQty = beforeQty + delta;

		if (afterQty < 0) {
			throw new IllegalArgumentException("Stock cannot go negative");
		}

		stock.setQuantityAvailable(afterQty);
		stock.setUpdatedBy(actorId);
		stockRepository.save(stock);

		StockTransactionType type = delta > 0 ? StockTransactionType.ADJUSTMENT : StockTransactionType.DAMAGED;

		StockTransaction tx = new StockTransaction();
		tx.setProductId(productId);
		tx.setType(type);
		tx.setQuantityDelta(delta);
		tx.setQuantityBefore(beforeQty);
		tx.setQuantityAfter(afterQty);
		tx.setReason(reason);
		tx.setCreatedBy(actorId);

		transactionRepository.save(tx);
	}

	@Override
	public List<Stock> getLowStockProducts() {
		return stockRepository.findLowStockProducts();
	}

	@Override
	public Page<StockTransaction> getStockTransactions(UUID productId, StockTransactionFilter filter,
			Pageable pageable) {

		return transactionRepository.findTransactions(productId, filter.getType(), filter.getOrderId(),
				filter.getReturnRequestId(), filter.getFromDate(), filter.getToDate(), pageable);
	}

	private Stock findActiveStockOrThrow(UUID productId) {
		return stockRepository.findActiveByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Active stock not found for productId: " + productId));
	}

	private int getAvailableQty(Stock stock) {
		return stock.getQuantityAvailable() != null ? stock.getQuantityAvailable() : 0;
	}

	private int getReservedQty(Stock stock) {
		return stock.getQuantityReserved() != null ? stock.getQuantityReserved() : 0;
	}

	private StockResponseDto toStockResponseDto(Stock stock) {
		StockResponseDto dto = new StockResponseDto();
		dto.setStockId(stock.getId());
		dto.setProductId(stock.getProduct() != null ? stock.getProduct().getId() : null);
		dto.setQuantityAvailable(stock.getQuantityAvailable() != null ? stock.getQuantityAvailable() : 0);
		dto.setQuantityReserved(stock.getQuantityReserved() != null ? stock.getQuantityReserved() : 0);
		dto.setReorderLevel(stock.getReorderLevel());
		dto.setReorderQuantity(stock.getReorderQuantity());
		dto.setStatus(stock.getStatus());
		dto.setLastUpdatedAt(stock.getLastUpdatedAt());
		return dto;
	}
}