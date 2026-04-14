import type { BleDevice, BleSdk } from './BleSdk.nitro';

function unsupported(operation: string): never {
  throw new Error(`${operation} is only available on native platforms.`);
}

const emptyDevices: BleDevice[] = [];

export const bleSdk: BleSdk = {
  name: 'BleSdk',
  toString() {
    return '[HybridObject BleSdk]';
  },
  equals(other) {
    return other === this;
  },
  dispose() {},
  isBluetoothSupported() {
    return false;
  },
  isBluetoothEnabled() {
    return false;
  },
  isScanning() {
    return false;
  },
  getRequiredPermissions() {
    return [];
  },
  hasRequiredPermissions() {
    return false;
  },
  startScan() {
    unsupported('bleSdk.startScan()');
  },
  stopScan() {},
  pairDevice() {
    unsupported('bleSdk.pairDevice()');
  },
  unpairDevice() {
    unsupported('bleSdk.unpairDevice()');
  },
  connectDevice() {
    unsupported('bleSdk.connectDevice()');
  },
  disconnectDevice() {
    unsupported('bleSdk.disconnectDevice()');
  },
  clearDiscoveredDevices() {},
  getDiscoveredDevices() {
    return emptyDevices;
  },
};
