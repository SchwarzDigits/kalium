JVM: the `app-preference` file, which holds the tokens and database keys, is now written to a temporary file, synced and moved into place, so a crash while saving can no longer leave it truncated.

  - ABI: no change
  - Source: no change
  - Behavior: no change apart from durability; the file stays plaintext.
  - Migration: none needed.
