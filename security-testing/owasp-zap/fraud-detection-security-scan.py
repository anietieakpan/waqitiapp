#!/usr/bin/env python3
"""
OWASP ZAP Security Penetration Testing - Fraud Detection Service
================================================================
Critical security testing for fraud detection endpoints.
Focus: ML model tampering, decision manipulation, data leakage, timing attacks.
"""

import time
import requests
import logging
import statistics
from zapv2 import ZAPv2
import json
import uuid
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('security-testing/reports/fraud-detection-security.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class FraudDetectionSecurityTester:
    def __init__(self, zap_proxy_url='http://127.0.0.1:8080', target_url='http://localhost:8083'):
        self.zap = ZAPv2(proxies={'http': zap_proxy_url, 'https': zap_proxy_url})
        self.target_url = target_url
        self.session_token = None
        self.results = {
            'timestamp': datetime.now().isoformat(),
            'target': target_url,
            'tests': [],
            'vulnerabilities': [],
            'timing_analysis': {},
            'summary': {}
        }
        
    def setup_authentication(self):
        """Setup authentication for fraud detection service"""
        logger.info("Setting up authentication...")
        
        try:
            auth_payload = {
                "username": "fraud-security-tester",
                "password": "SecureTest123!",
                "role": "FRAUD_ANALYST"
            }
            
            response = requests.post(
                f"{self.target_url}/api/v1/auth/login",
                json=auth_payload,
                timeout=10
            )
            
            if response.status_code == 200:
                auth_data = response.json()
                self.session_token = auth_data.get('token')
                logger.info("Authentication successful")
                return True
            else:
                logger.warning(f"Authentication failed: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"Authentication setup failed: {e}")
            return False
    
    def test_fraud_check_tampering(self):
        """Test fraud check endpoint for decision manipulation vulnerabilities"""
        logger.info("Testing fraud check decision tampering...")
        
        test_results = []
        endpoint = f"{self.target_url}/api/v1/fraud/check"
        
        # Test 1: Amount manipulation to bypass thresholds
        amount_manipulation_tests = [
            0.01,  # Very small amount
            999.99,  # Just below threshold
            -100.00,  # Negative amount
            float('inf'),  # Infinite amount
            None,  # Null amount
            "1000 OR 1=1",  # SQL injection attempt
        ]
        
        for amount in amount_manipulation_tests:
            test_data = {
                "transactionId": str(uuid.uuid4()),
                "userId": "security-test-user",
                "amount": amount,
                "currency": "USD",
                "merchantId": "test-merchant",
                "ipAddress": "192.168.1.100",
                "deviceFingerprint": str(uuid.uuid4()),
                "timestamp": datetime.now().isoformat()
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'AMOUNT_MANIPULATION',
                    'payload': str(amount),
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                if response.status_code == 200:
                    response_data = response.json()
                    risk_score = response_data.get('riskScore', 0)
                    decision = response_data.get('decision', 'UNKNOWN')
                    
                    # Check if obviously fraudulent patterns are scored as low risk
                    if amount and amount < 0 and risk_score < 0.5:
                        test_result['vulnerable'] = True
                        test_result['evidence'] = f"Negative amount scored as low risk: {risk_score}"
                        logger.warning(f"Amount validation bypass: {amount}")
                    
                    test_result['risk_score'] = risk_score
                    test_result['decision'] = decision
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing amount manipulation: {e}")
        
        # Test 2: IP address spoofing and geolocation bypass
        suspicious_ips = [
            "0.0.0.0",
            "127.0.0.1",
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",  # Private IP ranges
            "'; DROP TABLE blocked_ips; --",  # SQL injection
            "1.1.1.1' OR '1'='1",
            "256.256.256.256",  # Invalid IP
        ]
        
        for ip in suspicious_ips:
            test_data = {
                "transactionId": str(uuid.uuid4()),
                "userId": "security-test-user",
                "amount": 1000.00,
                "currency": "USD",
                "merchantId": "test-merchant",
                "ipAddress": ip,
                "deviceFingerprint": str(uuid.uuid4()),
                "timestamp": datetime.now().isoformat()
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'IP_SPOOFING',
                    'payload': ip,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                if response.status_code == 200:
                    response_data = response.json()
                    risk_score = response_data.get('riskScore', 0)
                    
                    # Private IPs and localhost should be flagged as high risk
                    if ip in ["127.0.0.1", "0.0.0.0"] and risk_score < 0.8:
                        test_result['vulnerable'] = True
                        test_result['evidence'] = f"Localhost/invalid IP scored as low risk: {risk_score}"
                        logger.warning(f"IP validation bypass: {ip}")
                    
                    test_result['risk_score'] = risk_score
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing IP spoofing: {e}")
        
        # Test 3: Device fingerprint manipulation
        fingerprint_tests = [
            "",  # Empty fingerprint
            "A" * 10000,  # Oversized fingerprint
            "<script>alert('XSS')</script>",  # XSS attempt
            "../../etc/passwd",  # Path traversal
            str(uuid.uuid4()) + "'; DROP TABLE device_fingerprints; --"  # SQL injection
        ]
        
        for fingerprint in fingerprint_tests:
            test_data = {
                "transactionId": str(uuid.uuid4()),
                "userId": "security-test-user",
                "amount": 1000.00,
                "currency": "USD",
                "merchantId": "test-merchant",
                "ipAddress": "203.0.113.1",  # Test IP
                "deviceFingerprint": fingerprint,
                "timestamp": datetime.now().isoformat()
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'DEVICE_FINGERPRINT_MANIPULATION',
                    'payload': fingerprint[:100] + "..." if len(fingerprint) > 100 else fingerprint,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                # Check for SQL injection indicators
                if response.status_code == 500:
                    response_text = response.text.lower()
                    sql_errors = ['sql error', 'mysql', 'postgresql', 'syntax error']
                    if any(error in response_text for error in sql_errors):
                        test_result['vulnerable'] = True
                        test_result['evidence'] = "SQL error in response"
                        logger.warning(f"SQL injection vulnerability in device fingerprint: {fingerprint}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing device fingerprint manipulation: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_ml_model_evasion(self):
        """Test ML model evasion techniques"""
        logger.info("Testing ML model evasion...")
        
        test_results = []
        endpoint = f"{self.target_url}/api/v1/fraud/check"
        
        # Test known fraud patterns that should be caught
        known_fraud_patterns = [
            {
                "pattern": "velocity_attack",
                "data": {
                    "transactionId": str(uuid.uuid4()),
                    "userId": "repeat-offender-123",
                    "amount": 50.00,
                    "currency": "USD",
                    "merchantId": "high-risk-merchant",
                    "ipAddress": "203.0.113.1",
                    "deviceFingerprint": "known-fraud-device",
                    "timestamp": datetime.now().isoformat()
                }
            },
            {
                "pattern": "amount_structuring",
                "data": {
                    "transactionId": str(uuid.uuid4()),
                    "userId": "structurer-456",
                    "amount": 9999.99,  # Just below reporting threshold
                    "currency": "USD",
                    "merchantId": "cash-intensive-business",
                    "ipAddress": "203.0.113.2",
                    "deviceFingerprint": str(uuid.uuid4()),
                    "timestamp": datetime.now().isoformat()
                }
            },
            {
                "pattern": "geographic_anomaly",
                "data": {
                    "transactionId": str(uuid.uuid4()),
                    "userId": "traveler-789",
                    "amount": 5000.00,
                    "currency": "USD",
                    "merchantId": "foreign-merchant",
                    "ipAddress": "1.2.3.4",  # Foreign IP
                    "deviceFingerprint": str(uuid.uuid4()),
                    "timestamp": datetime.now().isoformat()
                }
            }
        ]
        
        for pattern_test in known_fraud_patterns:
            pattern_name = pattern_test["pattern"]
            test_data = pattern_test["data"]
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': f'ML_MODEL_EVASION_{pattern_name.upper()}',
                    'pattern': pattern_name,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                if response.status_code == 200:
                    response_data = response.json()
                    risk_score = response_data.get('riskScore', 0)
                    decision = response_data.get('decision', 'UNKNOWN')
                    
                    # Known fraud patterns should score high risk
                    if risk_score < 0.7:  # Threshold for high risk
                        test_result['vulnerable'] = True
                        test_result['evidence'] = f"Known fraud pattern scored low risk: {risk_score}"
                        logger.warning(f"ML model evasion detected for {pattern_name}: score {risk_score}")
                    
                    test_result['risk_score'] = risk_score
                    test_result['decision'] = decision
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing ML model evasion for {pattern_name}: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_timing_attacks(self):
        """Test for timing attack vulnerabilities"""
        logger.info("Testing timing attacks...")
        
        endpoint = f"{self.target_url}/api/v1/fraud/check"
        
        # Test timing differences for blocked vs allowed transactions
        blocked_user_times = []
        normal_user_times = []
        
        # Known blocked user (should be in blocked list)
        blocked_user_data = {
            "transactionId": str(uuid.uuid4()),
            "userId": "known-blocked-user",
            "amount": 100.00,
            "currency": "USD",
            "merchantId": "test-merchant",
            "ipAddress": "203.0.113.1",
            "deviceFingerprint": str(uuid.uuid4()),
            "timestamp": datetime.now().isoformat()
        }
        
        # Normal user
        normal_user_data = {
            "transactionId": str(uuid.uuid4()),
            "userId": f"normal-user-{uuid.uuid4()}",
            "amount": 100.00,
            "currency": "USD",
            "merchantId": "test-merchant",
            "ipAddress": "203.0.113.1",
            "deviceFingerprint": str(uuid.uuid4()),
            "timestamp": datetime.now().isoformat()
        }
        
        # Measure timing for blocked user
        for i in range(20):
            blocked_user_data["transactionId"] = str(uuid.uuid4())
            start_time = time.time()
            
            try:
                response = requests.post(
                    endpoint,
                    json=blocked_user_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                end_time = time.time()
                blocked_user_times.append((end_time - start_time) * 1000)  # Convert to ms
                
            except Exception as e:
                logger.error(f"Error in timing test (blocked user): {e}")
        
        # Measure timing for normal user
        for i in range(20):
            normal_user_data["transactionId"] = str(uuid.uuid4())
            normal_user_data["userId"] = f"normal-user-{uuid.uuid4()}"
            start_time = time.time()
            
            try:
                response = requests.post(
                    endpoint,
                    json=normal_user_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                end_time = time.time()
                normal_user_times.append((end_time - start_time) * 1000)  # Convert to ms
                
            except Exception as e:
                logger.error(f"Error in timing test (normal user): {e}")
        
        # Analyze timing differences
        if blocked_user_times and normal_user_times:
            blocked_avg = statistics.mean(blocked_user_times)
            normal_avg = statistics.mean(normal_user_times)
            time_difference = abs(blocked_avg - normal_avg)
            
            timing_analysis = {
                'blocked_user_avg_ms': blocked_avg,
                'normal_user_avg_ms': normal_avg,
                'time_difference_ms': time_difference,
                'vulnerable': time_difference > 50  # Significant timing difference
            }
            
            if timing_analysis['vulnerable']:
                logger.warning(f"Timing attack vulnerability detected: {time_difference:.2f}ms difference")
            
            self.results['timing_analysis'] = timing_analysis
            
            test_result = {
                'test': 'TIMING_ATTACK',
                'time_difference_ms': time_difference,
                'vulnerable': timing_analysis['vulnerable'],
                'evidence': f"Blocked user avg: {blocked_avg:.2f}ms, Normal user avg: {normal_avg:.2f}ms"
            }
            
            self.results['tests'].append(test_result)
            return timing_analysis
        
        return None
    
    def test_sanctions_screening_bypass(self):
        """Test sanctions screening for bypass vulnerabilities"""
        logger.info("Testing sanctions screening bypass...")
        
        test_results = []
        endpoint = f"{self.target_url}/api/v1/fraud/sanctions-check"
        
        # Test various name manipulation techniques
        bypass_techniques = [
            "John Smith",  # Common name
            "JOHN SMITH",  # Case variation
            "John  Smith",  # Extra spaces
            "J0hn Smith",  # Character substitution
            "John\tSmith",  # Tab character
            "John\nSmith",  # Newline character
            "Jöhn Smith",  # Unicode variation
            "Smith, John",  # Name order change
            "John (Smith)",  # Parentheses
            "John 'Smith'",  # Quotes
            ""; DROP TABLE sanctions_watchlist; --",  # SQL injection
        ]
        
        for name in bypass_techniques:
            test_data = {
                "entityId": str(uuid.uuid4()),
                "entityType": "USER",
                "fullName": name,
                "country": "US"
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'SANCTIONS_SCREENING_BYPASS',
                    'payload': name,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                if response.status_code == 200:
                    response_data = response.json()
                    is_match = response_data.get('isMatch', False)
                    confidence = response_data.get('confidence', 0)
                    
                    # If this is a known sanctioned name variant, it should be caught
                    # For testing purposes, assume "John Smith" variations should be flagged
                    if "john" in name.lower() and "smith" in name.lower() and not is_match:
                        test_result['vulnerable'] = True
                        test_result['evidence'] = f"Name variation not detected: {name}"
                        logger.warning(f"Sanctions screening bypass: {name}")
                    
                    test_result['is_match'] = is_match
                    test_result['confidence'] = confidence
                
                # Check for SQL injection
                if response.status_code == 500:
                    response_text = response.text.lower()
                    sql_errors = ['sql error', 'mysql', 'postgresql', 'syntax error']
                    if any(error in response_text for error in sql_errors):
                        test_result['vulnerable'] = True
                        test_result['evidence'] = "SQL injection vulnerability detected"
                        logger.warning(f"SQL injection in sanctions screening: {name}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing sanctions screening bypass: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_data_leakage(self):
        """Test for sensitive data leakage in responses"""
        logger.info("Testing data leakage...")
        
        test_results = []
        
        # Test fraud check response for sensitive data
        endpoint = f"{self.target_url}/api/v1/fraud/check"
        test_data = {
            "transactionId": str(uuid.uuid4()),
            "userId": "data-leakage-test",
            "amount": 100.00,
            "currency": "USD",
            "merchantId": "test-merchant",
            "ipAddress": "203.0.113.1",
            "deviceFingerprint": str(uuid.uuid4()),
            "timestamp": datetime.now().isoformat()
        }
        
        try:
            response = requests.post(
                endpoint,
                json=test_data,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            
            test_result = {
                'test': 'DATA_LEAKAGE_FRAUD_RESPONSE',
                'status_code': response.status_code,
                'vulnerable': False,
                'leaked_data': []
            }
            
            if response.status_code == 200:
                response_text = response.text.lower()
                
                # Check for sensitive data patterns
                sensitive_patterns = [
                    'password',
                    'secret',
                    'private',
                    'internal',
                    'debug',
                    'model_weights',
                    'algorithm',
                    'threshold',
                    'sql',
                    'database'
                ]
                
                for pattern in sensitive_patterns:
                    if pattern in response_text:
                        test_result['vulnerable'] = True
                        test_result['leaked_data'].append(pattern)
                        logger.warning(f"Sensitive data leaked in response: {pattern}")
            
            test_results.append(test_result)
            
        except Exception as e:
            logger.error(f"Error testing data leakage: {e}")
        
        # Test error responses for information disclosure
        malformed_requests = [
            {},  # Empty request
            {"invalid": "data"},  # Invalid fields
            {"transactionId": "invalid-uuid"},  # Invalid UUID
        ]
        
        for malformed_data in malformed_requests:
            try:
                response = requests.post(
                    endpoint,
                    json=malformed_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'ERROR_INFORMATION_DISCLOSURE',
                    'payload': str(malformed_data),
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                if response.status_code >= 400:
                    response_text = response.text.lower()
                    
                    # Check for stack traces, file paths, etc.
                    disclosure_patterns = [
                        'stack trace',
                        'exception',
                        'error at line',
                        'file not found',
                        '/home/',
                        '/opt/',
                        'java.',
                        'springframework'
                    ]
                    
                    for pattern in disclosure_patterns:
                        if pattern in response_text:
                            test_result['vulnerable'] = True
                            test_result['evidence'] = f"Information disclosure: {pattern}"
                            logger.warning(f"Information disclosure in error response: {pattern}")
                            break
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing information disclosure: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def run_active_scan(self):
        """Run OWASP ZAP active security scan"""
        logger.info("Starting OWASP ZAP active scan for fraud detection...")
        
        # Configure authentication
        if self.session_token:
            self.zap.replacer.add_rule(
                description="Fraud Auth Token",
                enabled=True,
                matchtype="REQ_HEADER",
                matchregex=False,
                replacement=f"Bearer {self.session_token}",
                matchstring="Authorization",
                initiators=""
            )
        
        # Spider the application first
        scan_id = self.zap.spider.scan(self.target_url)
        while int(self.zap.spider.status(scan_id)) < 100:
            logger.info(f"Spider progress: {self.zap.spider.status(scan_id)}%")
            time.sleep(5)
        
        # Run active scan
        scan_id = self.zap.ascan.scan(self.target_url)
        while int(self.zap.ascan.status(scan_id)) < 100:
            logger.info(f"Active scan progress: {self.zap.ascan.status(scan_id)}%")
            time.sleep(10)
        
        # Get alerts
        alerts = self.zap.core.alerts()
        
        for alert in alerts:
            vulnerability = {
                'name': alert['alert'],
                'risk': alert['risk'],
                'confidence': alert['confidence'],
                'url': alert['url'],
                'param': alert['param'],
                'evidence': alert['evidence'],
                'description': alert['description'],
                'solution': alert['solution']
            }
            self.results['vulnerabilities'].append(vulnerability)
        
        logger.info(f"Found {len(alerts)} security issues in fraud detection service")
        return alerts
    
    def generate_report(self):
        """Generate comprehensive security report"""
        logger.info("Generating fraud detection security report...")
        
        # Calculate summary statistics
        total_tests = len(self.results['tests'])
        vulnerable_tests = len([t for t in self.results['tests'] if t.get('vulnerable', False)])
        
        high_risk_vulns = len([v for v in self.results['vulnerabilities'] if v['risk'] == 'High'])
        medium_risk_vulns = len([v for v in self.results['vulnerabilities'] if v['risk'] == 'Medium'])
        low_risk_vulns = len([v for v in self.results['vulnerabilities'] if v['risk'] == 'Low'])
        
        self.results['summary'] = {
            'total_tests': total_tests,
            'vulnerable_tests': vulnerable_tests,
            'vulnerability_rate': (vulnerable_tests / total_tests * 100) if total_tests > 0 else 0,
            'high_risk_vulnerabilities': high_risk_vulns,
            'medium_risk_vulnerabilities': medium_risk_vulns,
            'low_risk_vulnerabilities': low_risk_vulns,
            'total_vulnerabilities': len(self.results['vulnerabilities']),
            'timing_attack_detected': self.results['timing_analysis'].get('vulnerable', False) if self.results['timing_analysis'] else False
        }
        
        # Save detailed JSON report
        report_file = f"security-testing/reports/fraud-detection-security-{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_file, 'w') as f:
            json.dump(self.results, f, indent=2, default=str)
        
        logger.info(f"Security report saved to {report_file}")
        
        # Print summary
        print("\n" + "="*70)
        print("FRAUD DETECTION SERVICE SECURITY ASSESSMENT SUMMARY")
        print("="*70)
        print(f"Target: {self.target_url}")
        print(f"Tests Conducted: {total_tests}")
        print(f"Vulnerable Tests: {vulnerable_tests} ({self.results['summary']['vulnerability_rate']:.1f}%)")
        print(f"")
        print(f"Critical Security Issues:")
        print(f"  ML Model Evasion: {len([t for t in self.results['tests'] if 'ML_MODEL_EVASION' in t.get('test', '') and t.get('vulnerable', False)])}")
        print(f"  Timing Attacks: {'DETECTED' if self.results['summary']['timing_attack_detected'] else 'NOT DETECTED'}")
        print(f"  Data Leakage: {len([t for t in self.results['tests'] if 'DATA_LEAKAGE' in t.get('test', '') and t.get('vulnerable', False)])}")
        print(f"")
        print(f"General Vulnerabilities:")
        print(f"  High Risk: {high_risk_vulns}")
        print(f"  Medium Risk: {medium_risk_vulns}")
        print(f"  Low Risk: {low_risk_vulns}")
        print(f"  Total: {len(self.results['vulnerabilities'])}")
        print("="*70)
        
        return self.results

def main():
    """Main execution function"""
    logger.info("Starting Fraud Detection Service Security Testing...")
    
    # Initialize tester
    tester = FraudDetectionSecurityTester()
    
    # Setup authentication
    if not tester.setup_authentication():
        logger.error("Failed to setup authentication, continuing with limited tests...")
    
    # Run fraud detection specific tests
    tester.test_fraud_check_tampering()
    tester.test_ml_model_evasion()
    tester.test_timing_attacks()
    tester.test_sanctions_screening_bypass()
    tester.test_data_leakage()
    
    # Run OWASP ZAP active scan
    tester.run_active_scan()
    
    # Generate report
    results = tester.generate_report()
    
    logger.info("Fraud Detection Service security testing completed")
    
    return results

if __name__ == "__main__":
    main()