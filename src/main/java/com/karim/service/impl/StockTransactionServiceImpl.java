package com.karim.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.entity.StockTransaction;
import com.karim.repository.StockTransactionRepository;
import com.karim.service.StockTransactionService;

@Service
public class StockTransactionServiceImpl implements StockTransactionService {

	private final StockTransactionRepository stockTransactionRepository;

	public StockTransactionServiceImpl(StockTransactionRepository stockTransactionRepository) {
		this.stockTransactionRepository = stockTransactionRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<StockTransaction> getTransactionsByProduct(UUID productId, Pageable pageable) {

		return stockTransactionRepository.findByProduct(productId, pageable);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StockTransaction> getTransactionsByOrder(UUID orderId) {

		return stockTransactionRepository.findByOrderId(orderId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StockTransaction> getTransactionsByReturn(UUID returnRequestId) {

		return stockTransactionRepository.findByReturnRequestId(returnRequestId);
	}
}