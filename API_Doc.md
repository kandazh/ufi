## 0. Before You Start

> **This API document applies to `UFI-TOOLS v3.1.5`.**
> **All `POST` request bodies in this document (except official APIs) are `JSON`.**
> **All `GET` parameters in this document are passed as `query` parameters.**

## 1. Request Signature Rules

The signing mechanism provides:

- Prevents request forgery (cross-site, replay, etc.)
- Allows the server to verify whether `kano-sign` is valid and matches `kano-t`
- A simple approach for "authentication + tamper resistance"

### 1. Add request headers

Each request automatically includes these custom headers:

| Header | Description |
| --- | --- |
| `kano-t` | Current timestamp (milliseconds, `Date.now()`) |
| `kano-sign` | Signature string used to validate the request |
| `Authorization` | SHA-256 hash of the password (lowercase hex) |

---

### 2. Signature calculation

The core formula is:

```
kano-sign = SHA256( SHA256(part1) + SHA256(part2) )
```

Steps:

#### (1) Build raw data

```js
rawData = "minikano" + HTTP_METHOD + URL_PATH + timestamp
```

- `HTTP_METHOD`: request method, e.g. `GET` / `POST` (uppercase)
- `URL_PATH`: request path without query parameters, e.g. `/api/data`
- `timestamp`: `Date.now()` (milliseconds)

#### (2) First step: HMAC-MD5

```js
hmac = HMAC_MD5(rawData, secretKey)
```

Fixed secret key:

```js
"minikano_kOyXz0Ciz4V7wR0IeKmJFYFQ20jd"
```

#### (3) Split the HMAC bytes into two halves

- `part1`: first half of bytes
- `part2`: second half of bytes

#### (4) SHA-256 both parts

```js
sha1 = SHA256(part1)
sha2 = SHA256(part2)
```

#### (5) Concatenate and SHA-256 again

```js
finalHash = SHA256(sha1 + sha2)
```

---

### 3. Example

Assume the request is:

```js
fetch("/api/user?id=123", { method: "POST" });
```

Internal steps:

- Method: `POST`
- Path: `/api/user`
- Timestamp example: `1718438543772`
- Raw data:

  ```
  minikanoPOST/api/user1718438543772
  ```

- Generate the signature and attach headers:

```http
kano-t: 1718438543772
kano-sign: <computed SHA-256 hash>
```

JS reference: https://github.com/kanoqwq/UFI-TOOLS/blob/http-server-version/app/frontEnd/public/script/requests.js

## 2. API Examples

> Note: In this document, `POST` request bodies are JSON (unless it is an official API). `GET` requests use query parameters or no parameters.

**GET example**

```
GET /api/AT?command=AT+CSQ&slot=0
```

Response:

```json
{
  "result": "XXXXXX OK"
}
```

**POST example (official API)**

```
POST http://192.168.1.1/goform/login
Authorization: sha256(password)
Content-Type: application/json
{ "username": "admin", "password": "123456" }
```

Response:

```json
{
  "result": "success"
}
```

---

### ADB Module

| Method | Path | Description | Params | Auth |
| --- | --- | --- | --- | --- |
| GET | `/api/adb_wifi_setting` | Get network ADB auto-start status | None | Yes |
| POST | `/api/adb_wifi_setting` | Set network ADB auto-start | `enabled`, `password` | Yes |
| GET | `/api/adb_alive` | Check whether network ADB is running | None | Yes |

---

### Advanced Tools Module

| Method | Path | Description | Params (brief) | Auth |
| --- | --- | --- | --- | --- |
| GET | `/api/smbPath` | Toggle Samba share mapping to root-style share folders | `enable=1/0` | Yes |
| GET | `/api/hasTTYD` | Check whether the ttyd service is available | `port=<port>` | Yes |
| GET | `/api/one_click_shell` | One-click: enter engineer mode and run script | None | Yes |
| POST | `/api/root_shell` | Send a command to Root Shell Socket | JSON: `{ "command": "..." }` | Yes |

---

### Any Proxy Module

This reverse proxy forwards the client request to the specified upstream and returns the upstream response.
Path format:

```shell
GET /api/proxy/--http://example.com/api/xxx
```

Supported methods: `GET` `POST` `PUT` `PATCH`

The request body (e.g., JSON for POST) is forwarded as-is.

Notes:

1. This endpoint also requires auth verification.
2. To avoid collisions between UFI-TOOLS auth headers and upstream auth headers, you can pass the proxy auth token via `kano-authorization` (see table below).
3. To avoid exposing LAN services to WAN, the proxy blocks access to private/internal addresses by default.

---

#### Custom headers (auto-forward)

- Common safe headers are forwarded by default (e.g., `Accept`, `User-Agent`).
- If you need to inject sensitive headers (e.g., `Authorization`), use the `kano-` prefix:

| Custom header | Forwarded as |
| --- | --- |
| `kano-Authorization` | `Authorization` |
| `kano-Cookie` | `Cookie` |

---

#### Response handling

- Normal responses: returned as-is (status code, Content-Type, etc.)
- HTML responses: resource paths that start with `/` (e.g., `/static/js/app.js`) are rewritten to the proxy path so pages can load assets correctly

---

#### Example

