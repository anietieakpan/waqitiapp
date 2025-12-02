//
//  ApplePayBridge.h
//  WaqitiMobile
//
//  Apple Pay Native Module for React Native Integration
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <PassKit/PassKit.h>

@interface ApplePayBridge : RCTEventEmitter <RCTBridgeModule, PKPaymentAuthorizationViewControllerDelegate>

@property (nonatomic, strong) PKPaymentRequest *currentPaymentRequest;
@property (nonatomic, strong) RCTPromiseResolveBlock currentResolve;
@property (nonatomic, strong) RCTPromiseRejectBlock currentReject;

@end