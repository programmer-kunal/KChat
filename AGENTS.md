# KChat - Agent Instructions

## Project Structure
Monorepo with two components:
- **app/** - Android app (Kotlin, Jetpack Compose, Hilt, Gradle Kotlin DSL)
- **functions/** - Firebase Cloud Functions (TypeScript, Node 18)

## Android App (`app/`)
- **Entry point**: `KChat.kt` (Application class, initializes ZegoCloud call service on auth state change)
- **Main Activity**: `MainActivity.kt` (Compose UI, handles FCM intents, online presence)
- **Min SDK**: 26, **Target/Compile SDK**: 36, **Java**: 11
- **Key deps**: Firebase (Auth, Database, Messaging, Storage, Crashlytics), ZegoCloud (calls), Supabase, Ktor, Coil, Hilt

### Build Commands
```bash
./gradlew build              # Full build
./gradlew test               # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests (requires device/emulator)
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK (minify + shrink enabled)
```

### Gradle Version Catalog
Dependencies managed in `gradle/libs.versions.toml`. Use `alias(libs.*)` in build files.

## Firebase Functions (`functions/`)
- **Entry point**: `functions/src/index.ts` - triggers on message creation, sends FCM notifications
- **Runtime**: Node 18, TypeScript 5.7
- **Region**: us-central1

### Commands
```bash
cd functions
npm run build        # Compile TypeScript
npm run serve        # Build + start emulators
npm run deploy       # Deploy to Firebase
npm run logs         # View function logs
```

### Deploy Precheck
`firebase.json` runs `npm run lint` then `npm run build` before deploy (lint currently disabled).

## Common Workflows
1. **Android changes**: Edit in `app/src/main/java/com/example/kchat/`, run `./gradlew assembleDebug`
2. **Functions changes**: Edit in `functions/src/`, run `npm run build` then `npm run serve` for local testing
3. **Full verify**: `./gradlew build` + `cd functions && npm run build`

## Important Notes
- ZegoCloud credentials (`AppID`, `AppSign`) defined in `const.kt` - do not commit real keys
- FCM tokens auto-uploaded to Realtime Database on auth state change (both `KChat.kt` and `MainActivity.kt`)
- Online presence system uses `.info/connected` + `onDisconnect` in Realtime Database
- ABI splits enabled: only `arm64-v8a` APKs produced (no universal APK)
- ProGuard/R8 enabled for release with custom rules in `proguard-rules.pro`
- ZegoCloud dependency version forced to `3.17.3` via resolutionStrategy