import { PermissionsAndroid, Platform, type Permission } from 'react-native';
import { bleSdk } from './sdk';

export const BleScanMode = {
  lowPower: 0,
  balanced: 1,
  lowLatency: 2,
} as const;

export async function requestBlePermissions(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    return true;
  }

  const permissions = bleSdk.getRequiredPermissions() as Permission[];

  if (permissions.length === 0) {
    return true;
  }

  const results = await PermissionsAndroid.requestMultiple(permissions);
  return permissions.every(
    (permission) => results[permission] === PermissionsAndroid.RESULTS.GRANTED
  );
}
