//
//  WQTCertificatePinning.m
//  WaqitiApp
//
//  Certificate Pinning implementation for iOS
//

#import "WQTCertificatePinning.h"
#import <CommonCrypto/CommonDigest.h>
#import <Security/Security.h>

@interface WQTCertificatePinning ()

@property (nonatomic, strong) NSMutableDictionary<NSString *, NSArray<NSString *> *> *certificatePins;
@property (nonatomic, strong) NSURLSession *pinnedSession;
@property (nonatomic, assign) BOOL enforcePinning;
@property (nonatomic, strong) NSString *enforcementMode;
@property (nonatomic, strong) NSMutableArray *validationReports;

@end

@implementation WQTCertificatePinning

RCT_EXPORT_MODULE(CertificatePinning)

- (instancetype)init {
    if (self = [super init]) {
        _certificatePins = [NSMutableDictionary dictionary];
        _enforcePinning = YES;
        _enforcementMode = @"strict";
        _validationReports = [NSMutableArray array];
        
        [self initializeDefaultPins];
        [self setupPinnedSession];
    }
    return self;
}

- (void)initializeDefaultPins {
    // Production certificate pins for Waqiti services
    self.certificatePins[@"api.example.com"] = @[
        @"sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        @"sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    ];
    
    self.certificatePins[@"auth.example.com"] = @[
        @"sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=",
        @"sha256/FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF="
    ];
    
    self.certificatePins[@"payments.example.com"] = @[
        @"sha256/IIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII=",
        @"sha256/JJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ="
    ];
}

- (void)setupPinnedSession {
    NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
    configuration.TLSMinimumSupportedProtocolVersion = tls_protocol_version_TLSv12;
    configuration.TLSMaximumSupportedProtocolVersion = tls_protocol_version_TLSv13;
    
    self.pinnedSession = [NSURLSession sessionWithConfiguration:configuration
                                                       delegate:self
                                                  delegateQueue:nil];
}

#pragma mark - RCT Export Methods

