import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_esp_ble_prov/flutter_esp_ble_prov.dart';

void main() {
  runApp(const MyApp());
}

class ProvisionConfig {
  List<String> devices = [];
  List<String> networks = [];
  String selectedDevice = '';
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _flutterEspBleProvPlugin = FlutterEspBleProv();

  final _config = ProvisionConfig();

  final PAD = 12.0;
  final DEVICE_PREFIX = 'PROV';

  List<String> devices = [];
  List<String> networks = [];

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion = await _flutterEspBleProvPlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  scanBleDevices(String prefix) async {
    final scannedDevices = await _flutterEspBleProvPlugin.scanBleDevices(prefix);
    setState(() {
      devices = scannedDevices;
    });
  }

  scanWifiNetworks(String deviceName, String proofOfPosession) async {

  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('ESP BLE Provisioning Example'),
          actions: [
            IconButton(
                icon: Icon(Icons.bluetooth),
                onPressed: () => scanBleDevices(DEVICE_PREFIX),
            ),
          ],
        ),
        body: Container(
          child: Column(
            mainAxisSize: MainAxisSize.max,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Flexible(
                child: Container(
                    padding: EdgeInsets.all(PAD),
                    child: Text('BLE devices'),
                ),
              ),
              Expanded(
                child: ListView.builder(
                  itemCount: devices.length,
                  itemBuilder: (context, i) {
                    return ListTile(
                      title: Text(devices[i]),
                      onTap: () {
                        _flutterEspBleProvPlugin
                            .scanWifiNetworks(devices[i])
                            .then((nets) {
                          print(nets);
                          setState(() {
                            networks.addAll(nets);
                          });
                        });
                      },
                    );
                  },
                ),
              ),
              Flexible(
                child: Container(
                    padding: EdgeInsets.all(PAD),
                    child: Text('WiFi networks'),
                ),
              ),
              Expanded(
                child: ListView.builder(
                  itemCount: networks.length,
                  itemBuilder: (context, i) {
                    return ListTile(
                        title: Text(networks[i]),
                        onTap: () {
                          getPasswordFor(context, networks[i])
                              .then((passphrase) {
                            _flutterEspBleProvPlugin.provisionWifi(
                                networks[i], passphrase);
                          });
                        });
                  },
                ),
              ),
            ],
          ),
        ),
        floatingActionButton: FloatingActionButton(
          child: Icon(Icons.scanner),
          onPressed: () {
            _flutterEspBleProvPlugin.scanBleDevices("PROV_").then((devs) {
              setState(() {
                devices.addAll(devs);
              });
              //print(devices);
              //_flutterEspBleProvPlugin.scanWifiNetworks(devices[0]).then((networks) {
              //  print("here");
              //  print(networks);
              //  _flutterEspBleProvPlugin.provisionWifi("PineCorner", "hildaali").then((r) {
              //    print(r);
              //  });
              //});
            });
          },
        ),
      ),
    );
  }

  Future<String> getPasswordFor(BuildContext context, String ssid) {
    final completer = Completer<String>();
    final controller = TextEditingController();
    showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: Text('passphrase for $ssid'),
            content: TextField(
              controller: controller,
              decoration: InputDecoration(hintText: 'enter passphrase'),
              obscureText: true,
            ),
            actions: <Widget>[
              TextButton(
                child: Text('CANCEL'),
                onPressed: () {
                  setState(() {
                    Navigator.pop(context);
                    completer.complete(controller.text);
                  });
                },
              ),
              TextButton(
                child: Text('OK'),
                onPressed: () {
                  setState(() {
                    Navigator.pop(context);
                    completer.complete(controller.text);
                  });
                },
              ),
            ],
          );
        });
    return completer.future;
  }
}
