import Foundation
import Security
import CommonCrypto

/**
 * Native module for certificate pinning on iOS
 * Provides comprehensive SSL/TLS security for network communications
 */
@objc(CertificatePinningModule)
class CertificatePinningModule: NSObject, URLSessionDelegate {
    
    private var pinnedSession: URLSession?
    private var domainPins: [String: [String]] = [:]
    private var enforceMode: Bool = true
    private var certificateCache: [String: SecCertificate] = [:]
    
    // Production certificate pins for Waqiti services
    private let defaultPins: [String: [String]] = [
        "api.example.com": [
            "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=" // Backup pin
        ],
        "auth.example.com": [
            "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
            "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=" // Backup pin
        ],
        "wallet.example.com": [
            "sha256/FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=",
            "sha256/GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG=" // Backup pin
        ]
    ]
    
    override init() {
        super.init()
        self.domainPins = defaultPins
        initializePinnedSession()
    }
    
    /**
     * Initialize URLSession with certificate pinning
     */
    private func initializePinnedSession() {
        let configuration = URLSessionConfiguration.default
        
        // Configure TLS settings
        configuration.tlsMinimumSupportedProtocolVersion = .TLSv12
        configuration.tlsMaximumSupportedProtocolVersion = .TLSv13
        
        // Security settings
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.httpShouldUsePipelining = false
        configuration.httpShouldSetCookies = false
        
        // Timeout settings
        configuration.timeoutIntervalForRequest = 30
        configuration.timeoutIntervalForResource = 60
        
        // Create session with pinning delegate
        pinnedSession = URLSession(
            configuration: configuration,
            delegate: self,
            delegateQueue: nil
        )
        
        print("[CertificatePinning] Session initialized with \(domainPins.count) pinned domains")
    }
    
    /**
     * Configure pinned session with custom pins
     */
    @objc func configurePinnedSession(
        _ config: NSDictionary,
        resolver: @escaping RCTPromiseResolveBlock,
        rejecter: @escaping RCTPromiseRejectBlock
    ) {
        guard let domains = config["domains"] as? [[String: Any]] else {
            rejecter("CONFIG_ERROR", "Invalid configuration format", nil)
            return
        }
        
        domainPins.removeAll()
        
        for domain in domains {
            guard let domainName = domain["domain"] as? String,
                  let pins = domain["pins"] as? [String] else {
                continue
            }
            domainPins[domainName] = pins
        }
        
        if let enforce = config["enforceMode"] as? Bool {
            enforceMode = enforce
        }
        
        // Reinitialize session with new configuration
        initializePinnedSession()
        
        resolver([
            "success": true,
            "domainCount": domainPins.count
        ])
    }
    
    /**
     * Test certificate pinning for a specific domain
     */
    @objc func testPinning(
        _ hostname: String,
        resolver: @escaping RCTPromiseResolveBlock,
        rejecter: @escaping RCTPromiseRejectBlock
    ) {
        guard let url = URL(string: "https://\(hostname)/health") else {
            rejecter("URL_ERROR", "Invalid hostname", nil)
            return
        }
        
        let task = pinnedSession?.dataTask(with: url) { data, response, error in
            if let error = error {
                // Check if it's a pinning failure
                if (error as NSError).code == NSURLErrorServerCertificateUntrusted {
                    resolver([
                        "success": false,
                        "hostname": hostname,
                        "error": "PINNING_FAILED",
                        "message": error.localizedDescription
                    ])
                    self.sendSecurityEvent(type: "pinning_test_failed", details: hostname)
                } else {
                    rejecter("TEST_ERROR", error.localizedDescription, error)
                }
            } else {
                resolver([
                    "success": true,
                    "hostname": hostname,
                    "pinned": self.domainPins[hostname] != nil
                ])
            }
        }
        
        task?.resume()
    }
    
    /**
     * Get current pinning status
     */
    @objc func getStatus(
        _ resolver: @escaping RCTPromiseResolveBlock,
        rejecter: @escaping RCTPromiseRejectBlock
    ) {
        var domainInfo: [String: Int] = [:]
        for (domain, pins) in domainPins {
            domainInfo[domain] = pins.count
        }
        
        resolver([
            "enabled": pinnedSession != nil,
            "enforceMode": enforceMode,
            "domainCount": domainPins.count,
            "domains": domainInfo
        ])
    }
    
    /**
     * Update certificate pins dynamically
     */
    @objc func updatePins(
        _ updates: NSDictionary,
        resolver: @escaping RCTPromiseResolveBlock,
        rejecter: @escaping RCTPromiseRejectBlock
    ) {
        guard let domains = updates["domains"] as? [[String: Any]] else {
            rejecter("UPDATE_ERROR", "Invalid update format", nil)
            return
        }
        
        for domain in domains {
            guard let hostname = domain["hostname"] as? String,
                  let pins = domain["pins"] as? [String] else {
                continue
            }
            domainPins[hostname] = pins
        }
        
        // Reinitialize session with updated pins
        initializePinnedSession()
        
        sendSecurityEvent(
            type: "pins_updated",
            details: "Updated \(domains.count) domains"
        )
        
        resolver([
            "success": true,
            "updatedDomains": domains.count
        ])
    }
    
