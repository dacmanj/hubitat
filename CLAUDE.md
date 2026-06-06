# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Hubitat home automation drivers/apps for the Moen Flo Smart Shutoff valve and Smart Water Detector. The Groovy code runs on a Hubitat hub (not locally); the Python toolset syncs files between this repo and a live hub.

## Two Packages

- **MoenFloManager/** — Active package. Multi-app architecture: a parent app (`MoenDeviceManager`) manages child app instances (`MoenLocationInstance`, `MoenSmartShutoffInstance`, `MoenSmartWaterDetectorInstance`) and their corresponding drivers. Polls `https://api-gw.meetflo.com/api/v2`.
- **MoenFloStandalone/** — Legacy standalone driver only. Bug fixes only; no new features.

## Python Toolset (`tools/`)

Syncs Groovy source files to/from a live Hubitat hub over HTTP. Requires a `.env` file (gitignored) with:
```
HUBITAT=<hub-ip>
TARGET=MoenFloManager
DIRECTION=upload   # or retrieve
AUTOUPLOAD=false
```

**Key commands:**
```bash
# Install dependencies
poetry install

# Upload local Groovy files to hub
python tools/uploader.py MoenFloManager upload

# Retrieve Groovy files from hub to local
python tools/uploader.py MoenFloManager retrieve

# Auto-upload (watches for file changes and uploads automatically)
AUTOUPLOAD=true python tools/uploader.py
```

VS Code tasks (`Terminal > Run Task`) also expose "Upload to Hubitat" and "Retrieve from Hubitat".

## Python Architecture

- `tools/uploader.py` — Entry point; reads env vars or CLI args
- `tools/utils/session_factory.py` — `SessionWrapper` wraps `requests.Session` to prepend the hub base URL; `create_hubitat_session(ip)` creates one
- `tools/utils/api.py` — `HubitatAPIWrapper` — CRUD operations against Hubitat's internal web API (`/app/list`, `/driver/list/data`, `/app/ajax/update`, etc.)
- `tools/utils/file.py` — `HubitatFileManager` — orchestrates upload/retrieve; builds a source-path↔hub-id mapping by walking the package directory and matching filenames to hub code names; also runs `auto_upload` via watchdog
- `tools/utils/manifest.py` — `PackageFile` (path + hub id + type), `AppPackageManifest` (lists of apps/drivers)
- `tools/utils/source_watchdog.py` — Watchdog observer that calls `update_latest_version` on file modification

## Groovy / Hubitat Conventions

- Namespace for all files: `dacmanj`
- Device type→driver/app mapping is in `@Field final Map driverMap` / `appMap` in `MoenDeviceManager.groovy`
- `packageManifest.json` in each package root is what Hubitat Package Manager uses — keep UUIDs stable; bump `version` on releases
- The Smart Water Detector driver (`MoenSmartWaterDetector.groovy`) is marked `required: false` in the manifest — it's optional

## Version Management

Version strings live in two places that must stay in sync: the `version` field in `packageManifest.json` and the `version` metadata at the top of each Groovy driver/app file.

When bumping the version for a package, always update release notes in both places:
1. **`<package>/readme.md`** — add a dated bullet list entry under `## Release Notes` summarising what changed
2. **`<package>/packageManifest.json`** — update the `releaseNotes` field with a short summary (this is what Hubitat Package Manager shows users during upgrades); also update `dateReleased` to today's date

Also update the Node.js deploy tooling if relevant:
- `npm run deploy` — pushes all files to the hub at once (uses `.hubitat.json` for the id mapping)
- `npm run watch` — watches for file changes and deploys automatically
