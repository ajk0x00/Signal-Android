# Upstream sync — 2026-11-19

Branch: `sync/upstream-2026-11-19` (based on `main` + one merge commit of `signalapp/Signal-Android`).

| | Before | After |
|---|---|---|
| Upstream base | 8.31.1 (`92f5ccd3`) | 8.37.1 (`aa38b25d`) |
| Upstream commits brought in | — | 516 |
| Fork-only commits | 99 | 99 (all preserved) |

## Fork features verified present after the merge

- **Groq AI** — `GroqApiClient`, `AskGroqJob`, the modular slash commands (`/ask`, `/topic`, `/judge`, `/rapabout`), model picker in Chats settings (`selectedAiModel`, `availableAiModels`).
- **Cloud storage save** — `CloudStorageValues`, `CloudStorageServiceHelper`, `SaveStoryToCloudJob`, saved-stories DB/UI, GCS/Drive picker, ProGuard keep rules.
- **Attachment upload fixes** — retry-loop / media-queue deadlock fix in `AttachmentUploadJob`.
- **Voice-recording typing indicator** — `stopVoiceRecordingTypingIndicator()` in `ConversationFragment`.
- **Labs row** visible without the internal-preferences gate (`showLabsPreferences`).

## Conflict resolutions (15 files)

| File | Resolution |
|---|---|
| `strings.xml` (app, feature/app-settings), `verification-metadata.xml` | 3-way union — both sides' additions kept |
| `SignalStore.kt` | upstream's `LabsValues(store) { RemoteConfig.internalUser }` + `init` block, fork's `cloudStorageValues` kept |
| `JobManagerFactories.java` | union: fork's `SaveStoryToDriveJob`/`DriveSyncJob` + upstream's `PaymentNotificationSendJob` |
| `lib/storage/.../LabsValues.kt` | took upstream (file moved out of `app`; fork had no unique change) |
| `AppSettingsFragment.kt` | took upstream (now a 62-line nav host) |
| `AppSettingsState.kt` | removed (moved to `feature/app-settings/home/`); `showLabsPreferences` added there |
| `feature/.../AppSettingsScreen.kt` | Labs row now ungated (fork behaviour), internal row still gated |
| `ChatsSettingsViewModel.kt` | upstream's `NavigateBackClicked`/`ChatFoldersClicked` handlers; fork's AI-model code kept |
| `ChatsSettingsScreen.kt` | import union; fork's AI-model row + dialog kept |
| `ConversationFragment.kt` | both imports kept; dropped upstream's removed `EventBus` unregister, kept fork's voice-typing stop |
| `StoryViewerPageFragment.kt` | import union (`Toast` + `DrawableRes`) |
| `.github/workflows/android.yml` | kept deleted (upstream's private CI) |

## Deliberately ported, not preserved verbatim

`MediaPreviewFragment` + `R.menu.media_preview` were **deleted upstream** — chat media now opens in the Compose
`feature/media-viewer`. The fork's "Save to Cloud" action was re-implemented there:

- `MediaViewerEvent.SaveToCloudClicked`, `MediaViewerAction.SaveToCloud(messageId, attachmentId)`
- overflow-menu item (non-draft only) → `SaveStoryToCloudJob.enqueue(...)` + "Saving to cloud…" toast
- `symbol_cloud_24` added to the feature module's drawables

## CI

- **`build-debug-apk.yml`** (new) — builds `assembleWebsiteProdDebug` on `sync/**`, `feat/**`, `fix/**`, `feature/**`
  pushes and PRs to `main`; uploads the APK as an artifact. No signing secrets required.
- Existing fork workflows kept: `release-apk.yml` (signed release on `main`/release), `verify-compile.yml`
  (`sync/**`, PRs), `retarget-main-prs.yml`, `update-screenshots.yml`.
- Upstream's `build-artifacts.yml` is dispatch-only and harmless; `android.yml`, `diffuse.yml`, `docker.yml`,
  `stale.yml` stay deleted.

## Still unverified

The merge has **not been compiled** — this host has no Android SDK. `build-debug-apk.yml` is the verification
step: push the branch and watch the run.
