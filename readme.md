# Moen Flo for Hubitat

Groovy drivers and apps that integrate [Moen FLO Smart Water](https://www.moenflo.com/) devices with a [Hubitat](https://hubitat.com/) hub. Communicates with the Moen Flo cloud API to monitor water usage, pressure, temperature, and control your shutoff valve — all from within Hubitat.

---

## Packages

There are two packages. **MoenFloManager is recommended** for all new installs.

| Package | Status | Description |
|---|---|---|
| [MoenFloManager](MoenFloManager/) | Active — new features | Multi-app architecture supporting all three Flo device types |
| [MoenFloStandalone](MoenFloStandalone/) | Legacy — bug fixes only | Single standalone driver for the Smart Shutoff valve |

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
| App | MoenDeviceManager | `MoenFloManager/apps/MoenDeviceManager.groovy` |
| App | MoenLocationInstance | `MoenFloManager/apps/MoenLocationInstance.groovy` |
| App | MoenSmartShutoffInstance | `MoenFloManager/apps/MoenSmartShutoffInstance.groovy` |
| App | MoenSmartWaterDetectorInstance | `MoenFloManager/apps/MoenSmartWaterDetectorInstance.groovy` |
| Driver | MoenLocation | `MoenFloManager/drivers/MoenLocation.groovy` |
| Driver | MoenSmartShutoff | `MoenFloManager/drivers/MoenSmartShutoff.groovy` |
| Driver | MoenSmartWaterDetector *(optional)* | `MoenFloManager/drivers/MoenSmartWaterDetector.groovy` |

**MoenFloStandalone** (legacy):

| Type | File | Import URL |
|---|---|---|
| Driver | moenflo | `MoenFloStandalone/drivers/moenflo.groovy` |
| Driver | moenflodetector | `MoenFloStandalone/drivers/moenflodetector.groovy` |

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
  "MoenFloManager/apps/MoenDeviceManager.groovy": 123,
  "MoenFloManager/drivers/MoenSmartShutoff.groovy": 456
}
```

(Find the IDs in your hub's Apps Code / Drivers Code URLs.)

**Run:**

```bash
npm run watch
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

tools/                              ← legacy Python toolset
deploy.js                           ← Node.js watch/deploy script
```

---

## License

Moen Flo for Hubitat by [David Manuel](https://github.com/dacmanj) is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0).

Software is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the license for details.

> This project is not affiliated with, endorsed by, or sponsored by Moen Inc. or Flo Technologies, Inc. All trademarks are reserved to their respective owners.
