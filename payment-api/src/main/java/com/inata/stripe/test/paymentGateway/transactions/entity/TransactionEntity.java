package com.inata.stripe.test.paymentGateway.transactions.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "stripe_transactions")
public class TransactionEntity {

    @Id
    @Column(name = "trans_id", nullable = false, unique = true)
    private String transactionId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;  // Links transaction to a user.

    @Column(name = "payment_method_id")
    private String paymentMethodId; // Stores Stripe pm_xxx for ACH debits.


    @Column(name = "transaction_type", nullable = false)
    private String transactionType; // "deposit" (ACH Debit) or "payout" (ACH Withdrawal).

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount; // Transaction amount.

    @Column(name = "status", nullable = false)
    private String status; // "pending", "succeeded", "failed", "refunded".

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt; // Timestamp of transaction.

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // Timestamp of last status update.
    @Column(name = "comments")
    private String comments;
}
