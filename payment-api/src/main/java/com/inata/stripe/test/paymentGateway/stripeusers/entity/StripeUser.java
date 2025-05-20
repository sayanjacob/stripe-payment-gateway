package com.inata.stripe.test.paymentGateway.stripeusers.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "stripe_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StripeUser {
    @Id
    @Column(name = "id", nullable = false, unique = true)
    private int id;

    @Column(name = "customer_id", unique = true)
    private String customerId;

    @Column(name = "connect_acc_id", unique = true)
    private String connectAccountId;

    @Column(name = "connect_acc_status")
    private String connectAccountStatus;

    @Column(name = "fca_id", unique = true)
    private String financialConnectionId;

    @Column(name = "pm_id", unique = true)
    private String paymentMethodId;

}
