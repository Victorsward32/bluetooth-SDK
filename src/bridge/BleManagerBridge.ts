import { NativeEventEmitter } from 'react-native';

export type DeviceId = string;

export type WriteMode = 'withResponse' | 'withoutResponse';

export interface CharacteristicRef {
  deviceId: DeviceId;
  serviceUuid: string;
  characteristicUuid: string;
}

export interface ConnectOptions {
  autoReconnect?: boolean;
  requestMaxMtu?: boolean;
  mtu?: number;
  connectionPriority?: 'balanced' | 'high' | 'lowPower';
  phy?: '1m' | '2m' | 'coded';
}

export interface SubscribeOptions {
  indication?: boolean;
}

export interface BleDiscoveredDevice {
  id: DeviceId;
  name?: string;
  rssi: number;
  isConnectable?: boolean;
  manufacturerDataBase64?: string;
  serviceUuids?: string[];
}

export interface BleManagerEvents {
  deviceDiscovered: (device: BleDiscoveredDevice) => void;
  scanStateChanged: (isScanning: boolean) => void;
  connectionStateChanged: (event: {
    deviceId: DeviceId;
    state: 'connecting' | 'connected' | 'disconnecting' | 'disconnected';
    status?: number;
  }) => void;
  characteristicNotification: (event: {
    characteristic: CharacteristicRef;
    valueBase64: string;
    sequence?: number;
  }) => void;
  operationError: (event: {
    deviceId: DeviceId;
    operation: string;
    code: string;
    message: string;
    status?: number;
    retriable?: boolean;
  }) => void;
}

/**
 * Production bridge contract for BLE commands.
 *
 * Notes:
 * - Commands are Promise based.
 * - Async state and data updates are emitted via event emitter.
 */
export interface BleManagerBridge {
  scanDevices(filter?: { serviceUuid?: string; name?: string }): Promise<void>;
  stopScan(): Promise<void>;

  connect(deviceId: DeviceId, options?: ConnectOptions): Promise<void>;
  disconnect(deviceId: DeviceId): Promise<void>;

  requestMtu(deviceId: DeviceId, mtu: number): Promise<number>;

  readCharacteristic(charRef: CharacteristicRef): Promise<string>;
  writeCharacteristic(
    charRef: CharacteristicRef,
    valueBase64: string,
    mode: WriteMode
  ): Promise<void>;

  subscribe(
    charRef: CharacteristicRef,
    options?: SubscribeOptions
  ): Promise<void>;
  unsubscribe(charRef: CharacteristicRef): Promise<void>;

  startStream(charRef: CharacteristicRef): Promise<void>;
  stopStream(charRef: CharacteristicRef): Promise<void>;
}

export type BleTypedEventEmitter = NativeEventEmitter;
