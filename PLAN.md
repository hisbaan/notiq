# Notiq — implementation plan

Notiq reads notifications and acts on them. Its first job: one-time codes (SMS, RCS, email,
in-app) get to the clipboard, or straight into the code field, without Google's autofill.
Bitwarden owns the autofill slot, so codes sent by text never get filled. Later jobs: email
codes, batching noisy notifications into digests, syncing codes to a desktop client.

This file is the grounding source for the project. Check items off as they land, and record
decisions here when they change.

## 0. Decisions

- **Kotlin + Jetpack Compose, Android only.** Same stack and versions as doorman / orbit.
- **Package:** `com.hisbaan.notiq`.
- **Distribution:** personal sideload / GitHub releases, maybe F-Droid later. No Google
  Play Services or other proprietary dependencies.
- **Notifications are the only source.** No `RECEIVE_SMS`: with the sensitive-notification
  grant (§1), the notification path covers SMS, RCS, email and in-app messages through one
  pipeline. The `Source` abstraction stays, so an SMS source is about a day's work if real
  use shows gaps. Known costs, accepted:
  - Everything depends on the grant. Mitigation: detect redacted notifications from allowed
    apps, alert loudly, re-grant through Shizuku when it's running.
  - No notification, no code: muted conversations, or the conversation open in Messages.
  - Sender is a display name, not a number.
- **Grant via Shizuku first**, adb command as the fallback, both shown in the setup screen.
  Shizuku only needs to be running when the grant is (re)applied, since app-ops persist.
  A companion-device association also makes the listener trusted (§1); Phase 0 evaluates it
  as a no-dependency alternative.
- **Filling:** Bitwarden holds the autofill service slot (one per device), so no autofill.
  MVP: copy to clipboard (marked sensitive, auto-cleared) + Notiq's own notification with a
  Copy action. v1.1: opt-in accessibility service that fills the focused code field.
- **No `INTERNET` permission** until desktop sync exists. Codes live in memory only, never
  in logs. Notification sources are allow-listed (messaging apps on by default).
- **minSdk 33** (runtime `POST_NOTIFICATIONS`, `ClipDescription.EXTRA_IS_SENSITIVE`),
  compileSdk/targetSdk 37.
- **The listener's class name is permanent:** `com.hisbaan.notiq.listener.NotiqListener`.
  Renaming or moving it revokes the user's notification-access grant (orbit learned this).

## 1. Sensitive notification redaction: the critical path

Android 15+ (`redact_sensitive_notifications_from_untrusted_listeners`) hides notifications
that Android System Intelligence flags as containing an OTP from untrusted listeners. From
AOSP `NotificationManagerService` (main, read 2026-10-25):

- **Trusted** (`isAppTrustedNotificationListenerService`) if any of:
  - holds `RECEIVE_SENSITIVE_NOTIFICATIONS` (`signature|role`, not obtainable);
  - is platform signed;
  - app-op `RECEIVE_SENSITIVE_NOTIFICATIONS` (`android:receive_sensitive_notifications`) is
    `MODE_ALLOWED`. Shell can set it:
    `cmd appops set --user 0 com.hisbaan.notiq RECEIVE_SENSITIVE_NOTIFICATIONS allow`;
  - has a non-revoked **CompanionDeviceManager association** for that user.
- **Trust is cached per uid** and only re-evaluated when the listener is enabled
  (`setPackageOrComponentEnabled`) or connects (`onServiceAdded`). Setting the app-op alone
  does nothing until the listener rebinds. `cmd notification allow_listener <component>`
  re-runs the check; so does unbinding and rebinding the listener. Enabling only ever *adds*
  trust: it's dropped when the listener is disabled (`disallow_listener`) or unbinds.
- **The op's default mode is `MODE_IGNORED`** (`cmd appops get` says "Default mode: ignore"),
  so `ignore` is the untouched state, not `default`.
- **What a redacted notification looks like:** title = the app's label, text =
  `R.string.redacted_notification_message` ("Sensitive notification content hidden" in
  English), sub text / text lines / big large icon removed, action titles blanked,
  MessagingStyle replaced by one message with that text from an empty person, BigTextStyle
  big text replaced too. The key, package and post time are unchanged. Detect it by comparing
  the text with the framework string (`Resources.getSystem()`, so it follows the locale).

Shizuku: Notiq binds a Shizuku *user service* (`ShellService`, runs as shell uid) and runs
`cmd` there. `Shizuku.newProcess` is deprecated; calling `IAppOpsService` through
`ShizukuBinderWrapper` needs hidden-API workarounds, not worth it for a few calls. The same
shell access automates the rest of setup:

