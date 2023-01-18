package how.virc.flutter_esp_ble_prov


import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.WiFiAccessPoint
import com.espressif.provisioning.listeners.BleScanListener
import com.espressif.provisioning.listeners.ProvisionListener
import com.espressif.provisioning.listeners.WiFiScanListener
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class BleConnector(val device: BluetoothDevice, scanResult: ScanResult) {

  val primaryServiceUuid: String

  init {
    primaryServiceUuid = scanResult.scanRecord?.serviceUuids?.get(0)?.toString() ?: ""
  }
}


class CallContext(val call: MethodCall, val result: Result) {

  fun arg(name: String): String? {
    val v = call.argument<String>(name)
    if (v == null) {
      result.error("E0", "Missing argument: $name", "The argument $name was not provided")
    }
    return v
  }

}


abstract class ActionManager(val boss: Boss) {
  abstract fun call(ctx: CallContext)
}


class Boss(val context: Context, val activity : Activity) {

  private val logTag = "FlutterEspBleProv"

  private val scanBleMethod = "scanBleDevices"
  private val scanWifiMethod = "scanWifiNetworks"
  private val provisionWifiMethod = "provisionWifi"

  val devices = mutableMapOf<String, BleConnector>();
  val networks = mutableSetOf<String>();

  val bleScanner: BleScanManager
  val wifiScanner: WifiScanManager
  val wifiProvisioner : WifiProvisionManager

  lateinit var lastConn: BleConnector

  init {
    bleScanner = BleScanManager(this)
    wifiScanner = WifiScanManager(this)
    wifiProvisioner = WifiProvisionManager(this)
  }

  val provisionManager get() = ESPProvisionManager.getInstance(context)
  val esp get() = provisionManager.espDevice

  fun d(msg: String) = Log.d(logTag, msg)
  fun e(msg: String) = Log.e(logTag, msg)
  fun i(msg: String) = Log.e(logTag, msg)

  fun create(): ESPDevice {
    return ESPProvisionManager.getInstance(context).createESPDevice(
      ESPConstants.TransportType.TRANSPORT_BLE,
      ESPConstants.SecurityType.SECURITY_1
    )
  }

  fun connector(deviceName: String): BleConnector? {
    return devices[deviceName]
  }

  fun connect(conn: BleConnector, pop : String, callback: (ESPDevice) -> Unit) {
    lastConn = conn
    val esp = create()
    EventBus.getDefault().register(object {
      @Subscribe(threadMode = ThreadMode.MAIN)
      fun onEvent(event: DeviceConnectionEvent) {
        d("bus event $event ${event.eventType}")
        when (event.eventType) {
          ESPConstants.EVENT_DEVICE_CONNECTED -> {
            EventBus.getDefault().unregister(this)
            esp.proofOfPossession = pop
            callback(esp)
          }
        }
      }
    })
    esp.connectBLEDevice(conn.device, conn.primaryServiceUuid)
  }

  fun call(call: MethodCall, result: Result) {
    val ctx = CallContext(call, result)
    when (call.method) {
      scanBleMethod -> bleScanner.call(ctx)
      scanWifiMethod -> wifiScanner.call(ctx)
      provisionWifiMethod -> wifiProvisioner.call(ctx)
    }
  }

}


class BleScanManager(boss: Boss) : ActionManager(boss) {

  override fun call(ctx: CallContext) {
    ActivityCompat.requestPermissions(
      boss.activity,
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH
      ),
      12345
    )
    boss.d("searchBleEspDevices: start")
    val prefix = ctx.arg("prefix") ?: return

    boss.provisionManager.searchBleEspDevices(prefix, object : BleScanListener{
      override fun scanStartFailed() {
        TODO("Not yet implemented")
      }

      override fun onPeripheralFound(device: BluetoothDevice?, scanResult: ScanResult?) {
        device ?: return
        scanResult ?: return
        boss.devices.put(device.name, BleConnector(device, scanResult))
      }

      override fun scanCompleted() {
        ctx.result.success(ArrayList<String>(boss.devices.keys))
        boss.d("searchBleEspDevices: scanComplete")
      }

      override fun onFailure(e: java.lang.Exception?) {
        TODO("Not yet implemented")
      }

    })
  }

}

