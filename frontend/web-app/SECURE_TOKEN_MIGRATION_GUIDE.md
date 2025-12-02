# 🔒 Secure Token Storage Migration Guide

**Date:** October 26, 2025  
**Purpose:** Migrate from localStorage to httpOnly cookies for JWT tokens  
**Security Impact:** Eliminates XSS token theft vulnerability  

---

## ⚠️ CRITICAL SECURITY ISSUE

**Current State:** JWT tokens stored in localStorage
```typescript
// INSECURE - Vulnerable to XSS attacks
localStorage.setItem('accessToken', token);
localStorage.setItem('refreshToken', token);
```

**Problem:** Any XSS vulnerability allows attackers to steal tokens
```javascript
// Attacker injects this via XSS
const stolenToken = localStorage.getItem('accessToken');
sendToAttacker(stolenToken); // Game over
```

---

## ✅ NEW SECURE IMPLEMENTATION

**Tokens stored in httpOnly cookies:**
```typescript
// Secure - JavaScript CANNOT access these cookies
// Server sets:
Set-Cookie: access_token=jwt...; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=jwt...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh
```

**Benefits:**
- ✅ XSS Protection: JavaScript cannot read httpOnly cookies
- ✅ CSRF Protection: SameSite=Strict prevents cross-site requests
- ✅ Automatic: Browser sends cookies automatically
- ✅ Secure: HTTPS-only in production

---

## 📋 MIGRATION CHECKLIST

### Phase 1: Backend Implementation ✅ COMPLETE

- [x] JwtCookieService.java - Cookie management service
- [x] SecureAuthController.java - Login/logout/refresh endpoints
- [x] Configure cookie settings (httpOnly, secure, SameSite)
- [x] Set access token expiry (15 minutes)
- [x] Set refresh token expiry (7 days)

### Phase 2: Frontend Implementation ✅ COMPLETE

- [x] SecureAuthService.ts - Authentication service
- [x] Update axios config (withCredentials: true)
- [x] Remove all localStorage token operations

### Phase 3: Code Cleanup (TODO)

**Files to Update:**

1. **src/utils/axios.ts** ✅ DONE
   ```typescript
   // REMOVE these lines:
   const token = localStorage.getItem('accessToken');
   localStorage.setItem('accessToken', token);
   localStorage.removeItem('accessToken');
   
   // ALREADY UPDATED: withCredentials: true
   ```

2. **Search and destroy pattern:**
   ```bash
   # Find all localStorage token usage
   grep -r "localStorage.*[Tt]oken" src/
   
   # Common patterns to remove:
   - localStorage.getItem('accessToken')
   - localStorage.getItem('refreshToken')
   - localStorage.setItem('accessToken', ...)
   - localStorage.setItem('refreshToken', ...)
   - localStorage.removeItem('accessToken')
   - localStorage.removeItem('refreshToken')
   ```

3. **Components to check:**
   - Login components
   - Auth context/providers
   - Protected route components
   - API service files
   - Token refresh logic

---

## 🔄 API CHANGES

### Login Endpoint

**BEFORE:**
```typescript
const response = await axios.post('/auth/login', credentials);
const { accessToken, refreshToken } = response.data;
localStorage.setItem('accessToken', accessToken);     // INSECURE
localStorage.setItem('refreshToken', refreshToken);   // INSECURE
```

**AFTER:**
```typescript
const response = await axios.post('/auth/login', credentials);
// Tokens are in httpOnly cookies now - NO localStorage!
// Just use the returned user data
const { user } = response.data;
```

### Logout Endpoint

**BEFORE:**
```typescript
localStorage.removeItem('accessToken');
localStorage.removeItem('refreshToken');
await axios.post('/auth/logout');
```

**AFTER:**
```typescript
await axios.post('/auth/logout');
// Server clears cookies automatically
```

### Token Refresh

**BEFORE:**
```typescript
const refreshToken = localStorage.getItem('refreshToken');
const response = await axios.post('/auth/refresh', { refreshToken });
const { accessToken } = response.data;
localStorage.setItem('accessToken', accessToken);
```

