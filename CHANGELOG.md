# Changelog

## [1.1.0](https://github.com/azesmway/react-native-unity/compare/v1.0.11...v1.1.0)

Community pull requests reviewed and integrated, plus a round of correctness
hardening across the native layers. Thanks to everyone who contributed!

### Features

- **ios:** support `UnityFramework.xcframework` (device + simulator slices) with a fallback to the classic device-only framework, and document the iOS Simulator export flow — [#190](https://github.com/azesmway/react-native-unity/pull/190) (@vasihc)

### Bug Fixes (community PRs)

- **android:** support Unity 6 in `UPlayer.requestFrame()` — [#164](https://github.com/azesmway/react-native-unity/pull/164) (@Beatn1ck)
- **ios:** `pauseUnity` now takes `BOOL` instead of `BOOL *` — [#186](https://github.com/azesmway/react-native-unity/pull/186) (@vasihc)
- **ios:** pause Unity instead of unloading it in `prepareForRecycle` (Fabric) — [#187](https://github.com/azesmway/react-native-unity/pull/187) (@vasihc)
- **ios:** initialize Unity from `layoutSubviews` and retry attaching the root view — [#188](https://github.com/azesmway/react-native-unity/pull/188) (@vasihc, supersedes [#174](https://github.com/azesmway/react-native-unity/pull/174) by @amitpdev)
- **types:** point `types` at the emitted declaration file and include `ViewProps` — [#185](https://github.com/azesmway/react-native-unity/pull/185) (@vasihc)
- **expo:** ship the Expo config plugin in the published package — [#189](https://github.com/azesmway/react-native-unity/pull/189) (@vasihc)

### Bug Fixes (additional hardening)

- **expo:** the config plugin no longer overwrites the consumer app's `name` on every `expo prebuild`.
- **ios:** deliver `onPlayerUnload` / `onPlayerQuit` through the Fabric event emitter (they never reached JS on the new architecture).
- **ios:** implement the `resumeUnity` command on the Fabric view, and resume the shared engine when a recycled view returns on screen.
- **ios:** the `layoutSubviews` boot retry now holds `self` weakly and stops after unload so it cannot resurrect a torn-down engine.
- **android:** log and abort (instead of NPE) when the `UnityPlayer` constructor fails.
- **android:** select the `UnityPlayer` constructor by signature instead of a hard-coded index (fragile on Unity 6).
- **android:** `pauseUnity` now honors its `pause` argument and keeps `_isUnityPaused` in sync with `resumeUnity` / host lifecycle.
- **android:** guard the `null` `FrameLayout` the Unity 6 `requestFrame()` fix can return; fix `setZ` reflection; make the `fullScreen` prop take effect; clear the static view reference on drop.

### Docs

- Document `resumeUnity()`, `onPlayerUnload`, `onPlayerQuit`; fix the `windowFocusChanged` default; reconcile the iOS Simulator support table with the XCFramework section.
