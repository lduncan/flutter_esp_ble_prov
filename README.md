
# flutter_esp_ble_prov

Note: Forked from https://pub.dev/packages/esp_bluetooth_provisioning according
to MIT license. That package doesn't provide a github repository

# Original README esp_bluetooth_provisioning

A cross platform package for flutter to provision device.

## Getting Started


### Installation

```
dependencies:
  esp_bluetooth_provisioning:
```

#### Android Configuration

Make use you have flutter_embedding v2 enabled. Add the following code on the manifest file inside `<application>` tag to enable embedding.

```
<meta-data
    android:name="flutterEmbedding"
    android:value="2" />
```

Also, use `io.flutter.embedding.android.FlutterActivity` as your FlutterActivity

### Bluetooth Permission

#### Ask permission

```
    EspBleProvisioning().requestPermission();
```

### Bluetooth Adapter State



### Scanning

Start Scanning on foreground.

```
    EspBleProvisioning().scanBluetoothDevice()
```

## Connecting to device

```
   EspBleProvisioning().connectToBluetoothDevice()
```

### Provisiong device
```
    EspBleProvisioning().startProvisioning()
```
