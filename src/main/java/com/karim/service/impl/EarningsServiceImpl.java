//package com.karim.service.impl;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.domain.Specification;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import com.karim.dto.AgentEarningResponse;
//import com.karim.dto.EarningFilterDto;
//import com.karim.dto.PaymentDetailsDto;
//import com.karim.dto.PayoutFilter;
//import com.karim.dto.PayoutResponse;
//import com.karim.entity.AgentEarning;
//import com.karim.entity.AgentPayout;
//import com.karim.entity.AgentPayoutItem;
//import com.karim.entity.DeliveryAgent;
//import com.karim.entity.DeliveryRide;
//import com.karim.enums.EarningStatus;
//import com.karim.enums.PayoutStatus;
//import com.karim.repository.AgentEarningRepository;
//import com.karim.repository.AgentPayoutRepository;
//import com.karim.repository.DeliveryRideRepository;
//import com.karim.service.EarningsService;
//import com.karim.specifications.PayoutSpecification;
//
//@Service
//public class EarningsServiceImpl implements EarningsService {
//
//	private final DeliveryRideRepository deliveryRideRepository;
//	private final AgentEarningRepository agentEarningRepository;
//	private final AgentPayoutRepository agentPayoutRepository;
//
//	public EarningsServiceImpl(DeliveryRideRepository deliveryRideRepository,
//			AgentEarningRepository agentEarningRepository,AgentPayoutRepository agentPayoutRepository) {
//		this.deliveryRideRepository = deliveryRideRepository;
//		this.agentEarningRepository = agentEarningRepository;
//		this.agentPayoutRepository=agentPayoutRepository;
//	}
//
//	@Transactional
//	@Override
//	public void calculateEarning(UUID rideId, UUID assignmentId, UUID agentId, UUID actorId) {
//
//		// 🔍 1. Fetch ride
//		DeliveryRide ride = deliveryRideRepository.findById(rideId)
//				.orElseThrow(() -> new RuntimeException("Ride not found"));
//
//		// ⚠️ 2. Validate assignment & agent
//		if (!ride.getAssignmentId().equals(assignmentId) || !ride.getAgentId().equals(agentId)) {
//			throw new RuntimeException("Invalid ride, assignment, or agent mismatch");
//		}
//
//		// ⚠️ 3. Prevent duplicate earnings
//		Optional<AgentEarning> existing = agentEarningRepository.findByRideId(rideId);
//		if (existing.isPresent()) {
//			throw new RuntimeException("Earning already calculated for this ride");
//		}
//
//		// 💰 4. Extract amounts from ride
//		float baseAmount = ride.getBaseAmount();
//		float kmAmount = ride.getKmAmount();
//		float surgeAmount = ride.getSurgeAmount();
//
//		// 🧮 5. Calculate net earning
//		float netEarning = baseAmount + kmAmount + surgeAmount;
//
//		// 🧾 6. Create earning record
//		AgentEarning earning = new AgentEarning();
//		earning.setRideId(rideId);
//		earning.setAssignmentId(assignmentId);
//		earning.setAgentId(agentId);
//		earning.setBaseEarning(baseAmount);
//		earning.setKmEarning(kmAmount);
//		earning.setSurgeEarning(surgeAmount);
//		earning.setNetEarning(netEarning);
//		earning.setStatus(EarningStatus.PENDING);
//		earning.setCreatedAt(LocalDateTime.now());
//
//		agentEarningRepository.save(earning);
//	}
//
//	@Transactional
//	@Override
//	public void approveEarning(UUID earningId, UUID actorId) {
//
//		// 🔍 1. Fetch earning
//		AgentEarning earning = agentEarningRepository.findById(earningId)
//				.orElseThrow(() -> new RuntimeException("Earning not found"));
//
//		// ⚠️ 2. Validate current status
//		if (earning.getStatus() != EarningStatus.PENDING) {
//			throw new RuntimeException("Only pending earnings can be approved");
//		}
//
//		// ✅ 3. Update fields
//		earning.setStatus(EarningStatus.APPROVED);
//		earning.setApprovedBy(actorId);
//		earning.setApprovedAt(LocalDateTime.now());
//		earning.setUpdatedAt(LocalDateTime.now());
//
//		// 💾 4. Save
//		agentEarningRepository.save(earning);
//	}
//
//	@Transactional
//	@Override
//	public void disputeEarning(UUID earningId, String reason, UUID actorId) {
//
//		// 🔍 1. Fetch earning
//		AgentEarning earning = agentEarningRepository.findById(earningId)
//				.orElseThrow(() -> new RuntimeException("Earning not found"));
//
//		// ⚠️ 2. Validate current status
//		if (earning.getStatus() == EarningStatus.PAID) {
//			throw new RuntimeException("Paid earnings cannot be disputed");
//		}
//
//		if (earning.getStatus() == EarningStatus.DISPUTED) {
//			throw new RuntimeException("Earning is already disputed");
//		}
//
//		// 🧾 3. Update status + dispute details
//		earning.setStatus(EarningStatus.DISPUTED);
//		earning.setIncentiveReason(reason);
//		earning.setUpdatedAt(LocalDateTime.now());
//
//		// 💾 4. Save
//		agentEarningRepository.save(earning);
//	}
//
//	@Override
//	public AgentEarningResponse getEarning(UUID earningId) {
//
//		AgentEarning earning = agentEarningRepository.findById(earningId)
//				.orElseThrow(() -> new RuntimeException("Earning not found"));
//
//		return AgentEarningResponse.builder().id(earning.getId()).rideId(earning.getRideId())
//				.assignmentId(earning.getAssignmentId()).agentId(earning.getAgentId())
//				.baseEarning(earning.getBaseEarning()).kmEarning(earning.getKmEarning())
//				.surgeEarning(earning.getSurgeEarning()).netEarning(earning.getNetEarning()).status(earning.getStatus())
//				.createdAt(earning.getCreatedAt()).build();
//	}
//
//	@Override
//	public Page<AgentEarningResponse> listEarningsByAgent(UUID agentId, EarningFilterDto filters, Pageable pageable) {
//
//		LocalDateTime fromDateTime = null;
//		LocalDateTime toDateTime = null;
//
//		if (filters.getFromDate() != null) {
//			fromDateTime = filters.getFromDate().atStartOfDay();
//		}
//
//		if (filters.getToDate() != null) {
//			toDateTime = filters.getToDate().atTime(23, 59, 59);
//		}
//
//		Page<AgentEarning> earningsPage = agentEarningRepository.findByFilters(agentId, filters.getStatus(),
//				fromDateTime, toDateTime, pageable);
//
//		return earningsPage.map(e -> AgentEarningResponse.builder().id(e.getId()).rideId(e.getRideId())
//				.assignmentId(e.getAssignmentId()).agentId(e.getAgentId()).baseEarning(e.getBaseEarning())
//				.kmEarning(e.getKmEarning()).surgeEarning(e.getSurgeEarning()).netEarning(e.getNetEarning())
//				.status(e.getStatus()).createdAt(e.getCreatedAt()).build());
//	}
//
//	@Override
//	public BigDecimal getPendingEarningsSummary(UUID agentId) {
//
//		if (agentId == null) {
//			throw new RuntimeException("AgentId cannot be null");
//		}
//
//		return agentEarningRepository.sumPendingEarningsByAgentId(agentId);
//	}
//
//	@Transactional
//	@Override
//	public UUID createPayout(UUID agentId, List<UUID> earningIds, PaymentDetailsDto paymentDetails, UUID actorId) {
//
//		if (earningIds == null || earningIds.isEmpty()) {
//			throw new RuntimeException("Earning IDs cannot be empty");
//		}
//
//		// 🔍 1. Fetch earnings
//		List<AgentEarning> earnings = agentEarningRepository.findAllById(earningIds);
//
//		if (earnings.size() != earningIds.size()) {
//			throw new RuntimeException("Some earnings not found");
//		}
//
//		// ⚠️ 2. Validate all belong to agent and are pending
//		for (AgentEarning earning : earnings) {
//			if (!earning.getAgentId().equals(agentId)) {
//				throw new RuntimeException("Earning does not belong to agent");
//			}
//			if (earning.getStatus() != EarningStatus.PENDING) {
//				throw new RuntimeException("Only pending earnings can be paid");
//			}
//		}
//
//		// 💰 3. Calculate total payout
//		double totalAmount = earnings.stream().mapToDouble(AgentEarning::getNetEarning).sum();
//
//		// 🧾 4. Create payout record
//		AgentPayout payout = new AgentPayout();
//		payout.setAgentId(agentId);
//		payout.setTotalAmount(totalAmount);
//		payout.setPaymentMethod(paymentDetails.getPaymentMethod());
//		payout.setPaymentReference(paymentDetails.getPaymentReference());
//		payout.setStatus(PayoutStatus.PROCESSING);
//		payout.setCreatedAt(LocalDateTime.now());
//		payout.setCreatedBy(actorId);
//
//		payout = agentPayoutRepository.save(payout);
//
//		// 📦 5. Create payout items
//		List<AgentPayoutItem> payoutItems = new ArrayList<>();
//
//		for (AgentEarning earning : earnings) {
//			AgentPayoutItem item = new AgentPayoutItem();
//			item.setPayoutId(payout.getId());
//			item.setEarningId(earning.getId());
//			item.setAmount(earning.getNetEarning());
//
//			payoutItems.add(item);
//
//			// 🔄 6. Update earning status
//			earning.setStatus(EarningStatus.PAID);
//			earning.setUpdatedAt(LocalDateTime.now());
//		}
//
//		agentPayoutItemRepository.saveAll(payoutItems);
//		agentEarningRepository.saveAll(earnings);
//
//		return payout.getId();
//	}
//
//	@Transactional
//	@Override
//	public void processPayout(UUID payoutId, UUID actorId) {
//
//		// 🔍 1. Fetch payout
//		AgentPayout payout = agentPayoutRepository.findById(payoutId).orElseThrow(() -> new RuntimeException("Payout not found"));
//
//		// ⚠️ 2. Validate status
//		if (payout.getStatus() != PayoutStatus.PROCESSING) {
//			throw new RuntimeException("Only pending payouts can be processed");
//		}
//
//		// 🔄 3. Update status → PROCESSING
//		payout.setStatus(PayoutStatus.PROCESSING);
//		payout.setUpdatedAt(LocalDateTime.now());
//		payout.setUpdatedBy(actorId);
//
//		payoutRepository.save(payout);
//
//		// 💳 4. Initiate transfer (mock / integration point)
//		// In real-world: call bank API / UPI gateway / payment provider
//
//		boolean transferSuccess = initiateTransfer(payout);
//
//		if (transferSuccess) {
//			payout.setStatus(PayoutStatus.SUCCESS);
//		} else {
//			payout.setStatus(PayoutStatus.FAILED);
//		}
//
//		payout.setUpdatedAt(LocalDateTime.now());
//		payoutRepository.save(payout);
//	}
//
//	// 🧪 Mock transfer method (replace with real integration later)
//	private boolean initiateTransfer(Payout payout) {
//		// Example: call external payment gateway API
//		// For now, assume success
//		return true;
//	}
//
//	@Transactional
//	@Override
//	public void markPayoutSuccess(UUID payoutId, String transactionRef, UUID actorId) {
//
//		// 🔍 1. Fetch payout
//		Payout payout = payoutRepository.findById(payoutId).orElseThrow(() -> new RuntimeException("Payout not found"));
//
//		// ⚠️ 2. Prevent double processing (idempotency)
//		if (payout.getStatus() == PayoutStatus.SUCCESS) {
//			throw new RuntimeException("Payout already marked as success");
//		}
//
//		if (payout.getStatus() == PayoutStatus.FAILED) {
//			throw new RuntimeException("Cannot mark failed payout as success");
//		}
//
//		// 🔍 3. Fetch agent
//		DeliveryAgent agent = deliveryAgentRepository.findById(payout.getAgentId())
//				.orElseThrow(() -> new RuntimeException("Delivery agent not found"));
//
//		// 💰 4. Update payout
//		payout.setStatus(PayoutStatus.SUCCESS);
//		payout.setCompletedAt(LocalDateTime.now());
//		payout.setTransactionRef(transactionRef);
//		payout.setUpdatedAt(LocalDateTime.now());
//		payout.setUpdatedBy(actorId);
//
//		payoutRepository.save(payout);
//
//		// 💳 5. Update agent wallet balance
//		float currentBalance = agent.getWalletBalance();
//		float updatedBalance = currentBalance - payout.getAmount();
//
//		if (updatedBalance < 0) {
//			throw new RuntimeException("Insufficient wallet balance for payout adjustment");
//		}
//
//		agent.setWalletBalance(updatedBalance);
//		agent.setUpdatedAt(LocalDateTime.now());
//
//		deliveryAgentRepository.save(agent);
//	}
//
//	}
//
//	@Transactional
//	@Override
//	public void markPayoutFailed(UUID payoutId, UUID actorId) {
//
//		// 🔍 1. Fetch payout
//		AgentPayout payout = agentPayoutRepository.findById(payoutId)
//				.orElseThrow(() -> new RuntimeException("Payout not found"));
//
//		// ⚠️ 2. Validate status
//		if (payout.getStatus() == PayoutStatus.FAILED) {
//			throw new RuntimeException("Payout is already failed");
//		}
//
//		if (payout.getStatus() == PayoutStatus.COMPLETED) {
//			throw new RuntimeException("Completed payout cannot be marked as failed");
//		}
//
//		UUID agentId = payout.getAgentId();
//
//		// 🔄 3. Update payout
//		payout.setStatus(PayoutStatus.FAILED);
//		payout.setCompletedAt(LocalDateTime.now());
//		payout.setProcessedBy(actorId);
//
//		agentPayoutRepository.save(payout);
//
//		// 🔄 4. Revert earnings back to APPROVED
//		List<AgentEarning> earnings = agentEarningRepository.findByAgentIdAndStatus(agentId, EarningStatus.PAID);
//
//		for (AgentEarning earning : earnings) {
//			earning.setStatus(EarningStatus.APPROVED);
//			earning.setPaidAt(null);
//			earning.setApprovedAt(LocalDateTime.now());
//			earning.setApprovedBy(actorId);
//		}
//
//		agentEarningRepository.saveAll(earnings);
//	}
//
//	@Override
//	public PayoutResponse getPayout(UUID payoutId) {
//
//		// 🔍 1. Fetch payout
//		Payout payout = payoutRepository.findById(payoutId).orElseThrow(() -> new RuntimeException("Payout not found"));
//
//		// 🔍 2. Fetch payout items
//		List<PayoutItem> items = payoutItemRepository.findByPayoutId(payoutId);
//
//		// 🧾 3. Map items to DTO
//		List<PayoutItemResponse> itemResponses = items.stream()
//				.map(item -> PayoutItemResponse.builder().id(item.getId()).rideId(item.getRideId())
//						.earningAmount(item.getEarningAmount()).status(item.getStatus()).build())
//				.toList();
//
//		// 📦 4. Build response
//		return PayoutResponse.builder().id(payout.getId()).agentId(payout.getAgentId())
//				.totalAmount(payout.getTotalAmount()).status(payout.getStatus()).createdAt(payout.getCreatedAt())
//				.items(itemResponses).build();
//	}
//
//	@Override
//	public Page<PayoutResponse> listPayoutsByAgent(UUID agentId, PayoutFilter filter, Pageable pageable) {
//
//		Specification<Payout> spec = PayoutSpecification.getPayouts(agentId, filter);
//
//		Page<Payout> payouts = payoutRepository.findAll(spec, pageable);
//
//		return payouts.map(this::mapToResponse);
//	}
//
//	private PayoutResponse mapToResponse(Payout payout) {
//		return PayoutResponse.builder().id(payout.getId()).agentId(payout.getAgentId()).amount(payout.getAmount())
//				.status(payout.getStatus().name()).createdAt(payout.getCreatedAt()).processedAt(payout.getProcessedAt())
//				.build();
//	}
//
//	@Transactional
//	@Override
//	public void applyIncentive(UUID earningId, float amount, String reason, UUID actorId) {
//
//		// 🔍 1. Fetch earning
//		AgentEarning earning = agentEarningRepository.findById(earningId)
//				.orElseThrow(() -> new RuntimeException("Earning not found"));
//
//		// ⚠️ 2. Validate earning status (optional but recommended)
//		if (earning.getStatus() == EarningStatus.PAID) {
//			throw new RuntimeException("Cannot apply incentive on paid earning");
//		}
//
//		// 💰 3. Update incentive fields
//		earning.setIncentiveAmount(amount);
//		earning.setIncentiveReason(reason);
//		earning.setApprovedBy(actorId);
//		earning.setApprovedAt(LocalDateTime.now());
//
//		// 🧮 4. Recalculate net earning
//		float netEarning = earning.getBaseEarning() + earning.getKmEarning() + earning.getSurgeEarning()
//				+ earning.getIncentiveAmount() - earning.getPenaltyAmount();
//
//		earning.setNetEarning(netEarning);
//
//		// 🧾 5. Save updated earning
//		agentEarningRepository.save(earning);
//	}
//	
//	@Service
//	public class EarningsServiceImpl implements EarningsService {
//
//	    private final AgentEarningRepository agentEarningRepository;
//
//	    public EarningsServiceImpl(AgentEarningRepository agentEarningRepository) {
//	        this.agentEarningRepository = agentEarningRepository;
//	    }
//
//	    @Transactional
//	    @Override
//	    public void applyPenalty(UUID earningId, float amount, String reason, UUID actorId) {
//
//	        // 🔍 1. Fetch earning
//	        AgentEarning earning = agentEarningRepository.findById(earningId)
//	                .orElseThrow(() -> new RuntimeException("Earning not found"));
//
//	        // ⚠️ 2. Validate amount
//	        if (amount <= 0) {
//	            throw new RuntimeException("Penalty amount must be greater than 0");
//	        }
//
//	        // ⚠️ 3. Update penalty fields
//	        earning.setPenaltyAmount(earning.getPenaltyAmount() + amount);
//	        earning.setPenaltyReason(reason);
//
//	        // 🧮 4. Recalculate net earning
//	        float net = earning.getBaseEarning()
//	                + earning.getKmEarning()
//	                + earning.getSurgeEarning()
//	                + earning.getIncentiveAmount()
//	                - earning.getPenaltyAmount();
//
//	        earning.setNetEarning(net);
//
//	        // 🧾 5. Audit fields
//	        earning.setUpdatedAt(LocalDateTime.now());
//	        earning.setApprovedBy(actorId);
//
//	        // 💾 6. Save
//	        agentEarningRepository.save(earning);
//	    }
//	}
//}
