package com.example.booking.payment.midtrans;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class MidtransDtos {

    private MidtransDtos() {}

    public record SnapRequest(
            @JsonProperty("transaction_details") TransactionDetails transactionDetails,
            @JsonProperty("customer_details") CustomerDetails customerDetails,
            Expiry expiry) {}

    public record TransactionDetails(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("gross_amount") long grossAmount) {}

    public record CustomerDetails(
            @JsonProperty("first_name") String firstName, String email) {}

    public record Expiry(String unit, int duration) {}

    public record SnapResponse(String token, @JsonProperty("redirect_url") String redirectUrl) {}

    public record Notification(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("status_code") String statusCode,
            @JsonProperty("gross_amount") String grossAmount,
            @JsonProperty("signature_key") String signatureKey,
            @JsonProperty("transaction_status") String transactionStatus,
            @JsonProperty("fraud_status") String fraudStatus,
            @JsonProperty("transaction_id") String transactionId) {}
}