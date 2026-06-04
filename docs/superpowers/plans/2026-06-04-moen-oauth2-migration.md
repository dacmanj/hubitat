# Moen OAuth2 API Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `MoenFloManager` from the deprecated Moen password auth endpoint to the new OAuth2 password/refresh-token flow, with Bearer header and 401-hardened retry logic.

**Architecture:** All changes are in one file — `MoenFloManager/apps/MoenDeviceManager.groovy`. No drivers or child apps change; they already delegate all HTTP through `makeAPIGet`/`makeAPIPost`. The OAuth2 flow replaces bare-token auth with access + refresh tokens. All `/api/v2/` data endpoints are unchanged.

**Tech Stack:** Groovy (Hubitat), `java.util.Base64` (JWT decode), `java.net.URLEncoder` (form encoding), Hubitat `httpPost`/`httpGet`

**Spec:** `docs/superpowers/specs/2026-06-04-moen-oauth2-migration-design.md`

---

## File Map

| File | Changes |
|------|---------|
| `MoenFloManager/apps/MoenDeviceManager.groovy` | Constants, new helpers/methods, updated auth/HTTP methods, settings UI |

---

### Task 1: Update constants and add JWT decode helper

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

- [ ] **Step 1: Replace the two `AUTH_URL`/`BASE_URL` constant lines (43–44) with updated auth URL and new client credential defaults**

Replace:
```groovy
@Field final String BASE_URL = 'https://api-gw.meetflo.com/api/v2'
@Field final String AUTH_URL = 'https://api.meetflo.com/api/v1/users/auth'
```
With:
```groovy
@Field final String BASE_URL             = 'https://api-gw.meetflo.com/api/v2'
@Field final String AUTH_URL             = 'https://api-gw.meetflo.com/api/v1/oauth2/token'
@Field final String DEFAULT_CLIENT_ID     = '3baec26f-0e8b-4e1d-84b0-e178f05ea0a5'
@Field final String DEFAULT_CLIENT_SECRET = '3baec26f-0e8b-4e1d-84b0-e178f05ea0a5'
```

- [ ] **Step 2: Add `extractUserIdFromJwt()` helper method**

Add this method anywhere before `authenticate()` (e.g. just before the `authenticate()` definition at line ~200):
```groovy
def extractUserIdFromJwt(String jwt) {
    try {
        def payload = jwt.split('\\.')[1]
        def padding = (4 - payload.length() % 4) % 4
        def padded = payload + ('=' * padding)
        def decoded = new String(java.util.Base64.getUrlDecoder().decode(padded))
        def json = new groovy.json.JsonSlurper().parseText(decoded)
        return json.userId ?: json.sub
    } catch (Exception e) {
        log.error "Failed to extract userId from JWT: ${e}"
        return null
    }
}
```

- [ ] **Step 3: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: update auth URL constants and add JWT decode helper"
```

---

### Task 2: Rewrite `authenticate()` for OAuth2 password grant

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

The current `authenticate()` (lines ~200–248) POSTs JSON to the old endpoint and reads `tokenPayload.user.user_id`. Replace it entirely.

- [ ] **Step 1: Replace the full `authenticate()` method body**

Replace the entire `authenticate()` method with:
```groovy
def authenticate() {
    state.authenticated = false
    if (logEnable) log.debug("authenticate()")
    def uri = AUTH_URL
    if (!password) {
        log.info("Login Skipped: No password")
        state.authenticationFailures = 99
        return
    }
    if (state.authenticationFailures >= 3) {
        log.error("Failed to authenticate after three tries. Giving up. Log out and back in to retry.")
        return
    }

    def cid     = settings.clientId     ?: DEFAULT_CLIENT_ID
    def csecret = settings.clientSecret ?: DEFAULT_CLIENT_SECRET
    def body = "grant_type=password" +
               "&username=${java.net.URLEncoder.encode(username as String, 'UTF-8')}" +
               "&password=${java.net.URLEncoder.encode(password as String, 'UTF-8')}" +
               "&client_id=${cid}" +
               "&client_secret=${csecret}"
    def headers = ['Content-Type': 'application/x-www-form-urlencoded']

    try {
        httpPost([headers: headers, uri: uri, body: body]) { response ->
            if (logEnable) log.debug("Login received response code ${response?.status}")
            if (response?.status == 200) {
                state.token            = response.data.access_token
                state.refreshToken     = response.data.refresh_token
                state.tokenExpiration  = System.currentTimeMillis() + ((long)response.data.expires_in * 1000L)
                state.tokenExpirationDate = new Date(state.tokenExpiration).toString()
                state.userId           = extractUserIdFromJwt(state.token as String)
                state.authenticated    = true
                state.authenticationFailures = 0
                if (logEnable) log.debug("authentication successful, userId: ${state.userId}")
            } else {
                log.error "Login Failed: (${response?.status}) ${response?.data}"
                state.authenticated = false
                state.authenticationFailures = (state.authenticationFailures >= 0 ? state.authenticationFailures + 1 : 0)
            }
        }
    } catch (Exception e) {
        log.error "Login exception: ${e}"
        log.error "Login Failed: Please confirm your Flo Credentials"
        state.authenticated = false
        state.authenticationFailures = (state.authenticationFailures >= 0 ? state.authenticationFailures + 1 : 0)
    }
    if (logEnable) log.debug("failure count: ${state.authenticationFailures}")
}
```

- [ ] **Step 2: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: rewrite authenticate() for OAuth2 password grant"
```

