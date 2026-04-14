import { NitroModules } from 'react-native-nitro-modules';
import type { BleSdk } from './BleSdk.nitro';

const hybridBleSdk = NitroModules.createHybridObject<BleSdk>('BleSdk');

const requiredMethods = new Set<keyof BleSdk>([
  'isBluetoothSupported',
  'isBluetoothEnabled',
  'isScanning',
  'getRequiredPermissions',
  'hasRequiredPermissions',
  'startScan',
  'stopScan',
  'pairDevice',
  'unpairDevice',
  'connectDevice',
  'disconnectDevice',
  'clearDiscoveredDevices',
  'getDiscoveredDevices',
]);

function throwMissingMethodError(methodName: string): never {
  throw new Error(
    `bleSdk.${methodName}() is unavailable in the current native build. Rebuild the app after changing BleSdk.nitro.ts or native BleSdk methods.`
  );
}

// Nitro methods can go missing if the JavaScript interface changes before the app is rebuilt.
export const bleSdk = new Proxy(hybridBleSdk, {
  get(target, prop, receiver) {
    const value = Reflect.get(target, prop, receiver);

    if (
      typeof prop === 'string' &&
      requiredMethods.has(prop as keyof BleSdk) &&
      typeof value !== 'function'
    ) {
      return () => throwMissingMethodError(prop);
    }

    return typeof value === 'function' ? value.bind(target) : value;
  },
}) as BleSdk;
