# Contributing to PlayerVaultsX

Thank you for your interest in contributing to the modern fork of PlayerVaultsX!

## 🏗 Architectural Standards

To maintain the performance and stability goals of this fork, all contributions must adhere to the following strict standards:

1.  **Java 21 Native**: Use modern Java features (Switch Expressions, Records, Pattern Matching) where appropriate. We do not support Java 8/11/17.
2.  **No `sun.misc.Unsafe`**: We have fully removed internal JVM dependency usage. Do not re-introduce it.
3.  **Strategy Pattern**: If touching the storage layer, you **must** implement the `StorageProvider` interface properly. Do not hardcode SQL or File logic in the core plugin classes.
4.  **Adventure API**: All user-facing text must use the [Adventure](https://docs.advntr.dev/) library (MiniMessage) for formatting. Legacy `ChatColor` is deprecated.

## 🛠 Setup

1.  Fork the repo.
2.  Open in IDE.
3.  Run `mvn clean install` to verify the build.

## 🚀 Pull Request Process

1.  Ensure your code builds successfully.
2.  Update `release-notes.md` if your change is user-facing.
3.  Fill out the Pull Request Template completely.
