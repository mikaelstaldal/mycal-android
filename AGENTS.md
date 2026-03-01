# AI coding agent instructions

This file provides guidance to AI coding agents when working with code in this repository.

## Build

```bash
gradle assembleDebug       # debug build
gradle assembleRelease     # release build
```

No Gradle wrapper — uses system `gradle` command.

## Architecture

Native Android app (Kotlin, Jetpack Compose) that consumes the MyCal REST API with HTTP Basic Auth.

**Layers:**
- **data/api/** — Retrofit interface (`ApiService`), request/response DTOs (`EventDto`), HTTP client with Basic Auth interceptor (`RetrofitClient`)
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
- Dates are entered as text fields (yyyy-MM-dd, HH:mm) — no native date/time pickers
- All API timestamps use RFC 3339 format, converted to local timezone for display

## API

The app consumes the MyCal REST API:
- `GET /api/v1/events?from=...&to=...` — list events in range
- `GET /api/v1/events?q=...` — search events
- `POST /api/v1/events` — create event
- `GET /api/v1/events/{id}` — get event
- `PUT /api/v1/events/{id}` — update event (partial)
- `DELETE /api/v1/events/{id}` — delete event

See the server's `docs/API.md` for full field documentation.

## Event Colors

The app uses the same 8 CSS color names as the web frontend: `dodgerblue`, `red`, `gold`, `green`, `orange`, `mediumturquoise`, `cornflowerblue`, `salmon`. These are mapped to hex values in `CalendarScreen.kt` and `EventFormScreen.kt`.

## Version control

Git is used for version control. When creating new files, make sure to add them to Git.
