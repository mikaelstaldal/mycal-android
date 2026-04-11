# Known Issues

## Security

### Critical

### Accessibility

**U12. Missing content descriptions on interactive elements**
- Event list items (`CalendarScreen.kt:478`) have no semantic label for screen readers
- Day cells with event indicators (`CalendarScreen.kt:373-418`) have no description of the dot or that events exist
- Color picker circles (`SettingsScreen.kt:129-140`, `EventFormScreen.kt:252-264`) have no labels, making them inaccessible to color-blind users and screen readers

**U13. Touch targets below 48 dp minimum**
`EventFormScreen.kt:252-264` and `SettingsScreen.kt:129-140` — Color selection circles are 36 dp, below the Material Design 48 dp minimum.

### Offline Mode

**U14. Offline mode limitations not communicated**
`CalendarScreen.kt:69` — The "Work Offline" button does not explain that changes cannot be synced. Once offline, there is no persistent indicator on the calendar screen, and it is unclear how to return to online mode.

**U15. Sync button active while offline**
`CalendarScreen.kt:142-154` — The sync button is not disabled in offline mode, misleading users into thinking a sync will occur.

### Other

**U16. Unexpected end-date auto-adjustment**
`EventViewModel.kt:219-230` — When the user changes the start date, the end date silently shifts to maintain the original duration, potentially overwriting an intentional end date the user had set.

**U17. Empty calendar has no onboarding hint**
`CalendarScreen.kt:436-442` — A date with no events shows "No events", but a completely empty calendar on first launch shows only an empty grid with no prompt to sync or configure the server.
