# Protected preferences v3

`EncryptedSharedPreferences(context, filename)` retains its public API. New writes
use AES-GCM authentication and encryption, with a random 96-bit nonce and 128-bit
tag. The v3 key is domain-separated from the existing primary integrity key using
HMAC-SHA256. The primary material must come from Android Keystore; the old
`failClosedWhenKeyStoreUnavailable=false` configuration option cannot select the
v3 key. No plaintext, file-key or device-ID write fallback is available.

## Binding and representation

- Names use `e3_`; values and the authenticated format header use `v3_`.
- Length-prefixed AAD authenticates library domain, version, purpose and file
  identity. Value AAD additionally authenticates the nullable logical key.
- The name, value and header purposes are distinct. A ciphertext cannot be
  reused across purposes by changing a prefix.
- The file identity includes the canonical data directory of the supplied
  context and the actual preference filename. Credential-protected and
  device-protected files are distinct. Null/empty filename and the explicit
  `${packageName}_preferences` default name identify the same file.
- Null, empty and literal `__NULL__` *keys* have separate v3 representations.
  Legacy used a colliding reserved key sentinel; an old sentinel key is migrated
  as null because its original intent cannot be reconstructed. The existing
  typed value encoding and null-value/empty-set getter semantics are retained.
- Internal metadata is excluded from `all`, `keySet()` and user listeners.

Moving a value between logical keys, moving entries/header between files,
altering a protected name, or an invalid/unknown v3 payload fails closed with
`ProtectedStorageUnavailableException`. A v3 failure never attempts legacy
decryption. The physical file path is part of the contract: copying/renaming a v3
file or moving it between storage contexts is not an authorized migration API.

## One-way legacy migration

Migration happens on the first value read or committed editor operation. It
rewrites the entire remaining legacy snapshot, rather than accepting mixed old
and new entries indefinitely. Names can still be enumerated without decrypting
values so explicitly authorized removal of corrupt values remains possible.

1. Require the strict Keystore-backed primary key. Read the legacy snapshot under
   a shared per-file process lock. Reject duplicate logical aliases, unreadable
   retained records and malformed typed data before writing.
2. Encode the replacement entirely as file/key-bound v3 data in memory. Legacy
   key providers are evaluated lazily and only read existing material. Salt-only
   device-ID state is supported; missing legacy keys/salt are never regenerated.
3. Create a per-file Keystore `pending` alias containing the SHA-256 of the exact
   canonically encoded source snapshot. This authorizes retry of that snapshot
   if the process stops before the preference commit.
4. Commit all v3 records and the authenticated header in one synchronous
   `SharedPreferences.Editor.commit()`.
5. Create the permanent per-file Keystore `committed` alias before returning
   decrypted data or reporting a successful user edit. After this point, legacy
   data or a missing header is rejected even if an old XML snapshot is restored.

A failure or exception from preference commit can already have changed Android's
in-memory preference map. It poisons every wrapper of that file in the current
process. No other wrapper may finalize the fence from an unpersisted map. After
restart, the unchanged original legacy snapshot may retry under its pending
digest; a changed legacy snapshot is rejected. A fully authenticated v3 snapshot
can finish an interrupted fence without going back to legacy.

Migration itself emits no logical change notifications. User editors stage
logical operations and resolve names at commit under the same file lock, so two
editors cannot produce conflicting randomized aliases. `apply()` also performs a
synchronous durable commit for protected data. Callbacks run after persistence
and outside the file lock.

## Explicit recovery and deployment

Unreadable data is not treated as an empty store, default enrollment, or a reason
to replace keys. `remove()` can delete selected corrupt values when names/header
remain authentic. An explicit `edit().clear()` discards all old data and creates
a fresh bound v3 snapshot, including under a mismatching pending migration. It
retains the version fence. Consumers must authorize destructive reset/enrollment
at their own authenticated boundary; the library never requests one silently.

Old binaries cannot read v3. Coordinate library upgrades across code accessing
the same protected files, retain backup exclusions, and do not mix raw/plain
`SharedPreferences` writers with the protected wrapper. The protocol uses the
normal single-process SharedPreferences contract; it does not add cross-process
preference transactions.

## Security and verification limits

Legacy ciphertext had no key/file binding. Migration establishes that binding
for future operations; it cannot prove that legacy data was never substituted
before its first accepted migration. The fence prevents downgrade to legacy,
not replay of an older valid v3 value/snapshot in the same file/key or deletion
of entries. It is not hardware rollback resistance or remote attestation.

Compromised app code/Frida can access plaintext while it is used and invoke
Keystore under the app UID. AEAD and migration fencing do not attest an intact
process. Keep low-assurance behavior signals supplemental to stronger auth.

Local Robolectric/JVM regressions cover binding, legacy types and providers,
pending retry/recovery/reset, failed commits, listeners and concurrent editors.
Migration fences are injected in those tests; actual AndroidKeyStore alias
creation/durability, reboot/direct-boot and device-specific failures require
real-device QA. No such device evidence is claimed by the unit tests or APK build.
