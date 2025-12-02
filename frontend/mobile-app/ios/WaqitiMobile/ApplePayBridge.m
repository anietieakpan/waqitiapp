//
//  ApplePayBridge.m
//  WaqitiMobile
//
//  Apple Pay Native Module Implementation
//

#import "ApplePayBridge.h"
#import <React/RCTUtils.h>
#import <React/RCTLog.h>

@implementation ApplePayBridge

RCT_EXPORT_MODULE(ApplePay);

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"ApplePayEvent"];
}

#pragma mark - Capability Checking

RCT_EXPORT_METHOD(checkCapabilities:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSMutableDictionary *capabilities = [[NSMutableDictionary alloc] init];
        
        // Check if Apple Pay is available
        BOOL isAvailable = [PKPaymentAuthorizationViewController canMakePayments];
        [capabilities setObject:@(isAvailable) forKey:@"isAvailable"];
        
        // Check supported networks
        NSArray<PKPaymentNetwork> *supportedNetworks = @[
            PKPaymentNetworkVisa,
            PKPaymentNetworkMasterCard,
            PKPaymentNetworkAmex,
            PKPaymentNetworkDiscover
        ];
        
        BOOL canMakePaymentsUsingNetworks = [PKPaymentAuthorizationViewController 
                                           canMakePaymentsUsingNetworks:supportedNetworks];
        [capabilities setObject:@(canMakePaymentsUsingNetworks) forKey:@"canMakePaymentsUsingNetworks"];
        
        // Check if user can set up cards
        BOOL canSetupCards = [PKPaymentAuthorizationViewController canMakePayments];
        [capabilities setObject:@(canSetupCards) forKey:@"canSetupCards"];
        
        // Supported networks
        NSMutableArray *networkStrings = [[NSMutableArray alloc] init];
        for (PKPaymentNetwork network in supportedNetworks) {
            if ([network isEqualToString:PKPaymentNetworkVisa]) {
                [networkStrings addObject:@"visa"];
            } else if ([network isEqualToString:PKPaymentNetworkMasterCard]) {
                [networkStrings addObject:@"masterCard"];
            } else if ([network isEqualToString:PKPaymentNetworkAmex]) {
                [networkStrings addObject:@"amex"];
            } else if ([network isEqualToString:PKPaymentNetworkDiscover]) {
                [networkStrings addObject:@"discover"];
            }
        }
        [capabilities setObject:networkStrings forKey:@"supportedNetworks"];
        
        // Supported capabilities
        NSArray *capabilityStrings = @[@"supports3DS", @"supportsEMV", @"supportsCredit", @"supportsDebit"];
        [capabilities setObject:capabilityStrings forKey:@"supportedCapabilities"];
        
        [capabilities setObject:@(isAvailable) forKey:@"canMakePayments"];
        
        resolve(capabilities);
    } @catch (NSException *exception) {
        reject(@"CAPABILITY_CHECK_FAILED", [exception reason], nil);
    }
}

#pragma mark - Payment Sheet Presentation

RCT_EXPORT_METHOD(presentPaymentSheet:(NSDictionary *)requestData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            self.currentResolve = resolve;
            self.currentReject = reject;
            
            // Create payment request
            PKPaymentRequest *request = [self createPaymentRequestFromData:requestData];
            self.currentPaymentRequest = request;
            
            // Create and present payment authorization controller
            PKPaymentAuthorizationViewController *paymentController = 
                [[PKPaymentAuthorizationViewController alloc] initWithPaymentRequest:request];
            
            if (!paymentController) {
                reject(@"PAYMENT_CONTROLLER_FAILED", @"Failed to create payment authorization controller", nil);
                return;
            }
            
            paymentController.delegate = self;
            
            // Get the root view controller
            UIViewController *rootViewController = RCTKeyWindow().rootViewController;
            while (rootViewController.presentedViewController) {
                rootViewController = rootViewController.presentedViewController;
            }
            
            [rootViewController presentViewController:paymentController animated:YES completion:nil];
            
        } @catch (NSException *exception) {
            reject(@"PAYMENT_SHEET_FAILED", [exception reason], nil);
        }
    });
}

#pragma mark - Wallet Integration

