package com.inata.stripe.test.paymentGateway.transactions.service;

import com.inata.stripe.test.paymentGateway.stripeusers.entity.StripeUser;
import com.inata.stripe.test.paymentGateway.stripeusers.repository.StripeUserRepository;
import com.inata.stripe.test.paymentGateway.stripeusers.service.ConnectAccountService;
import com.inata.stripe.test.paymentGateway.transactions.entity.TransactionEntity;
import com.inata.stripe.test.paymentGateway.transactions.repository.TransactionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.financialconnections.Account;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import com.stripe.param.financialconnections.AccountRefreshParams;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class TransactionService {
    @Autowired
    private StripeUserRepository stripeUserRepository;
    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ConnectAccountService connectAccountService;

    /**
     * Creates an ACH Debit (Pulling funds from the user's bank).
     */
    @Transactional
    public TransactionEntity createAchDebit(Integer userId, Long amount) throws StripeException {
        // Step 1: Get Stripe user info
        Optional<StripeUser> optionalUser = connectAccountService.getStripeDetails(userId);
        if (optionalUser.isEmpty()) {
            log.warn("No Stripe details found for user ID: {}", userId);
            return null;
        }
        String paymentMethodId;

        StripeUser user = optionalUser.get();
        if (user.getPaymentMethodId().isEmpty()) {
            PaymentMethodListParams listParams = PaymentMethodListParams.builder()
                    .setCustomer(user.getCustomerId())
                    .setType(PaymentMethodListParams.Type.US_BANK_ACCOUNT)
                    .build();

            PaymentMethodCollection paymentMethods = PaymentMethod.list(listParams);
            List<PaymentMethod> methods = paymentMethods.getData();

            if (methods.isEmpty()) {
                log.info("No US bank account payment methods found for user ID: {}", userId);
                return null;
            }

            // Use the first available payment method
            PaymentMethod selectedMethod = methods.getFirst();
            paymentMethodId = selectedMethod.getId();
            user.setPaymentMethodId(paymentMethodId);
            stripeUserRepository.save(user);
        } else {
            paymentMethodId = user.getPaymentMethodId();
        }


        // Step 2: Get user's US bank account payment methods


        // Step 3: Create PaymentIntent
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount) // Stripe expects amount in cents
                .setCurrency("usd")
                .setCustomer(user.getCustomerId())
                .addPaymentMethodType("us_bank_account")
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setDescription("ACH Debit Payment for User ID: " + userId)
                .build();

        PaymentIntent intent = PaymentIntent.create(params);
//        log.info("PaymentIntent created: {}", intent.toJson());

        // Step 4: Save transaction to DB
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionId(intent.getId());
        transaction.setUserId(userId);
        transaction.setPaymentMethodId(paymentMethodId);
        transaction.setTransactionType("deposit");
        transaction.setAmount(convertLongtoBigDec(amount));
        transaction.setStatus("pending"); // Will need webhook to update status later
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    /**
     * Creates an ACH Withdrawal (sending funds to the user's bank).
     */
    @Transactional
    public TransactionEntity createAchWithdrawal(Integer userId, Long amount) throws StripeException {

        // Step 1: Transfer money to the connected account (Stripe balance)
        StripeUser user = connectAccountService.getStripeDetails(userId).get();

        TransferCreateParams transferParams = TransferCreateParams.builder()
                .setAmount(amount) // cents
                .setCurrency("usd")
                .setDestination(user.getConnectAccountId()) // The Connected Account ID
                .build();

        Transfer transfer = Transfer.create(transferParams);

        // Step 2: Now create a payout from the connected account's balance to their bank
        RequestOptions connectedAccountOptions = RequestOptions.builder()
                .setStripeAccount(user.getConnectAccountId()) // Context: the connected account
                .build();

        PayoutCreateParams payoutParams = PayoutCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setMethod(PayoutCreateParams.Method.INSTANT) // Or INSTANT (if supported)
                .build();

        Payout payout = Payout.create(payoutParams, connectedAccountOptions);

        // Round to 2 decimal places

        TransactionEntity transaction = new TransactionEntity();
        transaction.setTransactionId(payout.getId());
        transaction.setUserId(userId);
        transaction.setTransactionType("withdraw");
        transaction.setAmount(convertLongtoBigDec(amount));
        transaction.setStatus("pending");
        transaction.setPaymentMethodId(payout.getDestination());
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }


    /**
     * Updates the status of a transaction (based on Stripe Webhook events).
     */
    @Transactional
    public void updateTransactionStatus(String paymentMethodId, String status) {
        Optional<TransactionEntity> optionalTransaction = transactionRepository.findByPaymentMethodId(paymentMethodId);

        optionalTransaction.ifPresent(transaction -> {
            transaction.setStatus(status);
            transaction.setUpdatedAt(LocalDateTime.now()); // Ensure updatedAt is set
            transactionRepository.save(transaction);
        });
    }

    /**
     * Updates the status of a transaction based on payout ID (for ACH withdrawals).
     */
    @Transactional
    public void updateTransactionStatusByPayoutId(String paymentMethodId, String status) {
        Optional<TransactionEntity> optionalTransaction = transactionRepository.findByPaymentMethodId(paymentMethodId);

        optionalTransaction.ifPresent(transaction -> {
            transaction.setStatus(status);
            transaction.setUpdatedAt(LocalDateTime.now()); // Ensure updatedAt is set
            transactionRepository.save(transaction);
        });
    }

    public Map<String, Object> getPaymentMethodData(Integer userId) throws StripeException {
        Optional<StripeUser> optionalUser = connectAccountService.getStripeDetails(userId);
        if (optionalUser.isEmpty()) {
            log.warn("No Stripe details found for userID: {}", userId);
            return null;
        }
        StripeUser user = optionalUser.get();
        com.stripe.model.financialconnections.Account resource = com.stripe.model.financialconnections.Account.retrieve(user.getFinancialConnectionId());
        AccountRefreshParams refreshParams =
                AccountRefreshParams.builder()
                        .addFeature(AccountRefreshParams.Feature.BALANCE)
                        .build();
        Account account = resource.refresh(refreshParams);
        PaymentMethod paymentMethod = PaymentMethod.retrieve(user.getPaymentMethodId());
        log.info("paymentMethod:{}", paymentMethod.toJson());
        log.info("balance:{}", account.getBalance().getCash().getAvailable());
        Map<String, String> paymentMethodData = new HashMap<>();
        paymentMethodData.put("bank_name", paymentMethod.getUsBankAccount().getBankName());
        paymentMethodData.put("last4", paymentMethod.getUsBankAccount().getLast4());
        paymentMethodData.put("account_type", paymentMethod.getUsBankAccount().getAccountType());
        paymentMethodData.put("routing_no", paymentMethod.getUsBankAccount().getRoutingNumber());


        return Map.of("paymentMethod", paymentMethodData,
                "balance", account.getBalance().getCash().getAvailable().get("usd"));
    }

    public Map<String, Object> getExternalBankDetails(Integer userId) throws StripeException {
        Optional<StripeUser> optionalUser = connectAccountService.getStripeDetails(userId);
        if (optionalUser.isEmpty()) {
            log.warn("No Stripe account found for userID: {}", userId);
            return null;
        }
        StripeUser user = optionalUser.get();

        com.stripe.model.Account account = com.stripe.model.Account.retrieve(user.getConnectAccountId());
//        ExternalAccount externalAccount =
//                account.getExternalAccounts().retrieve("ba_1NAinX2eZvKYlo2CpFGcuuEG");
        ExternalAccountCollectionListParams params =
                ExternalAccountCollectionListParams.builder().setObject("bank_account").build();
        ExternalAccountCollection externalAccounts =
                account.getExternalAccounts().list(params);

        return Map.of("external_acc", externalAccounts.getData());
    }

    /**
     * Retrieves a transaction by its ID.
     */
    public TransactionEntity getTransactionById(String transactionId) {
        return transactionRepository.findById(transactionId).get();
    }

    public void updateTransactionStatus(TransactionEntity transactionEntity) {
        transactionRepository.save(transactionEntity);
    }


    /**
     * Retrieves all transactions for a user.
     */
    public List<TransactionEntity> getTransactionsByUserId(Integer userId) {
        return transactionRepository.findByUserId(userId);
    }

    private BigDecimal convertLongtoBigDec(Long amount) {
        BigDecimal amountInCents = new BigDecimal(amount);  // Convert Long to BigDecimal
        BigDecimal divisor = new BigDecimal(100);  // The divisor (100 for cents to dollars)

        return amountInCents.divide(divisor, 2, RoundingMode.HALF_UP);
    }
}
