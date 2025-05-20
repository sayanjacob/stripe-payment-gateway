package com.inata.stripe.test.paymentGateway.transactions.repository;

import com.inata.stripe.test.paymentGateway.transactions.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByUserId(Integer userId);

    Optional<TransactionEntity> findByPaymentMethodId(String paymentMethodId);
}
