Fixed link previews without a permanent URL: `MessageDetailsView` returned the literal text `' || url || '` as `permanentUrl` instead of falling back to the preview's `url`.

  - ABI: no change
  - Source: no change
  - Behavior: databases that run user database migration 139 get the fixed `MessageDetailsView`; databases that already ran it keep the old view until a later migration recreates it.
  - Migration: none added.