RCT_EXPORT_METHOD(addCardToWallet:(NSDictionary *)cardData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    @try {
        // Check if we can add cards to Wallet
        if (![PKAddPaymentPassViewController canAddPaymentPass]) {
            reject(@"WALLET_UNAVAILABLE", @"Cannot add payment passes to Wallet", nil);
            return;
        }
        
        // Create add payment pass request
        PKAddPaymentPassRequestConfiguration *config = 
            [[PKAddPaymentPassRequestConfiguration alloc] initWithEncryptionScheme:PKEncryptionSchemeECC_V2];
        
        config.cardholderName = cardData[@"cardholderName"];
        config.primaryAccountSuffix = cardData[@"primaryAccountSuffix"];
        config.localizedDescription = cardData[@"localizedDescription"];
        config.paymentNetwork = [self paymentNetworkFromString:cardData[@"paymentNetwork"]];
        
        dispatch_async(dispatch_get_main_queue(), ^{
            PKAddPaymentPassViewController *addPassController = 
                [[PKAddPaymentPassViewController alloc] initWithRequestConfiguration:config 
                                                                            delegate:nil];
            
            if (!addPassController) {
                reject(@"ADD_CARD_FAILED", @"Failed to create add payment pass controller", nil);
                return;
            }
            
            UIViewController *rootViewController = RCTKeyWindow().rootViewController;
            while (rootViewController.presentedViewController) {
                rootViewController = rootViewController.presentedViewController;
            }
            
            [rootViewController presentViewController:addPassController animated:YES completion:^{
                resolve(@{@"success": @YES});
            }];
        });
        
    } @catch (NSException *exception) {
        reject(@"ADD_CARD_FAILED", [exception reason], nil);
    }
}

#pragma mark - Helper Methods

- (PKPaymentRequest *)createPaymentRequestFromData:(NSDictionary *)data {
    PKPaymentRequest *request = [[PKPaymentRequest alloc] init];
    
    // Set merchant identifier
    request.merchantIdentifier = data[@"merchantIdentifier"];
    
    // Set country and currency
    request.countryCode = data[@"countryCode"] ?: @"US";
    request.currencyCode = data[@"currencyCode"] ?: @"USD";
    
    // Set supported networks
    NSArray *networkStrings = data[@"supportedNetworks"];
    NSMutableArray<PKPaymentNetwork> *networks = [[NSMutableArray alloc] init];
    
    for (NSString *networkString in networkStrings) {
        PKPaymentNetwork network = [self paymentNetworkFromString:networkString];
        if (network) {
            [networks addObject:network];
        }
    }
    request.supportedNetworks = networks;
    
    // Set merchant capabilities
    PKMerchantCapability capabilities = PKMerchantCapabilityEMV;
    NSArray *capabilityStrings = data[@"merchantCapabilities"];
    
    for (NSString *capability in capabilityStrings) {
        if ([capability isEqualToString:@"supports3DS"]) {
            capabilities |= PKMerchantCapability3DS;
        } else if ([capability isEqualToString:@"supportsEMV"]) {
            capabilities |= PKMerchantCapabilityEMV;
        } else if ([capability isEqualToString:@"supportsCredit"]) {
            capabilities |= PKMerchantCapabilityCredit;
        } else if ([capability isEqualToString:@"supportsDebit"]) {
            capabilities |= PKMerchantCapabilityDebit;
        }
    }
    request.merchantCapabilities = capabilities;
    
    // Set payment items
    NSArray *paymentItemsData = data[@"paymentItems"];
    NSMutableArray<PKPaymentSummaryItem *> *paymentItems = [[NSMutableArray alloc] init];
    
    for (NSDictionary *itemData in paymentItemsData) {
        PKPaymentSummaryItem *item = [PKPaymentSummaryItem 
                                     summaryItemWithLabel:itemData[@"label"]
                                     amount:[NSDecimalNumber decimalNumberWithString:itemData[@"amount"]]];
        
        if ([itemData[@"type"] isEqualToString:@"final"]) {
            item.type = PKPaymentSummaryItemTypeFinal;
        } else {
            item.type = PKPaymentSummaryItemTypePending;
        }
        
        [paymentItems addObject:item];
    }
    request.paymentSummaryItems = paymentItems;
    
    // Set required contact fields
    NSArray *billingFields = data[@"requiredBillingContactFields"];
    NSArray *shippingFields = data[@"requiredShippingContactFields"];
    
    PKContactField billingContactFields = PKContactFieldNone;
    PKContactField shippingContactFields = PKContactFieldNone;
    
    for (NSString *field in billingFields) {
        billingContactFields |= [self contactFieldFromString:field];
    }
    
    for (NSString *field in shippingFields) {
        shippingContactFields |= [self contactFieldFromString:field];
    }
    
    request.requiredBillingContactFields = billingContactFields;
    request.requiredShippingContactFields = shippingContactFields;
    
    return request;
}

