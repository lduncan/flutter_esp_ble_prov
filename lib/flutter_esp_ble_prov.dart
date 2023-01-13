
import 'flutter_esp_ble_prov_platform_interface.dart';

class FlutterEspBleProv {
  Future<String?> getPlatformVersion() {
    return FlutterEspBleProvPlatform.instance.getPlatformVersion();
  }
}
