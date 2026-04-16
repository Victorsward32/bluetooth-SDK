# Production BLE SDK Architecture (React Native + Android/iOS)

This document describes a production-focused architecture for high-throughput, reliable BLE communication with clean native-module boundaries.

## 1) Architecture

### Core layers

1. **BLEManager (Facade / API surface)**
   - Owns scan, connect, disconnect, read/write/subscribe operations.
   - Converts native callbacks into bridge events.
2. **Scanner**
   - Dedicated scan filters, scan mode, and dedupe policy.
3. **Connection Handler (per device session)**
   - Owns `BluetoothGatt` (Android) / `CBPeripheral` (iOS).
   - Maintains connection state, service discovery, MTU/PHY/priority.
4. **Operation Queue (single-flight)**
   - Serializes GATT operations.
   - Handles timeout/retry and maps callback completion to queued operation.
5. **Stream codec / chunker**
   - MTU-aware segmentation and framing for large payloads.

### Data patterns

- **Request/Response**: read + write-with-response.
- **Fire-and-forget**: write-without-response for throughput.
- **Streaming**: notifications preferred (indications for reliability).

## 2) Android modules added

- `GattOperationQueue.kt`
  - Single-flight queue for BLE operations.
  - Timeout/retry support.
  - Structured operation errors.
- `MtuChunker.kt`
  - Calculates ATT payload size (`mtu - 3`).
  - Splits payload for long writes and streaming.
- `StreamFrameCodec.kt`
  - Frame encoder/decoder with sequence support.
  - Supports fragmented notification stream reconstruction.

## 3) Bridge contract (TypeScript)

`src/bridge/BleManagerBridge.ts` defines a production-ready RN interface:

- Promise APIs for commands:
  - `scanDevices`, `connect`, `requestMtu`, `readCharacteristic`, `writeCharacteristic`, `subscribe`.
- Event model:
  - scan state, connection state, notification packets, operation errors.
- Throughput-aware controls:
  - write mode (`withResponse` / `withoutResponse`)
  - subscribe mode (`notification` vs `indication`)
  - connection options (priority, phy, request max mtu)

## 4) Recommended production behavior

1. Connect → discover services → request MTU (up to 517) → request high priority for streaming.
2. For streaming TX:
   - Prefer write-without-response.
   - Chunk by ATT payload (`mtu - 3`).
3. For streaming RX:
   - Prefer notifications.
   - Reassemble chunks using `StreamFrameCodec.Decoder`.
4. On error:
   - Retry transient failures (busy, timeout) with bounded attempts.
   - Surface operation failures to JS through `operationError` event.

## 5) iOS parity guidance

Mirror same layers with:

- `CBPeripheralDelegate` callback-driven operation completion.
- Serial operation queue per peripheral.
- `maximumWriteValueLength(for:)` for chunk sizing.
- `setNotifyValue(_:for:)` for notifications/indications.
- State restoration + reconnect policy for production resiliency.

