//
//  WaqitiWidgetModule.m
//  WaqitiApp
//
//  Widget management module for iOS
//

#import "WaqitiWidgetModule.h"
#import <React/RCTLog.h>
#import <React/RCTUtils.h>

@implementation WaqitiWidgetModule

RCT_EXPORT_MODULE();

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"WidgetTapped", @"WidgetDataRequested", @"WidgetConfigurationChanged"];
}

#pragma mark - Public Methods

RCT_EXPORT_METHOD(updateWidgets:(NSDictionary *)widgetData
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            // Save widget data to shared user defaults
            NSUserDefaults *sharedDefaults = [[NSUserDefaults alloc] initWithSuiteName:@"group.com.waqiti.app"];
            
            if (sharedDefaults) {
                NSError *error;
                NSData *jsonData = [NSJSONSerialization dataWithJSONObject:widgetData
                                                                   options:0
                                                                     error:&error];
                
                if (error) {
                    reject(@"JSON_SERIALIZATION_ERROR", @"Failed to serialize widget data", error);
                    return;
                }
                
                [sharedDefaults setObject:jsonData forKey:@"widget_data"];
                [sharedDefaults setObject:[NSDate date] forKey:@"widget_last_updated"];
                [sharedDefaults synchronize];
                
                // Reload all widgets
                if (@available(iOS 14.0, *)) {
                    [WidgetCenter.sharedCenter reloadAllTimelines];
                    RCTLogInfo(@"Widgets updated successfully");
                }
                
                resolve(@{@"success": @YES, @"timestamp": @([[NSDate date] timeIntervalSince1970])});
            } else {
                reject(@"SHARED_DEFAULTS_ERROR", @"Failed to access shared user defaults", nil);
            }
        } @catch (NSException *exception) {
            reject(@"UPDATE_ERROR", exception.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(configureWidget:(NSString *)widgetType
                  config:(NSDictionary *)config
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            NSUserDefaults *sharedDefaults = [[NSUserDefaults alloc] initWithSuiteName:@"group.com.waqiti.app"];
            
            if (sharedDefaults) {
                NSString *configKey = [NSString stringWithFormat:@"widget_config_%@", widgetType];
                
                NSError *error;
                NSData *jsonData = [NSJSONSerialization dataWithJSONObject:config
                                                                   options:0
                                                                     error:&error];
                
                if (error) {
                    reject(@"CONFIG_SERIALIZATION_ERROR", @"Failed to serialize widget config", error);
                    return;
                }
                
                [sharedDefaults setObject:jsonData forKey:configKey];
                [sharedDefaults synchronize];
                
                // Reload specific widget kind
                if (@available(iOS 14.0, *)) {
                    NSString *widgetKind = [self widgetKindForType:widgetType];
                    if (widgetKind) {
                        [WidgetCenter.sharedCenter reloadTimelineForKind:widgetKind];
                    }
                }
                
                resolve(@{@"success": @YES, @"widgetType": widgetType});
            } else {
                reject(@"SHARED_DEFAULTS_ERROR", @"Failed to access shared user defaults", nil);
            }
        } @catch (NSException *exception) {
            reject(@"CONFIG_ERROR", exception.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(setWidgetEnabled:(NSString *)widgetType
                  enabled:(BOOL)enabled
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            NSUserDefaults *sharedDefaults = [[NSUserDefaults alloc] initWithSuiteName:@"group.com.waqiti.app"];
            
            if (sharedDefaults) {
                NSString *enabledKey = [NSString stringWithFormat:@"widget_enabled_%@", widgetType];
                [sharedDefaults setBool:enabled forKey:enabledKey];
                [sharedDefaults synchronize];
                
                // Reload widget
                if (@available(iOS 14.0, *)) {
                    NSString *widgetKind = [self widgetKindForType:widgetType];
                    if (widgetKind) {
                        [WidgetCenter.sharedCenter reloadTimelineForKind:widgetKind];
                    }
                }
                
                resolve(@{@"success": @YES, @"widgetType": widgetType, @"enabled": @(enabled)});
            } else {
                reject(@"SHARED_DEFAULTS_ERROR", @"Failed to access shared user defaults", nil);
            }
        } @catch (NSException *exception) {
            reject(@"ENABLE_ERROR", exception.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(getWidgetInfo:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            NSMutableDictionary *widgetInfo = [NSMutableDictionary dictionary];
            
            if (@available(iOS 14.0, *)) {
                widgetInfo[@"supportsWidgets"] = @YES;
                widgetInfo[@"widgetKits"] = @[
                    @{@"kind": @"WaqitiBalanceWidget", @"displayName": @"Waqiti Balance"},
                    @"kind": @"WaqitiQuickActionsWidget", @"displayName": @"Waqiti Quick Actions"}
                ];
            } else {
                widgetInfo[@"supportsWidgets"] = @NO;
                widgetInfo[@"reason"] = @"iOS 14.0 or later required";
            }
            
            widgetInfo[@"platform"] = @"iOS";
            widgetInfo[@"version"] = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleShortVersionString"];
            
            resolve(widgetInfo);
        } @catch (NSException *exception) {
            reject(@"INFO_ERROR", exception.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(refreshAllWidgets:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            if (@available(iOS 14.0, *)) {
                [WidgetCenter.sharedCenter reloadAllTimelines];
                
                // Send event to React Native
                [self sendEventWithName:@"WidgetDataRequested" body:@{@"timestamp": @([[NSDate date] timeIntervalSince1970])}];
                
                resolve(@{@"success": @YES, @"message": @"All widgets refreshed"});
            } else {
                reject(@"UNSUPPORTED_VERSION", @"iOS 14.0 or later required", nil);
            }
        } @catch (NSException *exception) {
            reject(@"REFRESH_ERROR", exception.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(handleWidgetTap:(NSString *)widgetType
                  actionId:(NSString * _Nullable)actionId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    
    // Send event to React Native for handling
    NSDictionary *eventData = @{
        @"widgetType": widgetType,
        @"actionId": actionId ?: [NSNull null],
        @"timestamp": @([[NSDate date] timeIntervalSince1970])
    };
    
    [self sendEventWithName:@"WidgetTapped" body:eventData];
    
    resolve(@{@"success": @YES, @"handled": @YES});
}

#pragma mark - Helper Methods

- (NSString *)widgetKindForType:(NSString *)widgetType {
    NSDictionary *typeToKind = @{
        @"balance": @"WaqitiBalanceWidget",
        @"quick_actions": @"WaqitiQuickActionsWidget",
        @"recent_transactions": @"WaqitiBalanceWidget",
        @"crypto_prices": @"WaqitiBalanceWidget"
    };
    
    return typeToKind[widgetType];
}

@end