| Step | Command |
|---|---|
| Restricted settings (sideloaded APK) | `cmd appops set --user N <pkg> ACCESS_RESTRICTED_SETTINGS allow` |
| Notification access (+ trust re-check) | `cmd notification allow_listener <component> N` |
| Sensitive notifications | `cmd appops set --user N <pkg> RECEIVE_SENSITIVE_NOTIFICATIONS allow` |
| Post notifications | `pm grant --user N <pkg> android.permission.POST_NOTIFICATIONS` |
| Unrestricted battery | `cmd deviceidle whitelist +<pkg>` |
| Accessibility (v1.1) | read `enabled_accessibility_services`, **append** ours, write back |

## 2. Architecture

```
Sources                    Processors               Actions
NotificationListener ─► Message ─► OTP extractor ─┬─ Clipboard (+ auto-clear)
(later) IMAP           (normalized)  dedupe        ├─ Notiq notification (Copy)
                                     rules         ├─ (v1.1) accessibility fill
                                                   ├─ (later) batch / digest
                                                   └─ (later) desktop sync
```

```
:core   Message model, OTP extractor, dedupe, rules        (pure Kotlin, no android.*)
:app    listener, Shizuku shell, actions, settings, Compose UI
```

`:core` stays free of Android so a desktop client can reuse it (Kotlin Multiplatform later).

Build/dev env: `flake.nix` dev shell copied from doorman (JDK 17, Android SDK 37 via
`androidenv`, aapt2 override for NixOS) + Gradle wrapper 9.8. AGP 9.4 (built-in Kotlin),
Kotlin 2.4, Compose BOM 2026.09, Shizuku API 13.1.5.

```sh
nix develop            # or direnv
./gradlew test installDebug
```

## 3. Phases

### Phase 0 — Probe

Goal: know, on the Pixel (Android 17), exactly what Notiq receives and which grant routes
work, before building the pipeline.

- [x] Project skeleton: Nix dev shell, Gradle, `:core`, `:app`
- [x] `NotiqListener` records every non-ongoing notification into an in-memory probe log:
      package, key, new vs update, template, category, title, text, big text, MessagingStyle
      messages (sender, time), group summary flag, redacted flag, extracted code
- [x] First-pass `OtpExtractor` in `:core` with unit tests (Phase 1 hardens it)
- [x] Setup checklist: notification access, post notifications, sensitive app-op state,
      unrestricted battery, Shizuku state
- [x] Shizuku: permission request, `ShellService` user service, "Set up with Shizuku" runs
      every command in §1's table (except accessibility)
- [x] adb fallback: copyable command
- [x] Experiments: post a test OTP notification from Notiq itself, copy codes to the
      clipboard from the listener (toggle), rebind the listener, CDM association
- [x] Copy the probe log as text, for sharing / pasting into notes
- [x] On the phone, record results in §4 (the rest left open on 2026-10-25 to start Phase 1;
      the user confirmed background clipboard writes work):
  - [ ] Is Notiq's own test OTP notification redacted to Notiq before the grant?
  - [ ] Real SMS code before the grant: redacted?
  - [ ] Shizuku grant: does `allow_listener` alone make the listener trusted, or is a rebind
        needed? Survives reboot? `adb install -r`? Uninstall + reinstall?
  - [ ] adb grant: same questions
  - [ ] CDM association: does it make the listener trusted with no app-op?
  - [ ] Does a sideloaded (adb-installed) build need restricted settings at all?
  - [ ] Clipboard write from the listener with the app in the background: works? Toast?
        Does Gboard offer the sensitive clip?
  - [ ] Google Messages: muted conversation, conversation open on screen (a second message in
        the same conversation: see Phase 1)
  - [ ] RCS code, email code (Gmail), in-app code (any) for format notes

### Phase 1 — MVP

Design decisions for this phase:

- **Sources** (settings): known messaging apps (on), email apps (off), any app when Android
  flags the notification as containing a code (on; `Ranking.hasSensitiveContent` by
  reflection, so Pixel / ASI only), any app at all (off; extractor only).
- **Skipped before reading:** ongoing notifications, group summaries, media players
  (MediaStyle / `transport`), Notiq's own (except the test channel).
- **Which text:** MessagingStyle → the newest message only. Otherwise title + big text (or
  text), so a code in either is found.
- **Newness and dedupe** (`CodeFilter`, `:core`): per notification key, only messages newer
  than the last one acted on; messages over 10 min old are ignored; the same code within
  2 min is acted on once (SMS + RCS + an email of the same code, or re-posts).
- **Extractor:** marketing messages ("Use code SAVE20 at checkout") are rejected unless they
  also carry a strong code signal (one-time, verification, passcode, "don't share", ...).
- **Actions:** copy to the clipboard, marked sensitive (on). Notiq's own notification with the
  code and a Copy button: silent, times out after 10 min, replaced by the next code (on).
  Recent codes on the home screen, in memory, for 10 min.
