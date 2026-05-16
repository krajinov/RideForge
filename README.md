# RideForge

RideForge is a Kotlin Multiplatform and Compose Multiplatform prototype for an indoor cycling ERG training app. The mobile app can read core data from the local Ktor backend and falls back to mock data when the backend is unavailable. Bluetooth, trainer control, payments, subscriptions, and analytics SDKs are not connected.

## What Is Implemented

- Android and iOS Compose Multiplatform app entry points
- Dark Material 3 UI for onboarding, dashboard, trainer connection, plans, workout detail, active workout, completion, history, and profile
- Bottom navigation for Home, Plans, Workout, History, and Profile
- Clean shared module with models, repository interfaces, mock repositories, use cases, placeholder clients, and a Koin module
- Mock smart trainer scanning and connection state
- Coroutine-backed mock ERG workout engine with `StateFlow`
- Live mocked power, target power, cadence, heart rate, interval progress, total progress, pause/resume, skip interval, and end workout
- Canvas-based interval timeline and power graph components
- Kotlinx Serialization and Kotlinx DateTime model support
- Ktor Client read-only API integration for profile, plans, recommended workout, intervals, and history
- Login, register, refresh-token rotation, logout, and platform token persistence for the prototype auth flow
- Workout session sync for start, pause, resume, completion, and batched metric uploads
- Mock fallback with an in-app offline/mock data banner

## Project Structure

- `composeApp/src/commonMain/kotlin/com/delminiusapps/rideforge`
  - `App.kt` app shell and route switching
  - `theme/` dark RideForge Material 3 theme
  - `navigation/` sealed app routes and bottom-tab definitions
  - `presentation/components/` reusable cards, buttons, charts, badges, chips, and bottom bar
  - `presentation/*/` feature screens
- `shared/src/commonMain/kotlin/com/delminiusapps/rideforge`
  - `models/` serializable domain models
  - `core/network/` Ktor client setup, platform base URLs, and API data-source state
  - `data/dto/` backend response DTOs
  - `data/mapper/` DTO-to-domain mappers
  - `data/repository/` future-facing repository contracts
  - `data/repository/remote/` Ktor-backed repositories with mock fallback
  - `data/repository/sync/` in-memory pending session queue, sync manager, and batched metric uploader
  - `data/mock/` mocked data, repositories, and ERG engine
  - `data/remote/` placeholder `ApiClient` and `BluetoothTrainerClient`
  - `domain/usecase/` simple use cases
  - `di/` Koin module plus a lightweight dependency container for the prototype

## Run Android

From the project root:

```shell
./gradlew :composeApp:assembleDebug
```

Then run the `composeApp` Android configuration from Android Studio, or install the generated debug APK from:

```text
composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

## Run iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` target on an iOS simulator. The SwiftUI entry point loads the Compose UI through:

```text
composeApp/src/iosMain/kotlin/com/delminiusapps/rideforge/MainViewController.kt
```

## Validation

Validated with:

```shell
./gradlew :shared:compileKotlinMetadata :composeApp:compileKotlinMetadata
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew :server:test
```

## Mobile Backend Integration

Start the backend first:

```shell
./gradlew :server:run
```

Then run the app from Android Studio or Xcode. The app uses these default development base URLs:

- Android emulator: `http://10.0.2.2:8080`
- Android real device: `http://192.168.31.152:8080`
- iOS simulator: `http://localhost:8080`
- iOS real device: `http://192.168.31.152:8080`
- JVM/shared tests: `http://localhost:8080`

The defaults live in `shared/src/*Main/kotlin/com/delminiusapps/rideforge/core/network/AppConfig.*.kt` and `shared/src/commonMain/kotlin/com/delminiusapps/rideforge/core/network/ServerConfig.kt`. If your Mac gets a different LAN IP, update `ServerHosts.DEVICE_LAN` before installing the app on a physical device.

The backend endpoints are JWT-protected. The mobile app now uses a simple login screen with the seeded MVP user:

```text
marko@example.com / password
```

