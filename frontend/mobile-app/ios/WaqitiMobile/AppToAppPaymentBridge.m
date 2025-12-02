//
//  AppToAppPaymentBridge.m
//  WaqitiMobile
//
//  App-to-App Payment Native Module Implementation
//

#import "AppToAppPaymentBridge.h"
#import <React/RCTUtils.h>
#import <React/RCTLog.h>
#import <UIKit/UIKit.h>

@implementation AppToAppPaymentBridge

RCT_EXPORT_MODULE(AppToAppPaymentModule);

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"onPaymentResponse", @"onAppInstallationChanged"];
}

#pragma mark - App Detection Methods

RCT_EXPORT_METHOD(isAppInstalled:(NSString *)bundleId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        // For iOS, we check if the app can open its URL scheme
        // This is a simplified check - in practice, you'd need the actual URL scheme
        NSString *urlScheme = [self urlSchemeForBundleId:bundleId];
        
        if (urlScheme) {
            NSURL *url = [NSURL URLWithString:urlScheme];
            BOOL canOpen = [[UIApplication sharedApplication] canOpenURL:url];
            
            RCTLogInfo(@"App %@ installation check: %@", bundleId, canOpen ? @"installed" : @"not installed");
            resolve(@(canOpen));
        } else {
            // Unknown bundle ID
            resolve(@NO);
        }
        
    } @catch (NSException *exception) {
        reject(@"APP_CHECK_FAILED", [exception reason], nil);
    }
}

RCT_EXPORT_METHOD(getInstalledPaymentApps:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSMutableDictionary *installedApps = [[NSMutableDictionary alloc] init];
        
        // Common payment app bundle IDs and their URL schemes
        NSDictionary *paymentApps = @{
            @"net.venmo": @"venmo://",
            @"com.squareup.cash": @"cashapp://",
            @"com.paypal.mobile": @"paypal://",
            @"com.zellepay.zelle": @"zelle://",
            @"com.google.paisa": @"gpay://",
        };
        
        for (NSString *bundleId in paymentApps) {
            NSString *urlScheme = paymentApps[bundleId];
            NSURL *url = [NSURL URLWithString:urlScheme];
            BOOL isInstalled = [[UIApplication sharedApplication] canOpenURL:url];
            
            [installedApps setObject:@(isInstalled) forKey:bundleId];
            
            if (isInstalled) {
                RCTLogInfo(@"Payment app installed: %@", bundleId);
            }
        }
        
        resolve(installedApps);
        
    } @catch (NSException *exception) {
        reject(@"PAYMENT_APPS_ERROR", [exception reason], nil);
    }
}

#pragma mark - Payment Request Creation

RCT_EXPORT_METHOD(createApplePayRequest:(NSDictionary *)requestData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSMutableDictionary *applePayData = [[NSMutableDictionary alloc] init];
        
        // Extract data from request
        NSNumber *amount = requestData[@"amount"] ?: @0.0;
        NSString *currency = requestData[@"currency"] ?: @"USD";
        NSString *merchantId = requestData[@"merchantId"] ?: @"merchant.com.waqiti";
        NSString *requestId = requestData[@"requestId"] ?: @"";
        
        // Create Apple Pay specific payment data structure
        [applePayData setObject:merchantId forKey:@"merchantIdentifier"];
        [applePayData setObject:currency forKey:@"currencyCode"];
        [applePayData setObject:@"US" forKey:@"countryCode"];
        [applePayData setObject:requestId forKey:@"requestId"];
        
        // Add payment summary item
        NSMutableDictionary *paymentItem = [[NSMutableDictionary alloc] init];
        [paymentItem setObject:@"Waqiti Payment" forKey:@"label"];
        [paymentItem setObject:[amount stringValue] forKey:@"amount"];
        [paymentItem setObject:@"final" forKey:@"type"];
        
        [applePayData setObject:paymentItem forKey:@"paymentItem"];
        
        RCTLogInfo(@"Created Apple Pay request for amount: %@", amount);
        resolve(applePayData);
        
    } @catch (NSException *exception) {
        reject(@"APPLE_PAY_REQUEST_ERROR", [exception reason], nil);
    }
}

RCT_EXPORT_METHOD(createGooglePayRequest:(NSDictionary *)requestData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        // Google Pay is not natively supported on iOS
        // This method exists for consistency with Android
        NSMutableDictionary *response = [[NSMutableDictionary alloc] init];
        [response setObject:@NO forKey:@"supported"];
        [response setObject:@"Google Pay is not supported on iOS" forKey:@"message"];
        
        resolve(response);
        
    } @catch (NSException *exception) {
        reject(@"GOOGLE_PAY_REQUEST_ERROR", [exception reason], nil);
    }
}

#pragma mark - App Launching

