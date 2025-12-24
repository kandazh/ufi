<div style="display:flex;justify-content:center">
<img src="https://kanokano.cn/wp-content/uploads/2025/04/5acb8625d65a3fd5d7b228830a9450a1.webp" style="width:50%;text-align:center" />
</div>

## Features

- Remote management (requires a LAN tunneling solution)
- SMS send/receive
- SMS forwarding
- AT command sending
- LAN speed test
- Theme and background customization
- Real-time metrics (QCI rate, CPU temperature, memory load, signal strength, SNR, PCI, cell ID, band, IPv6 address, etc.)
- Band lock and cell lock (no reboot)
- Auto-enable USB debugging and network USB debugging
- Works in two modes: install on a phone (client) or install on F50 as the server
- Auto-start on boot
- One-click OTA
- Performance mode, LED control, file sharing switch
- 3G/4G/5G mode switching
- More features may be added over time

| ![](img/1.jpg) | ![](img/2.jpg) |
| --- | --- |

| ![](img/3.jpg) | ![](img/4.jpg) |
| --- | --- |

## How to use

### Android

1. Download the APK, install it on your phone, and open it.
2. Make sure your phone and the pocket WiFi are on the same network. Open the control page, sign in, and enable ADB.
3. Use ADB (from a PC or phone) to connect to the pocket WiFi and install the APK on the pocket WiFi device.
4. Use scrcpy (or similar) to start `zte-ufi-tools`, configure the gateway, start the service, disable battery optimizations, and enable notifications (to ensure auto-start works reliably).
5. Open `http://<pocket-wifi-ip>:2333` from your phone.

### iOS

iOS requires enabling ADB using the traditional method:

- Connect to WiFi and open `http://192.168.0.1/index.html#usb_port` to enable ADB.
- Then continue from Android step 3.

Notes:

- Feature availability depends on your device model and firmware. A known good tested version was `MU300_ZYV1.0.0B09`.
- Some metrics (CPU usage/temperature/memory) may come from the phone if you run the APK on a phone rather than on the pocket WiFi.

Download link: https://www.123684.com/s/7oa5Vv-R05D3

Code: `2333`

API docs: https://kanokano.cn/wp-content/uploads/2025/06/UFI-TOOLSAPI%E6%96%87%E6%A1%A3.html
