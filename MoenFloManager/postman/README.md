# Moen Flo API — Postman Collection

For debugging the Moen Flo API directly, outside of Hubitat. Mirrors the requests made by `MoenDeviceManager.groovy` and its child apps.

## Setup

1. Import `MoenFlo-API.postman_collection.json` into Postman.
2. Open the collection's **Variables** tab and fill in `username` / `password` (your Flo app credentials). Leave everything else as-is — `client_id`/`client_secret`/`base_url`/`auth_url` are copied from `MoenDeviceManager.groovy` and are not user-specific.
3. Run **Auth → Login (Password Grant)**. This sets `access_token`, `refresh_token`, and `user_id` (decoded from the JWT) as collection variables automatically. All other requests use `access_token` via the collection's Bearer auth.

## Typical debugging flow

1. **User & Discovery → Get User Info** — sets `location_id` from your first location.
2. **User & Discovery → Get Location (with devices)** — lists devices at that location and auto-sets `device_id` if it finds a water detector (`deviceType: puck_oem`). Check the Postman console for the full device list if you need a different device.
3. **User & Discovery → Get Device (raw telemetry)** — the main one for the water-sensor investigation. Logs `fwProperties` and `fwProperties.telemetry_water` straight to the Postman console so you can see the real value without touching Hubitat.

If `access_token` expires (short-lived), run **Auth → Refresh Token** rather than logging in again.

## Notes

- The `client_id`/`client_secret` in this collection are the same public/shared values hardcoded in `MoenDeviceManager.groovy` (`DEFAULT_CLIENT_ID`/`DEFAULT_CLIENT_SECRET`) — not secrets specific to your account.
- Your `username`/`password` and the resulting tokens are stored in Postman's collection variables. Don't share an exported copy of this collection without clearing them first (Collection → Variables → reset the current values).
