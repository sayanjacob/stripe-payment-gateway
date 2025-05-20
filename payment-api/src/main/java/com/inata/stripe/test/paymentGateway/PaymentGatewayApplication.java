package com.inata.stripe.test.paymentGateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@EnableJpaRepositories(basePackages = {
        "com.inata.stripe.test.paymentGateway.transactions.Repository",
        "com.inata.stripe.test.paymentGateway.stripeusers.repository",
        "com.inata.stripe.test.paymentGateway.webhook"})


public class PaymentGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentGatewayApplication.class, args);
    }


}
