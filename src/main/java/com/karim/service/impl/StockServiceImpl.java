package com.karim.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	public Stock getStock(UUID productId) {

		return stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found for productId: " + productId));
	}

	@Override
	@Transactional
	public void addStock(UUID productId, int quantity, String reason, UUID actorId) {

		if (quantity <= 0) {
			throw new RuntimeException("Quantity must be greater than 0");
		}

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found"));

		// ✅ Capture before
		int quantityBefore = stock.getQuantityAvailable();

		// ✅ Update stock
		int quantityAfter = quantityBefore + quantity;
		stock.setQuantityAvailable(quantityAfter);

		stockRepository.save(stock);

		// ✅ Create transaction log
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

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found"));

		int availableBefore = stock.getQuantityAvailable();
		int reservedBefore = stock.getQuantityReserved();

		// ✅ Validation: sufficient stock
		if (availableBefore < quantity) {
			throw new RuntimeException("Insufficient stock available");
		}

		// ✅ Update quantities
		int availableAfter = availableBefore - quantity;
		int reservedAfter = reservedBefore + quantity;

		stock.setQuantityAvailable(availableAfter);
		stock.setQuantityReserved(reservedAfter);

		stockRepository.save(stock);

		// ✅ Log transaction
		StockTransaction tx = new StockTransaction();
		tx.setProductId(productId);
		tx.setOrderId(orderId);
		tx.setType(StockTransactionType.SALE);
		tx.setQuantityDelta(-quantity); // negative since stock is reduced
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

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found"));

		int availableBefore = stock.getQuantityAvailable();
		int reservedBefore = stock.getQuantityReserved();

		// ✅ Validation: reserved stock should be sufficient
		if (reservedBefore < quantity) {
			throw new RuntimeException("Insufficient reserved stock to release");
		}

		// ✅ Reverse reservation
		int availableAfter = availableBefore + quantity;
		int reservedAfter = reservedBefore - quantity;

		stock.setQuantityAvailable(availableAfter);
		stock.setQuantityReserved(reservedAfter);

		stockRepository.save(stock);

		// ✅ Log transaction
		StockTransaction tx = new StockTransaction();
		tx.setProductId(productId);
		tx.setOrderId(orderId);
		tx.setType(StockTransactionType.ADJUSTMENT);
		tx.setQuantityDelta(quantity); // positive because stock is returned
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

		// ✅ Fetch stock
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found"));

		int reservedBefore = stock.getQuantityReserved();

		// ✅ Validation: ensure enough reserved stock
		if (reservedBefore < quantity) {
			throw new RuntimeException("Insufficient reserved stock to confirm sale");
		}

		// ✅ Reduce reserved stock (final sale)
		int reservedAfter = reservedBefore - quantity;
		stock.setQuantityReserved(reservedAfter);

		stockRepository.save(stock);

		// ✅ Note:
		// At this stage, available stock was already reduced during reservation.
		// So we only reduce reservedQuantity here.
	}
}