Android builds can leave AVS out, with `kalium.disableAndroidAvs=true` in Kalium's `gradle.properties`. With calling turned off, a network change no longer loads AVS on Android and JVM.

  - ABI: unchanged
  - Source: unchanged
  - Behavior: unchanged by default, except that `networkChanged` is ignored until a client has calling enabled, as on Apple. With the property, the Android targets don't depend on `com.wire:avs`; the flow and media managers do nothing, audio uploads carry no waveform, and calling must stay off.
  - Migration: none. Applications that turn calling off can set the property and stop shipping `libavs.so`.
