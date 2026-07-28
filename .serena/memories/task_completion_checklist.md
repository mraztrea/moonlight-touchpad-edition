# Task completion checklist

1. Confirm every changed line maps to the requested behavior and preserve unrelated dirty worktree files.
2. Add or update the smallest regression test/check for non-trivial logic.
3. Run the focused test/check that reproduces the bug.
4. Run `.\gradlew.bat test` when unit tests exist/apply.
5. Run `.\gradlew.bat lint` for Android source/resource changes.
6. Run `.\gradlew.bat assembleDebug` to verify Java/native integration.
7. Inspect `git diff --check`, `git diff --stat`, and the final scoped diff.
8. Report exactly what passed and any device/host behavior that still requires physical Nillkin + Windows verification.