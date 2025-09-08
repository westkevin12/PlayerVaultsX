# PlayerVaultsX Improvement Plan

## Phase 1: Dependency and API Updates

- [ ] **Address Deprecated API Usage:**
    - [x] Replace deprecated `Bukkit.getOfflinePlayer(String)` with `Bukkit.getOfflinePlayer(UUID)` where possible. For commands accepting player names, investigate the best approach for name-to-UUID conversion.
    - [x] Replace deprecated `YamlConfigurationOptions.header(String)` with the modern equivalent.
    - [x] Update `JsonParser` usage to `JsonParser.parseString()`.
    - [x] Replace deprecated `new URL(String)` constructor with `new URI(string).toURL()`.
    - [x] Replace deprecated `ItemMeta.hasCustomModelData()` with `getCustomModelData() != 0`.
    - [x] Remove the Spigot bug patch using `sun.misc.Unsafe` as it's likely obsolete for the target Spigot version (1.21.6).
    - [x] Replace deprecated `Class.newInstance()` with `getConstructor().newInstance()`.

## Phase 2: Code Cleanup and Refactoring

- [ ] **Remove Unused Code:**
    - [x] Delete unused fields, methods, and imports across the project to improve readability and reduce clutter.
    - [x] Remove unnecessary `@SuppressWarnings("unchecked")` annotations.

- [ ] **Fix Potential Bugs and Issues:**
    - [x] Add null checks in `Loader.java` to prevent potential `NullPointerException`s.
    - [x] Add `instanceof` checks before unchecked casts in `Loader.java`.
    - [x] Fix resource leak in `PlayerVaults.java` by using a try-with-resources block.
    - [x] Refactor `DeleteCommand.java` and `VaultOperations.java` to ensure consistent user-facing messages.
    - [x] Optimize `SignListener.java` to reduce potential lag by performing cheaper checks first.

## Phase 3: Typesafe Config Library Maintenance

- [ ] **Fix Warnings in Vendored Library:**
    - [x] Remove redundant `Serializable` interface declarations.
    - [x] Parameterize raw types such as `Enum`, `List`, and `HashSet`.
    - [x] Update deprecated `URL` constructor calls.

## Phase 4: Final Review

- [ ] **Final Build and Test:**
    - [x] Perform a final build of the project to ensure all changes compile successfully.
    - [ ] (Manual) Run existing tests and perform manual testing to verify that the fixes haven't introduced regressions.
    - [x] tested on papermc 1.21.8 40 containers and mysql db volume