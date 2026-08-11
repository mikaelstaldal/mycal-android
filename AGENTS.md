# AI coding agent instructions

This file provides guidance to AI coding agents when working with code in this repository.

## Build

```bash
gradle assembleDebug       # debug build
gradle assembleRelease     # release build
```

No Gradle wrapper — uses system `gradle` command.

## API

The app consumes the MyCal REST API which is documented in `../mycal/docs/API.md` and specified in `../mycal/openapi.yaml`.

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
- `MyNotesClient.kt` — reads notes from the MyNotes app's content provider (not an HTTP client); see **MyNotes integration** below

Generated files (do not edit):
- `DefaultApi` — Retrofit interface for all MyCal API endpoints
- `Event`, `Calendar`, `CreateEventRequest`, `UpdateEventRequest`, `UpdateCalendarRequest`, and other model classes

## Architecture

Native Android app (Kotlin, Jetpack Compose) that consumes the MyCal REST API with HTTP Basic Auth.

**Layers:**
- **data/api/** — Generated Retrofit interface (`DefaultApi`), generated request/response models, UI DTOs (`EventDto`, `CalendarDto`), HTTP client with Basic Auth interceptor (`RetrofitClient`)
- **data/preferences/** — Preferences DataStore for server URL and credentials (`UserPreferences`)
- **ui/calendar/** — Monthly calendar grid with event list (`CalendarScreen`, `CalendarViewModel`)
- **ui/event/** — Event detail, create/edit form (`EventDetailScreen`, `EventFormScreen`, `EventViewModel`), and the start/end coupling that form relies on (`EventTimeRange`)
- **ui/note/** — WebView host for the vendored MyNotes render kit (`NoteRendererWebView`)
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

## MyNotes integration

An event can link one [MyNotes](https://github.com/mikaelstaldal/mynotes) note, stored as
`note_slug` on the event (the server keeps only the slug and never fetches the note). The event form
picks a note by title prefix; the event detail screen shows the note's content.

- **The note comes from the MyNotes *app*, not its server.** MyCal is not a notes client: it holds
  no MyNotes credentials, opens no connection to a MyNotes server, and keeps no copy of any note —
  only the slug, on `EventEntity` (schema v4). `data/api/MyNotesClient.kt` reads through the content
  provider the MyNotes Android app exports (`nu.staldal.mynotes.notes`; its contract lives in
  `provider/NotesContract.kt` in that repo and is duplicated here, since the two repos build
  separately — keep them in step). That app already syncs notes into a local database, so **a linked
  note is readable offline** whenever MyNotes has it. Rendering one makes no network request at all.
  This replaced an earlier REST-based version; do not reintroduce a MyNotes HTTP client here.
- **Availability, not configuration.** There is nothing to set up. `MyNotesClient.availability`
  reports `NOT_INSTALLED`, `NOT_PERMITTED` or `AVAILABLE`, Settings shows which, and the note picker
  hides itself unless the integration works. `NOT_PERMITTED` means the two apps were signed with
  different keys: MyNotes guards the provider with a `signature` permission
  (`nu.staldal.mynotes.permission.READ_NOTES`, requested in our manifest along with the `<queries>`
  entry that makes MyNotes visible at all under Android 11+ package visibility).
- **Writes go to MyNotes.** The provider is read-only. Tapping a linked note — or a wikilink inside
  a rendered one — fires `ACTION_VIEW` on the provider's note/tag URI, restricted to the MyNotes
  package, and that app handles editing under its own conflict handling.
- **Rendering.** Never implement the MyNotes Markdown dialect here. The **MyNotes render kit** — the
  MyNotes web client's own pipeline (markdown-it → DOMPurify, plus Mermaid, AsciiMath, inline Lucide
  icons, emoji shortcodes, callouts, wikilinks), packaged as a static page exposing
  `globalThis.MyNotesRender` — is vendored at `app/src/main/assets/renderer/` and driven in a
  WebView by `ui/note/NoteRendererWebView.kt`. Refresh it with `tools/sync-renderer.sh [../mynotes]`
  (run `./build.sh` in that repo first) and **commit the result**. The MyCal web frontend embeds the
  same kit in an iframe, and the MyNotes Android app does exactly this — all three renderers are one
  implementation.
- **WebView contract.** The kit is served over a real origin with `WebViewAssetLoader`
  (`https://appassets.androidplatform.net/assets/renderer/…`), not `loadDataWithBaseURL`, because
  the page loads ES modules through an import map. Markdown and theme go **in** through
  `evaluateJavascript`, JSON-quoted — note content is never spliced into HTML or JS syntax, so the
  kit's DOMPurify gate stays the only path to the DOM. Taps come **out** through
  `shouldOverrideUrlLoading` (see above; http(s)/mailto open externally, anything else is blocked).
  `shouldInterceptRequest` is an **allow-list** — the kit's own asset files plus the two image
  references MyNotes can resolve (`api/v1/artifacts/<sha256>` and, after
  `rewriteLocalArtifactRefs`, `/local-artifact/<id>` for an image not yet uploaded); everything else
  gets a 403, so a note embedding a third-party image cannot phone home. Icons are not fetched: the
  kit inlines every icon it knows, and one it doesn't renders broken until the kit is re-synced.
- **Sizing.** The note is one section of a scrolling screen, so the page reports its own height
  through a `WebViewCompat` message channel and the view is sized to it.

## Event Colors

The app uses the same 8 CSS color names as the web frontend: `dodgerblue`, `red`, `gold`, `green`, `orange`, `mediumturquoise`, `cornflowerblue`, `salmon`. 
These are mapped to hex values in `CalendarScreen.kt` and `EventFormScreen.kt`.

## Version control

Git is used for version control. When creating new files, make sure to add them to Git.
