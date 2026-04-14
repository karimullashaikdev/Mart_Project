package com.karim.scheduler;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.karim.event.OrderConfirmedEvent;
import com.karim.service.OrderService;

import jakarta.annotation.PreDestroy;

/**
 * Listens for OrderConfirmedEvent and schedules an automatic transition from
 * CONFIRMED → PROCESSING after 5 minutes.
 *
 * No @Scheduled annotation needed — this is purely event-driven. Uses a
 * single-threaded ScheduledExecutorService so delays are independent per order
 * (one order's delay doesn't block another's).
 */
@Component
public class OrderProcessingScheduler {

	private static final Logger log = LoggerFactory.getLogger(OrderProcessingScheduler.class);

	// ── How long to wait before auto-moving CONFIRMED → PROCESSING ────────────
	private static final long PROCESSING_DELAY_MINUTES = 5;

	// ── System actor UUID — same constant used in OrderServiceImpl ────────────
	private static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final OrderService orderService;

	// Single-threaded scheduler — safe for concurrent orders because each
	// submitted task is independent. Increase pool size if order volume is high.
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

	public OrderProcessingScheduler(OrderService orderService) {
		this.orderService = orderService;
	}

	/**
	 * Called automatically by Spring when OrderServiceImpl publishes an
	 * OrderConfirmedEvent. Schedules markProcessing() to run after 5 min.
	 */
	@EventListener
	public void onOrderConfirmed(OrderConfirmedEvent event) {
		UUID orderId = event.getOrderId();
		log.info("[Scheduler] Order {} confirmed — will auto-move to PROCESSING in {} minutes", orderId,
				PROCESSING_DELAY_MINUTES);

		scheduler.schedule(() -> autoMoveToProcessing(orderId), PROCESSING_DELAY_MINUTES, TimeUnit.MINUTES);
	}

	/**
	 * Executed after the delay. Calls markProcessing() via OrderService. If the
	 * order was cancelled in the meantime, markProcessing() silently returns
	 * without throwing (see OrderServiceImpl.markProcessing()).
	 */
	private void autoMoveToProcessing(UUID orderId) {
		try {
			log.info("[Scheduler] Auto-moving order {} → PROCESSING", orderId);
			orderService.markProcessing(orderId, SYSTEM_ACTOR);
			log.info("[Scheduler] Order {} is now PROCESSING — visible to delivery agents", orderId);
		} catch (Exception e) {
			// Log but don't rethrow — this runs outside HTTP request scope
			log.warn("[Scheduler] Could not auto-move order {} to PROCESSING: {}", orderId, e.getMessage());
		}
	}

	/**
	 * Cleanly shuts down the executor when the Spring context closes, waiting up to
	 * 30 seconds for in-progress tasks to finish.
	 */
	@PreDestroy
	public void shutdown() {
		log.info("[Scheduler] Shutting down OrderProcessingScheduler...");
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}