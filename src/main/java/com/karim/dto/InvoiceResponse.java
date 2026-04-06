package com.karim.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvoiceResponse {

	private UUID invoiceId;

	private UUID orderId;

	private UUID paymentId;

	private String invoiceNumber;

	private String pdfUrl;

	private String status;

	private Integer retryCount;

	private LocalDateTime generatedAt;

	private LocalDateTime sentAt;
}
