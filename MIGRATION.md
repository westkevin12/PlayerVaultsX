# Migration Guide

## Migrating from Legacy PlayerVaults

If you are upgrading from an older version of PlayerVaults (versions < 1.0.0 or legacy forks), you must migrate your data if you wish to switch to MySQL.

### 1. Backup First

Always create a full backup of your `plugins/PlayerVaults` folder before performing any migration.

### 2. Update the Plugin

Replace your old `PlayerVaultsX.jar` with the new `PlayerVaultsX.jar`.

### 3. File-to-MySQL Migration

If you want to move from FlatFile (YAML) to MySQL:

1.  Stop the server.
2.  Set `storage.type` to `mysql` in `config.conf` and configure your credentials.
3.  Start the server.
4.  Run the conversion command:
    ```bash
    /pvconvert storage file mysql
    ```
    _(Note: This command will read from your existing files and insert them into the database.)_
5.  Wait for the completion message.

## Common Issues

- **Missing Items?** Ensure you didn't delete the `uuidvaults` folder before converting.
- **Connection Errors?** Verify your MySQL credentials and firewall settings.