---

### Task 3: Add `refreshToken()` method

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

- [ ] **Step 1: Add `refreshToken()` immediately after `authenticate()`**

```groovy
def refreshToken() {
    if (!state.refreshToken) {
        if (logEnable) log.debug("No refresh token — falling back to full login")
        authenticate()
        return
    }
    if (logEnable) log.debug("refreshToken()")
    def cid     = settings.clientId     ?: DEFAULT_CLIENT_ID
    def csecret = settings.clientSecret ?: DEFAULT_CLIENT_SECRET
    def body = "grant_type=refresh_token" +
               "&refresh_token=${java.net.URLEncoder.encode(state.refreshToken as String, 'UTF-8')}" +
               "&client_id=${cid}" +
               "&client_secret=${csecret}"
    def headers = ['Content-Type': 'application/x-www-form-urlencoded']
    try {
        httpPost([headers: headers, uri: AUTH_URL, body: body]) { response ->
            if (response?.status == 200) {
                state.token           = response.data.access_token
                state.refreshToken    = response.data.refresh_token
                state.tokenExpiration = System.currentTimeMillis() + ((long)response.data.expires_in * 1000L)
                state.tokenExpirationDate = new Date(state.tokenExpiration).toString()
                state.authenticated   = true
                state.authenticationFailures = 0
                if (logEnable) log.debug("Token refreshed successfully")
            } else {
                log.warn "Token refresh failed (${response?.status}), falling back to full login"
                authenticate()
            }
        }
    } catch (Exception e) {
        log.warn "Token refresh exception: ${e}, falling back to full login"
        authenticate()
    }
}
```

- [ ] **Step 2: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: add refreshToken() with fallback to full login"
```

---

### Task 4: Update `checkTokenLife()`

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

- [ ] **Step 1: Replace `checkTokenLife()` body**

Replace the entire `checkTokenLife()` method with:
```groovy
def checkTokenLife() {
    if (state.tokenExpiration) {
        remainingMinutes = (int)((state.tokenExpiration - System.currentTimeMillis()) / 1000 / 60)
        if (logEnable) log.info "Moen API Token Life Remaining: ${remainingMinutes} minutes"
    } else {
        remainingMinutes = 0
    }
    if (remainingMinutes < 5) {
        log.debug("Moen API Token Life Remaining Minutes only ${remainingMinutes} -- refreshing")
        refreshToken()
    }
    return remainingMinutes
}
```

Key changes from original:
- Expiry calculation no longer wraps `state.tokenExpiration` in `new Date(...).getTime()` (it's already ms since epoch)
- Threshold: 60 min → 5 min
- Calls `refreshToken()` instead of `authenticate()`

- [ ] **Step 2: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: update checkTokenLife() to use refresh token and 5-min threshold"
```

---

### Task 5: Update `makeAPIGet` — Bearer header and 401 hardening

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

- [ ] **Step 1: Fix the `Authorization` header line inside the `while` loop**

Find:
```groovy
        headers.put("Authorization", state.token)
```
Replace with:
```groovy
        headers.put("Authorization", "Bearer ${state.token}")
```

- [ ] **Step 2: Replace the exception handler in `makeAPIGet`**

Find (the entire catch block):
```groovy
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage()?.contains("Forbidden") || e.getMessage()?.contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception..."
            } else {
                log.error "${request_type} Failed ${e}"
            }
            authenticate()
        }
```
Replace with:
```groovy
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage()?.contains("Forbidden") || e.getMessage()?.contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized on ${request_type}, trying token refresh"
                refreshToken()
            } else {
                log.error "${request_type} Failed ${e}"
                authenticate()
            }
        }
```

