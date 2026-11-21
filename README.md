# wakeCTS

A modern Android app that shows a draggable floating button and attempts to wake Circle to Search.

This project includes both:

- The floating-button app
- A modern libxposed/LSPosed module required to convert the direct Assistant-session request into a Circle to Search request

## Build

1. Extract the project.
2. Open the folder in Android Studio.
3. Let Gradle sync.
4. Build and install the `app` module.

The project uses the modern libxposed API:

```kotlin
compileOnly("io.github.libxposed:api:102.0.0")
```

## Enable the app

1. Open **wakeCTS**.
2. Tap **Enable service**.
3. Enable the Accessibility service.
4. Return to the app.

## Enable the LSPosed module

1. Open LSPosed.
2. Enable **wakeCTS**.
3. Open its scope.
4. Select **System Framework** or **Android System**.
5. Reboot the device.

The module scope is declared as `system` in:

```text
app/src/main/resources/META-INF/xposed/scope.list
```

## Test

Tap **Test Circle to Search** in the app.

If the direct trigger works, Circle to Search opens.  
If it fails, the app opens the Assistant if **Open Assistant if CTS fails** is enabled.

## Entry point

The project uses entry point `1`, matching MiCTS’s Home-button long-press trigger.

If your device needs a different value, change this in `CtsTrigger.kt`:

```kotlin
private const val CTS_ENTRY_POINT = 1
```
