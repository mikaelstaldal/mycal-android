# MyCal Android

Native Android client for the [MyCal](https://github.com/mikaelstaldal/mycal) calendar server.

## Features

- Monthly calendar grid with colored event indicators
- Create, view, edit, and delete events
- Full-text event search
- All-day and timed events
- Color-coded events (8 colors matching the web frontend)
- Pull-to-refresh
- HTTP Basic Auth
- Material 3 with dynamic color (Material You) on Android 12+

## Requirements

- Android SDK (API 35)
- Gradle (system install, no wrapper)
- Min SDK: Android 8.0 (API 26)
- A running MyCal server

## Build

```bash
gradle assembleDebug
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

## Setup

1. Install the APK on a device or emulator
2. On the first launch, you'll be prompted to configure the server
3. Enter the MyCal server URL (e.g. `http://192.168.1.100:8080`), username, and password
4. Use "Test Connection" to verify connectivity before saving

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Retrofit 2 + OkHttp 3
- Preferences DataStore
- Navigation Compose

## API

This app consumes the MyCal REST API. See the server's [API documentation](https://github.com/mikaelstaldal/mycal/blob/main/docs/API.md) 
and [OpenAPI specification](https://github.com/mikaelstaldal/mycal/blob/main/openapi.yaml) for details.

## License

Copyright 2026 Mikael Ståldal.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