On successful login or registration, the app stores access and refresh tokens through a platform `TokenStorage`, restores the session on app launch, refreshes tokens once after a `401`, and logs out through `/auth/logout`. Android currently stores prototype tokens in private `SharedPreferences`; iOS stores them in `NSUserDefaults`. Before production, replace these stores with encrypted Android storage and Keychain-backed iOS storage.

If the backend is unavailable or a read-only API request fails, the remote repository returns the existing mock data and the app shows a compact “Backend unavailable. Showing mock data.” snackbar above the bottom navigation.

Workout sessions remain local for real-time ERG timing. The app creates a backend session on start, sends pause/resume/completion events, and buffers metric samples for upload every 10 seconds. Failed session events are placed into an in-memory `LocalPendingSyncQueue` and retried by `SessionSyncManager`; the active workout screen shows `Synced`, `Syncing`, `Pending sync`, or `Sync failed`.

## Backend API

The `server` module is a Ktor Netty backend foundation for RideForge. It uses in-memory repositories today, with repository interfaces shaped for PostgreSQL-backed implementations later.

Implemented areas:

- JWT auth structure: register, login, refresh-token rotation, logout, and current user
- Profile and FTP/weight updates
- Training plans, enrollment, and enrolled plan lookup
- Workouts, recommended workout, and FTP-adjusted workout intervals
- Workout sessions: start, pause, resume, complete, and metric samples
- Workout history list/detail/delete
- Smart trainer placeholder devices
- Structured error responses
- Pagination-ready list responses
- CORS for mobile/dev clients
- Lightweight OpenAPI document at `/openapi.json`

### Seed Workouts

The backend loads 20 realistic cycling workouts from `server/src/main/resources/seed/workouts.json` on startup. These workouts include:
- Realistic interval structures for endurance, tempo, sweet spot, threshold, VO2 max, and over-unders.
- Power targets based on % FTP, which are automatically calculated for the user's current FTP.
- Durations ranging from 30 to 90 minutes.
- Validated interval timing to ensure total workout duration matches the sum of intervals.

To modify the seed data, edit the JSON file and restart the server.

Run the backend:

```shell
./gradlew :server:run
```

Environment variables:

```shell
PORT=8080
DATABASE_URL=postgresql://localhost:5432/rideforge
JWT_SECRET=replace-in-real-env
JWT_ISSUER=rideforge-api
JWT_AUDIENCE=rideforge-mobile
JWT_REALM=rideforge
JWT_ACCESS_MINUTES=60
JWT_REFRESH_DAYS=30
```

Seeded login:

```text
email: marko@example.com
password: password
```

Example curl requests:

```shell
curl http://localhost:8080/health

curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"marko@example.com","password":"password"}'

TOKEN="paste-access-token"

curl http://localhost:8080/profile \
  -H "Authorization: Bearer $TOKEN"

curl -X PUT http://localhost:8080/profile/ftp \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ftp":255}'

curl http://localhost:8080/plans \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/plans/plan-vo2-booster/enroll \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/workouts/recommended \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/workouts/workout-vo2max-5x3/intervals \
  -H "Authorization: Bearer $TOKEN"

curl -X POST http://localhost:8080/sessions/start \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"workoutId":"workout-vo2max-5x3"}'

SESSION_ID="paste-session-id"

curl -X POST http://localhost:8080/sessions/$SESSION_ID/metrics \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPower":286,"targetPower":290,"cadence":91,"heartRate":151,"speedKmh":33.2}'

curl -X PUT http://localhost:8080/sessions/$SESSION_ID/complete \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"elapsedSeconds":2700}'

curl http://localhost:8080/history \
  -H "Authorization: Bearer $TOKEN"

curl http://localhost:8080/devices/current \
  -H "Authorization: Bearer $TOKEN"
```

## Notes

- `ride_forge.pen` exists in the project root, but implementation can run without Pencil.
- The current navigation is a small sealed-route navigator to avoid adding an unavailable navigation dependency.
- `rideForgeModule` is ready for Koin-based wiring when the prototype moves beyond mocked data.
- Backend smart trainer endpoints are placeholders only. The mobile app remains responsible for direct BLE trainer control.
