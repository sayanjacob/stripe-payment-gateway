package com.inata.stripe.test.paymentGateway.webhook;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepo extends MongoRepository<EventEntity,Integer> {
}
