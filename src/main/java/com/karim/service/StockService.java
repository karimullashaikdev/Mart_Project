package com.karim.service;

import java.util.UUID;

import com.karim.entity.Stock;

public interface StockService {
	
	 Stock getStock(UUID productId);
	 
	  void addStock(UUID productId, int quantity, String reason, UUID actorId);
	  
	  void reserveStock(UUID productId, int quantity, UUID orderId, UUID actorId);
	  
	  void releaseReservedStock(UUID productId, int quantity, UUID orderId, UUID actorId);
	  
	  void confirmStockSale(UUID productId, int quantity, UUID orderId, UUID actorId);
}
