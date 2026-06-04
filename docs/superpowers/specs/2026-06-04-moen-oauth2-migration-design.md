# Design: Moen Flo OAuth2 API Migration

**Date:** 2026-06-04  
**Scope:** `MoenFloManager` only — `MoenFloStandalone` is unchanged.

---

## Background

Moen deprecated their old auth endpoint (`POST https://api.meetflo.com/api/v1/users/auth`). The replacement is a standard OAuth2 password/refresh-token flow at `POST https://api-gw.meetflo.com/api/v1/oauth2/token`. All `/api/v2/` data endpoints are unchanged.

---

## Scope

**One file changes:** `MoenFloManager/apps/MoenDeviceManager.groovy`

No drivers or child apps change — they already delegate all HTTP calls to the parent app via `makeAPIGet`/`makeAPIPost`.

---

## Design

### 1. Constants

Replace the old `AUTH_URL` and add OAuth client credential defaults:

```groovy
@Field final String AUTH_URL             = 'https://api-gw.meetflo.com/api/v1/oauth2/token'
@Field final String DEFAULT_CLIENT_ID     = '3baec26f-0e8b-4e1d-84b0-e178f05ea0a5'
@Field final String DEFAULT_CLIENT_SECRET = '3baec26f-0e8b-4e1d-84b0-e178f05ea0a5'
```

`BASE_URL` is unchanged (`https://api-gw.meetflo.com/api/v2`).

---

### 2. Settings UI

Add an **Advanced** section at the bottom of `deviceInstaller()`:

- `clientId` — string input, pre-filled with `DEFAULT_CLIENT_ID`
- `clientSecret` — password input, pre-filled with `DEFAULT_CLIENT_SECRET`
- Helper note: *"Only change these if Moen rotates the API credentials."*

---

### 3. State

| Key | Old | New |
|-----|-----|-----|
| `state.token` | Bare JWT from old auth | `access_token` from OAuth2 response |
| `state.refreshToken` | (did not exist) | `refresh_token` from OAuth2 response |
| `state.userId` | `response.data.tokenPayload.user.user_id` | Decoded from JWT payload (`userId` claim, fallback `sub`) |
| `state.tokenExpiration` | `(timeNow + tokenExpiration) * 1000` | `System.currentTimeMillis() + (expires_in * 1000)` |

---

### 4. `authenticate()` — full password login

- **Request:** `POST AUTH_URL`, `Content-Type: application/x-www-form-urlencoded`
- **Body:** `grant_type=password&username=<url-encoded>&password=<url-encoded>&client_id=<clientId setting or DEFAULT>&client_secret=<clientSecret setting or DEFAULT>`
- **Response parsing:**
  - `state.token = response.data.access_token`
  - `state.refreshToken = response.data.refresh_token`
  - `state.tokenExpiration = System.currentTimeMillis() + (response.data.expires_in * 1000L)`
  - `state.userId = extractUserIdFromJwt(state.token)`
  - `state.authenticated = true`, `state.authenticationFailures = 0`
- **Failure:** increment `state.authenticationFailures`; guard: give up after 3 failures (same as today)

---

### 5. New `refreshToken()` method

- If `state.refreshToken` is null/empty → call `authenticate()` and return
- **Request:** same endpoint, `grant_type=refresh_token&refresh_token=<url-encoded>&client_id=...&client_secret=...`
- **On success (200):** update `state.token`, `state.refreshToken`, `state.tokenExpiration` (userId unchanged)
- **On any failure** (non-200 or exception): log a warning and fall back to `authenticate()`

---

### 6. New `extractUserIdFromJwt()` helper

```groovy
def extractUserIdFromJwt(String jwt) {
    try {
        def payload = jwt.split('\\.')[1]
        def decoded = new String(java.util.Base64.getUrlDecoder().decode(payload))
        def json = new groovy.json.JsonSlurper().parseText(decoded)
        return json.userId ?: json.sub
    } catch (Exception e) {
        log.error "Failed to extract userId from JWT: ${e}"
        return null
    }
}
```

---

### 7. `checkTokenLife()` changes

- Threshold: 60 min → **5 min**
- Calls `refreshToken()` instead of `authenticate()`
- Expiry calculation simplified: `(state.tokenExpiration - System.currentTimeMillis()) / 1000 / 60`

---

### 8. `makeAPIGet` / `makeAPIPost` changes

**Auth header** (both methods):
```groovy
// before
headers.put("Authorization", state.token)
// after
headers.put("Authorization", "Bearer ${state.token}")
```

**401/403 hardening** (exception handlers in both methods): when exception message contains "Forbidden" or "Unauthorized", call `refreshToken()` instead of `authenticate()`. Since `refreshToken()` falls back to `authenticate()` on failure, the net recovery behaviour is the same as today but avoids a full re-login when the token simply expired.

---

### 9. `logout()` / state cleanup

Clear `state.refreshToken` alongside `state.token` on logout and on "Reset Token" diagnostic button press.

---

## Migration / Existing Users

Existing users have an old `state.token` (non-Bearer format) and no `state.refreshToken`. On first API call after upgrade:

1. Old token → API returns 401
2. Exception handler detects Forbidden/Unauthorized → calls `refreshToken()`
3. No `state.refreshToken` → `refreshToken()` falls back to `authenticate()`
4. Full re-login succeeds using saved username/password

No manual intervention required. Password must still be saved in settings (it is, for all existing installs).

---

## Out of Scope

- `MoenFloStandalone` — no changes
- Data endpoints — all unchanged
- Child apps and drivers — no changes
