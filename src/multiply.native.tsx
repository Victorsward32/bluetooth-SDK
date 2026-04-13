import { NitroModules } from 'react-native-nitro-modules';
import type { BleSdk } from './BleSdk.nitro';

const BleSdkHybridObject =
  NitroModules.createHybridObject<BleSdk>('BleSdk');

export function multiply(a: number, b: number): number {
  return BleSdkHybridObject.multiply(a, b);
}
