# To-do

An Android to-do app that finds idle moments (sedentary at home, not charging, no calendar event) and nudges one task at a time, so the list gets done instead of growing.

## Status

Three tabs (Now, Tasks, Goals) plus Settings for the times you're usually free. Tasks have a size, a "when", optional repeat and place, and "Not now" asks when to remind you again. Weekly goals are typed like `workout 3x`.

## Typing a task

One line, with optional cues anywhere:

| Cue | Meaning |
| --- | --- |
| `45m`, `1h`, `1h30m`, `1.5h` | time estimate |
| `@home`, `@out`, `@leaving`, `@any` | where it can be done (a bare trailing `home` or `out` also works) |
| `!asap` (or `asap`), `!week`, `!month`, `!quarter`, `!anytime`, `!someday` | when it should get done (default anytime idle) |
| `!daily`, `!weekly`, `!monthly` | repeat |
| `~low`, `~med`, `~high` | effort |

## Build

Open in Android Studio (JDK 17, Android SDK 35) and run on a device with Android 13 or newer. Unit tests: `./gradlew test`.

## Privacy

Everything stays on the phone. There is no account and no server.
