# Suggested commands (Windows / PowerShell)

Run from `D:\Projects\Android\moonlight-touchpad-edition`.

- Initialize native submodules: `git submodule update --init --recursive`
- Build debug APK: `.\gradlew.bat assembleDebug`
- Run JVM unit tests (if present): `.\gradlew.bat test`
- Run Android lint: `.\gradlew.bat lint`
- Run connected instrumentation tests (requires device/emulator and tests): `.\gradlew.bat connectedAndroidTest`
- Inspect worktree: `git status --short --branch`
- Inspect diff: `git diff -- app/src/main/java/...`
- Search from PowerShell when Serena cannot index a language: `rg "pattern" app/src/main/java`

README prerequisites: install Android Studio and Android NDK, initialize submodules, and configure `local.properties` with the Android SDK/NDK paths as required by the local environment.