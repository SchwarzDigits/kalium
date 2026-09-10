JVM: local databases are now encrypted in SQLCipher's format, version 4 (through SQLite3 Multiple Ciphers), when `KaliumConfigs.shouldEncryptData` is on, which is the default.

  - ABI: no change in `logic`; JVM `PlatformUserStorageProperties` takes a new `userDbSecretProvider` parameter.
  - Source: code that constructs JVM `PlatformUserStorageProperties` directly has to pass the secret provider.
  - Behavior: the JVM SQLite driver changes from `org.xerial:sqlite-jdbc` to `io.github.willena:sqlite-jdbc`. Both use the `org.sqlite` package, so consumers must not add xerial's driver next to it. The keys are kept in the `app-preference` file, which is still plaintext.
  - Migration: existing unencrypted JVM databases are not migrated.