- (PKPaymentNetwork)paymentNetworkFromString:(NSString *)networkString {
    if ([networkString isEqualToString:@"visa"]) {
        return PKPaymentNetworkVisa;
    } else if ([networkString isEqualToString:@"masterCard"]) {
        return PKPaymentNetworkMasterCard;
    } else if ([networkString isEqualToString:@"amex"]) {
        return PKPaymentNetworkAmex;
    } else if ([networkString isEqualToString:@"discover"]) {
        return PKPaymentNetworkDiscover;
    } else if ([networkString isEqualToString:@"maestro"]) {
        return PKPaymentNetworkMaestro;
    } else if ([networkString isEqualToString:@"jcb"]) {
        return PKPaymentNetworkJCB;
    } else if ([networkString isEqualToString:@"chinaUnionPay"]) {
        return PKPaymentNetworkChinaUnionPay;
    }
    return nil;
}

- (PKContactField)contactFieldFromString:(NSString *)fieldString {
    if ([fieldString isEqualToString:@"postalAddress"]) {
        return PKContactFieldPostalAddress;
    } else if ([fieldString isEqualToString:@"phoneNumber"]) {
        return PKContactFieldPhoneNumber;
    } else if ([fieldString isEqualToString:@"emailAddress"]) {
        return PKContactFieldEmailAddress;
    } else if ([fieldString isEqualToString:@"name"]) {
        return PKContactFieldName;
    }
    return PKContactFieldNone;
}

- (NSDictionary *)contactToDictionary:(PKContact *)contact {
    NSMutableDictionary *contactDict = [[NSMutableDictionary alloc] init];
    
    if (contact.name) {
        NSMutableDictionary *name = [[NSMutableDictionary alloc] init];
        if (contact.name.givenName) [name setObject:contact.name.givenName forKey:@"givenName"];
        if (contact.name.familyName) [name setObject:contact.name.familyName forKey:@"familyName"];
        if (contact.name.nickname) [name setObject:contact.name.nickname forKey:@"nickname"];
        if (contact.name.namePrefix) [name setObject:contact.name.namePrefix forKey:@"namePrefix"];
        if (contact.name.nameSuffix) [name setObject:contact.name.nameSuffix forKey:@"nameSuffix"];
        [contactDict setObject:name forKey:@"name"];
    }
    
    if (contact.postalAddress) {
        NSMutableDictionary *address = [[NSMutableDictionary alloc] init];
        if (contact.postalAddress.street) [address setObject:contact.postalAddress.street forKey:@"street"];
        if (contact.postalAddress.city) [address setObject:contact.postalAddress.city forKey:@"city"];
        if (contact.postalAddress.state) [address setObject:contact.postalAddress.state forKey:@"state"];
        if (contact.postalAddress.postalCode) [address setObject:contact.postalAddress.postalCode forKey:@"postalCode"];
        if (contact.postalAddress.country) [address setObject:contact.postalAddress.country forKey:@"country"];
        if (contact.postalAddress.ISOCountryCode) [address setObject:contact.postalAddress.ISOCountryCode forKey:@"countryCode"];
        [contactDict setObject:address forKey:@"postalAddress"];
    }
    
    if (contact.phoneNumber) {
        [contactDict setObject:contact.phoneNumber.stringValue forKey:@"phoneNumber"];
    }
    
    if (contact.emailAddress) {
        [contactDict setObject:contact.emailAddress forKey:@"emailAddress"];
    }
    
    return contactDict;
}

#pragma mark - PKPaymentAuthorizationViewControllerDelegate

