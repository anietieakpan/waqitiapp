#!/usr/bin/env python3
"""
OWASP ZAP Security Penetration Testing - Payment Service
========================================================
Comprehensive security testing for Waqiti Payment Service endpoints.
Tests: Authentication, Authorization, Input Validation, SQL Injection, XSS, CSRF, etc.
"""

import time
import requests
import logging
from zapv2 import ZAPv2
import json
import uuid
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('security-testing/reports/payment-service-security.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class PaymentServiceSecurityTester:
    def __init__(self, zap_proxy_url='http://127.0.0.1:8080', target_url='http://localhost:8081'):
        self.zap = ZAPv2(proxies={'http': zap_proxy_url, 'https': zap_proxy_url})
        self.target_url = target_url
        self.session_token = None
        self.results = {
            'timestamp': datetime.now().isoformat(),
            'target': target_url,
            'tests': [],
            'vulnerabilities': [],
            'summary': {}
        }
        
    def setup_authentication(self):
        """Setup authentication for protected endpoints"""
        logger.info("Setting up authentication...")
        
        try:
            # Authenticate with a test user
            auth_payload = {
                "username": "security-test-user",
                "password": "SecureTest123!",
                "role": "PAYMENT_PROCESSOR"
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
    
    def spider_application(self):
        """Discover all application endpoints"""
        logger.info("Starting application spidering...")
        
        scan_id = self.zap.spider.scan(self.target_url)
        
        # Wait for spider to complete
        while int(self.zap.spider.status(scan_id)) < 100:
            logger.info(f"Spider progress: {self.zap.spider.status(scan_id)}%")
            time.sleep(5)
        
        logger.info("Spider scan completed")
        
        # Get discovered URLs
        urls = self.zap.core.urls()
        logger.info(f"Discovered {len(urls)} URLs")
        
        return urls
    
    def test_payment_creation_vulnerabilities(self):
        """Test payment creation endpoint for security vulnerabilities"""
        logger.info("Testing payment creation endpoint...")
        
        test_results = []
        endpoint = f"{self.target_url}/api/v1/payments"
        
        # Test 1: SQL Injection in amount field
        sql_injection_payloads = [
            "100'; DROP TABLE payments; --",
            "100 OR 1=1",
            "100 UNION SELECT * FROM users",
            "100'; INSERT INTO payments (amount) VALUES (999999); --"
        ]
        
        for payload in sql_injection_payloads:
            test_data = {
                "userId": str(uuid.uuid4()),
                "amount": payload,
                "currency": "USD",
                "paymentMethod": "CARD",
                "provider": "STRIPE",
                "idempotencyKey": str(uuid.uuid4())
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'SQL_INJECTION_AMOUNT',
                    'payload': payload,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                # Check for SQL injection indicators
                response_text = response.text.lower()
                sql_errors = ['sql error', 'mysql', 'postgresql', 'syntax error', 'ora-']
                
                if any(error in response_text for error in sql_errors):
                    test_result['vulnerable'] = True
                    test_result['evidence'] = response.text[:500]
                    logger.warning(f"Potential SQL injection vulnerability detected: {payload}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing SQL injection: {e}")
        
        # Test 2: XSS in description field
        xss_payloads = [
            "<script>alert('XSS')</script>",
            "javascript:alert('XSS')",
            "<img src=x onerror=alert('XSS')>",
            "';alert('XSS');//"
        ]
        
        for payload in xss_payloads:
            test_data = {
                "userId": str(uuid.uuid4()),
                "amount": 100.00,
                "currency": "USD",
                "paymentMethod": "CARD",
                "provider": "STRIPE",
                "description": payload,
                "idempotencyKey": str(uuid.uuid4())
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'XSS_DESCRIPTION',
                    'payload': payload,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                # Check if payload is reflected without encoding
                if payload in response.text:
                    test_result['vulnerable'] = True
                    test_result['evidence'] = "Payload reflected without encoding"
                    logger.warning(f"Potential XSS vulnerability detected: {payload}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing XSS: {e}")
        
        # Test 3: Authentication bypass attempts
        unauthorized_test_data = {
            "userId": str(uuid.uuid4()),
            "amount": 999999.99,
            "currency": "USD",
            "paymentMethod": "CARD",
            "provider": "STRIPE",
            "idempotencyKey": str(uuid.uuid4())
        }
        
        # Test without authentication
        try:
            response = requests.post(endpoint, json=unauthorized_test_data, timeout=10)
            
            test_result = {
                'test': 'AUTHENTICATION_BYPASS',
                'payload': 'No authorization header',
                'status_code': response.status_code,
                'vulnerable': response.status_code == 200
            }
            
            if response.status_code == 200:
                test_result['evidence'] = "Endpoint accessible without authentication"
                logger.warning("Authentication bypass vulnerability detected")
            
            test_results.append(test_result)
            
        except Exception as e:
            logger.error(f"Error testing authentication bypass: {e}")
        
        # Test 4: Amount manipulation (negative amounts, zero amounts)
        manipulation_payloads = [
            -100.00,
            0.00,
            0.001,  # Below minimum
            999999999999.99,  # Extremely large amount
            float('inf'),
            "NaN"
        ]
        
        for amount in manipulation_payloads:
            test_data = {
                "userId": str(uuid.uuid4()),
                "amount": amount,
                "currency": "USD",
                "paymentMethod": "CARD",
                "provider": "STRIPE",
                "idempotencyKey": str(uuid.uuid4())
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
                    'vulnerable': response.status_code == 200 and amount <= 0
                }
                
                if test_result['vulnerable']:
                    test_result['evidence'] = f"Accepted invalid amount: {amount}"
                    logger.warning(f"Amount validation bypass: {amount}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing amount manipulation: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_payment_status_vulnerabilities(self):
        """Test payment status endpoints for authorization vulnerabilities"""
        logger.info("Testing payment status endpoints...")
        
        test_results = []
        
        # Test IDOR (Insecure Direct Object Reference)
        test_payment_ids = [
            "00000000-0000-0000-0000-000000000001",  # Sequential
            "11111111-1111-1111-1111-111111111111",  # Predictable
            "../../../etc/passwd",  # Path traversal
            "'; SELECT * FROM payments; --",  # SQL injection
            "<script>alert('XSS')</script>"  # XSS
        ]
        
        for payment_id in test_payment_ids:
            endpoint = f"{self.target_url}/api/v1/payments/{payment_id}"
            
            try:
                # Test with valid token
                response = requests.get(
                    endpoint,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'IDOR_PAYMENT_ACCESS',
                    'payload': payment_id,
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                # Check for IDOR vulnerability
                if response.status_code == 200:
                    response_data = response.json() if response.headers.get('content-type', '').startswith('application/json') else {}
                    if 'id' in response_data and response_data['id'] != payment_id:
                        test_result['vulnerable'] = True
                        test_result['evidence'] = "Accessing payment belonging to different user"
                        logger.warning(f"IDOR vulnerability detected: {payment_id}")
                
                test_results.append(test_result)
                
                # Test without authentication
                response_unauth = requests.get(endpoint, timeout=10)
                
                test_result_unauth = {
                    'test': 'UNAUTHORIZED_PAYMENT_ACCESS',
                    'payload': payment_id,
                    'status_code': response_unauth.status_code,
                    'vulnerable': response_unauth.status_code == 200
                }
                
                if test_result_unauth['vulnerable']:
                    test_result_unauth['evidence'] = "Payment data accessible without authentication"
                    logger.warning(f"Unauthorized access vulnerability: {payment_id}")
                
                test_results.append(test_result_unauth)
                
            except Exception as e:
                logger.error(f"Error testing payment status vulnerabilities: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_rate_limiting(self):
        """Test rate limiting implementation"""
        logger.info("Testing rate limiting...")
        
        endpoint = f"{self.target_url}/api/v1/payments"
        headers = {"Authorization": f"Bearer {self.session_token}"}
        
        # Send rapid requests
        responses = []
        for i in range(100):  # Send 100 requests rapidly
            test_data = {
                "userId": str(uuid.uuid4()),
                "amount": 10.00,
                "currency": "USD",
                "paymentMethod": "CARD",
                "provider": "STRIPE",
                "idempotencyKey": str(uuid.uuid4())
            }
            
            try:
                response = requests.post(endpoint, json=test_data, headers=headers, timeout=5)
                responses.append(response.status_code)
                
                if response.status_code == 429:  # Too Many Requests
                    logger.info(f"Rate limiting triggered after {i+1} requests")
                    break
                    
            except Exception as e:
                logger.error(f"Error in rate limiting test: {e}")
                break
        
        # Analyze results
        rate_limit_triggered = 429 in responses
        test_result = {
            'test': 'RATE_LIMITING',
            'requests_sent': len(responses),
            'rate_limit_triggered': rate_limit_triggered,
            'vulnerable': not rate_limit_triggered
        }
        
        if not rate_limit_triggered:
            test_result['evidence'] = f"No rate limiting after {len(responses)} requests"
            logger.warning("Rate limiting not implemented or ineffective")
        
        self.results['tests'].append(test_result)
        return test_result
    
    def test_csrf_protection(self):
        """Test CSRF protection mechanisms"""
        logger.info("Testing CSRF protection...")
        
        # Test payment creation without CSRF token
        endpoint = f"{self.target_url}/api/v1/payments"
        
        test_data = {
            "userId": str(uuid.uuid4()),
            "amount": 100.00,
            "currency": "USD",
            "paymentMethod": "CARD",
            "provider": "STRIPE",
            "idempotencyKey": str(uuid.uuid4())
        }
        
        # Remove CSRF headers and use different origin
        malicious_headers = {
            "Authorization": f"Bearer {self.session_token}",
            "Origin": "https://malicious-site.com",
            "Referer": "https://malicious-site.com/attack.html"
        }
        
        try:
            response = requests.post(endpoint, json=test_data, headers=malicious_headers, timeout=10)
            
            test_result = {
                'test': 'CSRF_PROTECTION',
                'status_code': response.status_code,
                'vulnerable': response.status_code == 200
            }
            
            if response.status_code == 200:
                test_result['evidence'] = "Request accepted from different origin without CSRF protection"
                logger.warning("CSRF vulnerability detected")
            
            self.results['tests'].append(test_result)
            return test_result
            
        except Exception as e:
            logger.error(f"Error testing CSRF protection: {e}")
            return None
    
    def test_input_validation(self):
        """Test comprehensive input validation"""
        logger.info("Testing input validation...")
        
        endpoint = f"{self.target_url}/api/v1/payments"
        test_results = []
        
        # Test oversized inputs
        oversized_tests = [
            ("userId", "A" * 10000),
            ("currency", "B" * 1000),
            ("paymentMethod", "C" * 5000),
            ("provider", "D" * 2000),
            ("description", "E" * 50000)
        ]
        
        for field, oversized_value in oversized_tests:
            test_data = {
                "userId": str(uuid.uuid4()),
                "amount": 100.00,
                "currency": "USD",
                "paymentMethod": "CARD",
                "provider": "STRIPE",
                "idempotencyKey": str(uuid.uuid4())
            }
            test_data[field] = oversized_value
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': f'INPUT_SIZE_VALIDATION_{field.upper()}',
                    'payload_size': len(oversized_value),
                    'status_code': response.status_code,
                    'vulnerable': response.status_code == 200
                }
                
                if response.status_code == 200:
                    test_result['evidence'] = f"Accepted oversized {field}: {len(oversized_value)} characters"
                    logger.warning(f"Input size validation bypass: {field}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing input validation for {field}: {e}")
        
        # Test null byte injection
        null_byte_tests = [
            "test\x00.jpg",
            "normal\x00<script>alert('XSS')</script>",
            "file\x00.txt"
        ]
        
        for payload in null_byte_tests:
            test_data = {
                "userId": str(uuid.uuid4()),
                "amount": 100.00,
                "currency": "USD",
                "paymentMethod": "CARD",
                "provider": "STRIPE",
                "description": payload,
                "idempotencyKey": str(uuid.uuid4())
            }
            
            try:
                response = requests.post(
                    endpoint,
                    json=test_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'NULL_BYTE_INJECTION',
                    'payload': repr(payload),
                    'status_code': response.status_code,
                    'vulnerable': response.status_code == 200 and '\x00' in payload
                }
                
                if test_result['vulnerable']:
                    test_result['evidence'] = "Null byte in input was processed"
                    logger.warning(f"Null byte injection vulnerability: {repr(payload)}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing null byte injection: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def run_active_scan(self):
        """Run OWASP ZAP active security scan"""
        logger.info("Starting OWASP ZAP active scan...")
        
        # Include authentication in scan
        if self.session_token:
            self.zap.replacer.add_rule(
                description="Auth Token",
                enabled=True,
                matchtype="REQ_HEADER",
                matchregex=False,
                replacement=f"Bearer {self.session_token}",
                matchstring="Authorization",
                initiators=""
            )
        
        scan_id = self.zap.ascan.scan(self.target_url)
        
        # Wait for active scan to complete
        while int(self.zap.ascan.status(scan_id)) < 100:
            logger.info(f"Active scan progress: {self.zap.ascan.status(scan_id)}%")
            time.sleep(10)
        
        logger.info("Active scan completed")
        
        # Get scan results
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
        
        logger.info(f"Found {len(alerts)} security issues")
        return alerts
    
    def generate_report(self):
        """Generate comprehensive security report"""
        logger.info("Generating security report...")
        
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
            'total_vulnerabilities': len(self.results['vulnerabilities'])
        }
        
        # Save detailed JSON report
        report_file = f"security-testing/reports/payment-service-security-{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_file, 'w') as f:
            json.dump(self.results, f, indent=2, default=str)
        
        logger.info(f"Security report saved to {report_file}")
        
        # Print summary
        print("\n" + "="*60)
        print("PAYMENT SERVICE SECURITY ASSESSMENT SUMMARY")
        print("="*60)
        print(f"Target: {self.target_url}")
        print(f"Tests Conducted: {total_tests}")
        print(f"Vulnerable Tests: {vulnerable_tests} ({self.results['summary']['vulnerability_rate']:.1f}%)")
        print(f"")
        print(f"Vulnerabilities Found:")
        print(f"  High Risk: {high_risk_vulns}")
        print(f"  Medium Risk: {medium_risk_vulns}")
        print(f"  Low Risk: {low_risk_vulns}")
        print(f"  Total: {len(self.results['vulnerabilities'])}")
        print("="*60)
        
        return self.results

def main():
    """Main execution function"""
    logger.info("Starting Payment Service Security Testing...")
    
    # Initialize tester
    tester = PaymentServiceSecurityTester()
    
    # Setup authentication
    if not tester.setup_authentication():
        logger.error("Failed to setup authentication, continuing with limited tests...")
    
    # Run spider to discover endpoints
    tester.spider_application()
    
    # Run custom security tests
    tester.test_payment_creation_vulnerabilities()
    tester.test_payment_status_vulnerabilities()
    tester.test_rate_limiting()
    tester.test_csrf_protection()
    tester.test_input_validation()
    
    # Run OWASP ZAP active scan
    tester.run_active_scan()
    
    # Generate report
    results = tester.generate_report()
    
    logger.info("Payment Service security testing completed")
    
    return results

if __name__ == "__main__":
    main()