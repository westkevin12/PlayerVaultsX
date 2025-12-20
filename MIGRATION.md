# Migration Guide

## Migrating from Legacy PlayerVaults

If you are upgrading from an older version of PlayerVaults (versions < 1.0.0 or legacy forks), you must migrate your data if you wish to switch to MySQL.

### 1. Backup First

Always create a full backup of your `plugins/PlayerVaults` folder before performing any migration.

### 2. Update the Plugin

Replace your old `PlayerVaultsX.jar` with the new `PlayerVaultsX.jar`.

### 3. Automatic Migration (Recommended)

PlayerVaultsX detects when you are switching storage backends and will automatically migrate your data for you.

**Scenario A: Continuing with FlatFile (Standard)**

1. Simply replace the `.jar`.
2. Restart the server.
3. Your data works automatically.

**Scenario B: Switching to MySQL (Performance)**

1. Stop the server.
2. Edit `config.conf`: set `storage.type` to `mysql` and enter your database credentials.
3. Start the server.
4. **That's it!** The plugin detects existing flat-file data and an empty database, and will automatically import your vaults into MySQL in the background. Check the console for progress.

## Manual Migration (Advanced)

If the automatic migration fails or you need to merge data manually, you can use the command line:

```bash
/pvconvert storage file mysql
```

_(Only run this if auto-migration did not trigger or if instructed by support.)_

### Example: Migrating from MySQL to MongoDB (Advanced)

If you are switching from MySQL to MongoDB, you can use the conversion command to move your data.

1. Configure `storage.type = "mongo"` in `config.conf` and set up your MongoDB connection details.
2. Start the server.
3. Run the conversion command:
   ```bash
   /pvconvert storage mongo
   ```
   _The plugin will detect you are currently on Mongo (destination) and will attempt to read from your previous MySQL (or File) source automatically._

## Common Issues

- **Missing Items?** Ensure you didn't delete the `uuidvaults` folder before converting.
- **Connection Errors?** Verify your MySQL credentials and firewall settings.
