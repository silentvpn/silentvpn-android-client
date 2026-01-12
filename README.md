# SilentVPN (Android Client)

⚠️ **IMPORTANT NOTICE**  
This repository contains **ONLY the Android client source code**.  
❌ No VPN servers  
❌ No API endpoints  
❌ No credentials, keys, or accounts  
❌ No binary VPN core files  

All sensitive or production components are intentionally excluded.

---

## Overview

SilentVPN is an Android VPN client built on top of **V2Ray** technology.  
This project focuses on the **client-side implementation only**, including UI, connection handling, and usage tracking.

---

## Features

- V2Ray-based VPN client
- Traffic usage tracking (RX / TX)
- Reward system (Ads-based)
- Multi-language support
- Modern Android architecture (Kotlin)
- Secure networking configuration

---

## Project Structure


---

## Requirements

- Android Studio (latest stable)
- Android SDK 24+
- Kotlin
- Your own VPN backend & servers
- Your own V2Ray core build (`libv2ray.aar`)

---

## ⚠️ Missing Dependencies (Important)

This repository **does NOT include** the following files:

- `libv2ray.aar`
- Any `.zip` or binary VPN core
- Any real server configuration

You **must provide these manually** for local builds.

---

## libv2ray Setup (Local Only)

1. Build or obtain `libv2ray.aar`
2. Place it in:
3. Make sure it is **NOT committed to Git**

This is enforced via `.gitignore`.

---

## Build Notes

This project is intended for:
- Educational purposes
- Client-side development
- UI / UX implementation
- Architecture reference

Production builds require **your own backend infrastructure**.

---

## Disclaimer

This project is provided **for educational and research purposes only**.  
The author does not provide VPN services, servers, or guarantees of privacy.

You are responsible for complying with local laws and platform policies.

---

## License

This project does not grant rights to use the SilentVPN name or branding.  
Use the source code at your own risk.