RCT_EXPORT_METHOD(launchApp:(NSString *)bundleId
                  action:(NSString *)action
                  data:(NSString *)data
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            NSString *urlScheme = [self urlSchemeForBundleId:bundleId];
            
            if (!urlScheme) {
                reject(@"UNSUPPORTED_APP", [NSString stringWithFormat:@"Unsupported app: %@", bundleId], nil);
                return;
            }
            
            NSURL *url;
            if (data && data.length > 0) {
                url = [NSURL URLWithString:data];
            } else {
                url = [NSURL URLWithString:urlScheme];
            }
            
            if ([[UIApplication sharedApplication] canOpenURL:url]) {
                [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:^(BOOL success) {
                    if (success) {
                        RCTLogInfo(@"Successfully launched app: %@", bundleId);
                        resolve(@YES);
                    } else {
                        reject(@"LAUNCH_FAILED", [NSString stringWithFormat:@"Failed to launch app: %@", bundleId], nil);
                    }
                }];
            } else {
                reject(@"CANNOT_OPEN_URL", [NSString stringWithFormat:@"Cannot open URL for app: %@", bundleId], nil);
            }
            
        } @catch (NSException *exception) {
            reject(@"LAUNCH_ERROR", [exception reason], nil);
        }
    });
}

#pragma mark - App Information

RCT_EXPORT_METHOD(getAppInfo:(NSString *)bundleId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSMutableDictionary *appInfo = [[NSMutableDictionary alloc] init];
        [appInfo setObject:bundleId forKey:@"bundleId"];
        
        NSString *urlScheme = [self urlSchemeForBundleId:bundleId];
        if (urlScheme) {
            NSURL *url = [NSURL URLWithString:urlScheme];
            BOOL isInstalled = [[UIApplication sharedApplication] canOpenURL:url];
            
            [appInfo setObject:@(isInstalled) forKey:@"isInstalled"];
            
            if (isInstalled) {
                // Add app name mapping
                NSString *appName = [self appNameForBundleId:bundleId];
                [appInfo setObject:appName forKey:@"appName"];
            }
        } else {
            [appInfo setObject:@NO forKey:@"isInstalled"];
        }
        
        RCTLogInfo(@"Retrieved app info for: %@", bundleId);
        resolve(appInfo);
        
    } @catch (NSException *exception) {
        reject(@"APP_INFO_ERROR", [exception reason], nil);
    }
}

#pragma mark - Event Handling

RCT_EXPORT_METHOD(notifyPaymentResponse:(NSDictionary *)responseData) {
    @try {
        NSMutableDictionary *response = [[NSMutableDictionary alloc] init];
        
        if (responseData[@"requestId"]) {
            [response setObject:responseData[@"requestId"] forKey:@"requestId"];
        }
        if (responseData[@"status"]) {
            [response setObject:responseData[@"status"] forKey:@"status"];
        }
        if (responseData[@"transactionId"]) {
            [response setObject:responseData[@"transactionId"] forKey:@"transactionId"];
        }
        if (responseData[@"error"]) {
            [response setObject:responseData[@"error"] forKey:@"error"];
        }
        
        [response setObject:@([[NSDate date] timeIntervalSince1970] * 1000) forKey:@"timestamp"];
        
        [self sendEventWithName:@"onPaymentResponse" body:response];
        RCTLogInfo(@"Notified JavaScript of payment response");
        
    } @catch (NSException *exception) {
        RCTLogError(@"Error notifying payment response: %@", [exception reason]);
    }
}

RCT_EXPORT_METHOD(notifyAppInstallationChange:(NSString *)appId
                  installed:(BOOL)installed) {
    @try {
        NSDictionary *data = @{
            @"appId": appId,
            @"installed": @(installed)
        };
        
        [self sendEventWithName:@"onAppInstallationChanged" body:data];
        RCTLogInfo(@"Notified JavaScript of app installation change: %@ -> %@", appId, installed ? @"installed" : @"uninstalled");
        
    } @catch (NSException *exception) {
        RCTLogError(@"Error notifying app installation change: %@", [exception reason]);
    }
}

#pragma mark - Helper Methods

- (NSString *)urlSchemeForBundleId:(NSString *)bundleId {
    // Map bundle IDs to their URL schemes
    NSDictionary *schemes = @{
        @"net.venmo": @"venmo://",
        @"com.squareup.cash": @"cashapp://",
        @"com.paypal.mobile": @"paypal://",
        @"com.zellepay.zelle": @"zelle://",
        @"com.google.paisa": @"gpay://",
        @"com.apple.PassbookUIService": @"shoebox://", // Apple Wallet
    };
    
    return schemes[bundleId];
}

- (NSString *)appNameForBundleId:(NSString *)bundleId {
    // Map bundle IDs to their display names
    NSDictionary *names = @{
        @"net.venmo": @"Venmo",
        @"com.squareup.cash": @"Cash App",
        @"com.paypal.mobile": @"PayPal",
        @"com.zellepay.zelle": @"Zelle",
        @"com.google.paisa": @"Google Pay",
        @"com.apple.PassbookUIService": @"Apple Wallet",
    };
    
    return names[bundleId] ?: bundleId;
}

@end