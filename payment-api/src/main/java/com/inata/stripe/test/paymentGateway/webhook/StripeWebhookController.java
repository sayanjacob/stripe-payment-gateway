package com.inata.stripe.test.paymentGateway.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inata.stripe.test.paymentGateway.transactions.entity.TransactionEntity;
import com.inata.stripe.test.paymentGateway.transactions.service.TransactionService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${stripe.api.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final EventRepo eventRepo;

    private final TransactionService transactionService;

    private final ObjectMapper objectMapper;

    public StripeWebhookController(ObjectMapper objectMapper, EventRepo eventRepo, TransactionService transactionService) {
        this.objectMapper = objectMapper;
        this.eventRepo = eventRepo;
        this.transactionService = transactionService;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(HttpServletRequest request) {
        Stripe.apiKey = secretKey;

        try {
            // Read the webhook payload
            String payload = new BufferedReader(request.getReader()).lines().collect(Collectors.joining("\n"));
            String sigHeader = request.getHeader("Stripe-Signature");
//            log.info("request:{}", request);
//            log.info("request payload:{}", payload);


            // Verify the webhook signature
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            EventEntity eventEntity = new EventEntity();
            eventEntity.setEventId(event.getId());
            eventEntity.setType(event.getType());

            eventEntity.setPayload(Document.parse(event.getData().toJson()));

            eventRepo.save(eventEntity);
//            log.info("Received Stripe Event: {}", event);

            // Process different event types
            switch (event.getType()) {
                // ACH Debit events (pulling money from customer bank)
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;

                case "payment_intent.canceled":
                    handlePaymentIntentCanceled(event);
                    break;

                // Charge events (actual money movement for ACH Debit)
                case "charge.succeeded":
                    handleChargeSucceeded(event);
                    break;
                case "charge.refunded":
                    handleChargeRefunded(event);
                    break;
                case "charge.failed":
                    handleChargeFailed(event);
                    break;
                case "charge.dispute.created":
                    handleChargeDisputeCreated(event);
                    break;
                case "charge.dispute.closed":
                    handleChargeDisputeClosed(event);
                    break;

                // ACH Withdrawal events (sending money to customer bank)
                case "payout.paid":
                    handlePayoutPaid(event);
                    break;
                case "payout.failed":
                    handlePayoutFailed(event);
                    break;
                case "payout.canceled":
                    handlePayoutCanceled(event);
                    break;

                default:
                    log.info("Unhandled event type: {}", event.getType());
            }

            return ResponseEntity.ok("Received");
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (IOException e) {
            log.error("Error reading webhook request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing request");
        } catch (Exception e) {
            log.error("Error handling webhook event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook handling error");
        }
    }

    /**
     * ACH Debit PaymentIntent handlers
     */
    private void handlePaymentIntentProcessing(Event event) throws IOException {
        JsonNode intentData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String paymentIntentId = intentData.get("id").asText();
        log.info("ACH Debit processing: PaymentIntent ID = {}", paymentIntentId);
    }

    private void handlePaymentIntentRequiresAction(Event event) throws IOException {
        JsonNode intentData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String paymentIntentId = intentData.get("id").asText();
        log.info("ACH Debit requires action: PaymentIntent ID = {}", paymentIntentId);
    }

    /*** Handle successful ACH Debit (funds pulled from the bank)*/
    private void handlePaymentIntentSucceeded(Event event) throws IOException {

        try {
            JsonNode intentData = objectMapper.readTree(event.toJson()).get("data").get("object");

            if (intentData == null || !intentData.has("id") || !intentData.has("status")) {
                log.error("Invalid intent data: missing id or status in intent event");
                return;
            }

            // Extract the payout ID and status
            String intentId = intentData.get("id").asText();

            // Log the payout event (before updating the transaction)
            log.info("Processing intent event: Intent ID = {}, Status = {}", intentId, "succeeded");

            // Retrieve the transaction associated with the payout ID
            TransactionEntity trans = transactionService.getTransactionById(intentId);

            // Check if transaction exists
            if (trans == null) {
                log.warn("No transaction found for Intent ID = {}", intentId);
                return;  // Exit if no matching transaction is found
            }

            // Update the transaction status
            trans.setStatus("deposited");
            trans.setComments("Deposit Completed");
            transactionService.updateTransactionStatus(trans);

            // Log the successful update
            log.info("ACH Debit successful: PaymentIntent ID = {} Status updated to = {}", intentId, "Successful");

        } catch (IOException e) {
            // Catch IOExceptions that may arise from reading/parsing the event JSON
            log.error("Error processing payout event", e);
            throw e;  // Rethrow the exception to propagate it up if needed
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            log.error("Unexpected error occurred while processing payout event", e);
        }

    }

    /**
     * Handles failed ACH Debit
     */
    private void handlePaymentIntentFailed(Event event) throws IOException {
        JsonNode intentData = objectMapper.readTree(event.toJson()).get("data").get("object");

        try {
            // Check for valid intent data
            if (intentData == null || !intentData.has("id") || !intentData.has("status")) {
                log.error("Invalid intent data: missing id or status in intent event ");
                return;
            }

            // Extract the PaymentIntent ID
            String intentId = intentData.get("id").asText();

            // Log the event (for a failed payment intent, we log 'failed' status)
            log.info("Processing PaymentIntent event: PaymentIntent ID = {}, Status = {}", intentId, "failed");

            // Retrieve the transaction associated with the PaymentIntent ID
            TransactionEntity trans = transactionService.getTransactionById(intentId);

            // Check if the transaction exists
            if (trans == null) {
                log.warn("No transaction found for PaymentIntent ID = {}", intentId);
                return;  // Exit if no matching transaction is found
            }

            // If the PaymentIntent failed, update the transaction status accordingly

            trans.setStatus("failed");
            String failureReason = intentData.has("last_payment_error") && intentData.get("last_payment_error").has("message")
                    ? intentData.get("last_payment_error").get("code").asText() + ":" + intentData.get("last_payment_error").get("network_decline_code").asText() + ":" + intentData.get("last_payment_error").get("message").asText()
                    : "No reason provided";
            trans.setComments(failureReason);

            // Log the failure reason
            log.error("PaymentIntent failed: PaymentIntent ID = {}, Reason = {}", intentId, failureReason);


            // Update the transaction status
            transactionService.updateTransactionStatus(trans);

        } catch (Exception e) {
            // Catch any unexpected exceptions
            log.error("Unexpected error occurred while processing PaymentIntent event", e);
        }
    }

    /**
     * Handles successful charge (when money is successfully deducted)
     */
    private void handleChargeSucceeded(Event event) throws IOException {
        JsonNode chargeData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String chargeId = chargeData.get("id").asText();
        log.info("Charge succeeded: Charge ID = {}", chargeId);
    }

    /**
     * Handles charge refunds
     */
    private void handleChargeRefunded(Event event) throws IOException {
        JsonNode chargeData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String chargeId = chargeData.get("id").asText();
        log.info("Charge refunded: Charge ID = {}", chargeId);
    }

    /**
     * Handles failed charges
     */
    private void handleChargeFailed(Event event) throws IOException {
        JsonNode chargeData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String chargeId = chargeData.get("id").asText();
        String failureReason = chargeData.has("failure_message") ? chargeData.get("failure_message").asText() : "No reason provided";
        log.error("Charge failed: Charge ID = {}, Reason = {}", chargeId, failureReason);
    }

    private void handleChargeDisputeCreated(Event event) throws IOException {
        JsonNode disputeData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String disputeId = disputeData.get("id").asText();
        String chargeId = disputeData.get("charge").asText();
        log.warn("Charge dispute created: Dispute ID = {}, Charge ID = {}", disputeId, chargeId);
    }

    private void handleChargeDisputeClosed(Event event) throws IOException {
        JsonNode disputeData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String disputeId = disputeData.get("id").asText();
        String chargeId = disputeData.get("charge").asText();
        String status = disputeData.get("status").asText();
        log.info("Charge dispute closed: Dispute ID = {}, Charge ID = {}, Status = {}", disputeId, chargeId, status);
    }

    /**
     * Handles successful payouts (withdrawals to the user's bank)
     */
    private void handlePayoutPaid(Event event) throws IOException {
        try {
            // Parse the payout data from the event
            JsonNode payoutData = objectMapper.readTree(event.toJson()).get("data").get("object");

            // Check if the payout data contains the necessary fields
            if (payoutData == null || !payoutData.has("id") || !payoutData.has("status")) {
                log.error("Invalid payout data: missing id or status in payout event");
                return;
            }

            // Extract the payout ID and status
            String payoutId = payoutData.get("id").asText();
            String payoutStatus = payoutData.get("status").asText();

            // Log the payout event (before updating the transaction)
            log.info("Processing payout event: Payout ID = {}, Status = {}", payoutId, payoutStatus);

            // Retrieve the transaction associated with the payout ID
            TransactionEntity trans = transactionService.getTransactionById(payoutId);

            // Check if transaction exists
            if (trans == null) {
                log.warn("No transaction found for Payout ID = {}", payoutId);
                return;  // Exit if no matching transaction is found
            }

            // Update the transaction status
            trans.setStatus(payoutStatus);
            trans.setComments("Payout Completed");
            transactionService.updateTransactionStatus(trans);

            // Log the successful update
            log.info("ACH Withdrawal successful: Payout ID = {}, Status updated to = {}", payoutId, payoutStatus);

        } catch (IOException e) {
            // Catch IOExceptions that may arise from reading/parsing the event JSON
            log.error("Error processing payout event", e);
            throw e;  // Rethrow the exception to propagate it up if needed
        } catch (Exception e) {
            // Catch any other unexpected exceptions
            log.error("Unexpected error occurred while processing payout event", e);
        }
    }


    /**
     * Handles failed ACH Withdrawal
     */
    private void handlePayoutFailed(Event event) throws IOException {
        JsonNode payoutData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String payoutId = payoutData.get("id").asText();
        String failureCode = payoutData.has("failure_code") ? payoutData.get("failure_code").asText() : "No code provided";
        log.error("ACH Withdrawal failed: Payout ID = {}, Reason = {}", payoutId, failureCode);
    }

    private void handlePayoutCanceled(Event event) throws IOException {
        JsonNode payoutData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String payoutId = payoutData.get("id").asText();
        log.info("ACH Withdrawal canceled: Payout ID = {}", payoutId);
    }

    private void handlePaymentIntentCanceled(Event event) throws IOException {
        JsonNode intentData = objectMapper.readTree(event.toJson()).get("data").get("object");
        String paymentIntentId = intentData.get("id").asText();
        log.info("ACH Debit canceled: PaymentIntent ID = {}", paymentIntentId);
    }
}