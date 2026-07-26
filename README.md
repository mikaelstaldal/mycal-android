# MyCal Android

Native Android client for the [MyCal](https://github.com/mikaelstaldal/mycal) calendar server.

## Features

- Monthly calendar grid with colored event indicators
- Create, view, edit, and delete events
- Full-text event search
- All-day and timed events
- Color-coded events (8 colors matching the web frontend)
- Pull-to-refresh
- Link a note from the [MyNotes app](https://github.com/mikaelstaldal/mynotes-android) to an event
  and read it on the event screen, offline included — rendered by MyNotes' own render kit, so
  callouts, wikilinks, inline icons, AsciiMath and Mermaid diagrams look exactly as they do there
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

The MyNotes render kit is vendored (and committed) under `app/src/main/assets/renderer/`, so the
build needs nothing extra. To pick up changes to the Markdown dialect, refresh it from a local
checkout of the MyNotes repo — run `./build.sh` there first — and commit the result:

```bash
tools/sync-renderer.sh ../mynotes
```

## Setup

1. Install the APK on a device or emulator
2. On the first launch, you'll be prompted to configure the server
3. Enter the MyCal server URL (e.g. `http://192.168.1.100:8080`), username, and password
4. Use "Test Connection" to verify connectivity before saving

### MyNotes

Linking a note to an event needs the
[MyNotes Android app](https://github.com/mikaelstaldal/mynotes-android) installed on the same
device. There is nothing to configure: MyCal reads notes straight out of that app, which brings its
own server, credentials and offline cache — so a linked note is readable whenever MyNotes has it,
with or without a network, and MyCal never sees a MyNotes password.

The two apps must be **signed with the same key**, because MyNotes guards this with a
`signature`-level permission. Settings reports which of "not installed", "did not grant access" or
"connected" applies, and the Note field simply does not appear unless the integration works.

MyCal can only read. Tapping a linked note, or a wikilink inside one, opens the MyNotes app.

#### Signing both apps with one key

The default `~/.android/debug.keystore` would satisfy the signature check, but it is a poor trust
anchor: world-readable, fixed password `android`, and shared by every debug APK built on the machine
— any of which would then be able to read all your notes, silently, since signature permissions are
granted at install with no prompt. Create one keystore of your own instead, and use it for both apps:

```bash
KS="$HOME/.android/staldal-apps.keystore"
PW="$(openssl rand -base64 24)"

keytool -genkeypair -v \
  -keystore "$KS" -storetype PKCS12 \
  -alias staldal-apps \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=Mikael Staldal, O=staldal.nu, C=SE" \
  -storepass "$PW" -keypass "$PW"

chmod 600 "$KS"
echo "$PW"   # keep this — it cannot be recovered from the keystore
```

`-storepass` and `-keypass` are the same value on purpose: PKCS12 has no real support for a separate
key password. Then add the following to `local.properties` **in both repos**, with identical values
(that file is never checked in):

```properties
debugKeystore=/home/you/.android/staldal-apps.keystore
debugKeystorePassword=…
debugKeyAlias=staldal-apps
debugKeyPassword=…
```

`app/build.gradle.kts` picks these up for the debug build type. CI can supply the same values as
`DEBUG_KEYSTORE`, `DEBUG_KEYSTORE_PASSWORD`, `DEBUG_KEY_ALIAS` and `DEBUG_KEY_PASSWORD` instead. With
none of them set the build still works, but falls back to the default debug key and warns that it did
— both apps then fall back alike, so the integration keeps working; it is the trust boundary that
weakens.

Back the keystore up, and to build on another machine **copy the keystore file** rather than re-running
the command — a second run produces a different key, which breaks the signature match between the apps
and prevents upgrading anything already installed with the first one.

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
