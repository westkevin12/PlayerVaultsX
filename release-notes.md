# PlayerVaultsX v1.0.3 Release Notes

This release focuses on platform stability, internal security, and performance optimizations through dependency updates.

## 🛠️ Internal Improvements & Dependency Updates

This version brings several key libraries up to date to ensure long-term stability and security fixes.

- **Storage Drivers**:
  - Updated **MySQL Connector** to `9.6.0` for improved connection handling.
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

## 🚀 Performance & Stability

- Verified full compatibility with the latest dependency versions across all storage backends (MySQL, MongoDB, Redis, File).
