package com.inata.stripe.test.paymentGateway;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/payment")
@CrossOrigin(origins = "http://localhost:4200")
public class StripePaymentController {

    private static final String STRIPE_SECRET_KEY = "replace key here";
    private static final Logger log = LoggerFactory.getLogger(StripePaymentController.class);

    @PostMapping("/create-checkout-session")
    public Map<String, Object> createCheckoutSession(@RequestBody RequestDTO request) throws StripeException {
        Stripe.apiKey = STRIPE_SECRET_KEY;

        log.info("Creating Stripe Checkout Session for amount: {}", request.getAmount());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:4200/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl("http://localhost:4200/cancel")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount((long) (request.getAmount() * 100)) // Convert to cents
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Payment for Order #" + request.getOrderId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("order_id", request.getOrderId())
                .putMetadata("user_id", request.getUserId())
                .setCustomerEmail(request.getEmail()) // Automatically fills email in Stripe Checkout
                .build();

        Session session = Session.create(params);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getId());
        response.put("url", session.getUrl()); // Return Stripe-hosted Checkout URL

        return response;
    }
}