```http
POST /api/proxy/--http://192.168.1.1/goform/login
Content-Type: application/json
kano-Authorization: Bearer abc123

{ "username": "admin", "password": "123456" }
```

Will be forwarded as:

```http
POST http://192.168.1.1/goform/login
Authorization: Bearer abc123
Content-Type: application/json

{ "username": "admin", "password": "123456" }
```

---

### AT Module

| Method | Path | Description | Params (brief) | Auth |
| --- | --- | --- | --- | --- |
| GET | `/api/AT` | Execute an AT command and return the result | `command=<AT command>` (required), `slot=<SIM slot>` (default 0) | Yes |

---

### Base Device Info Module

| Method | Path | Description | Params | Auth |
| --- | --- | --- | --- | --- |
| GET | `/api/baseDeviceInfo` | Get base device info (battery, IP, CPU, memory, storage, etc.) | None | Yes |
| GET | `/api/version_info` | Get app version and device model | None | No |
| GET | `/api/need_token` | Check whether login verification (token) is enabled | None | No |

---

The `otaModule` is a complete OTA (Over-The-Air) update module. It uses Ktor as the backend web service and runs on Android (e.g., embedded devices or phones). It includes the endpoints below.

---

### OTA Module

| Method | Path | Description | Params | Auth | Notes |
| --- | --- | --- | --- | --- | --- |
| GET | `/api/check_update` | Fetch changelog and file list | None | Yes | Calls Alist API to get OTA package info |
| POST | `/api/download_apk` | Start downloading an APK | `{apk_url}` | Yes | Downloads in a background thread; supports status query |
| GET | `/api/download_apk_status` | Get download progress and status | None | Yes | Status, percentage, error message |
| POST | `/api/install_apk` | Install downloaded APK | None | Yes | Uses socat (root) or ADB (non-root) |

---

### Plugins Module

| Method | Path | Description | Params | Auth |
| --- | --- | --- | --- | --- |
| POST | `/api/set_custom_head` | Set custom head text | JSON: `{ "text": "..." }` (max 1145KB) | Yes |
| GET | `/api/get_custom_head` | Get custom head text | None | No |

---

### SMS Forward Module

| Method | Path | Description | Params | Auth |
| --- | --- | --- | --- | --- |
| GET | `/api/sms_forward_method` | Get current forwarding method | None | Yes |
| POST | `/api/sms_forward_mail` | Configure email forwarding | `{smtp_host, smtp_port, smtp_to, smtp_username, smtp_password}` | Yes |
| GET | `/api/sms_forward_mail` | Get email forwarding config | None | Yes |
| POST | `/api/sms_forward_curl` | Configure curl forwarding | `{curl_text}` (must include `{{sms-body}}`, `{{sms-time}}`, `{{sms-from}}`) | Yes |
| GET | `/api/sms_forward_curl` | Get curl forwarding config | None | Yes |
| POST | `/api/sms_forward_dingtalk` | Configure DingTalk webhook forwarding | `{webhook_url, secret}` (`secret` is optional, for signing) | Yes |
| GET | `/api/sms_forward_dingtalk` | Get DingTalk webhook config | None | Yes |
| POST | `/api/sms_forward_enabled` | Set global forwarding switch | Query: `enable` (string) | Yes |
| GET | `/api/sms_forward_enabled` | Get forwarding enabled status | None | Yes |

---

### Speedtest Module

| Method | Path | Description | Params | Auth |
| --- | --- | --- | --- | --- |
| GET | `/api/speedtest` | Download test data (rate-limited) | Query: `ckSize` (chunk count), optional `cors` | Yes |

---

### Theme Module

| Method | Path | Description | Params (brief) | Auth |
| --- | --- | --- | --- | --- |
| POST | `/api/upload_img` | Upload an image and return a URL | Multipart form, image file | Yes |
| POST | `/api/delete_img` | Delete an image | JSON: `file_name` | Yes |
| POST | `/api/set_theme` | Save theme config | JSON theme fields (e.g. `backgroundEnabled`, `textColor`, etc.) | Yes |
| GET | `/api/get_theme` | Get current theme config | None | No |

---

#### Additional notes

- Uploaded images are saved under `filesDir/uploads/`. They are served statically at `/api/uploads/<filename>`.

---

### Official Web Reverse Proxy Module

| Method | Path | Description | Params | Auth |
| --- | --- | --- | --- | --- |
| All | `/api/goform/{...}` | Reverse-proxy official Web API | Path + query + body (POST/PUT) | No additional auth |

---

#### Details

- **Path rule**: any request starting with `/api/goform/` is proxied.
- **Target server**: specified via `targetServerIP` (e.g. `192.168.0.1`), and forwarded to `http://targetServerIP`.
- **Header forwarding**: all request headers except `Host` and `Referer` are forwarded. `Referer` is forced to the target server address.
- **Methods**: supports forwarding `GET`, `POST`, `PUT`, `OPTIONS`.
- **Body**: `POST` and `PUT` bodies are read and forwarded.
- **Response header handling**:
  - `Set-Cookie` from upstream is renamed to `kano-cookie` and forwarded back.
  - CORS response headers are added automatically.
- **Error handling**: all exceptions are caught; responds with HTTP 500 and error details.