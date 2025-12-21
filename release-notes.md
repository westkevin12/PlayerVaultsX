# PlayerVaultsX 1.0.2-RC Release Notes

This Release Candidate brings significant improvements to data safety, storage migration robustness, and code quality.

## 🔒 Data Safety & Migration

- **MySQL Primary Key Migration Safety**: The automatic migration to add `scope` to the primary key now performs a pre-check for duplicate entries. If duplicates are detected (e.g., same vault ID for the same player in the same scope), the migration aborts with a clear error to prevent data corruption.
- **Bulk Save Fallback**: The MySQL bulk save operation now includes a fallback mechanism. If a transaction fails, the system attempts to save vaults individually to minimize potential data loss.
- **Atomic File Operations**: Storage conversion (File <-> MySQL) now uses atomic file moves (`Files.move` with `ATOMIC_MOVE`) instead of `renameTo`, ensuring safer data handling during storage backend switches.
- **Conversion Warnings**: Added explicit warnings when converting from legacy formats regarding potential custom NBT data loss.

## 🎨 Scope-Aware Vault Icons

- **Scoped Icons**: Vault icons are now fully scope-aware across all storage providers (`MySQL`, `Mongo`, `File`, `Redis`). Icons set in a specific scope (e.g., a creative world) will be stored and retrieved independently of other scopes.
- **Intelligent Resolution**: `VaultManager` now automatically resolves the correct scope for icon operations, falling back to "global" if necessary.

## 🧹 Code Quality & Linting

- **Refined Suppressions**: Replaced broad `@SuppressWarnings("all")` annotations with specific ones (`unchecked`, `unused`, `null`) to improve code safety and prevent masking of legitimate errors.
- **Lint Fixes**: Resolved various "Unsafe interpretation" and null-safety warnings in `Loader.java` and `PlayerVaults.java`.

## 🖥️ Visual & UI Enhancements

- **New Aliases**: Added `/pv ui` and `/pv gui` as shortcuts to the interactive Vault Selector.
- **Interactive Search**: The "Search" button in the Selector GUI now opens an **Anvil Interface** for text input. Users no longer need to close the inventory and type in chat; they can simply type the item name in the Anvil UI and press the output slot to search!
- **Integrated Dependencies**: The `AnvilGUI` library is now shaded into the plugin, ensuring seamless UI operations without external dependencies.

## 🚀 Performance: Async I/O Refactor & Optimization

- **No More Main Thread Blocking**: All database operations (loading, saving, and deleting vaults) have been moved off the main server thread. This critical change prevents server lag spikes caused by database latency, especially when using remote MySQL or MongoDB servers.
- **Improved Responsiveness**: Vaults now open and close without freezing the server, even under heavy load or slow database connections.
- **Database Optimization (Batch Loading)**:
  - **The Problem**: Searching vaults for items previously triggered N database queries (where N = number of vaults), potentially causing lag on large datasets.
  - **The Solution**: Implemented a new `loadVaults` batch operation in `StorageProvider`.
  - **Efficiency**: Searching now executes **1 single optimized query** instead of 50+ individual ones.
    - **MySQL**: Uses efficient `WHERE vault_id IN (?)` queries.
    - **MongoDB**: Uses the `$in` operator for single-trip retrieval.
    - **File Backend**: Parses the player's data file only once per search.
    - **Redis**: Uses pipelining to batch fetch all keys in one network round-trip.
- **Smart Caching & Optimization**:
  - `VaultOperations`: Optimized to perform asynchronous existence checks before initiating economy transactions, eliminating redundant blocking database calls.
  - `VaultManager`: Refactored `saveVault` to serialize inventory data on the safe main thread while offloading the heavy I/O writing to an asynchronous task.
  - `VaultSearcher`: Moved CPU-heavy item deserialization and searching logic to background threads, ensuring large searches do not freeze the server.
  - `DeleteCommand`: The `delete all` operation for other players now runs asynchronously, preventing server freeze during bulk deletions.
  - `IconCommand`: Saving vault icons is now handled asynchronously.
- **Thread Safety Fixes**: Resulted in safer execution for `ConvertCommand` by ensuring all Bukkit API interactions (like inventory locking) occur strictly on the main thread.

## 🔓 Administrative Tools

- **Force Unlock Command**: Added `/pv unlock <player> <vault#>` to manually release locks on vaults. This is critical for admins to fix "lock held by another session" errors.
- **File Locking Fix**: Resolved an issue in `FileStorageProvider` where file locks could be held indefinitely if a thread was interrupted, preventing the vault from ever opening again.
