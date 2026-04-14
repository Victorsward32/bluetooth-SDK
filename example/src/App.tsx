import { useEffect, useState } from 'react';
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  bleSdk,
  BleScanMode,
  requestBlePermissions,
  type BleDevice,
} from 'ble_sdk';

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return 'Something went wrong while talking to the BLE SDK.';
}

function areDevicesEqual(
  currentDevices: BleDevice[],
  nextDevices: BleDevice[]
) {
  if (currentDevices.length !== nextDevices.length) {
    return false;
  }

  return currentDevices.every((device, index) => {
    const nextDevice = nextDevices[index];
    if (!nextDevice) {
      return false;
    }

    return (
      device.address === nextDevice.address &&
      device.name === nextDevice.name &&
      device.rssi === nextDevice.rssi &&
      device.type === nextDevice.type &&
      device.bondState === nextDevice.bondState &&
      device.connectionState === nextDevice.connectionState &&
      device.serviceUuids.join(',') === nextDevice.serviceUuids.join(',')
    );
  });
}

function isPairing(device: BleDevice) {
  return device.bondStateLabel === 'Pairing...';
}

function isPaired(device: BleDevice) {
  return device.bondStateLabel === 'Paired';
}

function isConnecting(device: BleDevice) {
  return device.connectionStateLabel === 'Connecting...';
}

function isConnected(device: BleDevice) {
  return device.connectionStateLabel === 'Connected';
}

function isDisconnecting(device: BleDevice) {
  return device.connectionStateLabel === 'Disconnecting...';
}

function DeviceRow({
  device,
  onPair,
  onUnpair,
  onConnect,
  onDisconnect,
}: {
  device: BleDevice;
  onPair: (address: string) => void;
  onUnpair: (address: string) => void;
  onConnect: (address: string) => void;
  onDisconnect: (address: string) => void;
}) {
  const paired = isPaired(device);
  const pairing = isPairing(device);
  const connecting = isConnecting(device);
  const connected = isConnected(device);
  const disconnecting = isDisconnecting(device);
  const bondActionDisabled =
    pairing || connecting || connected || disconnecting;
  const connectionActionDisabled =
    !device.isBle ||
    pairing ||
    connecting ||
    disconnecting ||
    (!paired && !connected);
  const bondAction = paired ? onUnpair : onPair;
  const connectionAction = connected ? onDisconnect : onConnect;

  return (
    <View style={styles.deviceCard}>
      <View style={styles.deviceHeader}>
        <View style={styles.deviceTextBlock}>
          <Text style={styles.deviceName}>
            {device.name ?? 'Unnamed device'}
          </Text>
          <Text style={styles.deviceMeta}>{device.address}</Text>
        </View>

        <View style={styles.deviceActions}>
          <Pressable
            disabled={bondActionDisabled}
            onPress={() => bondAction(device.address)}
            style={[
              styles.actionButton,
              styles.pairButton,
              bondActionDisabled && styles.disabledButton,
              paired && styles.unpairButton,
            ]}
          >
            <Text style={styles.actionButtonText}>
              {paired ? 'Unpair' : pairing ? 'Pairing...' : 'Pair'}
            </Text>
          </Pressable>

          <Pressable
            disabled={connectionActionDisabled}
            onPress={() => connectionAction(device.address)}
            style={[
              styles.actionButton,
              styles.connectButton,
              connectionActionDisabled && styles.disabledButton,
              connected && styles.disconnectButton,
            ]}
          >
            <Text style={styles.actionButtonText}>
              {connected
                ? 'Disconnect'
                : connecting
                  ? 'Connecting...'
                  : disconnecting
                    ? 'Disconnecting...'
                    : 'Connect'}
            </Text>
          </Pressable>
        </View>
      </View>

      <Text style={styles.deviceMeta}>
        RSSI {device.rssi} | {device.typeLabel}
      </Text>
      <Text style={styles.deviceMeta}>
        Bond: {device.bondStateLabel} | Connection:{' '}
        {device.connectionStateLabel}
      </Text>
      <Text style={styles.deviceMeta}>
        Services:{' '}
        {device.serviceUuids.length > 0
          ? device.serviceUuids.join(', ')
          : 'None advertised'}
      </Text>
    </View>
  );
}

