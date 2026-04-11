package com.karim.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karim.dto.AddStockRequestDto;
import com.karim.dto.AdjustStockRequestDto;
import com.karim.dto.ConfirmStockSaleRequestDto;
import com.karim.dto.ReleaseReservedStockRequestDto;
import com.karim.dto.ReserveStockRequestDto;
import com.karim.dto.RestockFromReturnRequestDto;
import com.karim.dto.StockTransactionFilter;
import com.karim.entity.Stock;
import com.karim.entity.StockTransaction;
import com.karim.service.StockService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class StockController {

	private final StockService stockService;

	// =========================================================
	// USER APIs
	// =========================================================

	/**
	 * Get stock details for a product Example: GET /api/user/stocks/{productId}
	 */
	@GetMapping("/user/stocks/{productId}")
	public ResponseEntity<Stock> getStock(@PathVariable UUID productId) {
		Stock stock = stockService.getStock(productId);
		return ResponseEntity.ok(stock);
	}

	// =========================================================
	// ADMIN APIs
	// =========================================================

	/**
	 * Add stock manually / purchase stock Example: POST
	 * /api/admin/stocks/{productId}/add
	 */
	@PostMapping("/admin/stocks/{productId}/add")
	public ResponseEntity<String> addStock(@PathVariable UUID productId, @RequestBody AddStockRequestDto request,
			@RequestHeader("X-Actor-Id") UUID actorId) {

		stockService.addStock(productId, request.getQuantity(), request.getReason(), actorId);
		return ResponseEntity.ok("Stock added successfully");
	}

	/**
	 * Reserve stock for an order Example: POST
	 * /api/admin/stocks/{productId}/reserve
	 */
	@PostMapping("/admin/stocks/{productId}/reserve")
	public ResponseEntity<String> reserveStock(@PathVariable UUID productId,
			@RequestBody ReserveStockRequestDto request, @RequestHeader("X-Actor-Id") UUID actorId) {

		stockService.reserveStock(productId, request.getQuantity(), request.getOrderId(), actorId);
		return ResponseEntity.ok("Stock reserved successfully");
	}

	/**
	 * Release reserved stock when order is cancelled Example: POST
	 * /api/admin/stocks/{productId}/release
	 */
	@PostMapping("/admin/stocks/{productId}/release")
	public ResponseEntity<String> releaseReservedStock(@PathVariable UUID productId,
			@RequestBody ReleaseReservedStockRequestDto request, @RequestHeader("X-Actor-Id") UUID actorId) {

		stockService.releaseReservedStock(productId, request.getQuantity(), request.getOrderId(), actorId);
		return ResponseEntity.ok("Reserved stock released successfully");
	}

	/**
	 * Confirm reserved stock as final sale Example: POST
	 * /api/admin/stocks/{productId}/confirm-sale
	 */
	@PostMapping("/admin/stocks/{productId}/confirm-sale")
	public ResponseEntity<String> confirmStockSale(@PathVariable UUID productId,
			@RequestBody ConfirmStockSaleRequestDto request, @RequestHeader("X-Actor-Id") UUID actorId) {

		stockService.confirmStockSale(productId, request.getQuantity(), request.getOrderId(), actorId);
		return ResponseEntity.ok("Stock sale confirmed successfully");
	}

	/**
	 * Restock from approved return Example: POST
	 * /api/admin/stocks/{productId}/restock-return
	 */
	@PostMapping("/admin/stocks/{productId}/restock-return")
	public ResponseEntity<String> restockFromReturn(@PathVariable UUID productId,
			@RequestBody RestockFromReturnRequestDto request, @RequestHeader("X-Actor-Id") UUID actorId) {

		stockService.restockFromReturn(productId, request.getQuantity(), request.getReturnRequestId(), actorId);
		return ResponseEntity.ok("Stock restocked from return successfully");
	}

	/**
	 * Manual stock adjustment Positive delta -> increase Negative delta -> decrease
	 * Example: PATCH /api/admin/stocks/{productId}/adjust
	 */
	@PatchMapping("/admin/stocks/{productId}/adjust")
	public ResponseEntity<String> adjustStock(@PathVariable UUID productId, @RequestBody AdjustStockRequestDto request,
			@RequestHeader("X-Actor-Id") UUID actorId) {

		stockService.adjustStock(productId, request.getDelta(), request.getReason(), actorId);
		return ResponseEntity.ok("Stock adjusted successfully");
	}

	/**
	 * Get low stock / out of stock products Example: GET
	 * /api/admin/stocks/low-stock
	 */
	@GetMapping("/admin/stocks/low-stock")
	public ResponseEntity<List<Stock>> getLowStockProducts() {
		List<Stock> stocks = stockService.getLowStockProducts();
		return ResponseEntity.ok(stocks);
	}

	/**
	 * Get stock transaction history for a product Example: GET
	 * /api/admin/stocks/{productId}/transactions
	 */
	@PostMapping("/admin/stocks/{productId}/transactions/search")
	public ResponseEntity<Page<StockTransaction>> getStockTransactions(@PathVariable UUID productId,
			@RequestBody StockTransactionFilter filter,
			@PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

		Page<StockTransaction> transactions = stockService.getStockTransactions(productId, filter, pageable);

		return ResponseEntity.ok(transactions);
	}
}