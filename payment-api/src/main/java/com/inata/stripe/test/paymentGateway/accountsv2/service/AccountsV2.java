package com.inata.stripe.test.paymentGateway.accountsv2.service;

import com.inata.stripe.test.paymentGateway.stripeusers.entity.StripeUser;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.v2.core.Account;
import com.stripe.param.v2.core.AccountCreateParams;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;

public class AccountsV2 {
    @Value("${stripe.api.key}")
    private String secretKey;
    StripeClient client = new StripeClient(secretKey);


    public Account createRecipient(Integer userId) throws StripeException {

        AccountCreateParams params =
                AccountCreateParams.builder()
                        .setContactEmail("furever@example.com")
                        .setDisplayName("Furever")
                        .setIdentity(
                                AccountCreateParams.Identity.builder()
                                        .setCountry(AccountCreateParams.Identity.Country.US)
                                        .setEntityType(AccountCreateParams.Identity.EntityType.INDIVIDUAL)
                                        .setIndividual(
                                                AccountCreateParams.Identity.Individual.builder()
                                                        .setGivenName("Test")

                                                        .build()
                                        )
                                        .build()
                        )
                        .setConfiguration(
                                AccountCreateParams.Configuration.builder()
                                        .setCustomer(
                                                AccountCreateParams.Configuration.Customer.builder()
                                                        .setCapabilities(
                                                                AccountCreateParams.Configuration.Customer.Capabilities.builder()
                                                                        .setAutomaticIndirectTax(
                                                                                AccountCreateParams.Configuration.Customer.Capabilities.AutomaticIndirectTax.builder()
                                                                                        .setRequested(true)
                                                                                        .build()
                                                                        )
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .setMerchant(
                                                AccountCreateParams.Configuration.Merchant.builder()
                                                        .setCapabilities(
                                                                AccountCreateParams.Configuration.Merchant.Capabilities.builder()
                                                                        .setCardPayments(
                                                                                AccountCreateParams.Configuration.Merchant.Capabilities.CardPayments.builder()
                                                                                        .setRequested(true)
                                                                                        .build()
                                                                        )
                                                                        .build()
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .setDefaults(
                                AccountCreateParams.Defaults.builder()
                                        .setResponsibilities(
                                                AccountCreateParams.Defaults.Responsibilities.builder()
                                                        .setFeesCollector(
                                                                AccountCreateParams.Defaults.Responsibilities.FeesCollector.STRIPE
                                                        )
                                                        .setLossesCollector(
                                                                AccountCreateParams.Defaults.Responsibilities.LossesCollector.STRIPE
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .setDashboard(AccountCreateParams.Dashboard.FULL)
                        .addInclude(AccountCreateParams.Include.CONFIGURATION__MERCHANT)
                        .addInclude(AccountCreateParams.Include.CONFIGURATION__CUSTOMER)
                        .addInclude(AccountCreateParams.Include.IDENTITY)
                        .addInclude(AccountCreateParams.Include.DEFAULTS)
                        .build();

        return client.v2().core().accounts().create(params);
    }

}