class WifiScanManager(boss: Boss) : ActionManager(boss) {
  override fun call(ctx: CallContext) {
    val name = ctx.arg("deviceName") ?: return
    val proofOfPossession = ctx.arg("proofOfPossession") ?: return
    val conn = boss.connector(name) ?: return
    boss.d("esp connect: start")
    boss.connect(conn, proofOfPossession) { esp ->
      boss.d("scanNetworks: start")
      esp.scanNetworks(
        object : WiFiScanListener {
          override fun onWifiListReceived(wifiList: ArrayList<WiFiAccessPoint>?) {
            wifiList ?: return
            wifiList.forEach { boss.networks.add(it.wifiName) }
            boss.d("scanNetworks: complete ${boss.networks}")
            Handler(Looper.getMainLooper()).post {
            // Call the desired channel message here.
              ctx.result.success(ArrayList<String>(boss.networks))
            }
            boss.d("scanNetworks: complete 2 ${boss.networks}")
            esp.disconnectDevice()
          }
          override fun onWiFiScanFailed(e: java.lang.Exception?) {
            boss.e("scanNetworks: error $e")
            ctx.result.error("E1", "WiFi scan failed", "Exception details $e")
          }
        }
      )
    }
  }
}


class WifiProvisionManager(boss: Boss) : ActionManager(boss) {
  override fun call(ctx: CallContext) {
    boss.e("provisionWifi ${ctx.call.arguments}")
    val ssid = ctx.arg("ssid") ?: return
    val passphrase = ctx.arg("passphrase") ?: return
    val deviceName = ctx.arg("deviceName") ?: return
    val proofOfPossession = ctx.arg("proofOfPossession") ?: return
    val conn = boss.connector(deviceName) ?: return

    boss.connect(conn, proofOfPossession) { esp ->
      boss.d("provision: start")
      esp.provision(ssid, passphrase, object : ProvisionListener{
        override fun createSessionFailed(e: java.lang.Exception?) {
          boss.e("wifiprovision createSessionFailed")
        }

        override fun wifiConfigSent() {
          boss.d("wifiConfigSent")
        }

        override fun wifiConfigFailed(e: java.lang.Exception?) {
          boss.e("wifiConfiFailed $e")
        }

        override fun wifiConfigApplied() {
          boss.d("wifiConfigApplied")
        }

        override fun wifiConfigApplyFailed(e: java.lang.Exception?) {
          boss.e("wifiConfigApplyFailed $e")
        }

        override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason?) {
          boss.e("provisioningFailedFromDevice $failureReason")
        }

        override fun deviceProvisioningSuccess() {
          boss.d("deviceProvisioningSuccess")
          ctx.result.success(true)
        }

        override fun onProvisioningFailed(e: java.lang.Exception?) {
          boss.e("onProvisioningFailed")
        }

      })
    }
  }

}

class ProvisionManager(boss: Boss) : ActionManager(boss) {
  override fun call(ctx: CallContext) {
    TODO("Not yet implemented")
  }

}


class ListensToBle(val result: Result) : BleScanListener {

  private val TAG = "FlutterEspBleProv"

  val devices: MutableMap<String, BleConnector>

  init {
    devices = hashMapOf()
  }

  override fun scanStartFailed() {
    result.success("scan failed")
  }

  override fun onPeripheralFound(device: BluetoothDevice?, scanResult: ScanResult?) {
    Log.e(TAG, "peripheral found $device $scanResult ${device?.name}")
    if (device != null && scanResult != null && !devices.containsKey(device.name)) {
      devices.put(device.name, BleConnector(device, scanResult))
    }
  }

  override fun scanCompleted() {
    Log.e(TAG, "scan complete")
    //val res : ArrayList<String> = arrayListOf()
    //for (k in devices.keys) {
    //  res.add(k)
    //}
    val res: List<String> = ArrayList(devices.keys)
    result.success(res)
  }

  override fun onFailure(e: Exception?) {
    result.error("error", e?.message, e)
  }

}

class ListensToWifi(fResult: Result) : WiFiScanListener {

  private val TAG = "FlutterEspBleProv"

  val networks: MutableList<WiFiAccessPoint>
  val result: Result


  init {
    result = fResult
    networks = arrayListOf()
  }

  override fun onWifiListReceived(wifiList: ArrayList<WiFiAccessPoint>?) {
    if (wifiList != null) {
      networks.addAll(wifiList)
    }
    Log.e(TAG, "$networks")
    val ssids: ArrayList<String> = arrayListOf()
    networks.forEach({ ssids.add(it.wifiName) })
    result.success(ssids)
  }

  override fun onWiFiScanFailed(e: java.lang.Exception?) {
    TODO("Not yet implemented")
  }

}

class ListensToProvisioning : ProvisionListener {
  private val TAG = "FlutterEspBleProv"

  override fun createSessionFailed(e: java.lang.Exception?) {
    Log.e(TAG, "session failed")

  }

  override fun wifiConfigSent() {
    Log.e(TAG, "config sent")
  }

  override fun wifiConfigFailed(e: java.lang.Exception?) {
    Log.e(TAG, "config failed")

  }

  override fun wifiConfigApplied() {
    Log.e(TAG, "config applied")

  }

