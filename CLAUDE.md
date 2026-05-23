# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

- **Full QA** (tests + lint + ktlint + stopship): `./gradlew qa`
- **Format code**: `./gradlew format`
- **ktlint check only**: `./gradlew ktlintCheck`
- **Unit tests**: `./gradlew :Signal-Android:testPlayProdReleaseUnitTest`
- **Single test**: `./gradlew :Signal-Android:testPlayProdReleaseUnitTest --tests "fully.qualified.TestClassName"`
- **Lint**: `./gradlew :Signal-Android:lintPlayProdRelease`
- **Build Play bundle**: `./gradlew bundlePlayProdRelease`
- **Build website APK**: `./gradlew assembleWebsiteProdRelease`
- **STOPSHIP check**: `./gradlew checkStopship`
- **Pre-push hook** (lefthook): runs `./gradlew format` — install with `lefthook install`

## Code Style

- **ktlint 1.5.0** enforces formatting (2-space indent, IntelliJ IDEA style, no trailing commas on call sites)
- **Custom lint checks** in `:lintchecks` module enforce Signal-specific rules:
  - Use `Log.tag()` / Signal logging APIs, not `android.util.Log`
  - `AlertDialogBuilderUsage`, `StartForegroundServiceDetector`, `BlockingGetDetector`, and others
- Run `./gradlew format` before committing — the pre-push hook will reject unformatted code

## Architecture Overview

### Dependency Injection: Manual Service Locator

No Dagger/Hilt/Koin. Uses `AppDependencies` (singleton) + `AppDependencies.Provider` interface:
- **Production**: `ApplicationDependencyProvider` creates real dependencies
- **Tests**: `MockApplicationDependencyProvider` returns `mockk(relaxed = true)` for all 50+ factory methods
- Initialized in `ApplicationContext.onCreate()` via `AppDependencies.init(context, provider)`
- Access dependencies statically: `AppDependencies.getJobManager()`, `AppDependencies.getPushServiceSocket()`, etc.

### Database: SQLCipher (not Room)

- `SignalDatabase` — main encrypted SQLite database (`signal.db`), extends `SQLiteOpenHelper`
- ~35 table objects accessed as `SignalDatabase.messages`, `SignalDatabase.threads`, `SignalDatabase.recipients`, etc.
- Separate encrypted databases: `KeyValueDatabase` (settings), `JobDatabase` (job queue), `LogDatabase`, `MegaphoneDatabase`, `LocalMetricsDatabase`
- Migrations in `database/helpers/SignalDatabaseMigrations.kt` and `database/helpers/migration/`

### Background Work: Custom JobManager (not WorkManager)

- `JobManager` at `jobmanager/` — custom job queue with SQLite persistence, constraints, and dedicated thread pools
- `Job` / `BaseJob` — base classes; `CoroutineJob` — Kotlin coroutine variant
- Key reserved job runners for: `PushProcessMessageJob`, `IndividualSendJob`, `PushGroupSendJob`, `AttachmentUploadJob`, `ReactionSendJob`, `TypingSendJob`
- Schedulers: `AlarmManagerScheduler`, `JobSchedulerScheduler`, `InAppScheduler`, `CompositeScheduler`

### Key-Value Store: SignalStore

- `SignalStore` backed by `KeyValueDatabase` (separate SQLCipher DB)
- Typed value subsets: `SignalStore.account`, `SignalStore.settings`, etc.

### Event Bus

- GreenRobot EventBus 3.0 for cross-component communication

### UI: Hybrid Compose + Views

- **Main shell** (`MainActivity.kt`): Jetpack Compose with `ThreePaneScaffold` (Material3 Adaptive), `NavHost` per tab (Chats, Calls, Stories)
- **Conversation screen**: Traditional Views with `RecyclerView`, `ViewBinding`, custom `ViewHolder` patterns
- **Registration v2**: Compose-based
- **Compose utilities**: `compose/Nav.kt`, `compose/StatusBarColorAnimator.kt`
- Navigation: Compose NavHost for main tabs; Activity/Fragment-based for conversation and feature screens

### Reactive Data

- **RxJava 3** extensively — `Flowable` for database queries, `CompositeDisposable` in ViewModels
- **Kotlin coroutines** in newer code — `viewModelScope`, `Flow`/`StateFlow`
- `DatabaseObserver` / `RxDatabaseObserver` provide table-level change notifications as Rx streams

### Message Flow

- **Outgoing**: `MessageSender.java` orchestrates sends; delegates to `IndividualSendJob` / `PushGroupSendJob` for push, Android telephony for SMS/MMS
- **Incoming**: `PushProcessMessageJob` processes all incoming Signal messages; `IncomingMessageObserver` listens on WebSocket

### Protocol & Network

- `libsignal-service` (local module `:lib:libsignal-service`) — Signal service API client
- `libsignal-client` — native Signal Protocol library
- Two persistent WebSocket connections (auth + unauth) via `AppDependencies`
- REST API classes: `AccountApi`, `MessageApi`, `ProfileApi`, `KeysApi`, `AttachmentApi`, `ArchiveApi`, etc.
- `NetworkDependenciesModule` — resettable when proxy config changes

### App Startup

- `AppStartup` in `ApplicationContext.onCreate()` — phased initialization (blocking → nonBlocking → postRender)

## Project Structure

- **`app/`** — main application module (package: `org.thoughtcrime.securesms`)
- **`core/`** — shared utilities (`util`, `util-jvm`, `models`, `models-jvm`, `network`, `ui`, `serialization`)
- **`lib/`** — library modules (`libsignal-service`, `network`, `glide`, `billing`, `paging`, `contacts`, `qr`, `video`, `image-editor`, `device-transfer`, `donations`, `archive`, etc.)
- **`feature/`** — feature modules (`registration`, `camera`, `media-send`)
- **`demo/`** — 12 standalone demo apps for individual libraries
- **`build-logic/`** — custom Gradle convention plugins (`signal-library.gradle.kts`, `ktlint.gradle.kts`, etc.)
- **`lintchecks/`** — custom Android lint rules
- **`reproducible-builds/`** — Docker config for reproducible builds

## Key SDK Versions

- compileSdk: `android-36`, targetSdk: `35`, minSdk: `23`
- Kotlin: `2.2.20`, JVM target: `17`
- AGP: `9.1.1`, Gradle: `9.3.1`
- Compose BOM: `2026.04.01`
- SQLCipher: `4.16.0`, OkHttp: `5.3.2`, RxJava3: `3.1.12`

## Testing

- **Unit tests**: JUnit 4 + Robolectric 4.15 + MockK 1.13 + assertk 0.28
- **Instrumentation tests**: Espresso, Compose UI testing, AndroidX Test Orchestrator
- **Test runner**: `SignalTestRunner`
- **Mock DI**: `MockApplicationDependencyProvider` auto-mocks all `AppDependencies.Provider` methods
- Tests located at `app/src/test/java/org/thoughtcrime/securesms/`
- Shared test code in `app/src/testShared/`
