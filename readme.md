# Hubitat Apps & Drivers by David Manuel

Groovy drivers and apps for [Hubitat](https://hubitat.com/) hubs, published here and distributed via [Hubitat Package Manager](https://github.com/HubitatCommunity/hubitatpackagemanager).

---

## Packages

| Package | Status | Description |
|---|---|---|
| [FordPass](FordPass/) | Active | Ford/Lincoln connected vehicle integration — lock/unlock, remote start, guard mode, preconditioning, vehicle status, and optional ABRP live telemetry |
| [MoenFloManager](MoenFloManager/) | Active — new features | Multi-app architecture supporting all three Moen Flo device types |
| [MoenFloStandalone](MoenFloStandalone/) | Legacy — bug fixes only | Single standalone driver for the Moen Flo Smart Shutoff valve |

---

## Moen Flo for Hubitat

Communicates with the Moen Flo cloud API to monitor water usage, pressure, temperature, and control your shutoff valve — all from within Hubitat.

---

## Supported Devices

### Moen FLO Smart Shutoff (`flo_device_v2`)
Controls and monitors the main water shutoff valve.

- **Capabilities:** Valve, Temperature Measurement, Signal Strength, Location Mode, Momentary, Pushable Button
- **Attributes:** `mode` (home/away/sleep), `gpm`, `psi`, `rssi`, `ssid`, water usage totals, health test status, last event details

### Moen FLO Smart Water Detector (`puck_oem`)
Detects leaks at individual locations (under sinks, near water heaters, etc.). Managed by MoenFloManager. Optional — install only if you have this device.

### Moen FLO Location
A virtual device representing a Flo "location" (e.g. your home). Aggregates status and allows location-level mode control.

---

## Installation

### Hubitat Package Manager (recommended)

[Hubitat Package Manager (HPM)](https://github.com/HubitatCommunity/hubitatpackagemanager) handles installation and future updates automatically.

1. If you don't have HPM installed yet:
   - Go to **Apps Code** on your hub → **New App** → **Import**
   - Paste the HPM install URL from the [HPM repo](https://github.com/HubitatCommunity/hubitatpackagemanager) → **Import** → **Save**
   - Go to **Apps** → **Add User App** → **Hubitat Package Manager** and complete setup
2. Open HPM on your hub and choose **Install**
3. Search for **Moen FLO Device Manager** (or **Moen Flo Integration** for the legacy standalone)
4. Follow the prompts to install

### Manual Installation

If you prefer to install without HPM, import each file individually via your hub's code editor.

**MoenFloManager** — import in this order:

| Type | File | Import URL |
|---|---|---|
| App | MoenDeviceManager | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/apps/MoenDeviceManager.groovy` |
| App | MoenLocationInstance | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/apps/MoenLocationInstance.groovy` |
| App | MoenSmartShutoffInstance | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/apps/MoenSmartShutoffInstance.groovy` |
| App | MoenSmartWaterDetectorInstance | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/apps/MoenSmartWaterDetectorInstance.groovy` |
| Driver | MoenLocation | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/drivers/MoenLocation.groovy` |
| Driver | MoenSmartShutoff | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/drivers/MoenSmartShutoff.groovy` |
| Driver | MoenSmartWaterDetector *(optional)* | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/drivers/MoenSmartWaterDetector.groovy` |

**MoenFloStandalone** (legacy):

| Type | File | Import URL |
|---|---|---|
| Driver | moenflo | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloStandalone/drivers/moenflo.groovy` |
| Driver | moenflodetector | `https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloStandalone/drivers/moenflodetector.groovy` |

To import: **Apps Code** (or **Drivers Code**) → **New** → **Import** → paste the raw GitHub URL → **Import** → **Save**.

See also: [Hubitat Documentation — How to Install Custom Apps](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps)

---

## Development Setup

### Node.js Deploy Script (recommended)

`deploy.js` watches `**/*.groovy` files and auto-deploys changes to your hub on save.

**Prerequisites:** Node.js

```bash
npm install
```

**Configuration** — create a `.env` file in the repo root:

```
HUBITAT_URL=http://<your-hub-ip>
```

Create a `.hubitat.json` file mapping source file paths to Hubitat driver/app IDs:

```json
{
  "<hub-ip>": {
    "MoenFloManager/apps/MoenDeviceManager.groovy": { "id": 123 },
    "MoenFloManager/drivers/MoenSmartShutoff.groovy": { "id": 456 }
  }
}
```

Find the IDs in your hub's Apps Code / Drivers Code page URLs (the number at the end of the URL when you open a file).

**Commands:**

```bash
# Watch for file changes and deploy automatically on save
npm run watch

# Push all files to the hub once (useful after pulling updates)
npm run deploy
```

### Legacy Python Toolset

A Poetry-based toolset in `tools/` supports manual upload and retrieve operations.

```bash
# Install dependencies
poetry install

# Upload local files to hub
python tools/uploader.py MoenFloManager upload

# Retrieve files from hub to local
python tools/uploader.py MoenFloManager retrieve
```

Requires a `.env` file:

```
HUBITAT=<hub-ip>
TARGET=MoenFloManager
DIRECTION=upload
```

---

## Repository Structure

```
FordPass/
  apps/
    FordPassConnect.groovy
  drivers/
    FordPassVehicle.groovy
  packageManifest.json

MoenFloManager/
  apps/
    MoenDeviceManager.groovy
    MoenLocationInstance.groovy
    MoenSmartShutoffInstance.groovy
    MoenSmartWaterDetectorInstance.groovy
  drivers/
    MoenLocation.groovy
    MoenSmartShutoff.groovy
    MoenSmartWaterDetector.groovy   ← optional
  packageManifest.json

MoenFloStandalone/
  drivers/
    moenflo.groovy
    moenflodetector.groovy
  packageManifest.json

tools/                              ← legacy Python toolset (Moen packages)
deploy.js                           ← Node.js watch/deploy script
```

---

## License

Each package is licensed individually — see the `license.txt` in its own folder. The Moen Flo packages by [David Manuel](https://github.com/dacmanj) are licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0). FordPass is a derivative of [marq24/ha-fordpass](https://github.com/marq24/ha-fordpass) and remains under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See each license for details.

> This project is not affiliated with, endorsed by, or sponsored by Moen Inc., Flo Technologies, Inc., Ford Motor Company, or Lincoln. All trademarks are reserved to their respective owners.
