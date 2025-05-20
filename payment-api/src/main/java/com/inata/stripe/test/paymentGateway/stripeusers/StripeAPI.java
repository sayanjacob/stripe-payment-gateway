package com.inata.stripe.test.paymentGateway.stripeusers;

import com.inata.stripe.test.paymentGateway.stripeusers.entity.StripeUser;
import com.inata.stripe.test.paymentGateway.stripeusers.service.ConnectAccountService;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stripe")
@CrossOrigin(origins = "http://localhost:4200")
public class StripeAPI {
    @Autowired
    private ConnectAccountService connectAccountService;

    @PostMapping("/create")
    public ResponseEntity<?> createAccount(@RequestParam Integer userId) {
        try {
            String accountId = connectAccountService.createStripeAccount(userId);
            // Prepare a map to return as JSON
            Map<String, String> responseJson = new HashMap<>();
            responseJson.put("message", "Account Created");
            responseJson.put("accountId", accountId);
            return ResponseEntity.ok().body(responseJson);
        } catch (StripeException e) {
            return ResponseEntity.status(500).body("Error creating Stripe account: " + e.getMessage());
        }
    }


    @PostMapping("/onboarding")
    public ResponseEntity<?> onboardUserToConnect(@RequestBody Map<String, String> requestBody) {

        try {
            Account.Requirements requirements = connectAccountService.userOnboarding(requestBody);
            return ResponseEntity.ok().body("AccountOnboarded" + requirements);
        } catch (StripeException e) {
            return ResponseEntity.status(500).body("Error OnBoarding User" + e.getStripeError() + e.getMessage());
        }
    }

    @GetMapping("/getStripeUserDetails")
    public ResponseEntity<?> getStripeUserDetails(@RequestParam Integer userId) {
        try {
            Optional<StripeUser> user = connectAccountService.getStripeDetails(userId); // you may need to define this method
            if (user.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("User doesn't have a Stripe account");
            } else {
                Map<String, String> resp = new HashMap<>();
                resp.put("connect_id", user.get().getConnectAccountId());
                resp.put("customer_id", user.get().getCustomerId());
                return ResponseEntity.ok().body(resp);

            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error occurred"));
        }

    }

    @PostMapping("/createCustomer")
    public ResponseEntity<?> createCustomer(@RequestParam Integer userId, @RequestParam String connectAccountId) {
        try {
            String customerId = connectAccountService.createStripeCustomer(userId, connectAccountId);
            return ResponseEntity.ok().body(Map.of("customer_id", customerId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error occurred"));
        }
    }

    @PostMapping("/addPaymentMethod")
    public ResponseEntity<?> addPaymentMethodForDeposit(@RequestParam String customerId) {

        try {
            String clientSecret = connectAccountService.createFinancialConnectionSession(customerId);
            return ResponseEntity.ok(Map.of("clientSecret", clientSecret));

        } catch (StripeException e) {
            return ResponseEntity.status(500).body("Error creating a fca session" + e.getStripeError() + e.getMessage());
        }
    }


    @PostMapping("/attachPaymentMethodToCustomer")
    public ResponseEntity<?> attachPaymentMethodToCustomer(@RequestBody Map<String, String> payload) {
        try {
            String customerId = payload.get("customerId");
            String financialConnectionsAccountId = payload.get("financialConnectionsAccountId");
            Map<String, String> response = connectAccountService.attachPaymentMethodToCustomer(customerId, financialConnectionsAccountId);
            return ResponseEntity.ok().body(response);
        } catch (StripeException e) {
            return ResponseEntity.status(500).body("Error creating a fca session" + e.getStripeError() + e.getMessage());

        }
    }

    @PostMapping("/addPayoutAccount")
    public ResponseEntity<?> addPayoutAccount(@RequestBody Map<String,String> request) {
        try {
            boolean status = connectAccountService.addExternalBankDetails(request.get("accountId"),request.get("token"));

            if (status) {
                return ResponseEntity.ok(Map.of("message", "Bank account added successfully."));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Failed to add bank account."));
            }

        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Stripe error: " + e.getMessage()));
        }
    }




}