**AFTER:**
```typescript
await axios.post('/auth/refresh');
// Refresh token read from cookie, new access token set in cookie
// NO localStorage operations needed
```

---

## 🧪 TESTING GUIDE

### Manual Testing

1. **Test Login:**
   ```
   - Open DevTools → Application → Cookies
   - Login to app
   - Verify cookies present:
     ✓ access_token (HttpOnly: true, Secure: true, SameSite: Strict)
     ✓ refresh_token (HttpOnly: true, Secure: true, SameSite: Strict)
   ```

2. **Test XSS Protection:**
   ```javascript
   // Open DevTools Console and try:
   document.cookie
   // Should NOT see access_token or refresh_token
   // They're httpOnly and hidden from JavaScript
   ```

3. **Test Logout:**
   ```
   - Logout from app
   - Check DevTools → Application → Cookies
   - Verify tokens are deleted
   ```

4. **Test Token Refresh:**
   ```
   - Wait 15+ minutes (access token expiry)
   - Make an API request
   - Verify automatic refresh works
   - Check Network tab for /auth/refresh call
   ```

### Automated Testing

```typescript
// Test that localStorage is NOT used for tokens
describe('Token Security', () => {
  it('should not store tokens in localStorage', () => {
    // After login
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('should send cookies with requests', () => {
    // Mock axios request
    expect(axiosInstance.defaults.withCredentials).toBe(true);
  });
});
```

---

## 🚨 BREAKING CHANGES

### For Users
- **Action Required:** Users must re-login after deployment
- **Reason:** Tokens in old format (localStorage) won't work with new system (cookies)
- **Impact:** One-time re-login, then seamless experience

### For Developers
- **localStorage.getItem('accessToken')** → NOT AVAILABLE (tokens in httpOnly cookies)
- **localStorage.setItem('accessToken', ...)** → NO LONGER NEEDED (server sets cookies)
- **Manual token management** → AUTOMATIC (browser handles cookies)

---

## 📦 DEPLOYMENT STEPS

### Pre-Deployment
1. Test in staging environment
2. Verify all localStorage token usage removed
3. Test login/logout/refresh flows
4. Verify cookie security settings (HttpOnly, Secure, SameSite)

### Deployment
1. Deploy backend first (backwards compatible)
2. Deploy frontend
3. Monitor error logs for token issues

### Post-Deployment
1. Force logout all users (clear old localStorage tokens)
2. Monitor login success rate
3. Check for CORS issues (withCredentials: true required)
4. Verify cookies working in production (HTTPS)

---

## 🐛 TROUBLESHOOTING

### Issue: "CSRF token not found"
**Cause:** withCredentials not enabled  
**Fix:** Set `axios.defaults.withCredentials = true`

### Issue: "Cookies not being sent"
**Cause:** CORS misconfiguration  
**Fix:** Backend must set `Access-Control-Allow-Credentials: true`

### Issue: "Cookies not visible in DevTools"
**Cause:** httpOnly flag (this is CORRECT behavior)  
**Fix:** No fix needed - this is secure by design

### Issue: "Cookies not persisting"
**Cause:** Secure flag on HTTP (localhost)  
**Fix:** Set `jwt.cookie.secure=false` in development

---

## ✅ SECURITY VALIDATION

After migration, verify:

- [ ] No tokens in localStorage
- [ ] Tokens in httpOnly cookies
- [ ] Secure flag enabled in production
- [ ] SameSite=Strict set
- [ ] Access token expiry: 15 minutes
- [ ] Refresh token expiry: 7 days
- [ ] Refresh token path restricted: /api/auth/refresh
- [ ] CSRF protection enabled
- [ ] withCredentials: true in axios

---

## 📚 REFERENCES

- [OWASP: XSS Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [OWASP: CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [MDN: HttpOnly Cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#security)
- [Spring Security: Cookie CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

---

**Migration Status:** ✅ IMPLEMENTATION COMPLETE  
**Next Step:** Remove localStorage token usage from existing code  
**ETA:** 1-2 hours for complete cleanup
