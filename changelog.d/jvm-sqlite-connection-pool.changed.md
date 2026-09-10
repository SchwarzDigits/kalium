JVM: file-backed databases keep their SQLite connections open, one writer and up to eight readers, instead of opening a new connection for every statement. Reads no longer wait for a running write, and per-connection state such as an attached database now lasts until it is detached.

  - ABI: no change
  - Source: no change
  - Behavior: SQLite's busy timeout is set to five seconds, so a second process writing to the same file waits that long for the lock.
  - Migration: none needed.
