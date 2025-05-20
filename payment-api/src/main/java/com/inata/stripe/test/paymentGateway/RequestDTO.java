package com.inata.stripe.test.paymentGateway;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestDTO {
    private Long amount;
    private String currency;
    private String customerId;
    private String paymentMethodId;
    private String description;
    private String paymentMethod;
    private String email;
    private String orderId;
    private String userId;
}
