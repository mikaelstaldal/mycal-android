# AI coding agent instructions

This file provides guidance to AI coding agents when working with code in this repository.

## Build

```bash
gradle assembleDebug       # debug build
gradle assembleRelease     # release build
```

No Gradle wrapper — uses system `gradle` command.

## API Client Generation

The Retrofit API client is generated from `../mycal/openapi.yaml` using the OpenAPI Generator Gradle Plugin. **Never edit generated files manually.**

To regenerate after spec changes:
```bash
gradle openApiGenerate
```

Generated files live in `app/build/generated/openapi/` and are excluded from version control. They are regenerated automatically before every build.

Hand-written files in `data/api/`:
- `EventDto.kt` — UI-facing DTOs (`EventDto`, `CalendarDto`) returned by `EventRepository` from the local database
- `RetrofitClient.kt` — OkHttp/Retrofit singleton with Basic Auth
- `NominatimService.kt` — Geocoding client (separate from main API)

Generated files (do not edit):
- `DefaultApi` — Retrofit interface for all MyCal API endpoints
- `Event`, `Calendar`, `CreateEventRequest`, `UpdateEventRequest`, `UpdateCalendarRequest`, and other model classes

## Architecture

Native Android app (Kotlin, Jetpack Compose) that consumes the MyCal REST API with HTTP Basic Auth.

**Layers:**
- **data/api/** — Generated Retrofit interface (`DefaultApi`), generated request/response models, UI DTOs (`EventDto`, `CalendarDto`), HTTP client with Basic Auth interceptor (`RetrofitClient`)
- **data/preferences/** — Preferences DataStore for server URL and credentials (`UserPreferences`)
- **ui/calendar/** — Monthly calendar grid with event list (`CalendarScreen`, `CalendarViewModel`)
- **ui/event/** — Event detail, create/edit form (`EventDetailScreen`, `EventFormScreen`, `EventViewModel`)
- **ui/settings/** — Server configuration screen (`SettingsScreen`, `SettingsViewModel`)
- **ui/navigation/** — Compose Navigation graph (`NavGraph`)
- **ui/theme/** — Material 3 theme with dynamic color support
- **util/** — Date formatting helpers (`DateUtils`)

**Key design decisions:**
- Single-activity architecture with Compose Navigation
- `RetrofitClient` is a singleton that rebuilds the OkHttp/Retrofit instance when server URL or credentials change
- ViewModels use `AndroidViewModel` to access application context for DataStore
- Dates/times use Material 3 DatePicker and TimePicker dialogs, storing values as `yyyy-MM-dd` and `HH:mm` strings
- All API timestamps use RFC 3339 format, converted to local timezone for display

## API

The app consumes the MyCal REST API which is documented in `../mycal/docs/API.md` and specified in `../mycal/openapi.yaml`.

## Event Colors

The app uses the same 8 CSS color names as the web frontend: `dodgerblue`, `red`, `gold`, `green`, `orange`, `mediumturquoise`, `cornflowerblue`, `salmon`. 
These are mapped to hex values in `CalendarScreen.kt` and `EventFormScreen.kt`.

## Version control

Git is used for version control. When creating new files, make sure to add them to Git.
