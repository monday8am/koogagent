---
description: Run Android instrumented tests on device/emulator
---
// turbo-all
1. Run instrumented tests
   ./gradlew :app:connectedAndroidTest

# Prerequisites
- Android device or emulator must be connected
- Check devices with: adb devices
- Requires actual device/emulator (LiteRT-LM and MediaPipe need model files)
