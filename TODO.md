# TODO: PlayerVaultsX Modern Edition

## 🎨 GUI & User Experience

- [ ] **Custom Icon Support:** Implement a system for administrators to define custom icons (using Custom Model Data) for vaults, such as "Locked" textures for unpurchased or unauthorized vaults.
- [ ] **Search & Filter Functionality:** Add a GUI button or internal command to allow players to search for specific items across all owned vaults, especially for large networks supporting 99+ vaults.

## 🛡️ Administrative & Logging Improvements

- [ ] **Detailed Audit Logging:** Develop a dedicated "Transaction Log" to record specific item additions and removals by players to track potential duplication or "insider trading".
- [ ] **Read-Only Inspector Mode:** Enhance the `/pv <user> <#>` command with an "Inspector" mode, allowing admins to view vaults without the risk of accidentally displacing items.

## 📈 Network Scalability & Safety

- [ ] **Redis Caching Layer:** Implement Redis support to cache frequently accessed vaults in memory, reducing the I/O burden on MySQL for massive networks.
- [ ] **Cross-Server Sync Locking:** Create an explicit "Sync Lock" mechanism to prevent players from opening the same vault on multiple sub-servers simultaneously, closing potential duplication vectors.

## 💾 Advanced Storage Providers

- [ ] **NoSQL Implementation:** Develop a storage provider for **MongoDB** to better handle flexible, document-based NBT data.
- [ ] **Cloud Redundancy:** Add native support for S3-compatible object storage to provide automated, off-site redundancy for flat-file backups.

## 🌍 Integration & Migration

- [ ] **World/Server-Group Scoping:** Implement built-in support for world-specific or server-group-specific vaults, allowing networks to separate inventories between different game modes (e.g., Survival vs. Creative).
- [ ] **Extended Migration Support:** Research and update the `/pvconvert` system to include converters for the latest versions of competing plugins and their modernized serialization formats.
