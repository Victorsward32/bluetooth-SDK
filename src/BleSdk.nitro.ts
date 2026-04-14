import type { HybridObject } from 'react-native-nitro-modules';

export interface BleDevice {
  name?: string;
  address: string;
  type: number;
  typeLabel: string;
  bondState: number;
  bondStateLabel: string;
  connectionState: number;
  connectionStateLabel: string;
  rssi: number;
  isBle: boolean;
  serviceUuids: string[];
}

export interface BleSdk extends HybridObject<{
  ios: 'swift';
  android: 'kotlin';
}> {
  isBluetoothSupported(): boolean;
  isBluetoothEnabled(): boolean;
  isScanning(): boolean;
  getRequiredPermissions(): string[];
  hasRequiredPermissions(): boolean;
  startScan(serviceUuid?: string, deviceName?: string, scanMode?: number): void;
  stopScan(): void;
  pairDevice(address: string): boolean;
  unpairDevice(address: string): boolean;
  connectDevice(address: string): boolean;
  disconnectDevice(address: string): boolean;
  clearDiscoveredDevices(): void;
  getDiscoveredDevices(): BleDevice[];
}
