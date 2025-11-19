# Garmin Activity Streaming MVP

Real-time activity data streaming from Garmin watches to Android devices.

## Overview

This project enables live streaming of fitness metrics (heart rate, GPS, speed, etc.) from Garmin watches to an Android phone in real-time.

### Features (MVP)

- **Heart Rate** streaming every 3 seconds
- **GPS Location** with live map tracking
- **Speed** in km/h
- **Distance** calculation
- **Pace** for running

## Architecture

```
┌─────────────────┐     Bluetooth/WiFi     ┌─────────────────┐
│   Garmin Watch  │  ──────────────────►   │  Android Phone  │
│   (Connect IQ)  │    Activity Data       │   (Companion)   │
└─────────────────┘                        └─────────────────┘
```

## Project Structure

```
/watch-app              # Garmin Connect IQ app (Monkey C)
  /source
    ActivityStreamingApp.mc
    ActivityStreamingView.mc
    ActivityStreamingDelegate.mc
  /resources
  manifest.xml

/android-app            # Android companion app (Kotlin + Compose)
  /app/src/main/java/com/garminstreaming/app
    MainActivity.kt
    GarminListenerService.kt
    ActivityData.kt
```

## Prerequisites

### For Watch App
- [Connect IQ SDK](https://developer.garmin.com/connect-iq/sdk/) (version 6.x or later)
- Visual Studio Code with [Monkey C extension](https://marketplace.visualstudio.com/items?itemName=garmin.monkey-c)

### For Android App
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Kotlin 1.9+
- [Garmin Connect IQ Mobile SDK](https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/)

## Build Instructions

### Watch App

1. Install Connect IQ SDK:
   ```bash
   # Download from https://developer.garmin.com/connect-iq/sdk/
   # Extract and set CIQ_HOME environment variable
   export CIQ_HOME=/path/to/connectiq-sdk
   ```

2. Build the app:
   ```bash
   cd watch-app
   $CIQ_HOME/bin/monkeyc -o bin/ActivityStreaming.prg \
     -m manifest.xml \
     -z resources/strings/strings.xml \
     -y /path/to/developer_key.der \
     source/*.mc
   ```

3. Deploy to watch:
   - Connect watch via USB
   - Copy `.prg` file to `GARMIN/APPS/` directory on watch

### Android App

1. Download Garmin Connect IQ Mobile SDK:
   - Get from https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/
   - Place `connectiq-mobile-sdk-android-1.5.aar` in `android-app/app/libs/`

2. Open in Android Studio:
   ```bash
   cd android-app
   # Open with Android Studio
   ```

3. Build and run:
   ```bash
   ./gradlew assembleDebug
   # or use Android Studio Run button
   ```

## Usage

1. **Start Android app** on your phone
2. **Open watch app** on Garmin watch
3. **Press START** (select button) on watch to begin streaming
4. Watch the real-time metrics appear on your phone
5. **Press STOP** or swipe down to stop streaming

### Watch Controls

- **Select button**: Start/Stop streaming
- **Swipe up**: Start streaming
- **Swipe down**: Stop streaming
- **Back button**: Exit (with confirmation if streaming)

## Data Format

Data is transmitted as a dictionary every 3 seconds:

```javascript
{
  "type": "activity_data",
  "timestamp": 123456789,     // milliseconds
  "hr": 145,                  // heart rate (bpm)
  "lat": 55.7558,            // latitude (degrees)
  "lon": 37.6173,            // longitude (degrees)
  "speed": 3.5,              // speed (m/s)
  "altitude": 156.0,         // altitude (meters)
  "distance": 2500.0         // distance (meters)
}
```

## Supported Devices

### Watches
- Fenix 5/5S/5X (Plus)
- Fenix 6/6S/6X Pro
- Fenix 7/7S/7X (Pro)
- Forerunner 245/255/265
- Forerunner 745/945/955/965
- Venu/Venu 2/Venu 3

### Android
- Android 8.0 (API 26) or higher
- Bluetooth Low Energy support

## Roadmap

### Phase 2
- [ ] Real-time heart rate graph
- [ ] Session history storage
- [ ] More metrics (cadence, power)
- [ ] Custom streaming interval

### Phase 3
- [ ] Multiple activity types
- [ ] Export to GPX/FIT/TCX
- [ ] Strava/Garmin Connect integration
- [ ] Training zones display

## Troubleshooting

### Watch app not connecting
- Ensure Garmin Connect Mobile is installed on phone
- Check that Bluetooth is enabled
- Verify watch is paired with phone in Garmin Connect

### No data streaming
- Check that location permissions are granted
- Ensure GPS has a fix on watch
- Verify app has required permissions in manifest

## License

MIT License

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