- **Clipboard clearing** (off by default): only if the clipboard still holds Notiq's copy. A
  background app may write or clear the clipboard but not read it (`ClipboardService.
  clipboardAccessAllowed`: reads need focus or the default IME), and there's no `cmd clipboard`.
  The shell holds `READ_CLIPBOARD_IN_BACKGROUND`, so the Shizuku user service calls the hidden
  `IClipboard.getPrimaryClipDescription` (by reflection) and Notiq compares a random tag it put
  in the description's extras. Only the description: reading the clip itself makes Android show
  a "Shell pasted from your clipboard" toast. Locked → nothing is readable, so retry every 15 s
  for up to 10 min. Shizuku not running → leave it alone. Verified on the Pixel 2026-10-25:
  cleared when untouched, left alone after another copy. The first call after the app starts
  waits a few seconds for Shizuku to start the user service.
- **Redaction alarm:** a redacted notification from an accepted source means the grant is
  gone. If Shizuku is ready, re-run setup (at most every 10 min), then rescan the shade:
  `getActiveNotifications` checks trust at call time, so the code is recovered. Otherwise an
  alert notification opens the app's setup.
- **Diagnostics:** the Phase 0 probe becomes a Diagnostics screen (log in memory, experiments).
  The file copy (`files/diagnostics.txt`) is only written in debuggable builds, and is cut back
  to its newest 256 KB once it passes 512 KB. In memory: the last 300 notifications and events.

Tasks:

- [x] `:core`: `Message`, `SourcePolicy`, `CodeFilter`, extractor hardening, tests
- [x] Listener → reader → pipeline → actions
- [x] Clipboard (sensitive, optional clear), code notification with Copy, recent codes
- [x] Redaction alarm: Shizuku re-grant + rescan, or alert notification
- [x] Settings screen; home screen (setup when incomplete, recent codes); Diagnostics screen
- [x] Clipboard clearing checks the clipboard still holds the code (Shizuku)
- [x] Debug-only adb hook: `adb shell am broadcast -n com.hisbaan.notiq/.DebugReceiver --es action
      copy|text|test ...` (see `DebugReceiver`)
- [x] On the phone: real SMS end to end (2026-10-25). Two codes in one thread: the second came as
      an UPDATE carrying both messages, and only the newest (365231) was taken and copied,
      about 3 s after the message's timestamp.
- [ ] Not yet tried on the phone: revoke → alarm → re-grant → recovery; clearing while locked

### Phase 2 — Fill codes into apps (opt-in)

Design decisions for this phase:

- **Accessibility service** (`fill.FillService`, class name permanent like the listener). Bitwarden
  keeps the autofill slot. Opt-in: off until the user enables it (Settings, or Shizuku appends
  it to `enabled_accessibility_services` without touching the others).
