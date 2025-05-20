package com.inata.stripe.test.paymentGateway.transactions.controller;

import com.inata.stripe.test.paymentGateway.transactions.entity.TransactionEntity;
import com.inata.stripe.test.paymentGateway.transactions.service.TransactionService;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "http://localhost:4200")

public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Initiates an ACH Debit (pull funds from the user’s bank).
     */
    @GetMapping("/data")
    public ResponseEntity<?> getPaymentMethodDetails(@RequestParam Integer userId) {
        try {

            return ResponseEntity.ok().body(transactionService.getPaymentMethodData(userId));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Failed to get method details: " + e.getMessage());

        }
    }

    @GetMapping("/payout-acc")
    public ResponseEntity<?> getPayoutMethodDetails(@RequestParam Integer userId) {
        try {

            return ResponseEntity.ok().body(transactionService.getExternalBankDetails(userId));
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Failed to get method details: " + e.getMessage());

        }
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> createDeposit(@RequestParam Integer userId, @RequestParam Long amount) {
        try {
            TransactionEntity transaction = transactionService.createAchDebit(userId, amount);
            return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create deposit: " + e.getMessage());
        }
    }

    /**
     * Initiates an ACH Withdrawal (send funds to the user’s bank).
     */
    @PostMapping("/withdraw")
    public ResponseEntity<?> createWithdrawal(@RequestParam Integer userId, @RequestParam Long amount) {
        try {
            TransactionEntity transaction = transactionService.createAchWithdrawal(userId, amount);
            return ResponseEntity.status(HttpStatus.CREATED).body(transaction.getPaymentMethodId());
        } catch (StripeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Failed to create withdrawal: " + e.getMessage());
        }
    }

    @GetMapping("/trans-history")
    public ResponseEntity<?> getTransactionHistory(@RequestParam Integer userId) {
        return ResponseEntity.ok().body(transactionService.getTransactionsByUserId(userId));
    }
}
