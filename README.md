# To-do

An Android to-do app that finds idle moments (sedentary at home, not charging, no calendar event) and nudges one task at a time, so the list gets done instead of growing.

## Status

Starter scaffold: three tabs (Now, Tasks, Goals), a local Room database, and an on-phone parser for typed task lines.

## Typing a task

One line, with optional cues anywhere:

| Cue | Meaning |
| --- | --- |
| `45m`, `1h`, `1h30m`, `1.5h` | time estimate |
| `@home`, `@out`, `@leaving`, `@any` | where it can be done (a bare trailing `home` or `out` also works) |
| `!must`, `!nice`, `!someday` | bucket (default nice) |
| `~low`, `~med`, `~high` | effort |

## Build

Open in Android Studio (JDK 17, Android SDK 35) and run on a device with Android 13 or newer. Unit tests: `./gradlew test`.

## Privacy

Everything stays on the phone. There is no account and no server.
