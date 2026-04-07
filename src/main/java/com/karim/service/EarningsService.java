package com.karim.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.karim.dto.AgentEarningResponse;
import com.karim.dto.EarningFilterDto;
import com.karim.dto.PaymentDetailsDto;
import com.karim.dto.PayoutFilter;
import com.karim.dto.PayoutResponse;

public interface EarningsService {

	void calculateEarning(UUID rideId, UUID assignmentId, UUID agentId, UUID actorId);

	void approveEarning(UUID earningId, UUID actorId);

	void disputeEarning(UUID earningId, String reason, UUID actorId);

	AgentEarningResponse getEarning(UUID earningId);

	Page<AgentEarningResponse> listEarningsByAgent(UUID agentId, EarningFilterDto filters, Pageable pageable);

	BigDecimal getPendingEarningsSummary(UUID agentId);

	UUID createPayout(UUID agentId, List<UUID> earningIds, PaymentDetailsDto paymentDetails, UUID actorId);

	void processPayout(UUID payoutId, UUID actorId);

	void markPayoutSuccess(UUID payoutId, String transactionRef, UUID actorId);

	void markPayoutFailed(UUID payoutId, UUID actorId);

	PayoutResponse getPayout(UUID payoutId);

	Page<PayoutResponse> listPayoutsByAgent(UUID agentId, PayoutFilter filter, Pageable pageable);
	
	void applyIncentive(UUID earningId, float amount, String reason, UUID actorId);
	
	void applyPenalty(UUID earningId, float amount, String reason, UUID actorId);
}
