package com.karim.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karim.dto.AdminOrderDto;
import com.karim.dto.OrderFilter;
import com.karim.dto.OrderItemRequestDto;
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
import com.karim.exception.ResourceNotFoundException;
import com.karim.repository.AddressRepository;
import com.karim.repository.OrderItemRepository;
import com.karim.repository.OrderRepository;
import com.karim.repository.ProductRepository;
import com.karim.repository.UserRepository;
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

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(OrderStatus.PENDING,
			Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED), OrderStatus.CONFIRMED,
			Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED), OrderStatus.PROCESSING,
			Set.of(OrderStatus.DISPATCHED, OrderStatus.CANCELLED), OrderStatus.DISPATCHED,
			Set.of(OrderStatus.OUT_FOR_DELIVERY), OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
			OrderStatus.DELIVERED, Set.of(), OrderStatus.CANCELLED, Set.of());

	@Override
	@Transactional
	public Order placeOrder(UUID userId, PlaceOrderRequestDto dto, UUID actorId) {

		// 🔍 1. Fetch user & address
		User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

		Address address = addressRepository.findById(dto.getAddressId())
				.orElseThrow(() -> new RuntimeException("Address not found"));

		// 🧾 2. Prepare order
		Order order = new Order();
		order.setUser(user);
		order.setAddress(address);
		order.setStatus(OrderStatus.PENDING);
		order.setCustomerNotes(dto.getCustomerNotes());
		order.setCreatedBy(actorId);

		// 🔢 Generate order number
		order.setOrderNumber("ORD-" + System.currentTimeMillis());

		double subtotal = 0;
		double totalTax = 0;

		List<OrderItem> orderItems = new ArrayList<>();

		// 🛒 3. Process each item
		for (OrderItemRequestDto itemDto : dto.getItems()) {

			Product product = productRepository.findById(itemDto.getProductId())
					.orElseThrow(() -> new RuntimeException("Product not found"));

			if (!product.getIsActive()) {
				throw new RuntimeException("Product is inactive: " + product.getName());
			}

			int quantity = itemDto.getQuantity();
			double price = product.getSellingPrice();
			double taxPercent = product.getTaxPercent() != null ? product.getTaxPercent() : 0;

			double base = price * quantity;
			double tax = base * taxPercent / 100;

			subtotal += base;
			totalTax += tax;

			// 📦 Create order item
			OrderItem orderItem = new OrderItem();
			orderItem.setOrder(order);
			orderItem.setProduct(product);
			orderItem.setQuantity(quantity);
			orderItem.setUnitPrice(price);
			orderItem.setTaxPercent(taxPercent);
			orderItem.setCreatedBy(actorId);

			orderItems.add(orderItem);
		}

		// 💰 4. Pricing calculation
		double deliveryFee = subtotal > 500 ? 0 : 40; // example rule
		double totalAmount = subtotal + totalTax + deliveryFee;

		order.setSubtotal(subtotal);
		order.setTaxAmount(totalTax);
		order.setDeliveryFee(deliveryFee);
		order.setTotalAmount(totalAmount);

		// 💾 5. Save order first
		Order savedOrder = orderRepository.save(order);

		// 💾 6. Save order items
		orderItems.forEach(item -> item.setOrder(savedOrder));
		orderItemRepository.saveAll(orderItems);

		// 📦 7. Reserve stock (IMPORTANT)
		for (OrderItem item : orderItems) {
			stockService.reserveStock(item.getProduct().getId(), item.getQuantity(), savedOrder.getId(), actorId);
		}

		return savedOrder;
	}

	@Override
	public OrderResponseDto getOrder(UUID orderId, UUID userId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// 🔐 2. Ownership check
		if (!order.getUser().getId().equals(userId)) {
			throw new RuntimeException("Unauthorized access to this order");
		}

		// 📦 3. Fetch items
		List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);

		// 🔄 4. Map items to DTO
		List<OrderItemResponseDto> itemDtos = items.stream()
				.map(item -> OrderItemResponseDto.builder().productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal()).build())
				.toList();

		// 🧾 5. Build response
		return OrderResponseDto.builder().orderId(order.getId()).orderNumber(order.getOrderNumber())
				.status(order.getStatus().name()).subtotal(order.getSubtotal()).taxAmount(order.getTaxAmount())
				.deliveryFee(order.getDeliveryFee()).totalAmount(order.getTotalAmount()).placedAt(order.getPlacedAt())
				.items(itemDtos).build();
	}

	@Override
	public OrderResponseDto getOrderByNumber(String orderNumber) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findByOrderNumber(orderNumber)
				.orElseThrow(() -> new RuntimeException("Order not found"));

		// 📦 2. Fetch items
		List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());

		// 🔄 3. Map items
		List<OrderItemResponseDto> itemDtos = items.stream()
				.map(item -> OrderItemResponseDto.builder().productId(item.getProduct().getId())
						.productName(item.getProduct().getName()).quantity(item.getQuantity())
						.unitPrice(item.getUnitPrice()).lineTotal(item.getLineTotal()).build())
				.toList();

		// 🧾 4. Build response
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
	@Transactional
	public void confirmOrder(UUID orderId, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate current status
		if (order.getStatus() != OrderStatus.PENDING) {
			throw new RuntimeException("Only PENDING orders can be confirmed");
		}

		// 🔄 3. Update fields
		order.setStatus(OrderStatus.CONFIRMED);
		order.setConfirmedAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);

		// 💾 4. Save (triggers @PreUpdate)
		orderRepository.save(order);
	}

	@Override
	@Transactional
	public void markProcessing(UUID orderId, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate transition
		if (order.getStatus() != OrderStatus.CONFIRMED) {
			throw new RuntimeException("Only CONFIRMED orders can be moved to PROCESSING");
		}

		// 🔄 3. Update status
		order.setStatus(OrderStatus.PROCESSING);
		order.setUpdatedBy(actorId);

		// 💾 4. Save
		orderRepository.save(order);
	}

	@Override
	@Transactional
	public void markDispatched(UUID orderId, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate transition
		if (order.getStatus() != OrderStatus.PROCESSING) {
			throw new RuntimeException("Only PROCESSING orders can be dispatched");
		}

		// 🔄 3. Update status + timestamp
		order.setStatus(OrderStatus.DISPATCHED);
		order.setDispatchedAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);

		// 💾 4. Save
		orderRepository.save(order);
	}

	@Override
	@Transactional
	public void markOutForDelivery(UUID orderId, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate transition
		if (order.getStatus() != OrderStatus.DISPATCHED) {
			throw new RuntimeException("Only DISPATCHED orders can be marked as OUT_FOR_DELIVERY");
		}

		// 🔄 3. Update status
		order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
		order.setUpdatedBy(actorId);

		// 💾 4. Save
		orderRepository.save(order);
	}

	@Override
	@Transactional
	public void markDelivered(UUID orderId, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate transition
		if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
			throw new RuntimeException("Only OUT_FOR_DELIVERY orders can be marked as DELIVERED");
		}

		// 📦 3. Fetch order items
		List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);

		// 🔄 4. Confirm stock sale (VERY IMPORTANT)
		for (OrderItem item : items) {
			stockService.confirmStockSale(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
		}

		// 🔄 5. Update order
		order.setStatus(OrderStatus.DELIVERED);
		order.setDeliveredAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);

		// 💾 6. Save
		orderRepository.save(order);
	}

	@Override
	@Transactional
	public void cancelOrder(UUID orderId, String reason, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// ⚠️ 2. Validate state
		if (order.getStatus() == OrderStatus.DISPATCHED || order.getStatus() == OrderStatus.OUT_FOR_DELIVERY
				|| order.getStatus() == OrderStatus.DELIVERED) {

			throw new RuntimeException("Order cannot be cancelled at this stage");
		}

		// ⚠️ 3. Prevent duplicate cancellation
		if (order.getStatus() == OrderStatus.CANCELLED) {
			throw new RuntimeException("Order is already cancelled");
		}

		// 📦 4. Fetch order items
		List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);

		// 🔄 5. Release reserved stock (CRITICAL)
		for (OrderItem item : items) {
			stockService.releaseReservedStock(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
		}

		// 🔄 6. Update order
		order.setStatus(OrderStatus.CANCELLED);
		order.setCancellationReason(reason);
		order.setCancelledAt(LocalDateTime.now());
		order.setUpdatedBy(actorId);

		// 💾 7. Save
		orderRepository.save(order);
	}

	@Override
	@Transactional
	public void updateOrderStatus(UUID orderId, OrderStatus newStatus, UUID actorId) {

		// 🔍 1. Fetch order
		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		OrderStatus currentStatus = order.getStatus();

		// ⚠️ 2. Validate transition
		Set<OrderStatus> allowedNext = ALLOWED_TRANSITIONS.get(currentStatus);

		if (allowedNext == null || !allowedNext.contains(newStatus)) {
			throw new RuntimeException("Invalid status transition: " + currentStatus + " → " + newStatus);
		}

		// 📦 3. Handle special actions (SIDE EFFECTS)

		// 🔄 CONFIRMED
		if (newStatus == OrderStatus.CONFIRMED) {
			order.setConfirmedAt(LocalDateTime.now());
		}

		// 🔄 DISPATCHED
		if (newStatus == OrderStatus.DISPATCHED) {
			order.setDispatchedAt(LocalDateTime.now());
		}

		// 🔄 DELIVERED (VERY IMPORTANT)
		if (newStatus == OrderStatus.DELIVERED) {

			List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);

			for (OrderItem item : items) {
				stockService.confirmStockSale(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
			}

			order.setDeliveredAt(LocalDateTime.now());
		}

		// 🔄 CANCELLED (VERY IMPORTANT)
		if (newStatus == OrderStatus.CANCELLED) {

			List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);

			for (OrderItem item : items) {
				stockService.releaseReservedStock(item.getProduct().getId(), item.getQuantity(), orderId, actorId);
			}

			order.setCancelledAt(LocalDateTime.now());
		}

		// 🔄 4. Update status
		order.setStatus(newStatus);
		order.setUpdatedBy(actorId);

		// 💾 5. Save
		orderRepository.save(order);
	}

	@Transactional
	@Override
	public void softDeleteOrder(UUID orderId, UUID actorId) {

		Order order = orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));

		// Check already deleted (extra safety, though SQLRestriction filters it)
		if (Boolean.TRUE.equals(order.getIsDeleted())) {
			throw new RuntimeException("Order already deleted");
		}

		// Validate status
		if (order.getStatus() != OrderStatus.CANCELLED) {
			throw new RuntimeException("Only cancelled orders can be deleted");
		}

		// Validate actor (pseudo - depends on your auth system)
		if (!isAdmin(actorId)) {
			throw new RuntimeException("Only admin can delete orders");
		}

		// Set audit fields
		order.setDeletedBy(actorId);
		order.setDeletedAt(LocalDateTime.now());

		// Important: save first so audit fields persist
		orderRepository.save(order);

		// Trigger soft delete
		orderRepository.delete(order);
	}
	
	@Override
	public List<OrderItemResponseDto> getOrderItems(UUID orderId) {

	    // 🔍 1. Validate order exists
	    Order order = orderRepository.findById(orderId)
	            .orElseThrow(() -> new RuntimeException("Order not found"));

	    // 📦 2. Fetch items
	    List<OrderItem> items = orderItemRepository.findByOrder_Id(orderId);

	    // 🔄 3. Map to DTO
	    return items.stream()
	            .map(item -> OrderItemResponseDto.builder()
	                    .productId(item.getProduct().getId())
	                    .productName(item.getProduct().getName())
	                    .quantity(item.getQuantity())
	                    .unitPrice(item.getUnitPrice())
	                    .lineTotal(item.getLineTotal())
	                    .build())
	            .toList();
	}
	
	@Transactional
	@Override
	public void updateOrderItemStatus(UUID itemId, OrderItemStatus newStatus, UUID actorId) {

	    // 🔍 1. Fetch item
	    OrderItem item = orderItemRepository.findById(itemId)
	            .orElseThrow(() -> new RuntimeException("Order item not found"));

	    Order order = item.getOrder();

	    // ⚠️ 2. Allow return only after delivery
	    if (order.getStatus() != OrderStatus.DELIVERED) {
	        throw new RuntimeException("Return allowed only for delivered orders");
	    }

	    // ⚠️ 3. Prevent duplicate return
	    if (item.getItemStatus() == OrderItemStatus.RETURNED ||
	        item.getItemStatus() == OrderItemStatus.PARTIALLY_RETURNED) {
	        throw new RuntimeException("Item already returned");
	    }

	    // ⚠️ 4. Validate allowed transitions
	    if (newStatus != OrderItemStatus.RETURNED &&
	        newStatus != OrderItemStatus.PARTIALLY_RETURNED) {
	        throw new RuntimeException("Invalid item status update");
	    }

	    // 📦 5. Restock logic (based on your current design)
	    stockService.restockFromReturn(
	            item.getProduct().getId(),
	            item.getQuantity(),
	            order.getId(),
	            actorId
	    );

	    // 🔄 6. Update status
	    item.setItemStatus(newStatus);

	    // 💾 7. Save
	    orderItemRepository.save(item);
	}
	
	
	
	private boolean isAdmin(UUID actorId) {

	    User user = userRepository.findById(actorId)
	            .orElseThrow(() -> new RuntimeException("Actor not found"));

	    return user.getRole() == com.karim.enums.Role.ADMIN;
	}
}