  override fun wifiConfigApplyFailed(e: java.lang.Exception?) {
    Log.e(TAG, "config apply failed")

  }

  override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason?) {
    Log.e(TAG, "provision faiuled from device")

  }

  override fun deviceProvisioningSuccess() {
    Log.e(TAG, "provision success")

  }

  override fun onProvisioningFailed(e: java.lang.Exception?) {
    Log.e(TAG, "provision failed")

  }

}


class DevicesManager {

}


/** FlutterEspBleProvPlugin */
class FlutterEspBleProvPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
  PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity

  private val TAG = "FlutterEspBleProv"

  private lateinit var boss: Boss

  private lateinit var channel: MethodChannel
  private lateinit var context: Context

  private lateinit var activity: Activity
  private lateinit var devices: Map<String, BleConnector>
  private lateinit var networks: List<WiFiAccessPoint>
  private lateinit var esp: ESPDevice
  private lateinit var wifiListener: ListensToWifi
  private lateinit var eventChannel: EventChannel

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_esp_ble_prov")
    channel.setMethodCallHandler(this)



    context = flutterPluginBinding.applicationContext
    var pv = ESPProvisionManager.getInstance(context)
    Log.e(TAG, "ASttach")


  }


  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "getPlatformVersion" -> getPlatformVersion(result)
      "scanBleDevices" -> boss.call(call, result)
      "scanWifiNetworks" -> boss.call(call, result)
      "provisionWifi" -> boss.call(call, result)
      else -> result.notImplemented()
    }

//    if (call.method == "getPlatformVersion") {
//      //result.success("Android ${android.os.Build.VERSION.RELEASE}")
//      Log.d("BLE", "ali")
//      //result.success("$context")
//      result.success(methods.getPlatformVersion())
//    } else {
//      result.notImplemented()
//    }
  }


  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    Log.e(TAG, "attach")
    activity = binding.activity
    binding.addActivityResultListener(this)
    boss = Boss(context, binding.activity)


  }

  override fun onDetachedFromActivityForConfigChanges() {
    TODO("Not yet implemented")
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    TODO("Not yet implemented")
  }

  override fun onDetachedFromActivity() {
    TODO("Not yet implemented")
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    Log.e(TAG, "onActivityResult $requestCode $resultCode $data")
    return false
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ): Boolean {
    Log.e(TAG, "on permission result")
    return true
  }

  fun getPlatformVersion(result: Result) {
    return result.success("Android ${android.os.Build.VERSION.RELEASE}")
  }

  fun scanBle(result: Result) {
    Log.d(TAG, "scanning BLE")
    // Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher. You can use either a val, as shown in this snippet,
// or a lateinit var in your onAttach() or onCreate() method.


    ActivityCompat.requestPermissions(
      activity,
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH
      ),
      12345
    )
    val bleListener = ListensToBle(result)
    devices = bleListener.devices
    ESPProvisionManager.getInstance(context).searchBleEspDevices("PROV_", bleListener)
    //return result.success("Android ${android.os.Build.VERSION.RELEASE}")
  }

  fun scanWifi(call: MethodCall, result: Result) {
    val deviceName = call.argument<String>("deviceName")
    if (deviceName == null) {
      result.error("E0", "No deviceName given.", "no deviceName given")
      return
    }
    val conn = devices[deviceName]
    if (conn == null) {
      result.error("E1", "Device not found.", "Device not found")
      return
    }

    EventBus.getDefault().register(this)

    esp = ESPProvisionManager.getInstance(context).createESPDevice(
      ESPConstants.TransportType.TRANSPORT_BLE,
      ESPConstants.SecurityType.SECURITY_1
    )
    esp.connectBLEDevice(conn.device, conn.primaryServiceUuid)


    wifiListener = ListensToWifi(result)
    networks = wifiListener.networks

  }


  private fun provisionWifi(call: MethodCall, result: Result) {
    val ssid = call.argument<String>("ssid")
    if (ssid == null) {
      result.error("E0", "No ssid given.", "no ssid given")
      return
    }
    val passphrase = call.argument<String>("passphrase")
    if (passphrase == null) {
      result.error("E0", "No passphrase given", "no passphrase given")
      return
    }
    val listener = ListensToProvisioning()
    ESPProvisionManager.getInstance(context).espDevice.provision(ssid, passphrase, listener)
    //esp.provision(ssid, passphrase, listener)
    result.success(true)

  }


  fun espConnected() {
    Log.e(TAG, "ESP CONNECTED!")
    esp.proofOfPossession = "abcd1234"
    esp.scanNetworks(wifiListener)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: DeviceConnectionEvent) {
    when (event.eventType) {
      ESPConstants.EVENT_DEVICE_CONNECTED -> espConnected();
    }
  }
}
