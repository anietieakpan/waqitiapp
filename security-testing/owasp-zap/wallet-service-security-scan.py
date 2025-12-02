#!/usr/bin/env python3
"""
OWASP ZAP Security Penetration Testing - Wallet Service
======================================================
Critical security testing for wallet and balance management endpoints.
Focus: Balance manipulation, double-spending, IDOR, race conditions, transaction integrity.
"""

import time
import requests
import logging
import threading
import concurrent.futures
from zapv2 import ZAPv2
import json
import uuid
from datetime import datetime
from decimal import Decimal

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('security-testing/reports/wallet-service-security.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

class WalletServiceSecurityTester:
    def __init__(self, zap_proxy_url='http://127.0.0.1:8080', target_url='http://localhost:8082'):
        self.zap = ZAPv2(proxies={'http': zap_proxy_url, 'https': zap_proxy_url})
        self.target_url = target_url
        self.session_token = None
        self.test_wallet_id = None
        self.results = {
            'timestamp': datetime.now().isoformat(),
            'target': target_url,
            'tests': [],
            'vulnerabilities': [],
            'race_condition_tests': [],
            'summary': {}
        }
        
    def setup_authentication(self):
        """Setup authentication for wallet service"""
        logger.info("Setting up authentication...")
        
        try:
            auth_payload = {
                "username": "wallet-security-tester",
                "password": "SecureTest123!",
                "role": "WALLET_MANAGER"
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
    
    def setup_test_wallet(self):
        """Create a test wallet for security testing"""
        logger.info("Setting up test wallet...")
        
        endpoint = f"{self.target_url}/api/v1/wallets"
        wallet_data = {
            "userId": "security-test-user",
            "currency": "USD",
            "initialBalance": 10000.00  # Start with sufficient balance
        }
        
        try:
            response = requests.post(
                endpoint,
                json=wallet_data,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            
            if response.status_code == 201:
                wallet_info = response.json()
                self.test_wallet_id = wallet_info.get('id')
                logger.info(f"Test wallet created: {self.test_wallet_id}")
                return True
            else:
                logger.warning(f"Failed to create test wallet: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"Test wallet setup failed: {e}")
            return False
    
    def test_balance_manipulation(self):
        """Test balance manipulation vulnerabilities"""
        logger.info("Testing balance manipulation...")
        
        test_results = []
        
        # Test 1: Direct balance update attempts
        balance_endpoint = f"{self.target_url}/api/v1/wallets/{self.test_wallet_id}/balance"
        
        manipulation_payloads = [
            {"balance": 999999999.99},  # Extremely high balance
            {"balance": -1000.00},  # Negative balance
            {"balance": "999999' OR '1'='1"},  # SQL injection
            {"balance": float('inf')},  # Infinite balance
            {"balance": None},  # Null balance
        ]
        
        for payload in manipulation_payloads:
            try:
                response = requests.put(
                    balance_endpoint,
                    json=payload,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'DIRECT_BALANCE_MANIPULATION',
                    'payload': str(payload),
                    'status_code': response.status_code,
                    'vulnerable': response.status_code == 200
                }
                
                if response.status_code == 200:
                    test_result['evidence'] = "Direct balance update was accepted"
                    logger.warning(f"Balance manipulation vulnerability: {payload}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing balance manipulation: {e}")
        
        # Test 2: Amount parameter manipulation in transfers
        transfer_endpoint = f"{self.target_url}/api/v1/wallets/transfer"
        
        amount_manipulation_tests = [
            -1000.00,  # Negative transfer (should reverse money flow)
            0.001,  # Below minimum
            999999999.99,  # Extremely large amount
            float('inf'),  # Infinite amount
            "1000.00'; DROP TABLE wallets; --",  # SQL injection
        ]
        
        for amount in amount_manipulation_tests:
            transfer_data = {
                "sourceWalletId": self.test_wallet_id,
                "destinationWalletId": str(uuid.uuid4()),  # Non-existent wallet
                "amount": amount,
                "currency": "USD",
                "idempotencyKey": str(uuid.uuid4())
            }
            
            try:
                response = requests.post(
                    transfer_endpoint,
                    json=transfer_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'TRANSFER_AMOUNT_MANIPULATION',
                    'payload': str(amount),
                    'status_code': response.status_code,
                    'vulnerable': False
                }
                
                # Negative amounts should always be rejected
                if amount < 0 and response.status_code == 200:
                    test_result['vulnerable'] = True
                    test_result['evidence'] = f"Negative transfer amount accepted: {amount}"
                    logger.warning(f"Negative transfer vulnerability: {amount}")
                
                # Check for SQL injection
                if response.status_code == 500:
                    response_text = response.text.lower()
                    sql_errors = ['sql error', 'mysql', 'postgresql', 'syntax error']
                    if any(error in response_text for error in sql_errors):
                        test_result['vulnerable'] = True
                        test_result['evidence'] = "SQL injection vulnerability detected"
                        logger.warning(f"SQL injection in transfer: {amount}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing transfer amount manipulation: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_idor_vulnerabilities(self):
        """Test Insecure Direct Object Reference vulnerabilities"""
        logger.info("Testing IDOR vulnerabilities...")
        
        test_results = []
        
        # Test accessing other users' wallets
        test_wallet_ids = [
            "00000000-0000-0000-0000-000000000001",  # Sequential ID
            "11111111-1111-1111-1111-111111111111",  # Predictable ID
            str(uuid.uuid4()),  # Random UUID
            "../../../etc/passwd",  # Path traversal
            "'; SELECT * FROM wallets; --",  # SQL injection
        ]
        
        for wallet_id in test_wallet_ids:
            # Test balance access
            balance_endpoint = f"{self.target_url}/api/v1/wallets/{wallet_id}/balance"
            
            try:
                response = requests.get(
                    balance_endpoint,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result = {
                    'test': 'IDOR_BALANCE_ACCESS',
                    'payload': wallet_id,
                    'status_code': response.status_code,
                    'vulnerable': response.status_code == 200
                }
                
                if response.status_code == 200:
                    test_result['evidence'] = "Unauthorized wallet balance access"
                    logger.warning(f"IDOR vulnerability - balance access: {wallet_id}")
                
                test_results.append(test_result)
                
                # Test transaction history access
                history_endpoint = f"{self.target_url}/api/v1/wallets/{wallet_id}/transactions"
                
                response_history = requests.get(
                    history_endpoint,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                
                test_result_history = {
                    'test': 'IDOR_TRANSACTION_HISTORY',
                    'payload': wallet_id,
                    'status_code': response_history.status_code,
                    'vulnerable': response_history.status_code == 200
                }
                
                if response_history.status_code == 200:
                    test_result_history['evidence'] = "Unauthorized transaction history access"
                    logger.warning(f"IDOR vulnerability - transaction history: {wallet_id}")
                
                test_results.append(test_result_history)
                
            except Exception as e:
                logger.error(f"Error testing IDOR for wallet {wallet_id}: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_race_conditions(self):
        """Test race condition vulnerabilities in concurrent transactions"""
        logger.info("Testing race condition vulnerabilities...")
        
        race_test_results = []
        
        # Get initial balance
        balance_endpoint = f"{self.target_url}/api/v1/wallets/{self.test_wallet_id}/balance"
        
        try:
            response = requests.get(
                balance_endpoint,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            
            if response.status_code != 200:
                logger.error("Failed to get initial balance for race condition test")
                return []
            
            initial_balance = Decimal(str(response.json().get('balance', 0)))
            logger.info(f"Initial balance for race condition test: {initial_balance}")
            
        except Exception as e:
            logger.error(f"Error getting initial balance: {e}")
            return []
        
        # Test 1: Concurrent withdrawals (attempt to create double spending)
        transfer_endpoint = f"{self.target_url}/api/v1/wallets/withdraw"
        withdrawal_amount = Decimal('100.00')
        num_concurrent_requests = 10
        
        def concurrent_withdrawal():
            withdrawal_data = {
                "walletId": self.test_wallet_id,
                "amount": float(withdrawal_amount),
                "currency": "USD",
                "idempotencyKey": str(uuid.uuid4()),  # Different idempotency keys
                "description": "Race condition test withdrawal"
            }
            
            try:
                response = requests.post(
                    transfer_endpoint,
                    json=withdrawal_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                return {
                    'status_code': response.status_code,
                    'response': response.json() if response.status_code == 200 else response.text,
                    'timestamp': time.time()
                }
            except Exception as e:
                return {'error': str(e), 'timestamp': time.time()}
        
        # Execute concurrent withdrawals
        logger.info(f"Executing {num_concurrent_requests} concurrent withdrawals...")
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=num_concurrent_requests) as executor:
            futures = [executor.submit(concurrent_withdrawal) for _ in range(num_concurrent_requests)]
            withdrawal_results = [future.result() for future in concurrent.futures.as_completed(futures)]
        
        # Analyze results
        successful_withdrawals = len([r for r in withdrawal_results if r.get('status_code') == 200])
        expected_balance = initial_balance - (withdrawal_amount * successful_withdrawals)
        
        # Check final balance
        try:
            time.sleep(2)  # Allow for eventual consistency
            response = requests.get(
                balance_endpoint,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            
            final_balance = Decimal(str(response.json().get('balance', 0)))
            balance_discrepancy = abs(final_balance - expected_balance)
            
            race_condition_test = {
                'test': 'CONCURRENT_WITHDRAWAL_RACE_CONDITION',
                'initial_balance': float(initial_balance),
                'successful_withdrawals': successful_withdrawals,
                'expected_final_balance': float(expected_balance),
                'actual_final_balance': float(final_balance),
                'balance_discrepancy': float(balance_discrepancy),
                'vulnerable': balance_discrepancy > Decimal('0.01'),  # Allow for minor rounding
                'withdrawal_results': withdrawal_results
            }
            
            if race_condition_test['vulnerable']:
                race_condition_test['evidence'] = f"Balance inconsistency: expected {expected_balance}, got {final_balance}"
                logger.warning(f"Race condition vulnerability detected: balance discrepancy of {balance_discrepancy}")
            
            race_test_results.append(race_condition_test)
            
        except Exception as e:
            logger.error(f"Error checking final balance after race condition test: {e}")
        
        # Test 2: Concurrent transfers with same idempotency key (should be idempotent)
        transfer_endpoint = f"{self.target_url}/api/v1/wallets/transfer"
        shared_idempotency_key = str(uuid.uuid4())
        transfer_amount = Decimal('50.00')
        
        def concurrent_transfer_same_key():
            transfer_data = {
                "sourceWalletId": self.test_wallet_id,
                "destinationWalletId": str(uuid.uuid4()),
                "amount": float(transfer_amount),
                "currency": "USD",
                "idempotencyKey": shared_idempotency_key,  # Same key for all requests
                "description": "Idempotency race condition test"
            }
            
            try:
                response = requests.post(
                    transfer_endpoint,
                    json=transfer_data,
                    headers={"Authorization": f"Bearer {self.session_token}"},
                    timeout=10
                )
                return {
                    'status_code': response.status_code,
                    'response': response.json() if response.status_code == 200 else response.text,
                    'timestamp': time.time()
                }
            except Exception as e:
                return {'error': str(e), 'timestamp': time.time()}
        
        # Execute concurrent transfers with same idempotency key
        logger.info("Testing idempotency with concurrent requests...")
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
            futures = [executor.submit(concurrent_transfer_same_key) for _ in range(5)]
            idempotency_results = [future.result() for future in concurrent.futures.as_completed(futures)]
        
        successful_transfers = len([r for r in idempotency_results if r.get('status_code') == 200])
        
        idempotency_test = {
            'test': 'IDEMPOTENCY_RACE_CONDITION',
            'concurrent_requests': 5,
            'successful_transfers': successful_transfers,
            'vulnerable': successful_transfers > 1,  # Should only succeed once
            'transfer_results': idempotency_results
        }
        
        if idempotency_test['vulnerable']:
            idempotency_test['evidence'] = f"Idempotency violation: {successful_transfers} transfers succeeded with same key"
            logger.warning(f"Idempotency race condition vulnerability: {successful_transfers} successful transfers")
        
        race_test_results.append(idempotency_test)
        
        self.results['race_condition_tests'] = race_test_results
        self.results['tests'].extend(race_test_results)
        return race_test_results
    
    def test_transaction_integrity(self):
        """Test transaction integrity and ACID properties"""
        logger.info("Testing transaction integrity...")
        
        test_results = []
        
        # Test 1: Atomic transaction failure handling
        transfer_endpoint = f"{self.target_url}/api/v1/wallets/transfer"
        
        # Attempt transfer to non-existent destination
        invalid_transfer_data = {
            "sourceWalletId": self.test_wallet_id,
            "destinationWalletId": "00000000-0000-0000-0000-000000000000",  # Non-existent
            "amount": 100.00,
            "currency": "USD",
            "idempotencyKey": str(uuid.uuid4())
        }
        
        # Get balance before failed transfer
        balance_endpoint = f"{self.target_url}/api/v1/wallets/{self.test_wallet_id}/balance"
        
        try:
            response_before = requests.get(
                balance_endpoint,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            balance_before = Decimal(str(response_before.json().get('balance', 0)))
            
            # Attempt the invalid transfer
            response_transfer = requests.post(
                transfer_endpoint,
                json=invalid_transfer_data,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            
            # Check balance after failed transfer
            response_after = requests.get(
                balance_endpoint,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            balance_after = Decimal(str(response_after.json().get('balance', 0)))
            
            integrity_test = {
                'test': 'TRANSACTION_ATOMICITY',
                'transfer_status_code': response_transfer.status_code,
                'balance_before': float(balance_before),
                'balance_after': float(balance_after),
                'balance_changed': balance_before != balance_after,
                'vulnerable': response_transfer.status_code != 200 and balance_before != balance_after
            }
            
            if integrity_test['vulnerable']:
                integrity_test['evidence'] = "Balance changed despite failed transfer"
                logger.warning("Transaction atomicity violation detected")
            
            test_results.append(integrity_test)
            
        except Exception as e:
            logger.error(f"Error testing transaction integrity: {e}")
        
        # Test 2: Insufficient balance handling
        large_transfer_data = {
            "sourceWalletId": self.test_wallet_id,
            "destinationWalletId": str(uuid.uuid4()),
            "amount": 999999999.99,  # Amount larger than available balance
            "currency": "USD",
            "idempotencyKey": str(uuid.uuid4())
        }
        
        try:
            response = requests.post(
                transfer_endpoint,
                json=large_transfer_data,
                headers={"Authorization": f"Bearer {self.session_token}"},
                timeout=10
            )
            
            insufficient_balance_test = {
                'test': 'INSUFFICIENT_BALANCE_HANDLING',
                'status_code': response.status_code,
                'vulnerable': response.status_code == 200
            }
            
            if insufficient_balance_test['vulnerable']:
                insufficient_balance_test['evidence'] = "Transfer succeeded despite insufficient balance"
                logger.warning("Insufficient balance validation bypass detected")
            
            test_results.append(insufficient_balance_test)
            
        except Exception as e:
            logger.error(f"Error testing insufficient balance handling: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def test_authorization_bypass(self):
        """Test authorization bypass vulnerabilities"""
        logger.info("Testing authorization bypass...")
        
        test_results = []
        
        # Test accessing wallet endpoints without authentication
        endpoints_to_test = [
            f"/api/v1/wallets/{self.test_wallet_id}/balance",
            f"/api/v1/wallets/{self.test_wallet_id}/transactions",
            "/api/v1/wallets/transfer",
            "/api/v1/wallets/withdraw"
        ]
        
        for endpoint in endpoints_to_test:
            full_url = f"{self.target_url}{endpoint}"
            
            # Test without any authentication
            try:
                if 'transfer' in endpoint or 'withdraw' in endpoint:
                    response = requests.post(
                        full_url,
                        json={"test": "data"},
                        timeout=10
                    )
                else:
                    response = requests.get(full_url, timeout=10)
                
                test_result = {
                    'test': 'AUTHORIZATION_BYPASS',
                    'endpoint': endpoint,
                    'status_code': response.status_code,
                    'vulnerable': response.status_code == 200
                }
                
                if test_result['vulnerable']:
                    test_result['evidence'] = "Endpoint accessible without authentication"
                    logger.warning(f"Authorization bypass: {endpoint}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing authorization bypass for {endpoint}: {e}")
        
        # Test with invalid/expired tokens
        invalid_tokens = [
            "Bearer invalid-token",
            "Bearer ",
            "Invalid-Format-Token",
            "Bearer " + "A" * 500,  # Oversized token
        ]
        
        for token in invalid_tokens:
            try:
                response = requests.get(
                    f"{self.target_url}/api/v1/wallets/{self.test_wallet_id}/balance",
                    headers={"Authorization": token},
                    timeout=10
                )
                
                test_result = {
                    'test': 'INVALID_TOKEN_HANDLING',
                    'token_type': token[:20] + "..." if len(token) > 20 else token,
                    'status_code': response.status_code,
                    'vulnerable': response.status_code == 200
                }
                
                if test_result['vulnerable']:
                    test_result['evidence'] = f"Invalid token accepted: {token[:20]}"
                    logger.warning(f"Invalid token bypass: {token[:20]}")
                
                test_results.append(test_result)
                
            except Exception as e:
                logger.error(f"Error testing invalid token: {e}")
        
        self.results['tests'].extend(test_results)
        return test_results
    
    def run_active_scan(self):
        """Run OWASP ZAP active security scan"""
        logger.info("Starting OWASP ZAP active scan for wallet service...")
        
        # Configure authentication
        if self.session_token:
            self.zap.replacer.add_rule(
                description="Wallet Auth Token",
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
        
        logger.info(f"Found {len(alerts)} security issues in wallet service")
        return alerts
    
    def generate_report(self):
        """Generate comprehensive security report"""
        logger.info("Generating wallet service security report...")
        
        # Calculate summary statistics
        total_tests = len(self.results['tests'])
        vulnerable_tests = len([t for t in self.results['tests'] if t.get('vulnerable', False)])
        
        high_risk_vulns = len([v for v in self.results['vulnerabilities'] if v['risk'] == 'High'])
        medium_risk_vulns = len([v for v in self.results['vulnerabilities'] if v['risk'] == 'Medium'])
        low_risk_vulns = len([v for v in self.results['vulnerabilities'] if v['risk'] == 'Low'])
        
        race_condition_vulns = len([t for t in self.results['race_condition_tests'] if t.get('vulnerable', False)])
        
        self.results['summary'] = {
            'total_tests': total_tests,
            'vulnerable_tests': vulnerable_tests,
            'vulnerability_rate': (vulnerable_tests / total_tests * 100) if total_tests > 0 else 0,
            'high_risk_vulnerabilities': high_risk_vulns,
            'medium_risk_vulnerabilities': medium_risk_vulns,
            'low_risk_vulnerabilities': low_risk_vulns,
            'total_vulnerabilities': len(self.results['vulnerabilities']),
            'race_condition_vulnerabilities': race_condition_vulns
        }
        
        # Save detailed JSON report
        report_file = f"security-testing/reports/wallet-service-security-{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        with open(report_file, 'w') as f:
            json.dump(self.results, f, indent=2, default=str)
        
        logger.info(f"Security report saved to {report_file}")
        
        # Print summary
        print("\n" + "="*65)
        print("WALLET SERVICE SECURITY ASSESSMENT SUMMARY")
        print("="*65)
        print(f"Target: {self.target_url}")
        print(f"Tests Conducted: {total_tests}")
        print(f"Vulnerable Tests: {vulnerable_tests} ({self.results['summary']['vulnerability_rate']:.1f}%)")
        print(f"")
        print(f"Critical Financial Security Issues:")
        print(f"  Balance Manipulation: {len([t for t in self.results['tests'] if 'BALANCE_MANIPULATION' in t.get('test', '') and t.get('vulnerable', False)])}")
        print(f"  Race Conditions: {race_condition_vulns}")
        print(f"  IDOR Vulnerabilities: {len([t for t in self.results['tests'] if 'IDOR' in t.get('test', '') and t.get('vulnerable', False)])}")
        print(f"  Transaction Integrity: {len([t for t in self.results['tests'] if 'TRANSACTION' in t.get('test', '') and t.get('vulnerable', False)])}")
        print(f"")
        print(f"General Vulnerabilities:")
        print(f"  High Risk: {high_risk_vulns}")
        print(f"  Medium Risk: {medium_risk_vulns}")
        print(f"  Low Risk: {low_risk_vulns}")
        print(f"  Total: {len(self.results['vulnerabilities'])}")
        print("="*65)
        
        return self.results

def main():
    """Main execution function"""
    logger.info("Starting Wallet Service Security Testing...")
    
    # Initialize tester
    tester = WalletServiceSecurityTester()
    
    # Setup authentication
    if not tester.setup_authentication():
        logger.error("Failed to setup authentication, continuing with limited tests...")
    
    # Setup test wallet
    if not tester.setup_test_wallet():
        logger.error("Failed to setup test wallet, some tests may fail...")
    
    # Run wallet-specific security tests
    tester.test_balance_manipulation()
    tester.test_idor_vulnerabilities()
    tester.test_race_conditions()
    tester.test_transaction_integrity()
    tester.test_authorization_bypass()
    
    # Run OWASP ZAP active scan
    tester.run_active_scan()
    
    # Generate report
    results = tester.generate_report()
    
    logger.info("Wallet Service security testing completed")
    
    return results

if __name__ == "__main__":
    main()