- [ ] **Step 3: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: add Bearer prefix and 401 hardening in makeAPIGet"
```

---

### Task 6: Update `makeAPIPost` — Bearer header and 401 hardening

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

- [ ] **Step 1: Fix the `Authorization` header line inside the `while` loop**

In `makeAPIPost`, find the same pattern:
```groovy
        headers.put("Authorization", state.token)
```
Replace with:
```groovy
        headers.put("Authorization", "Bearer ${state.token}")
```

- [ ] **Step 2: Replace the exception handler in `makeAPIPost`**

Find:
```groovy
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception... Refreshing token..."
                authenticate()
            }
        }
```
Replace with:
```groovy
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized on ${request_type}, trying token refresh"
                refreshToken()
            }
        }
```

- [ ] **Step 3: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: add Bearer prefix and 401 hardening in makeAPIPost"
```

---

### Task 7: Advanced settings UI and `btnInvalidToken` cleanup

**Files:**
- Modify: `MoenFloManager/apps/MoenDeviceManager.groovy`

- [ ] **Step 1: Add Advanced settings section to `deviceInstaller()`**

In `deviceInstaller()`, add a new section after the existing `"<b>Settings</b>"` section and before `"<b>Diagnostics</b>"`:
```groovy
    section("<b>Advanced</b>") {
      input(name: "clientId",     type: "string",   title: "OAuth Client ID",     defaultValue: DEFAULT_CLIENT_ID,     required: false)
      input(name: "clientSecret", type: "password", title: "OAuth Client Secret", defaultValue: DEFAULT_CLIENT_SECRET, required: false)
      paragraph("<i>Only change these if Moen rotates the API credentials.</i>")
    }
```

- [ ] **Step 2: Update `btnInvalidToken` handler to also clear the refresh token**

Find:
```groovy
    case "btnInvalidToken":
      state.token = '9999999999999999'
      break
```
Replace with:
```groovy
    case "btnInvalidToken":
      state.token = '9999999999999999'
      state.refreshToken = null
      break
```

- [ ] **Step 3: Commit**

```
git add MoenFloManager/apps/MoenDeviceManager.groovy
git commit -m "feat: add advanced OAuth credential settings; clear refresh token on reset"
```

---

### Task 8: Upload to hub and verify

**Files:**
- Read: `.env` (or pass args directly)

This is a Groovy app running on Hubitat — there is no local test runner. Verification is done by uploading and observing live logs.

- [ ] **Step 1: Upload to hub**

```
python tools/uploader.py MoenFloManager upload
```
Expected output: all 7 files uploaded successfully with 200 responses.

- [ ] **Step 2: Trigger a full re-login to test OAuth2 password grant**

In the Hubitat UI, open the **Moen FLO Device Manager** app. Click **Logout**, then re-enter credentials and click **Login**.

In Hubitat logs, expect to see:
```
[debug] authenticate()
[debug] Login received response code 200
[debug] authentication successful, userId: <uuid>
```
And NOT any error like "Login Failed".

- [ ] **Step 3: Verify Bearer header works — confirm a successful device poll**

In the Hubitat UI, open any child device (e.g. Smart Shutoff valve) and click **Poll** (or wait for the next scheduled poll).

In logs, expect:
```
[debug] makeAPIGet: Get Device https://api-gw.meetflo.com/api/v2/devices/<id>
[debug] Get Device Received Response Code: 200
```
No 401/403 errors.

- [ ] **Step 4: Verify refresh token path — use "Reset Token" diagnostic**

In the app settings, click **Reset Token** (the `btnInvalidToken` button). This sets `state.token` to a junk value and clears `state.refreshToken`. Then trigger a poll from any device.

In logs, expect:
```
[debug] Forbidden/Unauthorized on Get Device, trying token refresh
[debug] No refresh token — falling back to full login
[debug] authentication successful, userId: <uuid>
[debug] Get Device Received Response Code: 200
```
The poll recovers automatically without user intervention.

- [ ] **Step 5: Verify advanced settings appear in UI**

In the app settings UI, scroll to the new **Advanced** section. Confirm **OAuth Client ID** and **OAuth Client Secret** fields appear pre-filled with the default UUID.

- [ ] **Step 6: Commit a version bump**

In `MoenFloManager/packageManifest.json` and each Groovy file's `version` metadata field, bump the version (e.g. `1.0.17` → `1.0.18`).

```
git add MoenFloManager/
git commit -m "bump version to 1.0.18; migrate to OAuth2 auth"
```
