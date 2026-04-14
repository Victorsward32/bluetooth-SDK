import Foundation

private enum BleSdkError: LocalizedError {
    case unsupportedPlatform

    var errorDescription: String? {
        return "BLE scan, pair, and connection flow is currently implemented only on Android."
    }
}

class BleSdk: HybridBleSdkSpec {
    private var discoveredDevices: [BleDevice] = []
    private var scanning = false

    public func isBluetoothSupported() throws -> Bool {
        return false
    }

    public func isBluetoothEnabled() throws -> Bool {
        return false
    }

    public func isScanning() throws -> Bool {
        return scanning
    }

    public func getRequiredPermissions() throws -> [String] {
        return []
    }

    public func hasRequiredPermissions() throws -> Bool {
        return true
    }

    public func startScan(
        serviceUuid: String?,
        deviceName: String?,
        scanMode: Double?
    ) throws {
        scanning = false
        throw BleSdkError.unsupportedPlatform
    }

    public func stopScan() throws {
        scanning = false
    }

    public func pairDevice(address: String) throws -> Bool {
        throw BleSdkError.unsupportedPlatform
    }

    public func unpairDevice(address: String) throws -> Bool {
        throw BleSdkError.unsupportedPlatform
    }

    public func connectDevice(address: String) throws -> Bool {
        throw BleSdkError.unsupportedPlatform
    }

    public func disconnectDevice(address: String) throws -> Bool {
        throw BleSdkError.unsupportedPlatform
    }

    public func clearDiscoveredDevices() throws {
        discoveredDevices.removeAll()
    }

    public func getDiscoveredDevices() throws -> [BleDevice] {
        return discoveredDevices
    }
}
