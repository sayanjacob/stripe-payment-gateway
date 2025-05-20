package com.inata.stripe.test.paymentGateway.stripeusers.service;

import com.inata.stripe.test.paymentGateway.stripeusers.entity.StripeUser;
import com.inata.stripe.test.paymentGateway.stripeusers.repository.StripeUserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.financialconnections.Session;
import com.stripe.param.*;
import com.stripe.param.financialconnections.SessionCreateParams;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
public class ConnectAccountService {

    @Value("${stripe.api.key}")
    private String secretKey;

    private static final Logger log = LoggerFactory.getLogger(ConnectAccountService.class);

    private final StripeUserRepository stripeUserRepository;

    public ConnectAccountService(StripeUserRepository stripeUserRepository) {
        this.stripeUserRepository = stripeUserRepository;
    }


    @Transactional
    public String createStripeAccount(Integer userId) throws StripeException {
        Stripe.apiKey = secretKey;
        Optional<StripeUser> optionalUser = stripeUserRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            AccountCreateParams params =
                    AccountCreateParams.builder()
                            .setCountry("US")
                            .setType(AccountCreateParams.Type.CUSTOM)
                            .setBusinessType(
                                    AccountCreateParams.BusinessType.INDIVIDUAL
                            )
                            .setCapabilities(
                                    AccountCreateParams.Capabilities.builder()
//                                            .setUsBankAccountAchPayments(
//                                                    AccountCreateParams.Capabilities.UsBankAccountAchPayments.builder()
//                                                            .setRequested(true)
//                                                            .build()
//                                            )
                                            .setTransfers(
                                                    AccountCreateParams.Capabilities.Transfers.builder()
                                                            .setRequested(true)
                                                            .build()
                                            )
                                            .build()
                            )

                            .build();

            Account stripeAccount = Account.create(params);
            log.info("Account Created:{}{}", stripeAccount.getId(), stripeAccount.getRequirements().getCurrentlyDue());
            StripeUser user = new StripeUser();
            user.setId(userId);
            user.setConnectAccountId(stripeAccount.getId());
            stripeUserRepository.save(user);
            return stripeAccount.getId();

        } else {
            StripeUser user = optionalUser.get();
            log.info("Account Already Exist:{}", user.getConnectAccountId());
            return user.getConnectAccountId();

        }


    }

    public Account.Requirements userOnboarding(Map<String, String> payload) throws StripeException {
        String ip = payload.get("ip");
        String firstName = payload.get("firstName");
        String lastName = payload.get("lastName");
        String email = payload.get("email");
        String phone = payload.get("phone");
        String line1 = payload.get("addressLine1");

        String city = payload.get("city");
        String state = payload.get("state");
        String postalCode = payload.get("postalCode");
        String country = payload.get("country");
        String ssnLast4 = payload.get("ssnLast4");
        String dobString = payload.get("dob");

        LocalDate dob = LocalDate.parse(dobString, DateTimeFormatter.ISO_LOCAL_DATE);

        // Now you can access the day, month, and year from the LocalDate object
        Long day = (long) dob.getDayOfMonth();
        Long month = (long) dob.getMonthValue();
        Long year = (long) dob.getYear();
        String accountId = payload.get("accountId");
        AccountUpdateParams updateParams = AccountUpdateParams.builder()
                .setBusinessType(AccountUpdateParams.BusinessType.INDIVIDUAL)
                .setTosAcceptance(AccountUpdateParams.TosAcceptance.builder()
                        .setDate(Instant.now().getEpochSecond())
                        .setIp(ip)
                        .build())
                .setBusinessProfile(
                        AccountUpdateParams.BusinessProfile.builder()
                                .setMcc("5734")
                                .setUrl("https://inata.trade.com")
                                .build()
                )
                .setIndividual(
                        AccountUpdateParams.Individual.builder()
                                .setFirstName(firstName)
                                .setLastName(lastName)
                                .setEmail(email)
                                .setPhone(phone)
                                .setDob(
                                        AccountUpdateParams.Individual.Dob.builder()
                                                .setDay(day)
                                                .setMonth(month)
                                                .setYear(year)
                                                .build()
                                )
                                .setAddress(
                                        AccountUpdateParams.Individual.Address.builder()
                                                .setLine1(line1)
                                                .setCity(city)
                                                .setState(state)
                                                .setPostalCode(postalCode)
                                                .setCountry(country)
                                                .build()
                                )
                                .setSsnLast4(ssnLast4)
                                .build()
                )
                .build();

        Account updatedAccount = Account.retrieve(accountId);
        updatedAccount.update(updateParams);

        // Update local DB record if found
        Optional<StripeUser> optionalUser = stripeUserRepository.findByConnectAccountId(accountId);
        if (optionalUser.isPresent()) {
            StripeUser user = optionalUser.get();
            Account acc = Account.retrieve(accountId);

            user.setConnectAccountStatus(acc.getRequirements().getCurrentDeadline() != null ? "pending" : "verified");
            stripeUserRepository.save(user); // Persist changes
        } else {
            throw new RuntimeException("StripeUser not found for account ID: " + accountId);
        }
        // You can store the updated account or return it
        return updatedAccount.getRequirements();
    }

    @Transactional
    public String createStripeCustomer(Integer id, String connectAccountId) throws StripeException {
        Stripe.apiKey = secretKey;

        // Check if user with the connectAccountId already exists and has a customer ID
        Optional<StripeUser> existingUser = stripeUserRepository.findByConnectAccountId(connectAccountId);
        if (existingUser.isPresent() && existingUser.get().getCustomerId() != null) {
            return existingUser.get().getCustomerId();
        }
        Account account = Account.retrieve(connectAccountId);

        // Create Stripe Customer
        CustomerCreateParams params = CustomerCreateParams.builder()
                .setName(account.getIndividual().getFirstName() + " " + account.getIndividual().getLastName())
                .setEmail(account.getEmail())
                .setAddress(CustomerCreateParams.Address.builder()
                        .setLine1(account.getIndividual().getAddress().getLine1())
                        .setLine2(account.getIndividual().getAddress().getLine2())
                        .setCity(account.getIndividual().getAddress().getCity())
                        .setState(account.getIndividual().getAddress().getState())
                        .setCountry(account.getIndividual().getAddress().getCountry())
                        .setPostalCode(account.getIndividual().getAddress().getPostalCode())
                        .build())
                .setMetadata(Map.of("userId", String.valueOf(id), "connectId", connectAccountId))
                .build();
        Customer customer = Customer.create(params);

        // Update user with the new customer ID
        Optional<StripeUser> userOptional = stripeUserRepository.findById(id);
        if (userOptional.isPresent()) {
            StripeUser user = userOptional.get();
            user.setCustomerId(customer.getId());
            stripeUserRepository.save(user);
            return customer.getId();
        } else {
            throw new IllegalStateException("User with ID " + id + " not found in local database.");
        }
    }


    public String createFinancialConnectionSession(String customerId) throws StripeException {
        SessionCreateParams params = SessionCreateParams.builder()
                .setAccountHolder(
                        SessionCreateParams.AccountHolder.builder()
                                .setType(SessionCreateParams.AccountHolder.Type.CUSTOMER)
                                .setCustomer(customerId)

                                .build()
                )
                .addPermission(SessionCreateParams.Permission.BALANCES)
                .addPermission(SessionCreateParams.Permission.PAYMENT_METHOD)
                .addPermission(SessionCreateParams.Permission.OWNERSHIP)
                .setFilters(SessionCreateParams.Filters.builder().addCountry("US").build())
                .build();


        Session session = Session.create(params);
        return session.getClientSecret();
    }

    public Map<String, String> attachPaymentMethodToCustomer(String customerId, String financialConnectionsAccountId) throws StripeException {
        Customer customer = Customer.retrieve(customerId);
        Optional<StripeUser> optionalUser = stripeUserRepository.findByCustomerId(customerId);
        if (optionalUser.isPresent()) {
            // 1. Create a PaymentMethod
            PaymentMethodCreateParams params = PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.US_BANK_ACCOUNT)
                    .setUsBankAccount(PaymentMethodCreateParams.UsBankAccount.builder()
                            .setFinancialConnectionsAccount(financialConnectionsAccountId)
                            .build())
                    .setBillingDetails(
                            PaymentMethodCreateParams.BillingDetails.builder()
                                    .setName(customer.getName())
                                    .build()
                    )
                    .build();
            PaymentMethod paymentMethod = PaymentMethod.create(params);

//        PaymentMethodAttachParams params1=PaymentMethodAttachParams.builder()
//                .setCustomer(customerId)
//                .build();
//        paymentMethod.attach(params1);

            // 2. Create a SetupIntent
            SetupIntentCreateParams setupIntentParams = SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .setPaymentMethod(paymentMethod.getId())
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)// Indicate intent for future use
                    .addPaymentMethodType("us_bank_account") // Add us_bank_account to allowed types
                    .build();


            SetupIntent setupIntent = SetupIntent.create(setupIntentParams);
            optionalUser.get().setFinancialConnectionId(financialConnectionsAccountId);
            optionalUser.get().setPaymentMethodId(paymentMethod.getId());
            stripeUserRepository.save(optionalUser.get());

            // At this point, the PaymentMethod is associated with the customer
            // and optimized for future off-session payments.

//         3. Optionally, set as the default payment method for invoices and subscriptions
//        CustomerUpdateParams customerUpdateParams = CustomerUpdateParams.builder()
//                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
//                        .setDefaultPaymentMethod(paymentMethod.getId())
//                        .build())
//                .build();
//        Customer updatedCustomer = customer.update(customerUpdateParams);

            return Map.of(
                    "paymentMethod", paymentMethod.getId(),
                    "customerId", customer.getId(),
                    "setupIntentClientSecret", setupIntent.getClientSecret() // You might want to return the SetupIntent status
            );

        } else
            return Map.of("Message", "No Customer was Found Try Again");
    }

    public Boolean addExternalBankDetails(String accountId, String bankToken) throws StripeException {
        Account account = Account.retrieve(accountId);
        AccountUpdateParams params = AccountUpdateParams.builder()
                .setExternalAccount(bankToken)
                .build();
        Account updated = account.update(params);
        return updated != null;
    }

    public Optional<StripeUser> getUserDetailsFromId(Integer userId) {
        return stripeUserRepository.findById(userId);
    }

    public Optional<StripeUser> getStripeDetails(Integer userId) {
        return stripeUserRepository.findById(userId);
    }


}
