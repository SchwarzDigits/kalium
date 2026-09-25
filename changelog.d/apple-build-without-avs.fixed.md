Apple builds can leave AVS out again, with `kalium.disableAppleAvs=true` in Kalium's `gradle.properties`.

  - ABI: unchanged
  - Source: unchanged
  - Behavior: unchanged by default. With the property, the Apple targets neither depend on AVS nor apply the AVS runtime plugin; calling on Apple reports AVS as unavailable, and processing call notifications fails with `UnsupportedPlatform`.
  - Migration: none. Applications that turn calling off can drop the AVS runtime plugin and the Xcode steps that embed AVS.
