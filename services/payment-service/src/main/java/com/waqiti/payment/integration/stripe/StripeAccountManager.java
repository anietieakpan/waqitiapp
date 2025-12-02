package com.waqiti.payment.integration.stripe;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeAccountManager {

    @Value("${stripe.connect.country:US}")
    private String defaultCountry;
    
    @Value("${stripe.connect.capabilities:card_payments,transfers}")
    private String defaultCapabilities;
    
    public Account createConnectedAccount(Map<String, Object> accountDetails) throws StripeException {
        log.info("Creating Stripe Connected account");
        
        AccountCreateParams.Builder paramsBuilder = AccountCreateParams.builder()
                .setType(AccountCreateParams.Type.EXPRESS)
                .setCountry(accountDetails.getOrDefault("country", defaultCountry).toString())
                .setEmail(accountDetails.get("email").toString())
                .setBusinessType(AccountCreateParams.BusinessType.valueOf(
                        accountDetails.getOrDefault("businessType", "INDIVIDUAL").toString().toUpperCase()
                ))
                .putMetadata("user_id", accountDetails.get("userId").toString())
                .putMetadata("created_at", String.valueOf(System.currentTimeMillis()));
        
        // Add capabilities
        String[] capabilities = defaultCapabilities.split(",");
        for (String capability : capabilities) {
            paramsBuilder.setCapabilities(AccountCreateParams.Capabilities.builder()
                    .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                            .setRequested(true)
                            .build())
                    .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true)
                            .build())
                    .build());
        }
        
        // Add business profile if provided
        if (accountDetails.containsKey("businessProfile")) {
            Map<String, Object> profile = (Map<String, Object>) accountDetails.get("businessProfile");
            paramsBuilder.setBusinessProfile(AccountCreateParams.BusinessProfile.builder()
                    .setName(profile.getOrDefault("name", "").toString())
                    .setUrl(profile.getOrDefault("url", "").toString())
                    .setProductDescription(profile.getOrDefault("description", "").toString())
                    .build());
        }
        
        // Add TOS acceptance
        if (accountDetails.containsKey("tosAcceptance")) {
            Map<String, Object> tos = (Map<String, Object>) accountDetails.get("tosAcceptance");
            paramsBuilder.setTosAcceptance(AccountCreateParams.TosAcceptance.builder()
                    .setDate(Long.parseLong(tos.get("date").toString()))
                    .setIp(tos.get("ip").toString())
                    .build());
        }
        
        Account account = Account.create(paramsBuilder.build());
        
        log.info("Created Stripe Connected account: {}", account.getId());
        return account;
    }
    
    public Account updateAccount(String accountId, Map<String, Object> updates) throws StripeException {
        log.info("Updating Stripe Connected account: {}", accountId);
        
        Account account = Account.retrieve(accountId);
        
        AccountUpdateParams.Builder paramsBuilder = AccountUpdateParams.builder();
        
        // Update business profile
        if (updates.containsKey("businessProfile")) {
            Map<String, Object> profile = (Map<String, Object>) updates.get("businessProfile");
            paramsBuilder.setBusinessProfile(AccountUpdateParams.BusinessProfile.builder()
                    .setName(profile.getOrDefault("name", "").toString())
                    .setUrl(profile.getOrDefault("url", "").toString())
                    .setProductDescription(profile.getOrDefault("description", "").toString())
                    .build());
        }
        
        // Update individual details
        if (updates.containsKey("individual")) {
            Map<String, Object> individual = (Map<String, Object>) updates.get("individual");
            paramsBuilder.setIndividual(AccountUpdateParams.Individual.builder()
                    .setFirstName(individual.getOrDefault("firstName", "").toString())
                    .setLastName(individual.getOrDefault("lastName", "").toString())
                    .setEmail(individual.getOrDefault("email", "").toString())
                    .setPhone(individual.getOrDefault("phone", "").toString())
                    .build());
        }
        
        // Update metadata
        if (updates.containsKey("metadata")) {
            Map<String, String> metadata = (Map<String, String>) updates.get("metadata");
            metadata.forEach(paramsBuilder::putMetadata);
        }
        
        return account.update(paramsBuilder.build());
    }
    
    public AccountLink createAccountLink(String accountId, String refreshUrl, String returnUrl) throws StripeException {
        log.info("Creating account link for: {}", accountId);
        
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl(refreshUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();
        
        return AccountLink.create(params);
    }
    
    public LoginLink createLoginLink(String accountId) throws StripeException {
        log.info("Creating login link for: {}", accountId);
        
        Account account = Account.retrieve(accountId);
        return account.loginLinks().create();
    }
    
    public Account retrieveAccount(String accountId) throws StripeException {
        return Account.retrieve(accountId);
    }
    
    public void deleteAccount(String accountId) throws StripeException {
        log.info("Deleting Stripe Connected account: {}", accountId);
        
        Account account = Account.retrieve(accountId);
        account.delete();
    }
    
    public ExternalAccount addBankAccount(String accountId, Map<String, Object> bankDetails) throws StripeException {
        log.info("Adding bank account to: {}", accountId);
        
        Account account = Account.retrieve(accountId);
        
        ExternalAccountCreateParams params = ExternalAccountCreateParams.builder()
                .setExternalAccount(TokenCreateParams.builder()
                        .setBankAccount(TokenCreateParams.BankAccount.builder()
                                .setCountry(bankDetails.get("country").toString())
                                .setCurrency(bankDetails.get("currency").toString())
                                .setAccountHolderName(bankDetails.get("accountHolderName").toString())
                                .setAccountHolderType(TokenCreateParams.BankAccount.AccountHolderType.valueOf(
                                        bankDetails.getOrDefault("accountHolderType", "INDIVIDUAL").toString().toUpperCase()
                                ))
                                .setRoutingNumber(bankDetails.get("routingNumber").toString())
                                .setAccountNumber(bankDetails.get("accountNumber").toString())
                                .build())
                        .build().toString())
                .setDefaultForCurrency(true)
                .build();
        
        return account.externalAccounts().create(params);
    }
    
    public Person addPerson(String accountId, Map<String, Object> personDetails) throws StripeException {
        log.info("Adding person to account: {}", accountId);
        
        Account account = Account.retrieve(accountId);
        
        PersonCreateParams params = PersonCreateParams.builder()
                .setFirstName(personDetails.get("firstName").toString())
                .setLastName(personDetails.get("lastName").toString())
                .setEmail(personDetails.getOrDefault("email", "").toString())
                .setPhone(personDetails.getOrDefault("phone", "").toString())
                .setRelationship(PersonCreateParams.Relationship.builder()
                        .setTitle(personDetails.getOrDefault("title", "").toString())
                        .setOwner(Boolean.parseBoolean(personDetails.getOrDefault("owner", "false").toString()))
                        .setExecutive(Boolean.parseBoolean(personDetails.getOrDefault("executive", "false").toString()))
                        .setDirector(Boolean.parseBoolean(personDetails.getOrDefault("director", "false").toString()))
                        .setPercentOwnership(personDetails.containsKey("percentOwnership") 
                                ? new BigDecimal(personDetails.get("percentOwnership").toString()) 
                                : null)
                        .build())
                .build();
        
        return account.persons().create(params);
    }
    
    public Balance getAccountBalance(String accountId) throws StripeException {
        RequestOptions requestOptions = RequestOptions.builder()
                .setStripeAccount(accountId)
                .build();
        
        return Balance.retrieve(requestOptions);
    }
    
    public Transfer createTransferToAccount(String accountId, Long amount, String currency, String description) throws StripeException {
        log.info("Creating transfer to account: {} amount: {} {}", accountId, amount, currency);
        
        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency.toLowerCase())
                .setDestination(accountId)
                .setDescription(description)
                .build();
        
        return Transfer.create(params);
    }
    
    public Payout createPayout(String accountId, Long amount, String currency, String method) throws StripeException {
        log.info("Creating payout for account: {} amount: {} {}", accountId, amount, currency);
        
        RequestOptions requestOptions = RequestOptions.builder()
                .setStripeAccount(accountId)
                .build();
        
        PayoutCreateParams params = PayoutCreateParams.builder()
                .setAmount(amount)
                .setCurrency(currency.toLowerCase())
                .setMethod(PayoutCreateParams.Method.valueOf(method.toUpperCase()))
                .build();
        
        return Payout.create(params, requestOptions);
    }
    
    public boolean verifyAccountRequirements(String accountId) throws StripeException {
        Account account = Account.retrieve(accountId);
        
        // Check if account has any requirements
        boolean hasRequirements = account.getRequirements() != null && 
                (account.getRequirements().getCurrentlyDue() != null && 
                 !account.getRequirements().getCurrentlyDue().isEmpty());
        
        if (hasRequirements) {
            log.warn("Account {} has pending requirements: {}", 
                    accountId, account.getRequirements().getCurrentlyDue());
            return false;
        }
        
        // Check if charges and payouts are enabled
        boolean isEnabled = account.getChargesEnabled() && account.getPayoutsEnabled();
        
        if (!isEnabled) {
            log.warn("Account {} charges enabled: {}, payouts enabled: {}", 
                    accountId, account.getChargesEnabled(), account.getPayoutsEnabled());
        }
        
        return isEnabled;
    }
}