- (void)paymentAuthorizationViewController:(PKPaymentAuthorizationViewController *)controller
                       didAuthorizePayment:(PKPayment *)payment
                                   handler:(void (^)(PKPaymentAuthorizationResult *result))completion {
    
    @try {
        // Create response dictionary
        NSMutableDictionary *response = [[NSMutableDictionary alloc] init];
        
        // Payment data
        NSMutableDictionary *paymentData = [[NSMutableDictionary alloc] init];
        [paymentData setObject:payment.token.paymentData.version forKey:@"version"];
        [paymentData setObject:[payment.token.paymentData.data base64EncodedStringWithOptions:0] forKey:@"data"];
        [paymentData setObject:[payment.token.paymentData.signature base64EncodedStringWithOptions:0] forKey:@"signature"];
        
        NSMutableDictionary *header = [[NSMutableDictionary alloc] init];
        [header setObject:[payment.token.paymentData.header.ephemeralPublicKey base64EncodedStringWithOptions:0] forKey:@"ephemeralPublicKey"];
        [header setObject:[payment.token.paymentData.header.publicKeyHash base64EncodedStringWithOptions:0] forKey:@"publicKeyHash"];
        [header setObject:payment.token.transactionIdentifier forKey:@"transactionId"];
        [paymentData setObject:header forKey:@"header"];
        
        [response setObject:paymentData forKey:@"paymentData"];
        
        // Payment method
        NSMutableDictionary *paymentMethod = [[NSMutableDictionary alloc] init];
        [paymentMethod setObject:payment.token.paymentMethod.displayName forKey:@"displayName"];
        [paymentMethod setObject:payment.token.paymentMethod.network forKey:@"network"];
        
        NSString *typeString = @"credit";
        switch (payment.token.paymentMethod.type) {
            case PKPaymentMethodTypeDebit:
                typeString = @"debit";
                break;
            case PKPaymentMethodTypeCredit:
                typeString = @"credit";
                break;
            case PKPaymentMethodTypePrepaid:
                typeString = @"prepaid";
                break;
            case PKPaymentMethodTypeStore:
                typeString = @"store";
                break;
            default:
                typeString = @"credit";
                break;
        }
        [paymentMethod setObject:typeString forKey:@"type"];
        
        // Payment pass information
        if (payment.token.paymentMethod.paymentPass) {
            NSMutableDictionary *paymentPass = [[NSMutableDictionary alloc] init];
            [paymentPass setObject:payment.token.paymentMethod.paymentPass.primaryAccountIdentifier forKey:@"primaryAccountIdentifier"];
            [paymentPass setObject:payment.token.paymentMethod.paymentPass.primaryAccountNumberSuffix forKey:@"primaryAccountNumberSuffix"];
            [paymentPass setObject:payment.token.paymentMethod.paymentPass.deviceAccountIdentifier forKey:@"deviceAccountIdentifier"];
            [paymentPass setObject:payment.token.paymentMethod.paymentPass.deviceAccountNumberSuffix forKey:@"deviceAccountNumberSuffix"];
            [paymentMethod setObject:paymentPass forKey:@"paymentPass"];
        }
        
        [response setObject:paymentMethod forKey:@"paymentMethod"];
        
        // Transaction identifier
        [response setObject:payment.token.transactionIdentifier forKey:@"transactionIdentifier"];
        
        // Contact information
        if (payment.billingContact) {
            [response setObject:[self contactToDictionary:payment.billingContact] forKey:@"billingContact"];
        }
        
        if (payment.shippingContact) {
            [response setObject:[self contactToDictionary:payment.shippingContact] forKey:@"shippingContact"];
        }
        
        // Complete the payment authorization
        PKPaymentAuthorizationResult *result = [[PKPaymentAuthorizationResult alloc] initWithStatus:PKPaymentAuthorizationStatusSuccess errors:nil];
        completion(result);
        
        // Resolve the promise
        if (self.currentResolve) {
            self.currentResolve(response);
            self.currentResolve = nil;
            self.currentReject = nil;
        }
        
    } @catch (NSException *exception) {
        PKPaymentAuthorizationResult *result = [[PKPaymentAuthorizationResult alloc] initWithStatus:PKPaymentAuthorizationStatusFailure errors:nil];
        completion(result);
        
        if (self.currentReject) {
            self.currentReject(@"PAYMENT_PROCESSING_FAILED", [exception reason], nil);
            self.currentResolve = nil;
            self.currentReject = nil;
        }
    }
}

- (void)paymentAuthorizationViewControllerDidFinish:(PKPaymentAuthorizationViewController *)controller {
    dispatch_async(dispatch_get_main_queue(), ^{
        [controller dismissViewControllerAnimated:YES completion:nil];
        
        // If we reach here without a successful payment, it means the user cancelled
        if (self.currentReject) {
            self.currentReject(@"PAYMENT_CANCELLED", @"User cancelled the payment", nil);
            self.currentResolve = nil;
            self.currentReject = nil;
        }
    });
}

@end