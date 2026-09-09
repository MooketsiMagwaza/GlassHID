# Contributing to GlassHID

Thanks for helping turn ordinary Android phones into useful local input devices.
Small fixes, device compatibility reports, accessibility improvements, controller
layouts, documentation, and translations are all welcome.

## Before opening a change

1. Search existing issues to avoid duplicate work.
2. Open an issue before a large UI, protocol, or architecture change.
3. Keep the runtime offline-first. Do not add analytics, accounts, advertising,
   cloud services, or a Wi-Fi dependency.
4. Avoid new dependencies unless they solve a clear problem that platform APIs
   cannot reasonably handle.

## Build and test

- Use Android Studio or Gradle with JDK 17 or newer.
- Build with `gradle assembleDebug` from this directory.
- Install the resulting `app/build/outputs/apk/debug/app-debug.apk` on an Android
  9+ device that supports the Bluetooth HID Device profile.
- Test keyboard key-up behavior, modifier chords, pointer movement, neutral
  gamepad reports, app backgrounding, and Bluetooth reconnection.
- Run `python windows/usb_input_host.py --check` when changing the optional USB
  bridge. The helper must remain Python-standard-library-only.

## Pull requests

- Keep each pull request focused and explain the user-visible outcome.
- Include the phone model, Android version, and host OS used for device tests.
- Add a landscape screenshot for visible UI changes.
- Never include Bluetooth addresses, computer names, serial numbers, or personal
  library content in screenshots or logs.
- Run the build and `git diff --check` before submitting.

By contributing, you agree that your contribution is licensed under the MIT
License included with this project.
