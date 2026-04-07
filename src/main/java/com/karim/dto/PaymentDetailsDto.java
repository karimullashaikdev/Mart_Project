package com.karim.dto;

import lombok.Data;

@Data
public class PaymentDetailsDto {

	private String paymentMethod; // BANK_TRANSFER, UPI, etc.
	private String paymentReference; // transaction id / reference
}
