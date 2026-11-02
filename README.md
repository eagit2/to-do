# To-do

An Android to-do app that finds idle moments (sedentary at home, not charging, no calendar event) and nudges one task at a time, so the list gets done instead of growing.

## Status

Three tabs plus Settings, matching the approved phone preview:

- **Now** opens on the one item that best fits the time and place, with Done and Not now, and a short "Also fits now" list (tap a row for a tick box and Edit).
- **List** has everything open, crossed off when done (tap again to undo), with someday and not-yet-due yearly items folded at the bottom. The add box sits above the tabs.
- **Progress** shows repeating items with a bar and a pace mark.
- **Settings** has nine pages: nudges and notifications, free times, places ("I'm here now" saves the Wi-Fi name), signals, calendar, Garmin and Health Connect, permissions, appearance, and backup and data.

There is one kind of item. It happens once (on a date, ASAP, this week, month or quarter, whenever idle, or someday) or repeats N times per day, week, month or year. Yearly items hide until 1 to 3 months before they're due. Each item can have a size, best times and places.

Idle detection, notifications, calendar busy times and Health Connect are not built yet. Their settings are saved, and the Signals page marks those checks "not yet".

## Typing an item

One line, with optional cues anywhere:

| Cue | Meaning |
| --- | --- |
| `45m`, `1h`, `1h30m`, `1.5h` | how long |
| `3x`, `2/day`, `10/month` | repeats that many times per week, day or month |
| `!daily`, `!weekly`, `!monthly`, `!yearly` | repeats once per period |
| `!today`, `!tomorrow` | due that day |
| `!asap` (or `asap`), `!week`, `!month`, `!quarter`, `!whenever`, `!someday` | by when (default whenever idle) |
| `!morning`, `!afternoon`, `!evening`, `!weekdays`, `!weekends` | best times |
| `@home`, `@gym`, `@out`, any `@name` | places (a bare trailing `home` or `out` also works) |

## Build

Open in Android Studio (JDK 17, Android SDK 35) and run on a device with Android 13 or newer. Unit tests: `./gradlew test`.

## Privacy

Everything stays on the phone. There is no account and no server. Android backup is on (encrypted with the screen lock), and Settings can save a backup file, which leaves out Wi-Fi names.
