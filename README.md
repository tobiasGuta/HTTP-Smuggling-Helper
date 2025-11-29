# HTTP Smuggling Helper (Burp Suite Extension)

**A Context-Menu Utility for HTTP Request Smuggling & H2 Desync Attacks**

![Java](https://img.shields.io/badge/Java-21-orange)
![Burp Suite](https://img.shields.io/badge/Burp%20Suite-Montoya%20API-blue)
![License](https://img.shields.io/badge/License-MIT-green)

## Overview
**Smuggling Helper** is a Burp Suite extension designed to automate the tedious math and header manipulation required for **HTTP Request Smuggling** (CL.TE, TE.CL) and **HTTP/2 Desync** attacks.

It solves two specific problems:
1.  **The Math:** Instantly calculates Hex chunk sizes (for CL.TE) or Decimal body lengths (for H2.CL) without leaving the Repeater tab.
2.  **The "Auto-Fix" Problem:** Burp Suite Repeater automatically "fixes" the `Content-Length` header before sending requests. This extension includes a **Traffic Interceptor** that allows you to force a specific `Content-Length` (like 0) by bypassing Burp's internal safety checks.

## Features

The extension adds a **Right-Click Context Menu** to the Request Editor (Repeater/Intruder) with three powerful tools:

### 1. Chunkify Selection (Hex / CL.TE)
Converts the highlighted text into a valid HTTP/1.1 Chunk.
* **Use Case:** **CL.TE** attacks or **H2.TE** downgrades.
* **Action:** Calculates the byte length, converts it to Hexadecimal, and pre-pends it to the selection.
    ```text
    [Before] admin=true
    [After]  a\r\nadmin=true
    ```

### 2. Insert Content-Length (True Length)
Calculates the exact byte length of the selection and inserts a standard `Content-Length` header.
* **Use Case:** **TE.CL** attacks or constructing nested requests for **H2.CL**.
* **Action:**
    ```text
    Content-Length: 11\r\n\r\nadmin=true
    ```

### 3. Insert Desync Header (CL: 0 + Bypass)
**The "Desync" Button.** This is used for **H2.CL Tunneling** and **Desync** attacks where you need to lie to the backend about the request length.
* **Use Case:** H2.CL Request Tunneling, Cache Poisoning, or WAF Bypasses.
* **Action:** Inserts `Content-Length: 0` and a special "Magic Header" (`X-Smuggle-Ignore: true`).
* **The Bypass:** The extension listens to outgoing traffic. When it sees the magic header, it **deletes it** and forces the `Content-Length` to `0` at the network level, overriding Burp's default behavior.

## Installation

### Prerequisites
* Java Development Kit (JDK) 21.
* Burp Suite (Community or Professional).
* Gradle.

### Build from Source
1.  Clone the repository:
    ```bash
    git clone https://github.com/tobiasGuta/HTTP-Smuggling-Helper.git
    cd Burp-Smuggling-Helper
    ```
2.  Build the JAR:
    ```bash
    ./gradlew clean jar
    ```
3.  Load into Burp:
    * Go to **Extensions** -> **Installed**.
    * Click **Add** -> Select `build/libs/ChunkCalculator.jar`.

## Usage Guide

### Scenario: H2.CL Request Tunneling
You want to smuggle a request to `/admin` inside a wrapper request to `/hello`.

1.  Open **Repeater**. ensure protocol is **HTTP/2**.
2.  Create your wrapper and inner request:
    ```http
    POST /hello HTTP/2
    Host: target.com

    GET /admin HTTP/1.1
    Host: localhost
    Foo: bar
    ```
3.  **Highlight** the inner request (`GET /admin...`).
4.  **Right-Click** the selection -> **Insert Desync Header (CL: 0 + Bypass)**.
5.  The request will update to include `X-Smuggle-Ignore: true`.
6.  Click **Send**.
    * *Note:* Burp Repeater history will still show `Content-Length: [Real Length]`. This is normal.
    * **Verification:** Check **Logger** tab to confirm `Content-Length: 0` was sent to the wire.

## Tech Stack
* **Language:** Java 21
* **API:** Burp Suite Montoya API
* **Components:** `ContextMenuItemsProvider` (UI), `HttpHandler` (Traffic Manipulation).

## Disclaimer
This tool is for educational purposes and authorized security testing only. Do not use this tool on systems you do not have permission to test.
