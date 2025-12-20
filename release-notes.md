# Release Notes - v1.0.1

**PlayerVaultsX Modern Edition** | **The "Everything is New" Update**

This is not just a refactor; it is a complete reimagining of PlayerVaultsX for high-performance networks. We have introduced enterprise-grade storage, instant cross-server synchronization, and a beautiful new in-game UI.

### 🚀 New Features

#### **1. Visual Vault Selector & Custom Icons**

- **GUI Vault Selector**: No more chat-based navigation. Players can now browse their vaults in a paginated GUI (`/pv selector`).
- **Custom Icons**: Players can set specific icons for their vaults to stay organized (`/pv icon <data>`).
- **Dynamic Status**: The selector visualizes vault status (Locked, Unowned/Purchasable, Owned).

#### **2. Vault Search Engine**

- **Global Item Search**: Players can search for items across **all** their vaults instantly using `/pv search <query>`.
- **Interactive Results**: Click on search results to open the specific vault containing the item.

#### **3. Advanced Redis Synchronization**

- **Cross-Server Sync**: Implemented Redis Pub/Sub locking. When a player opens a vault on Server A, it is instantly locked on Server B to prevent dupes and data corruption.
- **Asynchronous Caching**: All vault data is cached in Redis to minimize database reads, ensuring valid data is always available instantly.

#### **4. Cloud Backups (Tiny S3)**

- **Zero-Dependency Client**: Built a custom S3 client (AWS/MinIO/Spaces) that adds **0 dependencies** to the jar.
- **Streaming Backups**: Capable of streaming large backups (100MB+) to the cloud without memory spikes.

#### **5. Modern Storage & Security**

- **Redis Optimization**: Implemented asynchronous operations for all Redis interactions to prevent main-thread blocking. Added Pub/Sub for instant cross-server synchronization.
- **Smart Caching**: Vault icons and metadata are now cached in Redis, significantly reducing database load on large networks.

- **Tiny S3 Client**: Built a custom, zero-dependency S3 implementation that supports **AWS Signature V4**.
- **Universal Compatibility**: Works out-of-the-box with AWS S3, MinIO, DigitalOcean Spaces, and other S3-compatible providers.
- **Efficient**: Uses `UNSIGNED-PAYLOAD` streaming to handle large backups with minimal memory footprint.

#### **6. Extended Migration Support**

- **Vanilla EnderChest Converter**: Easily migrate player EnderChest contents into PlayerVaults using `/pvconvert EnderChest`. This allows you to repurpose the vanilla enderchest storage for other gameplay mechanics while keeping player data safe in vaults.

#### **7. Admin Inspector Mode**

- **Safe Auditing**: Admins can now open any player's vault in **ReadOnly Mode** using `/pv <player> <number> -r` (or `-i`, `inspect`).
- **Risk-Free**: This allows staff to inspect suspicious vaults without any risk of accidentally modifying contents or triggering anti-dupe locks.
