# PlayerVaultsX (Modern Edition)

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-orange?style=for-the-badge&logo=minecraft)
![Java Version](https://img.shields.io/badge/Java-21-red?style=for-the-badge&logo=openjdk)
[![Build Status](https://github.com/westkevin12/PlayerVaultsX/actions/workflows/deploy.yml/badge.svg)](https://github.com/westkevin12/PlayerVaultsX/actions/workflows/deploy.yml)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue?style=for-the-badge)](https://github.com/westkevin12/PlayerVaultsX)

> [!IMPORTANT] > **Complete Architectural Overhaul**
> This fork modernizes the original PlayerVaultsX for the 1.21+ era. It removes legacy technical debt (including `sun.misc.Unsafe`) in favor of high-performance, stable, and future-proofed Java 21 code.

## 🚀 Why this Fork?

This version is designed for **Large Networks** that require database-backed reliability.

### Key Technical Enhancements

- **Pluggable Storage Engine**: Uses a strict Strategy Pattern. Swap between high-speed FlatFile or Enterprise-grade **MySQL/MariaDB** via config.
- **Zero Legacy Bloat**: Entirely removed internal `sun.misc` dependencies that often cause crashes on modern JVMs.
- **Adventure UI**: Native support for MiniMessage and Adventure Components for beautiful, translatable GUIs.
- **Advanced Serialization**: Utilizes **CardboardBox** for NBT-safe item serialization, ensuring items with complex metadata (custom enchants, attributes) are never lost during transfer.

## 🛠 Storage Strategy Architecture

The core logic is now decoupled from the storage layer. Developers can extend storage capabilities by implementing a single interface:

```mermaid
graph LR
    A[PlayerVaults Core] --> B{StorageProvider}
    B --> C[FileStorage]
    B --> D[MySQLStorage]
    B --> E[Custom Implementation]
```

## 📥 Installation

1. **Requirement**: [Java 21+](https://adoptium.net/) is required.
2. Drop the `PlayerVaultsX.jar` into your `/plugins/` directory.
3. Configure your backend in `config.conf`.

### Database Configuration (Recommended for Networks)

To move away from legacy `.yml` storage, set your provider to `mysql`:

```hocon
storage {
  type = "mysql"
  host = "localhost"
  port = 3306
  database = "playervaults"
  username = "vault_admin"
  password = "secure_password"
}
```

## 🔄 Migration

Upgrading from a legacy version? Check out our [Migration Guide](MIGRATION.md) for instructions on moving your data to MySQL safely.

## ⌨️ Commands & Permissions

| Command          | Permission                | Purpose                                |
| ---------------- | ------------------------- | -------------------------------------- |
| `/pv <#>`        | `playervaults.amount.<#>` | Open a specific vault.                 |
| `/pv <user> <#>` | `playervaults.admin`      | View/Edit another player's vault.      |
| `/pvdel <#>`     | `playervaults.delete`     | Wipe a vault's contents.               |
| `/pvconvert`     | `playervaults.convert`    | **Migrate** legacy file data to MySQL. |

## 🏗 Building

```bash
mvn clean install
```

## 📜 Credits

- **Original Author**: drtshock (Original PlayerVaults concept)
- **Modernization & Lead Developer**: [westkevin12](https://github.com/westkevin12)
