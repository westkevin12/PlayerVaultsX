# Release Notes - v1.0.0

**PlayerVaultsX Modern Edition** | **The Architectural Overhaul** _Performance & Stability for Minecraft 1.21+_

This release marks a complete departure from the legacy codebase. We have rebuilt the core systems to prioritize database reliability, modern API standards, and the total removal of technical debt.

### 🌟 Why Move to the Modern Edition?

If you are running a high-traffic network, legacy vault plugins are often the silent cause of main-thread lag and data corruption. This fork solves those issues at the architectural level.

#### **1. Enterprise-Grade Storage Engine**

- **From Flat-Files to SQL**: Replaced the legacy `.yml` storage bottleneck with a native **MySQL/MariaDB** backend using the **Strategy Pattern**.
- **Pluggable API**: Developers can now implement the `StorageProvider` interface to add custom backends like Redis or MongoDB without touching core logic.
- **Seamless Migration**: Use the new `/pvconvert` tool to migrate your entire legacy `.yml` database into MySQL instantly.

#### **2. Modernized, High-Performance Core**

- **Java 21 Native**: Optimized to leverage the performance and security benefits of the latest LTS Java version.
- **Technical Debt Removal**: Entirely removed unsafe internal dependencies, including `sun.misc.Unsafe`, ensuring your server won't crash on future JVM updates.
- **Adventure & MiniMessage**: Full native implementation of the **Adventure library** for modern, translatable, and high-quality text components in all GUIs and messages.

#### **3. Uncompromising Data Integrity**

- **Advanced Serialization**: Switched to **CardboardBox** serialization. This ensures that items with complex NBT data, custom enchantments, and deep attributes are preserved perfectly across server switches.
- **Refactored Shutdown Safety**: Implemented a new `safelyCloseVault` mechanism. This verifies player inventory states during server stops or reloads, preventing the "ghost item" edits and data loss common in the original fork.

### 🛠 Technical Summary

- **Null-Safe Handlers**: Refactored `onDisable` logic into a dedicated, null-safe helper method to ensure clean shutdowns.
- **Standardized Build**: Reorganized the project structure to follow modern Maven conventions for easier contribution.
- **CI/CD Guardrails**: Added strict version-consistency checks in the build pipeline to prevent mismatched or broken releases.