    /**
     * Reset to default pins
     */
    @objc func resetToDefaults(
        _ resolver: @escaping RCTPromiseResolveBlock,
        rejecter: @escaping RCTPromiseRejectBlock
    ) {
        domainPins = defaultPins
        initializePinnedSession()
        
        resolver([
            "success": true,
            "defaultDomains": defaultPins.count
        ])
    }
    
    // MARK: - URLSessionDelegate
    
    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        // Only handle server trust challenges
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let serverTrust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        
        let host = challenge.protectionSpace.host
        
        // Check if we have pins for this host
        guard let pins = domainPins[host] ?? domainPins[getBaseDomain(host)] else {
            if enforceMode {
                // No pins configured, reject if in enforce mode
                completionHandler(.cancelAuthenticationChallenge, nil)
                sendSecurityEvent(type: "no_pins_for_host", details: host)
            } else {
                // Allow connection without pinning
                completionHandler(.performDefaultHandling, nil)
            }
            return
        }
        
        // Validate certificate chain
        if validateCertificateChain(serverTrust: serverTrust, pins: pins, host: host) {
            let credential = URLCredential(trust: serverTrust)
            completionHandler(.useCredential, credential)
        } else {
            completionHandler(.cancelAuthenticationChallenge, nil)
            sendSecurityEvent(type: "pinning_validation_failed", details: host)
        }
    }
    
    /**
     * Validate certificate chain against pins
     */
    private func validateCertificateChain(
        serverTrust: SecTrust,
        pins: [String],
        host: String
    ) -> Bool {
        // Perform standard certificate validation
        var error: CFError?
        let isValid = SecTrustEvaluateWithError(serverTrust, &error)
        
        if !isValid {
            print("[CertificatePinning] Standard validation failed for \(host): \(error?.localizedDescription ?? "Unknown error")")
            return false
        }
        
        // Get certificate chain
        let certificateCount = SecTrustGetCertificateCount(serverTrust)
        
        for i in 0..<certificateCount {
            guard let certificate = SecTrustGetCertificateAtIndex(serverTrust, i) else {
                continue
            }
            
            // Calculate pin for this certificate
            let pin = calculatePin(for: certificate)
            
            // Check if pin matches any of our configured pins
            if pins.contains(pin) {
                print("[CertificatePinning] Pin matched for \(host)")
                return true
            }
        }
        
        print("[CertificatePinning] No matching pins found for \(host)")
        return false
    }
    
    /**
     * Calculate SHA256 pin for a certificate
     */
    private func calculatePin(for certificate: SecCertificate) -> String {
        guard let publicKey = SecCertificateCopyKey(certificate),
              let publicKeyData = SecKeyCopyExternalRepresentation(publicKey, nil) as Data? else {
            return ""
        }
        
        // Calculate SHA256 hash
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        publicKeyData.withUnsafeBytes { bytes in
            _ = CC_SHA256(bytes.baseAddress, CC_LONG(publicKeyData.count), &hash)
        }
        
        // Convert to base64
        let hashData = Data(hash)
        return "sha256/" + hashData.base64EncodedString()
    }
    
    /**
     * Get base domain from hostname
     */
    private func getBaseDomain(_ host: String) -> String {
        let components = host.split(separator: ".")
        if components.count > 2 {
            // Return last two components (e.g., "waqiti.com" from "api.example.com")
            return components.suffix(2).joined(separator: ".")
        }
        return host
    }
    
    /**
     * Send security events to JavaScript
     */
    private func sendSecurityEvent(type: String, details: String) {
        guard let bridge = self.bridge else { return }
        
        bridge.eventDispatcher().sendDeviceEvent(
            withName: "CertificatePinningSecurityEvent",
            body: [
                "type": type,
                "details": details,
                "timestamp": Date().timeIntervalSince1970 * 1000
            ]
        )
    }
    
    // MARK: - React Native Module Setup
    
    @objc static func requiresMainQueueSetup() -> Bool {
        return false
    }
    
    @objc var methodQueue: DispatchQueue {
        return DispatchQueue(label: "com.waqiti.certificatepinning", qos: .userInitiated)
    }
}

// MARK: - Objective-C Bridge

@objc(CertificatePinningModule)
extension CertificatePinningModule: RCTBridgeModule {
    static func moduleName() -> String! {
        return "CertificatePinningModule"
    }
}

// MARK: - Export Methods to React Native

extension CertificatePinningModule {
    @objc func exportedMethods() -> [String] {
        return [
            "configurePinnedSession",
            "testPinning",
            "getStatus",
            "updatePins",
            "resetToDefaults"
        ]
    }
}