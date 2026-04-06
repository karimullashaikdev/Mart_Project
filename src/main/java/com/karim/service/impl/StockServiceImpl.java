package com.karim.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	@Override
	@Transactional
	public void restockFromReturn(UUID productId, int quantity, UUID returnRequestId, UUID actorId) {

		// 🔍 Step 1: Fetch stock
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found for productId: " + productId));

		// 📊 Step 2: Capture before state
		int beforeQty = stock.getQuantityAvailable() == null ? 0 : stock.getQuantityAvailable();

		// ➕ Step 3: Update available quantity
		int afterQty = beforeQty + quantity;
		stock.setQuantityAvailable(afterQty);
		stock.setUpdatedBy(actorId);

		stockRepository.save(stock); // triggers @PreUpdate

		// 🧾 Step 4: Create stock transaction
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

		// 🔍 Step 1: Fetch stock
		Stock stock = stockRepository.findByProductId(productId)
				.orElseThrow(() -> new RuntimeException("Stock not found for productId: " + productId));

		// 📊 Step 2: Current quantity
		int beforeQty = stock.getQuantityAvailable() == null ? 0 : stock.getQuantityAvailable();

		// 🧠 Step 3: Validate (important!)
		int afterQty = beforeQty + delta;
		if (afterQty < 0) {
			throw new IllegalArgumentException("Stock cannot go negative");
		}

		// 🔄 Step 4: Update stock
		stock.setQuantityAvailable(afterQty);
		stock.setUpdatedBy(actorId);

		stockRepository.save(stock);

		// 🧾 Step 5: Decide transaction type
		StockTransactionType type;

		if (delta > 0) {
			type = StockTransactionType.ADJUSTMENT; // manual increase
		} else {
			type = StockTransactionType.DAMAGED; // loss/damage
		}

		// 🧾 Step 6: Save transaction
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
	public Page<StockTransaction> getStockTransactions(
	        UUID productId,
	        StockTransactionFilter filter,
	        Pageable pageable) {

	    return transactionRepository.findTransactions(
	            productId,
	            filter.getType(),
	            filter.getOrderId(),
	            filter.getReturnRequestId(),
	            filter.getFromDate(),
	            filter.getToDate(),
	            pageable
	    );
	}
}