RCT_EXPORT_METHOD(validateCertificateForHost:(NSString *)hostname
                  certificates:(NSArray<NSString *> *)certificates
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    @try {
        NSArray<NSString *> *expectedPins = self.certificatePins[hostname];
        if (!expectedPins || expectedPins.count == 0) {
            resolve(@{
                @"valid": @YES,
                @"hostname": hostname,
                @"message": @"No pins configured for hostname"
            });
            return;
        }
        
        BOOL isValid = NO;
        NSString *matchedPin = nil;
        
        for (NSString *certString in certificates) {
            NSString *pin = [self calculatePinFromCertificate:certString];
            if ([expectedPins containsObject:pin]) {
                isValid = YES;
                matchedPin = pin;
                break;
            }
        }
        
        if (isValid) {
            resolve(@{
                @"valid": @YES,
                @"hostname": hostname,
                @"matchedPin": matchedPin ?: @""
            });
        } else {
            [self reportPinningFailure:hostname reason:@"No matching pins"];
            
            if ([self.enforcementMode isEqualToString:@"strict"]) {
                reject(@"PINNING_FAILED", @"Certificate pinning validation failed", nil);
            } else {
                resolve(@{
                    @"valid": @NO,
                    @"hostname": hostname,
                    @"error": @"No matching certificate pins"
                });
            }
        }
    } @catch (NSException *exception) {
        reject(@"VALIDATION_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(addPinForHost:(NSString *)hostname
                  pins:(NSArray<NSString *> *)pins
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        self.certificatePins[hostname] = pins;
        [self savePinsToKeychain];
        resolve(@{@"success": @YES});
    } @catch (NSException *exception) {
        reject(@"ADD_PIN_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(removePinForHost:(NSString *)hostname
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        [self.certificatePins removeObjectForKey:hostname];
        [self savePinsToKeychain];
        resolve(@{@"success": @YES});
    } @catch (NSException *exception) {
        reject(@"REMOVE_PIN_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(clearAllPins:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        [self.certificatePins removeAllObjects];
        [self initializeDefaultPins];
        [self savePinsToKeychain];
        resolve(@{@"success": @YES});
    } @catch (NSException *exception) {
        reject(@"CLEAR_PINS_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(setEnforcementMode:(NSString *)mode
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        if ([@[@"strict", @"report", @"disabled"] containsObject:mode]) {
            self.enforcementMode = mode;
            self.enforcePinning = ![mode isEqualToString:@"disabled"];
            resolve(@{@"success": @YES});
        } else {
            reject(@"INVALID_MODE", @"Invalid enforcement mode", nil);
        }
    } @catch (NSException *exception) {
        reject(@"SET_MODE_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(getConfiguration:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSMutableDictionary *config = [NSMutableDictionary dictionary];
        config[@"enforcementMode"] = self.enforcementMode;
        config[@"enforcePinning"] = @(self.enforcePinning);
        config[@"configuredHosts"] = [self.certificatePins allKeys];
        config[@"reportCount"] = @(self.validationReports.count);
        
        resolve(config);
    } @catch (NSException *exception) {
        reject(@"GET_CONFIG_ERROR", exception.reason, nil);
    }
}

RCT_EXPORT_METHOD(testPinningForHost:(NSString *)hostname
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    @try {
        NSURL *url = [NSURL URLWithString:[NSString stringWithFormat:@"https://%@/health", hostname]];
        NSURLRequest *request = [NSURLRequest requestWithURL:url];
        
        __block BOOL pinningSuccess = NO;
        __block NSError *pinningError = nil;
        
        dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
        
        NSURLSessionDataTask *task = [self.pinnedSession dataTaskWithRequest:request
                                                           completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
            if (!error) {
                pinningSuccess = YES;
            } else {
                pinningError = error;
            }
            dispatch_semaphore_signal(semaphore);
        }];
        
        [task resume];
        
        dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 10 * NSEC_PER_SEC));
        
        if (pinningSuccess) {
            resolve(@{
                @"success": @YES,
                @"hostname": hostname
            });
        } else {
            resolve(@{
                @"success": @NO,
                @"hostname": hostname,
                @"error": pinningError.localizedDescription ?: @"Test failed"
            });
        }
    } @catch (NSException *exception) {
        reject(@"TEST_ERROR", exception.reason, nil);
    }
}

#pragma mark - NSURLSessionDelegate

- (void)URLSession:(NSURLSession *)session
didReceiveChallenge:(NSURLAuthenticationChallenge *)challenge
 completionHandler:(void (^)(NSURLSessionAuthChallengeDisposition, NSURLCredential * _Nullable))completionHandler {
    
    if (![challenge.protectionSpace.authenticationMethod isEqualToString:NSURLAuthenticationMethodServerTrust]) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, nil);
        return;
    }
    
    if (!self.enforcePinning) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, nil);
        return;
    }
    
    NSString *hostname = challenge.protectionSpace.host;
    NSArray<NSString *> *expectedPins = self.certificatePins[hostname];
    
    if (!expectedPins || expectedPins.count == 0) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, nil);
        return;
    }
    
    SecTrustRef serverTrust = challenge.protectionSpace.serverTrust;
    
    // Validate the certificate chain
    SecTrustResultType trustResult;
    SecTrustEvaluate(serverTrust, &trustResult);
    
    if (trustResult != kSecTrustResultUnspecified && trustResult != kSecTrustResultProceed) {
        [self reportPinningFailure:hostname reason:@"Certificate trust evaluation failed"];
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, nil);
        return;
    }
    
    // Extract and validate pins
    BOOL pinValidated = NO;
    CFIndex certificateCount = SecTrustGetCertificateCount(serverTrust);
    
    for (CFIndex i = 0; i < certificateCount; i++) {
        SecCertificateRef certificate = SecTrustGetCertificateAtIndex(serverTrust, i);
        NSString *pin = [self calculatePinFromCertificate:certificate];
        
        if ([expectedPins containsObject:pin]) {
            pinValidated = YES;
            break;
        }
    }
    
    if (pinValidated) {
        NSURLCredential *credential = [NSURLCredential credentialForTrust:serverTrust];
        completionHandler(NSURLSessionAuthChallengeUseCredential, credential);
    } else {
        [self reportPinningFailure:hostname reason:@"No matching certificate pins"];
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, nil);
    }
}

#pragma mark - Helper Methods

- (NSString *)calculatePinFromCertificate:(id)certificate {
    NSData *publicKeyData = nil;
    
    if ([certificate isKindOfClass:[NSString class]]) {
        // Base64 encoded certificate string
        NSData *certData = [[NSData alloc] initWithBase64EncodedString:certificate options:0];
        if (!certData) return @"";
        
        SecCertificateRef certRef = SecCertificateCreateWithData(NULL, (__bridge CFDataRef)certData);
        if (!certRef) return @"";
        
        publicKeyData = [self extractPublicKeyFromCertificate:certRef];
        CFRelease(certRef);
    } else if (certificate) {
        // SecCertificateRef
        publicKeyData = [self extractPublicKeyFromCertificate:(__bridge SecCertificateRef)certificate];
    }
    
    if (!publicKeyData) return @"";
    
    // Calculate SHA256 hash
    unsigned char hash[CC_SHA256_DIGEST_LENGTH];
    CC_SHA256(publicKeyData.bytes, (CC_LONG)publicKeyData.length, hash);
    
    // Convert to base64
    NSData *hashData = [NSData dataWithBytes:hash length:CC_SHA256_DIGEST_LENGTH];
    NSString *base64Hash = [hashData base64EncodedStringWithOptions:0];
    
    return [NSString stringWithFormat:@"sha256/%@", base64Hash];
}

