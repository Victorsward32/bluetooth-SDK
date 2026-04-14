# ble_sdk

React Native BLE SDK built with Nitro Modules.

## Installation

```sh
npm install ble_sdk react-native-nitro-modules

> `react-native-nitro-modules` is required as this library relies on [Nitro Modules](https://nitro.margelo.com/).
```

## Usage

```tsx
import { bleSdk, BleScanMode, requestBlePermissions } from 'ble_sdk';

async function scanForDevices() {
  const granted = await requestBlePermissions();
  if (!granted) return;

  bleSdk.clearDiscoveredDevices();
  bleSdk.startScan(undefined, undefined, BleScanMode.lowLatency);

  const devices = bleSdk.getDiscoveredDevices();
  console.log(devices);

  if (devices[0]) {
    bleSdk.pairDevice(devices[0].address);
    bleSdk.connectDevice(devices[0].address);
    bleSdk.disconnectDevice(devices[0].address);
    bleSdk.unpairDevice(devices[0].address);
  }
}
```

## Current scope

- Android: BLE scan, pairing/unpairing, and live GATT connect/disconnect flow are implemented through `BleFacade`.
- iOS: the same API surface exists, but scanning currently throws an unsupported-platform error.
- Advertising is intentionally not implemented.

## BLE API

- `bleSdk.isBluetoothSupported()`
- `bleSdk.isBluetoothEnabled()`
- `bleSdk.hasRequiredPermissions()`
- `bleSdk.getRequiredPermissions()`
- `bleSdk.startScan(serviceUuid?, deviceName?, scanMode?)`
- `bleSdk.stopScan()`
- `bleSdk.pairDevice(address)`
- `bleSdk.unpairDevice(address)`
- `bleSdk.connectDevice(address)`
- `bleSdk.disconnectDevice(address)`
- `bleSdk.clearDiscoveredDevices()`
- `bleSdk.getDiscoveredDevices()`

`BleScanMode.lowPower`, `BleScanMode.balanced`, and `BleScanMode.lowLatency` are exported for Android scan configuration.

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