export default function App() {
  const [devices, setDevices] = useState<BleDevice[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [permissionsGranted, setPermissionsGranted] = useState(
    bleSdk.hasRequiredPermissions()
  );
  const [error, setError] = useState<string | null>(null);

  const bluetoothSupported = bleSdk.isBluetoothSupported();
  const bluetoothEnabled = bleSdk.isBluetoothEnabled();
  const canStartScan =
    bluetoothSupported && bluetoothEnabled && permissionsGranted && !isScanning;

  function refreshState() {
    const nextIsScanning = bleSdk.isScanning();
    const nextDevices = bleSdk.getDiscoveredDevices();
    const nextPermissionsGranted = bleSdk.hasRequiredPermissions();

    setIsScanning((current) =>
      current === nextIsScanning ? current : nextIsScanning
    );
    setDevices((current) =>
      areDevicesEqual(current, nextDevices) ? current : nextDevices
    );
    setPermissionsGranted((current) =>
      current === nextPermissionsGranted ? current : nextPermissionsGranted
    );
  }

  useEffect(() => {
    function refreshStateFromPoll() {
      const nextIsScanning = bleSdk.isScanning();
      const nextDevices = bleSdk.getDiscoveredDevices();
      const nextPermissionsGranted = bleSdk.hasRequiredPermissions();

      setIsScanning((current) =>
        current === nextIsScanning ? current : nextIsScanning
      );
      setDevices((current) =>
        areDevicesEqual(current, nextDevices) ? current : nextDevices
      );
      setPermissionsGranted((current) =>
        current === nextPermissionsGranted ? current : nextPermissionsGranted
      );
    }

    refreshStateFromPoll();
    const interval = setInterval(refreshStateFromPoll, 750);
    return () => clearInterval(interval);
  }, []);

  async function ensurePermissionsFor(action: string) {
    if (permissionsGranted) {
      return true;
    }

    const granted = await requestBlePermissions();
    setPermissionsGranted(granted);

    if (!granted) {
      setError(
        `Grant the required Android Bluetooth permissions to ${action}.`
      );
    }

    return granted;
  }

  async function handleRequestPermissions() {
    setError(null);
    const granted = await requestBlePermissions();
    setPermissionsGranted(granted);

    if (!granted) {
      setError('Grant the required Android Bluetooth permissions to scan.');
    }
  }

  async function handleStartScan() {
    setError(null);

    if (!(await ensurePermissionsFor('scan'))) {
      return;
    }

    try {
      bleSdk.clearDiscoveredDevices();
      bleSdk.startScan(undefined, undefined, BleScanMode.lowLatency);
      refreshState();
    } catch (scanError) {
      setError(getErrorMessage(scanError));
    }
  }

  function handleStopScan() {
    setError(null);
    bleSdk.stopScan();
    refreshState();
  }

  function handleClearDevices() {
    bleSdk.clearDiscoveredDevices();
    refreshState();
  }

  async function handlePairDevice(address: string) {
    setError(null);

    if (!(await ensurePermissionsFor('pair'))) {
      return;
    }

    try {
      const pairingStarted = bleSdk.pairDevice(address);
      if (!pairingStarted) {
        setError(`Could not start pairing for ${address}.`);
      }
      refreshState();
    } catch (pairError) {
      setError(getErrorMessage(pairError));
    }
  }

  async function handleUnpairDevice(address: string) {
    setError(null);

    if (!(await ensurePermissionsFor('unpair'))) {
      return;
    }

    try {
      const unpairStarted = bleSdk.unpairDevice(address);
      if (!unpairStarted) {
        setError(`Could not unpair ${address}.`);
      }
      refreshState();
    } catch (unpairError) {
      setError(getErrorMessage(unpairError));
    }
  }

  async function handleConnectDevice(address: string) {
    setError(null);

    if (!(await ensurePermissionsFor('connect'))) {
      return;
    }

    try {
      const connectStarted = bleSdk.connectDevice(address);
      if (!connectStarted) {
        setError(`Could not connect to ${address}.`);
      }
      refreshState();
    } catch (connectError) {
      setError(getErrorMessage(connectError));
    }
  }

  async function handleDisconnectDevice(address: string) {
    setError(null);

    if (!(await ensurePermissionsFor('disconnect'))) {
      return;
    }

    try {
      const disconnectStarted = bleSdk.disconnectDevice(address);
      if (!disconnectStarted) {
        setError(`Could not disconnect ${address}.`);
      }
      refreshState();
    } catch (disconnectError) {
      setError(getErrorMessage(disconnectError));
    }
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView contentContainerStyle={styles.container}>
        <Text style={styles.eyebrow}>BLE SDK Example</Text>
        <Text style={styles.title}>Android BLE flow wired through Nitro</Text>
        <Text style={styles.description}>
          Advertising is intentionally excluded. This example covers permission
          checks, scan control, pairing, unpairing, and live BLE
          connect/disconnect state from the native facade.
        </Text>

        <View style={styles.statusPanel}>
          <Text style={styles.statusLine}>
            Bluetooth supported: {bluetoothSupported ? 'Yes' : 'No'}
          </Text>
          <Text style={styles.statusLine}>
            Bluetooth enabled: {bluetoothEnabled ? 'Yes' : 'No'}
          </Text>
          <Text style={styles.statusLine}>
            Permissions granted: {permissionsGranted ? 'Yes' : 'No'}
          </Text>
          <Text style={styles.statusLine}>
            Scan status: {isScanning ? 'Running' : 'Idle'}
          </Text>
          <Text style={styles.statusLine}>
            Devices discovered: {devices.length}
          </Text>
        </View>

        {error ? <Text style={styles.errorText}>{error}</Text> : null}

        <View style={styles.buttonRow}>
          <Pressable
            onPress={handleRequestPermissions}
            style={styles.secondaryButton}
          >
            <Text style={styles.secondaryButtonText}>Request Permissions</Text>
          </Pressable>

          <Pressable
            disabled={!canStartScan}
            onPress={handleStartScan}
            style={[
              styles.primaryButton,
              !canStartScan && styles.disabledButton,
            ]}
          >
            <Text style={styles.primaryButtonText}>Start Scan</Text>
          </Pressable>
        </View>

        <View style={styles.buttonRow}>
          <Pressable
            disabled={!isScanning}
            onPress={handleStopScan}
            style={[
              styles.secondaryButton,
              !isScanning && styles.disabledButton,
            ]}
          >
            <Text style={styles.secondaryButtonText}>Stop Scan</Text>
          </Pressable>

          <Pressable onPress={handleClearDevices} style={styles.ghostButton}>
            <Text style={styles.ghostButtonText}>Clear Devices</Text>
          </Pressable>
        </View>

        <View style={styles.listSection}>
          {devices.length === 0 ? (
            <Text style={styles.emptyState}>
              No BLE devices discovered yet. Start a scan to populate results.
            </Text>
          ) : (
            devices.map((device) => (
              <DeviceRow
                key={device.address}
                device={device}
                onPair={handlePairDevice}
                onUnpair={handleUnpairDevice}
                onConnect={handleConnectDevice}
                onDisconnect={handleDisconnectDevice}
              />
            ))
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#f4efe7',
  },
  container: {
    paddingHorizontal: 20,
    paddingVertical: 24,
    gap: 16,
  },
  eyebrow: {
    color: '#7a5c3e',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  title: {
    color: '#1f1c19',
    fontSize: 30,
    fontWeight: '800',
    lineHeight: 36,
  },
  description: {
    color: '#4f4941',
    fontSize: 16,
    lineHeight: 22,
  },
  statusPanel: {
    backgroundColor: '#fffaf3',
    borderColor: '#ddc6a7',
    borderRadius: 18,
    borderWidth: 1,
    gap: 8,
    padding: 16,
  },
  statusLine: {
    color: '#2d2824',
    fontSize: 15,
  },
  errorText: {
    color: '#8b1e1e',
    fontSize: 15,
    fontWeight: '600',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
  },
  primaryButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: '#1f6f5f',
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  secondaryButton: {
    flex: 1,
    alignItems: 'center',
    backgroundColor: '#d7c3a2',
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  ghostButton: {
    flex: 1,
    alignItems: 'center',
    borderColor: '#9b8361',
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  disabledButton: {
    opacity: 0.45,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '700',
  },
  secondaryButtonText: {
    color: '#2d2824',
    fontSize: 14,
    fontWeight: '700',
  },
  ghostButtonText: {
    color: '#5a4c3e',
    fontSize: 14,
    fontWeight: '700',
  },
  listSection: {
    gap: 12,
    paddingBottom: 24,
  },
  emptyState: {
    color: '#5e574f',
    fontSize: 15,
    lineHeight: 21,
  },
  deviceCard: {
    backgroundColor: '#fffdf8',
    borderColor: '#e5d4bc',
    borderRadius: 18,
    borderWidth: 1,
    gap: 6,
    padding: 16,
  },
  deviceHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: 12,
    justifyContent: 'space-between',
  },
  deviceTextBlock: {
    flex: 1,
    gap: 4,
  },
  deviceActions: {
    alignItems: 'stretch',
    gap: 8,
    minWidth: 108,
  },
  deviceName: {
    color: '#201d1a',
    fontSize: 17,
    fontWeight: '700',
  },
  deviceMeta: {
    color: '#5c554d',
    fontSize: 13,
    lineHeight: 18,
  },
  actionButton: {
    alignItems: 'center',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  pairButton: {
    backgroundColor: '#214d8a',
  },
  unpairButton: {
    backgroundColor: '#8a5f21',
  },
  connectButton: {
    backgroundColor: '#1f6f5f',
  },
  disconnectButton: {
    backgroundColor: '#8b1e1e',
  },
  actionButtonText: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '700',
  },
});