- (NSData *)extractPublicKeyFromCertificate:(SecCertificateRef)certificate {
    SecKeyRef publicKey = NULL;
    SecTrustRef trust = NULL;
    SecPolicyRef policy = SecPolicyCreateBasicX509();
    
    OSStatus status = SecTrustCreateWithCertificates(certificate, policy, &trust);
    if (status != errSecSuccess) {
        CFRelease(policy);
        return nil;
    }
    
    SecTrustResultType trustResult;
    status = SecTrustEvaluate(trust, &trustResult);
    if (status != errSecSuccess) {
        CFRelease(policy);
        CFRelease(trust);
        return nil;
    }
    
    publicKey = SecTrustCopyPublicKey(trust);
    CFRelease(policy);
    CFRelease(trust);
    
    if (!publicKey) return nil;
    
    // Export public key to data
    CFErrorRef error = NULL;
    NSData *publicKeyData = (__bridge_transfer NSData *)SecKeyCopyExternalRepresentation(publicKey, &error);
    CFRelease(publicKey);
    
    if (error) {
        CFRelease(error);
        return nil;
    }
    
    return publicKeyData;
}

- (void)reportPinningFailure:(NSString *)hostname reason:(NSString *)reason {
    if ([self.enforcementMode isEqualToString:@"disabled"]) return;
    
    NSDictionary *report = @{
        @"timestamp": @([[NSDate date] timeIntervalSince1970]),
        @"hostname": hostname,
        @"reason": reason,
        @"enforcementMode": self.enforcementMode,
        @"platform": @"iOS",
        @"osVersion": [[UIDevice currentDevice] systemVersion]
    };
    
    [self.validationReports addObject:report];
    
    // Send event to JavaScript
    if (self.bridge) {
        [self sendEventWithName:@"CertificatePinningFailure" body:report];
    }
    
    // Send report to server (in production)
    [self sendReportToServer:report];
}

- (void)sendReportToServer:(NSDictionary *)report {
    // Implementation for sending security reports to server
    NSString *reportURL = @"https://security.example.com/pinning-report";
    NSURL *url = [NSURL URLWithString:reportURL];
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"POST";
    request.HTTPBody = [NSJSONSerialization dataWithJSONObject:report options:0 error:nil];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    
    NSURLSession *session = [NSURLSession sharedSession];
    NSURLSessionDataTask *task = [session dataTaskWithRequest:request];
    [task resume];
}

- (void)savePinsToKeychain {
    // Save certificate pins to keychain for persistence
    NSError *error = nil;
    NSData *pinsData = [NSJSONSerialization dataWithJSONObject:self.certificatePins options:0 error:&error];
    
    if (!error && pinsData) {
        NSDictionary *query = @{
            (__bridge id)kSecClass: (__bridge id)kSecClassGenericPassword,
            (__bridge id)kSecAttrService: @"com.waqiti.certificatepins",
            (__bridge id)kSecAttrAccount: @"pins",
            (__bridge id)kSecValueData: pinsData
        };
        
        SecItemDelete((__bridge CFDictionaryRef)query);
        SecItemAdd((__bridge CFDictionaryRef)query, NULL);
    }
}

- (void)loadPinsFromKeychain {
    NSDictionary *query = @{
        (__bridge id)kSecClass: (__bridge id)kSecClassGenericPassword,
        (__bridge id)kSecAttrService: @"com.waqiti.certificatepins",
        (__bridge id)kSecAttrAccount: @"pins",
        (__bridge id)kSecReturnData: (__bridge id)kCFBooleanTrue
    };
    
    CFTypeRef result = NULL;
    OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &result);
    
    if (status == errSecSuccess && result) {
        NSData *pinsData = (__bridge_transfer NSData *)result;
        NSError *error = nil;
        NSDictionary *pins = [NSJSONSerialization JSONObjectWithData:pinsData options:0 error:&error];
        
        if (!error && pins) {
            self.certificatePins = [pins mutableCopy];
        }
    }
}

#pragma mark - Event Emitter

- (NSArray<NSString *> *)supportedEvents {
    return @[@"CertificatePinningFailure", @"CertificatePinningSuccess"];
}

@end