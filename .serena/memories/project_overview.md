# Project overview

- Purpose: Moonlight Android fork named Moonlight Touchpad Edition. It streams games/desktops from a Windows PC running NVIDIA GameStream or Sunshine to Android, with touchpad-oriented input behavior.
- Platform: Android app, single Gradle module `app`.
- Stack: Java (112 tracked files, Java 11), Android SDK 34 (minSdk 21, targetSdk 34), Android Gradle Plugin 8.5.1, native C/C++ via Android NDK 27 and ndk-build/Android.mk, JNI plus bundled moonlight-common-c/OpenSSL/Opus.
- Main entry surfaces: `PcView`, `AppView`, `Game`, `HelpActivity`, `SponsorActivity`; streaming and runtime input behavior is concentrated in `com.limelight.Game` and `com.limelight.binding.input`.
- Structure: `app/src/main/java/com/limelight` contains Android app logic; `app/src/main/jni` contains JNI/native dependencies; `app/src/main/res` contains Android resources; `app/src/root` and `app/src/nonRoot` contain flavor-specific code/manifests; `fastlane` contains store metadata; `LuaScripts` contains Wireshark/debug helpers.
- Build flavors/application IDs: root and nonRoot variants under `com.limelight.touchpadedition`; debug adds `.debug`.
- Current repository note (2026-07-28): `docs/` and `.serena/` are untracked user/project-support content; preserve them.