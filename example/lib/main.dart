import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_esp_ble_prov/flutter_esp_ble_prov.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _flutterEspBleProvPlugin = FlutterEspBleProv();

  final PAD = 12.0;
  final DEVICE_PREFIX = 'PROV';

  List<String> devices = [];
  List<String> networks = [];

  String selectedDeviceName = '';
  String selectedSsid = '';

  final prefixController = TextEditingController(text: 'PROV_');
  final proofOfPossessionController = TextEditingController(text: 'abcd1234');
  final passphraseController = TextEditingController();

  scanBleDevices() async {
    final prefix = prefixController.text;
    final scannedDevices =
        await _flutterEspBleProvPlugin.scanBleDevices(prefix);
    setState(() {
      devices = scannedDevices;
    });
  }

  scanWifiNetworks() async {
    final proofOfPossession = proofOfPossessionController.text;
    final scannedNetworks = await _flutterEspBleProvPlugin.scanWifiNetworks(
        selectedDeviceName, proofOfPossession);
    setState(() {
      networks = scannedNetworks;
    });
  }

  provisionWifi() async {
    final proofOfPossession = proofOfPossessionController.text;
    final passphrase = passphraseController.text;
    final success = await _flutterEspBleProvPlugin.provisionWifi(
        selectedDeviceName, proofOfPossession, selectedSsid, passphrase);
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
              onPressed: () => scanBleDevices(),
            ),
          ],
        ),
        body: Container(
          child: Column(
            mainAxisSize: MainAxisSize.max,
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Flexible(
                child: Container(
                  padding: EdgeInsets.all(PAD),
                  child: Row(
                    mainAxisSize: MainAxisSize.max,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Text('Device Prefix'),
                      ),
                      Expanded(
                        child: TextField(
                          controller: prefixController,
                          decoration:
                              InputDecoration(hintText: 'enter device prefix'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
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
                      title: Text(
                        devices[i],
                        style: TextStyle(
                          color: Colors.blue.shade700,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      onTap: () {
                        selectedDeviceName = devices[i];
                        scanWifiNetworks();
                      },
                    );
                  },
                ),
              ),
              Flexible(
                child: Container(
                  padding: EdgeInsets.all(PAD),
                  child: Row(
                    mainAxisSize: MainAxisSize.max,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Text('Proof of possession'),
                      ),
                      Expanded(
                        child: TextField(
                          controller: proofOfPossessionController,
                          decoration: InputDecoration(
                              hintText: 'enter proof of possession string'),
                        ),
                      ),
                    ],
                  ),
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
                      title: Text(
                        networks[i],
                        style: TextStyle(
                          color: Colors.green.shade700,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      onTap: () {
                        selectedSsid = networks[i];
                        provisionWifi();
                      },
                    );
                  },
                ),
              ),
              Flexible(
                child: Container(
                  padding: EdgeInsets.all(PAD),
                  child: Row(
                    mainAxisSize: MainAxisSize.max,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Text('WiFi Passphrase'),
                      ),
                      Expanded(
                        child: TextField(
                          controller: passphraseController,
                          decoration:
                              InputDecoration(hintText: 'enter passphrase'),
                          obscureText: true,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
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
