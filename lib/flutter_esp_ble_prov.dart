import 'src/flutter_esp_ble_prov_platform_interface.dart';

class FlutterEspBleProv {
  Future<String?> getPlatformVersion() {
    return FlutterEspBleProvPlatform.instance.getPlatformVersion();
  }

  Future<List<String>> scanBleDevices(String prefix) {
    return FlutterEspBleProvPlatform.instance.scanBleDevices(prefix);
  }

  Future<List<String>> scanWifiNetworks(
      String deviceName, String proofOfPosession) {
    return FlutterEspBleProvPlatform.instance
        .scanWifiNetworks(deviceName, proofOfPosession);
  }

  Future<bool> provisionWifi(String deviceName, String proofOfPosession,
      String ssid, String passphrase) {
    return FlutterEspBleProvPlatform.instance
        .provisionWifi(deviceName, proofOfPosession, ssid, passphrase);
  }
}
