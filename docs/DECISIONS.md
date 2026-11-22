# Architectural Decision Records

## 0001: Android SDK build guard and baseline test recording

### Status
Accepted

### Context
Running `./gradlew test` from the repository root failed on development environments lacking an Android SDK installation. The build failed with:
`Could not determine the dependencies of task ':keyfort-android:testDebugUnitTest'. SDK location not found.`

### Options Considered
1. Require all contributors and build environments to install the full Android SDK and configure `ANDROID_HOME` or `local.properties`.
2. Conditionally evaluate and include `:keyfort-android` in `settings.gradle` only when an Android SDK is detected via environment variables (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) or `local.properties` (`sdk.dir`).

### Decision
Option 2. In `settings.gradle`, inspect environment variables and `local.properties`. If no valid Android SDK directory exists, log a single notice and omit `:keyfort-android` from the project tree. Environments with an Android SDK continue to build and test `:keyfort-android`.

### Trade-offs
Developers lacking the Android SDK cannot execute Android unit tests locally. Baseline test execution without Android SDK passes 32 tests across `keyfort-core` (27 tests) and `keyfort-server` (5 tests).