- **Fill mode** (setting, user's call 2026-10-25): *Tap to fill* (default): a "Fill 482913" chip
  while a likely code field is focused. *Auto when sure*: fill strong matches, chip for weaker
  ones. *Always auto*: fill any likely match.
- **Idle unless a code is pending.** The service subscribes to no events until the pipeline
  offers a code; then focus / window changes for 5 min (codes usually live 5-10 min), until
  it's filled or the chip is dismissed.
- **Field detection** (`CodeFieldDetector`, `:core`, pure): the focused editable field's hint,
  view id, label and content description, numeric input type, max length, editable siblings
  (split one-digit boxes), and the screen's text. Confidence NONE / LIKELY / SURE:
  - SURE: split boxes matching the code's length; code words in the field and numeric or
    max length = code length.
  - LIKELY: code words in the field; numeric with max length = code length; the screen talks
    about a code and the field is numeric or the only field on screen.
  - NONE: password / email / phone / card / search / message fields without code words.
- **Filling:** `ACTION_SET_TEXT` (one digit per box for split fields), checked by re-reading;
  else paste (the code goes on the clipboard first).
- **Finding the field:** the input-focus query answers with the host view for Compose and web
  views (fields are virtual children), so search under it for the focused editable node. Found
  on the Try-it screen's Compose field; Firefox works with it too.
- **Press Enter after filling** (setting, on): after 250 ms, if the filled field still has focus
  (the app didn't submit by itself), the field's `ACTION_IME_ENTER`, else `input keyevent
  KEYCODE_ENTER` through Shizuku (web views may lack the action). Once only.
- **Chip:** `TYPE_ACCESSIBILITY_OVERLAY` window (no overlay permission), just above the
  keyboard, or below the field without one. Plain Views, system dynamic colours.
- **Try it** screen: a code field, split boxes (EditTexts with max length 1), a password field
  and a chat field, plus "Send a test code".

Tasks:

- [x] `:core`: `CodeFieldDetector`, `FillMode` decisions, tests
- [x] `FillService`, field reading, filler, chip, `FillController` (pending code)
- [x] Settings: service status (enable via Shizuku or Settings), mode; setup status
- [x] Try-it screen
- [x] On the phone (2026-10-25): split boxes and the code field on the Try-it screen, and a real
      SMS code into Firefox
- [x] Press Enter after filling (2026-10-25): Firefox has no IME action, so the Shizuku key
      event (exit 0, submitted); the Try-it fields use their own Done action. Not yet seen: an
      app that submits by itself (expect "the app moved on by itself").
- What fields report (Diagnostics):
  - Firefox: `EditText`, hint = the placeholder ("Verification code"), id from the page,
    inputType 0xa1 (text, web edit text) whatever the page's `inputmode`, no max length. So
    code words → LIKELY at best: "Auto when sure" shows the chip there.
  - Compose `OutlinedTextField`: no hint, no input type, no max length; the label isn't
    exposed. Found only through the screen's text ("only field"). Follow-up: read labels
    from nearby text nodes.

### Phase 3 — Email codes

Through mail apps' notifications (Gmail puts the whole email in `EXTRA_BIG_TEXT`, seen in
Phase 0); IMAP only if notifications turn out not to be enough. The "Email apps" source
already exists (off by default); this phase is about precision on long bodies full of numbers.

- [x] Extractor: "sign in" / "log in" no longer count as a strong code signal (every email has
      a "sign in to your account" link, which let "use code SAVE20" through); numbers labelled
      as something else rejected ("Order number", "Invoice no.", "Booking reference:", ...);
      US zip codes after a state rejected (footers); zero-width characters removed and
      non-breaking spaces normalised (HTML email). Tests from GitHub, Amazon, Epic emails.
- [x] Reader: InboxStyle text lines (Gmail with several new emails) when there's no big text
- [x] Debug hook `--es action email`: a Gmail-shaped test notification. End to end on the
      Pixel: GitHub-style email → code copied; marketing email (promo code, order number,
      address) → nothing.
- [ ] Real code emails in Gmail: does Android flag them (`sensitive=true`)? If so, the "any
      app when Android spots a code" source covers them without scanning every email, and
      "Email apps" can stay off by default.
- [ ] Decide the "Email apps" default from that

### Later

- Batching: cancel noisy notifications as they arrive, post a digest on a schedule;
  per-app rules. Optionally tap "Mark as read" on the source after copying a code.
- Desktop sync: QR pairing, end-to-end encrypted push over LAN first, relay later. This is
  where `INTERNET` gets added.

## 4. Phase 0 results

Pixel, Android 17, 2026-10-25:

- **Real SMS code (Google Messages), grant on:** `sensitive=true` (the system flagged it),
  delivered **unredacted**, extractor got `017424` from
  "[Project Name Not Rendered in Test] verification code: 017424". MessagingStyle; the title
  is the sender's number; the newest message carries the code.
- **The system only flags real codes.** Notiq's own test notification and one posted through
  `cmd notification post` were `sensitive=false`, so they can't test redaction. Use real SMS.
  The per-notification `sensitive` flag (`Ranking.hasSensitiveContent`, a @SystemApi) is
  readable by reflection on Android 17.
- **The grant survives `adb install -r`;** the system re-checks it when the listener reconnects
  after the reinstall.
- Sensitive app-op reads `MODE_IGNORED` before any grant, as the source says.
- **Real SMS code, grant revoked** (app-op `ignore` + listener disabled and re-enabled):
  `sensitive=true`, **redacted**: text and MessagingStyle message "Sensitive notification
  content hidden", empty title and sender, and the notification's stored ApplicationInfo is
  Android's (so it can't name the app; use the package). It came as an UPDATE: the earlier
  SMS notification for that conversation was still posted. Both halves of §1 confirmed.
- Still to test: reboot, uninstall,
  companion association, restricted settings, the clipboard from the background, Messages
  edge cases (muted / conversation open / second code in a thread), RCS, email codes.

Notes for Phase 1 from the log:

- Paused media players (Fennec, `MediaStyle`, category `transport`) aren't ongoing and update
  every second or so: skip by template/category, not just the ongoing flag (orbit hit this too).
- WhatsApp and Gmail post a group summary *and* the child with the same text: skip summaries
  whose children are posted, or the dedupe step sees everything twice.
- Gmail puts the whole email in `bigText` (useful for email codes later), and an update with
  title "Deleted" after archiving.
- Settings must be read after DataStore loads (`SettingsRepository.current()`): the "copy
  codes" switch was in memory at first and reset on reinstall.
