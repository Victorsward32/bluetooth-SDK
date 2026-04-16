import type { BleManagerBridge, CharacteristicRef } from 'ble_sdk';

/**
 * Example flow:
 * 1) connect
 * 2) request higher MTU
 * 3) subscribe for notifications
 * 4) stream chunks with write without response
 */
export async function startHighThroughputSession(
  ble: BleManagerBridge,
  deviceId: string,
  streamChar: CharacteristicRef,
  payloadBase64Chunks: string[]
) {
  await ble.connect(deviceId, {
    autoReconnect: true,
    requestMaxMtu: true,
    mtu: 517,
    connectionPriority: 'high',
    phy: '2m',
  });

  const negotiatedMtu = await ble.requestMtu(deviceId, 517);
  console.log('Negotiated MTU:', negotiatedMtu);

  await ble.subscribe(streamChar, { indication: false });

  for (const chunk of payloadBase64Chunks) {
    await ble.writeCharacteristic(streamChar, chunk, 'withoutResponse');
  }
}
