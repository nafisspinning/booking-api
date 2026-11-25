package com.example.booking.payment.midtrans;

import com.example.booking.payment.midtrans.MidtransDtos.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MidtransClient {

    private final RestClient restClient;
    private final String serverKey;

    public MidtransClient(MidtransProperties props) {
        this.serverKey = props.serverKey();

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        String basicAuth = Base64.getEncoder()
                .encodeToString((serverKey + ":").getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(props.snapBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public SnapResponse createTransaction(SnapRequest request) {
        return restClient.post()
                .uri("/snap/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SnapResponse.class);
    }

    /** signature_key = SHA512(order_id + status_code + gross_amount + server_key) */
    public boolean isValidSignature(Notification n) {
        String raw = n.orderId() + n.statusCode() + n.grossAmount() + serverKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(digest);
            return n.signatureKey() != null && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    n.signatureKey().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}