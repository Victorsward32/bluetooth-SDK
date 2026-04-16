export { bleSdk } from './sdk';
export { BleScanMode, requestBlePermissions } from './permissions';
export type { BleDevice, BleSdk } from './BleSdk.nitro';

export type {
  BleManagerBridge,
  BleManagerEvents,
  CharacteristicRef,
  ConnectOptions,
  SubscribeOptions,
  WriteMode,
} from './bridge/BleManagerBridge';
