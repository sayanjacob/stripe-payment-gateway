package com.inata.stripe.test.paymentGateway.stripeusers.repository;

import com.inata.stripe.test.paymentGateway.stripeusers.entity.StripeUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StripeUserRepository extends JpaRepository<StripeUser, Integer> {
    Optional<StripeUser> findByConnectAccountId(String connectAccountId);
    Optional<StripeUser> findByCustomerId (String customerId);


}
