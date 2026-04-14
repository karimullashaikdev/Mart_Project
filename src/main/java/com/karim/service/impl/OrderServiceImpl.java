package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.karim.dto.AdminOrderDto;
import com.karim.dto.CartItemResponse;
import com.karim.dto.CartSummaryResponse;
import com.karim.dto.OrderFilter;
import com.karim.dto.OrderItemResponseDto;
import com.karim.dto.OrderResponseDto;
import com.karim.dto.OrderSummaryDto;
import com.karim.dto.PlaceOrderRequestDto;
import com.karim.entity.Address;
import com.karim.entity.Order;
import com.karim.entity.OrderItem;
import com.karim.entity.Product;
import com.karim.entity.User;
import com.karim.enums.OrderItemStatus;
import com.karim.enums.OrderStatus;
import com.karim.event.OrderConfirmedEvent;
import com.karim.exception.ResourceNotFoundException;
import com.karim.repository.AddressRepository;
import com.karim.repository.OrderItemRepository;
import com.karim.repository.OrderRepository;
import com.karim.repository.ProductRepository;
import com.karim.repository.UserRepository;
import com.karim.service.CartService;
import com.karim.service.OrderService;
import com.karim.service.StockService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final AddressRepository addressRepository;
	private final StockService stockService;
	private final CartService cartService;
	private final ApplicationEventPublisher eventPublisher;
	private final OrderNotificationService orderNotificationService;

	// ── System actor ID used for automated status transitions ─────────────────
	// This is a fixed UUID that represents the system (not a real user).
	// It will appear in updatedBy for auto-confirm and auto-processing transitions.
	private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(OrderStatus.PENDING,
			Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED), OrderStatus.CONFIRMED,
			Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED), OrderStatus.PROCESSING,
			Set.of(OrderStatus.DISPATCHED, OrderStatus.CANCELLED), OrderStatus.DISPATCHED,
			Set.of(OrderStatus.OUT_FOR_DELIVERY), OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
			OrderStatus.DELIVERED, Set.of(), OrderStatus.CANCELLED, Set.of());

	// =========================================================================
	// PLACE ORDER → auto-confirms immediately, then schedules PROCESSING
	// =========================================================================

	@Override
	@Transactional
	public Order placeOrder(UUID userId, PlaceOrderRequestDto dto, UUID actorId) {

		// 1. Fetch user
		User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		// 2. Fetch address
		Address address = addressRepository.findById(dto.getAddressId())
				.orElseThrow(() -> new RuntimeException("Address not found"));

		// 3. Read cart
		CartSummaryResponse cartSummary = cartService.getCartSummary(userId);

		// 4. Build order — start as PENDING
		Order order = new Order();
		order.setUser(user);
		order.setAddress(address);
		order.setStatus(OrderStatus.PENDING);
		order.setCustomerNotes(dto.getCustomerNotes());
		order.setCreatedBy(actorId);
		order.setOrderNumber("ORD-" + System.currentTimeMillis());

		double subtotal = 0;
		double totalTax = 0;
		List<OrderItem> orderItems = new ArrayList<>();

		// 5. Build items from cart
		for (CartItemResponse cartItem : cartSummary.getItems()) {

			Product product = productRepository.findById(cartItem.getProductId())
					.orElseThrow(() -> new RuntimeException("Product not found: " + cartItem.getProductId()));

			if (!Boolean.TRUE.equals(product.getIsActive())) {
				throw new RuntimeException("Product is inactive: " + product.getName());
			}

			int quantity = cartItem.getQuantity();
			double price = cartItem.getUnitPrice().doubleValue();
			double taxPercent = product.getTaxPercent() != null ? product.getTaxPercent() : 0.0;
			double base = price * quantity;
			double tax = base * taxPercent / 100.0;

			subtotal += base;
			totalTax += tax;

			OrderItem orderItem = new OrderItem();
			orderItem.setOrder(order);
			orderItem.setProduct(product);
			orderItem.setQuantity(quantity);
			orderItem.setUnitPrice(price);
			orderItem.setTaxPercent(taxPercent);
			orderItem.setCreatedBy(actorId);
			orderItems.add(orderItem);
		}

		// 6. Pricing
		double deliveryFee = subtotal > 500 ? 0 : 40;
		double totalAmount = subtotal + totalTax + deliveryFee;

		order.setSubtotal(subtotal);
		order.setTaxAmount(totalTax);
		order.setDeliveryFee(deliveryFee);
		order.setTotalAmount(totalAmount);

		// 7. Save order
		Order savedOrder = orderRepository.save(order);

		// 8. Save items
		orderItems.forEach(item -> item.setOrder(savedOrder));
		orderItemRepository.saveAll(orderItems);

		// 9. Reserve stock
		for (OrderItem item : orderItems) {
			stockService.reserveStock(item.getProduct().getId(), item.getQuantity(), savedOrder.getId(), actorId);
		}

		// 10. Clear cart
		cartService.clearCart(userId, actorId);

		// 11. ── AUTO-CONFIRM immediately (PENDING → CONFIRMED, done by system) ──
		// We call confirmOrder inside the same transaction so it's atomic.
		// The event triggers the scheduler to auto-move to PROCESSING after 5 min.
		confirmOrder(savedOrder.getId(), SYSTEM_ACTOR);

		return savedOrder;
	}

	// =========================================================================
	// CONFIRM (PENDING → CONFIRMED)
	// Called by system immediately after placeOrder, and also available via API.
	// After saving, publishes OrderConfirmedEvent so the scheduler picks it up.
	// =========================================================================

	@Override
	@Transactional
	public void confirmOrder(UUID orderId, UUID actorId) {

		log.info("confirmOrder started for orderId={}", orderId);

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.PENDING) {
			throw new RuntimeException("Only PENDING orders can be confirmed. Current: " + order.getStatus());
		}

		order.setStatus(OrderStatus.CONFIRMED);
		order.setConfirmedAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);

		Order savedOrder = orderRepository.save(order);

		log.info("Order {} updated to CONFIRMED", orderId);

		// publish scheduler event as usual
		eventPublisher.publishEvent(new OrderConfirmedEvent(this, orderId));

		// IMPORTANT: notify delivery dashboard only AFTER COMMIT
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					log.info("Sending delivery websocket notification after commit for order={}", orderId);
					orderNotificationService.notifyNewOrder(savedOrder);
				} catch (Exception e) {
					log.error("Failed to send notification for order {}", orderId, e);
				}
			}
		});
	}
	// =========================================================================
	// MARK PROCESSING (CONFIRMED → PROCESSING)
	// Called automatically by OrderProcessingScheduler after 5 minutes.
	// NOT exposed as a manual admin action anymore.
	// =========================================================================

	@Override
	@Transactional
	public void markProcessing(UUID orderId, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.CONFIRMED) {
			// Already moved on (e.g. cancelled) — silently skip, don't throw.
			// The scheduler calls this; we don't want it to fail loudly on edge cases.
			return;
		}

		order.setStatus(OrderStatus.PROCESSING);
		order.setUpdatedBy(actorId);
		orderRepository.save(order);
	}

	// =========================================================================
	// DISPATCHED (PROCESSING → DISPATCHED)
	// Called by DeliveryController when agent accepts the order.
	// =========================================================================

	@Override
	@Transactional
	public void markDispatched(UUID orderId, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.PROCESSING) {
			throw new RuntimeException("Only PROCESSING orders can be dispatched. Current: " + order.getStatus());
		}

		order.setStatus(OrderStatus.DISPATCHED);
		order.setDispatchedAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);
		orderRepository.save(order);
	}

	// =========================================================================
	// OUT FOR DELIVERY (DISPATCHED → OUT_FOR_DELIVERY)
	// Called by DeliveryController when agent clicks "Start Delivery".
	// =========================================================================

	@Override
	@Transactional
	public void markOutForDelivery(UUID orderId, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.DISPATCHED) {
			throw new RuntimeException(
					"Only DISPATCHED orders can be marked OUT_FOR_DELIVERY. Current: " + order.getStatus());
		}

		order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
		order.setUpdatedBy(actorId);
		orderRepository.save(order);
	}

	// =========================================================================
	// DELIVERED (OUT_FOR_DELIVERY → DELIVERED)
	// Called by DeliveryController after OTP is verified.
	// =========================================================================

	@Override
	@Transactional
	public void markDelivered(UUID orderId, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
			throw new RuntimeException(
					"Only OUT_FOR_DELIVERY orders can be marked DELIVERED. Current: " + order.getStatus());
		}

		List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);
		for (OrderItem item : items) {
			stockService.confirmStockSale(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
		}

		order.setStatus(OrderStatus.DELIVERED);
		order.setDeliveredAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);
		orderRepository.save(order);
	}

	// =========================================================================
	// CANCEL ORDER
	// =========================================================================

	@Override
	@Transactional
	public void cancelOrder(UUID orderId, String reason, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY
				|| order.getStatus() == OrderStatus.DELIVERED) {
			throw new RuntimeException("Order cannot be cancelled at this stage: " + order.getStatus());
		}

		if (order.getStatus() == OrderStatus.CANCELLED) {
			throw new RuntimeException("Order is already cancelled");
		}

		List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);
		for (OrderItem item : items) {
			stockService.releaseReservedStock(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
		}

		order.setStatus(OrderStatus.CANCELLED);
		order.setCancellationReason(reason);
		order.setCancelledAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);
		orderRepository.save(order);
	}

	// =========================================================================
	// UPDATE ORDER STATUS (generic, used by admin dashboard for edge cases)
	// Validates against ALLOWED_TRANSITIONS — cannot skip steps.
	// =========================================================================

	@Override
	@Transactional
	public void updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		OrderStatus currentStatus = order.getStatus();
		Set<OrderStatus> allowedNext = ALLOWED_TRANSITIONS.get(currentStatus);

		if (allowedNext == null || !allowedNext.contains(newStatus)) {
			throw new RuntimeException("Invalid status transition: " + currentStatus + " → " + newStatus);
		}

		if (newStatus == OrderStatus.CONFIRMED)
			order.setConfirmedAt(LocalDateTime.now());
		if (newStatus == OrderStatus.DISPATCHED)
			order.setDispatchedAt(LocalDateTime.now());

		if (newStatus == OrderStatus.DELIVERED) {
			List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);
			for (OrderItem item : items) {
				stockService.confirmStockSale(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
			}
			order.setDeliveredAt(LocalDateTime.now());
		}

		if (newStatus == OrderStatus.CANCELLED) {
			List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);
			for (OrderItem item : items) {
				stockService.releaseReservedStock(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
			}
			order.setCancelledAt(LocalDateTime.now());
		}

		order.setStatus(newStatus);
		order.setUpdatedBy(actorId);
		orderRepository.save(order);
	}

	// =========================================================================
	// SOFT DELETE (only cancelled orders)
	// =========================================================================

	@Override
	@Transactional
	public void softDeleteOrder(UUID orderId, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (Boolean.TRUE.equals(order.getIsDeleted())) {
			throw new RuntimeException("Order already deleted");
		}

		if (order.getStatus() != OrderStatus.CANCELLED) {
			throw new RuntimeException("Only cancelled orders can be deleted");
		}

		if (!isAdmin(actorId)) {
			throw new RuntimeException("Only admin can delete orders");
		}

		order.setDeletedBy(actorId);
		order.setDeletedAt(LocalDateTime.now());
		orderRepository.save(order);
		orderRepository.delete(order);
	}

	// =========================================================================
	// READ METHODS — unchanged from original
	// =========================================================================

	@Override
	public OrderResponseDto getOrder(UUID orderId, UUID userId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		if (!order.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this order");
		}

		List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);
		List<OrderItemResponseDto> itemDtos = items.stream()
				.map(item -> OrderItemResponseDto.builder().productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal()).build())
				.toList();

		return OrderResponseDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.status(order.getStatus().name()).subtotal(order.getSubtotal()).taxAmount(order.getTaxAmount())
				.deliveryFee(order.getDeliveryFee()).totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt())
				.items(itemDtos).build();
	}

	@Override
	public OrderResponseDto getOrderByNumber(String orderNumber) {

		Order order = orderRepository.findByOrderNumber(orderNumber)
				.orElseThrow(() -> new RuntimeException("Order not found"));

		List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
		List<OrderItemResponseDto> itemDtos = items.stream()
				.map(item -> OrderItemResponseDto.builder().productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal()).build())
				.toList();

		return OrderResponseDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.status(order.getStatus().name()).subtotal(order.getSubtotal()).taxAmount(order.getTaxAmount())
				.deliveryFee(order.getDeliveryFee()).totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt())
				.items(itemDtos).build();
	}

	@Override
	public Page<OrderSummaryDto> listOrdersByUser(UUID userId, OrderFilter filter, Pageable pageable) {

		Page<Order> orders = orderRepository.findOrdersByUser(userId, filter.getStatus(), filter.getFromDate(),
				filter.getToDate(), pageable);

		return orders.map(order -> OrderSummaryDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.status(order.getStatus().name()).totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt())
				.build());
	}

	@Override
	public Page<AdminOrderDto> listOrdersAdmin(OrderFilter filter, Pageable pageable) {

		Page<Order> orders = orderRepository.listOrdersAdmin(filter.getStatus(), filter.getFromDate(),
				filter.getToDate(), pageable);

		return orders.map(order -> AdminOrderDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.userName(order.getUser().getFullName()).status(order.getStatus().name())
				.totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt()).build());
	}

	@Override
	public List<OrderItemResponseDto> getOrderItems(UUID orderId) {

		orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		return orderItemRepository.findByOrder_Id(orderId).stream()
				.map(item -> OrderItemResponseDto.builder().productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal()).build())
				.toList();
	}

	@Override
	@Transactional
	public void updateOrderItemStatus(UUID itemId, OrderItemStatus newStatus, UUID actorId) {

		OrderItem item = orderItemRepository.findById(itemId)
				.orElseThrow(() -> new RuntimeException("Order item not found"));

		Order order = item.getOrder();

		if (order.getStatus() != OrderStatus.DELIVERED) {
			throw new RuntimeException("Return allowed only for delivered orders");
		}

		if (item.getItemStatus() == OrderItemStatus.RETURNED
				|| item.getItemStatus() == OrderItemStatus.PARTIALLY_RETURNED) {
			throw new RuntimeException("Item already returned");
		}

		if (newStatus != OrderItemStatus.RETURNED && newStatus != OrderItemStatus.PARTIALLY_RETURNED) {
			throw new RuntimeException("Invalid item status update");
		}

		stockService.restockFromReturn(item.getProduct().getId(), item.getQuantity(), order.getId(), actorId);
		item.setItemStatus(newStatus);
		orderItemRepository.save(item);
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	private boolean isAdmin(UUID actorId) {
		User user = userRepository.findById(actorId).orElseThrow(() -> new RuntimeException("Actor not found"));
		return user.getRole() == com.karim.enums.Role.ADMIN;
	}
}