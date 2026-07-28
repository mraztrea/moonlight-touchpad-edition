# Style and conventions

- Primary app code is Java, not Kotlin. Use Java 11 syntax compatible with the current Android toolchain.
- Follow existing Android/Java style: 4 spaces, no tabs; opening brace on the declaration line; camelCase fields/methods, PascalCase classes, UPPER_SNAKE_CASE constants.
- Package code under `com.limelight` and the closest existing functional subpackage.
- Prefer the existing direct/event-driven patterns over introducing abstractions or dependencies.
- Make surgical changes: preserve surrounding formatting/comments and unrelated legacy behavior.
- Input fixes should trace `MotionEvent` source/tool/axes through `Game`, input capture, touch adapter, and `ControllerHandler`; fix the shared root cause rather than caller-specific symptoms.
- No tracked unit or instrumentation tests currently exist; for non-trivial fixes add the smallest runnable regression check compatible with the Gradle Android project, without adding a new test framework.