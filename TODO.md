PlayerVaultsX NBT API Refactor TODO
This document outlines the remaining tasks to complete the transition from CardboardBox serialization to the more robust NBT API for item storage.

Phase 1: Implement NBT Serialization Logic
The core of this refactor is to replace the existing CardboardBoxSerialization with a new NBT-based system.

[x] Create NBTSerialization.java:

Create a new class com.drtshock.playervaults.vaultmanagement.NBTSerialization.

This class will house the new logic for saving and loading inventories.

[x] Implement toStorage method:

Create a static method public static String toStorage(Inventory inventory, String target).

This method will take an inventory's contents (ItemStack[]).

It will convert the array of ItemStacks into a serialized, Base64-encoded NBT string. This ensures full metadata preservation.

[x] Implement fromStorage method:

Create a static method public static ItemStack[] fromStorage(String data, String target).

This method will take the Base64 NBT string from the storage backend.

It will deserialize the string back into an ItemStack[] array.

Include robust error handling for corrupted data, similar to the existing implementation.

Phase 2: Integration and Deprecation
Once the new serialization logic is in place, we need to integrate it into the plugin and handle the old data.

[x] Integrate NBTSerialization into VaultManager.java:

In VaultManager.java, find all calls to CardboardBoxSerialization.toStorage and CardboardBoxSerialization.fromStorage.

Replace them with calls to the new NBTSerialization.toStorage and NBTSerialization.fromStorage methods.

[x] Deprecate CardboardBoxSerialization.java:

Add a @Deprecated annotation to the CardboardBoxSerialization class.

Add a Javadoc comment explaining that it is deprecated in favor of NBTSerialization and will be removed in a future version. This class will be kept temporarily for the data conversion process.

Phase 3: Data Migration
We must provide a seamless way for server owners to upgrade their existing data to the new NBT format.

[x] Create a VaultDataConverter.java:

Create a new converter class in the com.drtshock.playervaults.converters package.

This converter will be responsible for migrating vault data from the old serialization format to the new NBT format.

It should iterate through all existing vault files (or database entries), read the data using CardboardBoxSerialization.fromStorage, and then re-save it using NBTSerialization.toStorage.

[x] Register the new converter:

Add the VaultDataConverter to the list of converters in ConvertCommand.java.

This will allow server admins to run a command like /pvconvert vaultdata to migrate their data.

[x] Document the conversion process:

Add a note to the plugin's documentation or release notes explaining the need for data conversion and how to perform it.


Phase 4: Standardize NBT Data
[ ] Finish migration and converters to a standardized versioned serialized nbt data.

Phase 5: Final Cleanup
After the refactor is complete and a data migration path is established, we can clean up the codebase.

[ ] Remove CardboardBox dependency:

After a suitable deprecation period (e.g., one or two major releases), the dev.kitteh.cardboardbox dependency can be removed from the pom.xml.

[ ] Remove CardboardBoxSerialization.java:

Once the dependency is gone, the deprecated CardboardBoxSerialization.java file can be safely deleted.