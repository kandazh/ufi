# 🧰 UFI-TOOLS

> A multifunction management and extension tool for ZTE pocket WiFi devices (F50 / U30 Air).
> Supports remote management, signal monitoring, system control, plugin extensions, and more.
> Also supports some ZTE UNISOC Android phones/tablets.

> This project is fully open-source and free. If you like it, you can buy me a coffee:
>
> | ![ali_pay](./images/ali_pay.jpeg) | ![wechat_pay](./images/wechat_pay.webp) |
> | --- | --- |

> Join the group chat:
> TG: https://t.me/ufi_tools_chat
>
> | ![group2](./images/group_2.jpg) | ![group3](./images/group_3.jpg) |
> | --- | --- |

---

F50 / U30 Air installation guide (video): https://www.bilibili.com/video/BV1qUHpzeEDd

Magisk module version installation guide (video): https://www.bilibili.com/video/BV1nsW4zpE1T

---

## 🧩 Editions

UFI-TOOLS provides two variants to cover different usage scenarios:

| Edition | Deployment | Target devices | Feature level | Typical use |
| --- | --- | --- | --- | --- |
| 📱 **[Pocket Edition (PE)](https://github.com/kanoqwq/UFI-TOOLS/tree/main)** | Install on your phone only | Phone connects to a MIFI/UFI device | ⭐ Reduced feature set<br>🚫 No install on the pocket WiFi<br>⚙️ Remote control of the pocket WiFi | Lightweight remote management from your phone |
| 💻 **[Full](https://github.com/kanoqwq/UFI-TOOLS/tree/http-server-version)** | Install on the target device (pocket WiFi / tablet / router) | Pocket WiFi devices (U30 Air / F50, etc.) | 🌟 Full feature set<br>🧠 Full plugin store support<br>🔐 Advanced features available | Deep system management and plugin extensions |

Notes:

- PE is intended for quick use by regular users; Full is aimed at advanced users.
- If your device is a ZTE pocket WiFi running Android on a UNISOC platform, it is likely worth trying.

---

## 📘 Overview

**UFI-TOOLS** is an all-in-one system management and extension framework for **ZTE devices based on UNISOC platforms**.
It can run on **pocket routers, phones, and tablets**, and can be deployed via **Web UI / APK / Magisk module**.

- ✅ Known targets: ZTE F50, U30 Air, and related UNISOC-based ZTE devices
- 🧩 Modular plugin system
- 🌐 Remote web control and device management
- ⚙️ Can run as a background service with auto-start

---

## ⚙️ Core features

### 🔧 System and device control

- One-click advanced mode (root-level control)
- Performance mode switching / CPU core control / battery charge limit
- Auto-enable USB debugging and network ADB
- File sharing / indicator LED control / OTA updates
- Boot scripts and background services

---

### 📶 Network and signal management

- Lock bands / lock cells without reboot (takes effect immediately)
- 3G / 4G / 5G mode switching
- Live metrics: RSRP, SINR, PCI, Band, QCI, SNR, QoS, IPv6, throughput, etc.
- LAN speed test and real-time charts

---

### 💬 Messaging and commands

- SMS send/receive and auto-forwarding
- Built-in AT command terminal (custom commands supported)
- Remote SSH management (requires advanced mode)
- Lightweight web console for LAN / remote access

---

### 🧩 Plugin store

UFI-TOOLS includes an online plugin store for installing additional features.
Examples:

| Category | Plugin | Description |
| --- | --- | --- |
| 🛡️ Security | ADGuardHome | Ad blocking and DNS management |
| 📊 Monitoring | Traffic status card | Live traffic and speed display |
| 🤖 Smart apps | AI dashboard | Smart monitoring dashboard |
| 🔑 Remote access | SSH tools | Remote shell entry |
| ⚙️ System control | CPU core control | Dynamic core enable/disable |
| 🎨 UI customization | Theme/layout editor | Customize theme and layout |
| 🔋 Power | Battery charge limit | Extend battery life by limiting charge threshold |
| 🏫 Networking | EasyConnect | Campus VPN support |
| ⏰ Automation | Crontab tasks | Scheduled pushes and scripts |
| 🌐 Connectivity | EasyTier | Multi-device networking across locations |

---

### 🧠 Advanced mode

Advanced mode unlocks privileged features:

- Highest system permissions available on the device
- Access to hidden/low-level management interfaces
- Full plugin store access
- Faster update channel
- Remote SSH, file push, and system-level debugging

---

### 📱 Platform compatibility

Supported installation modes:

- 📲 Magisk module (phones/tablets)
- 💻 One-click install / screen-cast install (recommended for pocket WiFi devices)

Known tested devices/environments:

- ZTE Changxing 60 / Yuanhang 60 / Changxing 60 Plus
- ZTE cloud-computing tablet (W200DS series)
- ZTE F50, U30 Air
- Other UNISOC CPU + ZTE MyOS 13 devices (likely compatible)

---

### 🌐 Remote web UI

- Built-in lightweight web server with a browser UI
- Includes:
  - Device status cards
  - Live performance monitoring
  - Plugin store
  - Network controls and debugging
- Default URL: `http://<device-ip>:2333`

---

## ⚠️ Notes

- Some features depend on the exact device model and OS version.
- Some plugins require advanced mode.
- Back up important data before using advanced mode.

---

## 📜 License

This project uses the **MIT License**.

Issues and pull requests are welcome.
