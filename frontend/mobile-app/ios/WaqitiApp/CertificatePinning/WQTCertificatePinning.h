//
//  WQTCertificatePinning.h
//  WaqitiApp
//
//  Certificate Pinning implementation for iOS
//  Provides native certificate validation for enhanced security
//

#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

NS_ASSUME_NONNULL_BEGIN

@interface WQTCertificatePinning : RCTEventEmitter <RCTBridgeModule, NSURLSessionDelegate>

// Certificate validation methods
- (void)validateCertificateForHost:(NSString *)hostname
                      certificates:(NSArray<NSString *> *)certificates
                          resolver:(RCTPromiseResolveBlock)resolve
                          rejecter:(RCTPromiseRejectBlock)reject;

// Pin management methods
- (void)addPinForHost:(NSString *)hostname
                 pins:(NSArray<NSString *> *)pins
             resolver:(RCTPromiseResolveBlock)resolve
             rejecter:(RCTPromiseRejectBlock)reject;

- (void)removePinForHost:(NSString *)hostname
                resolver:(RCTPromiseResolveBlock)resolve
                rejecter:(RCTPromiseRejectBlock)reject;

- (void)clearAllPins:(RCTPromiseResolveBlock)resolve
            rejecter:(RCTPromiseRejectBlock)reject;

// Configuration methods
- (void)setEnforcementMode:(NSString *)mode
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject;

- (void)getConfiguration:(RCTPromiseResolveBlock)resolve
                rejecter:(RCTPromiseRejectBlock)reject;

// Testing methods
- (void)testPinningForHost:(NSString *)hostname
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject;

@end

NS_ASSUME_NONNULL_END