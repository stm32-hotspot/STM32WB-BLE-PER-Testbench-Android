# Android Studio Project

This repository contains the ST PER Testbench app source code.

The ST PER Testbench is an application created in Android Studio by ST Microelectronics for mobile phones running Android. It allows users to setup and monitor a Bluetooth PER (Packet Error Rate) Test between two ST Nucleo-WB55RG boards. The test can be performed in four steps: scan & connect to devices (boards), configure their parameters, start the test, and observe & save the live test data. 

Follow the link to the User Guide below for more detailed instructions on how to use the application.

Please note, the mobile device must be running Android 8 or newer to run the application.

## Application Details

* App Name: ST PER Testbench
* App Version: 1.0.4
* Package: com.stm.pertestbench

- Minimum Android SDK: 26 (Android 8)
- Target Android SDK: 31 (Android 12)

* 100% Kotlin
* MVVM Architecture

- Tested on the Following Devices:
    * Samsung Galaxy Tab A7 Lite
    * Google Pixel 5
    * Samsung Galaxy S10

## Permissions

* BLUETOOTH
* BLUETOOTH_ADMIN
* ACCESS_FINE_LOCATION
* ACCESS_COARSE_LOCATION
* BLUETOOTH_CONNECT
* WRITE_EXTERNAL_STORAGE

## Dependencies

* Core-KTX
* AppCompat
* Material
* ConstraintLayout
* Legacy-Support-V4
* Junit
* Espresso
* [Timber](https://github.com/JakeWharton/timber)
* RecyclerView
* Anko
* Fragment
* Lifecycle-Runtime
* Lifecycle-ViewModel
* Lifecycle-LiveData
* [GraphView](https://github.com/jjoe64/GraphView)

## Download
Go to [Utilities/Android Software](https://github.com/stm32-hotspot/STM32WB-BLE-PER-Testbench/tree/main/Utilities/Android_Software) to download the APK.

## User Guide
Go to the [README](https://github.com/stm32-hotspot/STM32WB-BLE-PER-Testbench#users-guide) to learn how to use the application.
