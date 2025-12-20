# TODO: PlayerVaultsX Modern Edition

## 🎨 GUI & User Experience

- [x] **Custom Icon Support:** Implement a system for administrators to define custom icons (using Custom Model Data) for vaults, such as "Locked" textures for unpurchased or unauthorized vaults.
- [x] **Search & Filter Functionality:** Add a GUI button or internal command to allow players to search for specific items across all owned vaults, especially for large networks supporting 99+ vaults.

## 🛡️ Administrative & Logging Improvements

- [x] **Detailed Audit Logging:** Develop a dedicated "Transaction Log" to record specific item additions and removals by players to track potential duplication or "insider trading".
- [x] **Read-Only Inspector Mode:** Enhance the `/pv <user> <#>` command with an "Inspector" mode, allowing admins to view vaults without the risk of accidentally displacing items.

## 📈 Network Scalability & Safety

- [x] **Redis Caching Layer:** Implement Redis support to cache frequently accessed vaults in memory, reducing the I/O burden on MySQL for massive networks.
- [x] **Cross-Server Sync Locking:** Create an explicit "Sync Lock" mechanism to prevent players from opening the same vault on multiple sub-servers simultaneously, closing potential duplication vectors.

## 💾 Advanced Storage Providers

- [x] **NoSQL Implementation:** Develop a storage provider for **MongoDB** to better handle flexible, document-based NBT data.
- [x] **Cloud Redundancy:** Add native support for S3-compatible object storage to provide automated, off-site redundancy for flat-file backups.

## 🌍 Integration & Migration

- [x] **World/Server-Group Scoping:** Implement built-in support for world-specific or server-group-specific vaults, allowing networks to separate inventories between different game modes (e.g., Survival vs. Creative).
- [x] **Extended Migration Support:** Research and update the `/pvconvert` system to include converters for the latest versions of competing plugins and their modernized serialization formats. (Implemented Vanilla EnderChest Converter)

## 🧪 Quality Assurance & Security Testing

- [x] **Duplication Exploit Simulation:** Create automated tests that attempt race conditions (e.g., rapid open/close, server crashes during save) to verify the "Sync Lock" and transactional safety.
- [x] **Real-World Load Testing:** Simulate high-concurrency usage (e.g., 100+ players opening vaults simultaneously) to ensure Redis/MySQL connection pools handle the load without data loss.
- [ ] **Storage Failover Verification:** Verify behavior when storage backends (MySQL/Redis) suddenly go offline, ensuring graceful failure (prevent access) rather than item loss.
- [ ] **Complex Item Serialization:** Test limits of serialization with complex items (Shulker boxes with NBT, Written Books, illegal stack sizes, custom model data) to ensure no NBT stripping or corruption occurs.
