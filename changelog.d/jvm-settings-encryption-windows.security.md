JVM on Windows: the master key of the encrypted settings is now protected with DPAPI for the current user and kept in the `settings-master-key` file.

  - ABI: no change
  - Source: no change
  - Behavior: the key follows the user profile (roaming profiles, Citrix profile management) and survives an admin password reset of a domain account. After such a reset of a local account, or when the profile's DPAPI keys are lost, Kalium throws `SettingsEncryptionException`.
  - Migration: none needed.
