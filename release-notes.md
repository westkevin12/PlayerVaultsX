# PlayerVaultsX v1.0.3 Release Notes

This release focuses on platform stability, internal security, and performance optimizations through dependency updates.

## 🛠️ Internal Improvements & Dependency Updates

This version brings several key libraries up to date to ensure long-term stability and security fixes.

- **Storage Drivers**:
  - **Switched to MariaDB JDBC Driver**: Replaced MySQL Connector with MariaDB client, reducing driver footprint by ~2 MB while maintaining full MySQL compatibility.
  - Updated **MongoDB Driver** to `5.1.0` for better modern MongoDB compatibility.
  - Updated **Jedis (Redis)** to `5.2.0` with performance optimizations.
- **Support Libraries**:
  - Updated **PlaceholderAPI** to `2.12.2` for improved integration robustness.
  - Updated **CardboardBox** to `3.0.6` for better item serialization.
  - Updated **Checker Qual** to `3.53.1` for enhanced static analysis.
- **Testing Frameworks**:
  - Updated **MockBukkit** to `3.133.2` for more accurate Spigot server simulation.
  - Updated **JUnit Jupiter** to `5.11.4` for better test execution.
  - Updated **Mockito** to `5.21.0` for improved mocking capabilities.

## 🚀 Performance & Build Optimizations

- **18% JAR Size Reduction**: Reduced final binary from **4.43 MB** to **3.60 MB**.
- **Dependency Pruning**: Removed nearly **2.5 MB** of unnecessary transitive libraries (JNA, Waffle-JNA).
- **Modern Platform Support**: Marked Adventure and GSON libraries as `provided` to leverage native server APIs in modern Paper/Spigot environments.
- **Shaded Refinement**: Optimized class relocations and aggressive resource filtering for a leaner, more efficient JAR.
