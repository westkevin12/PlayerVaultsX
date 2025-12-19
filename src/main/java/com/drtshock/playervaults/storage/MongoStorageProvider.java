package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MongoStorageProvider implements StorageProvider {

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    @Override
    public void initialize() throws StorageException {
        Config.Storage.MongoDB mongoConfig = PlayerVaults.getInstance().getConf().getStorage().getMongo();
        if (mongoConfig == null) {
            throw new StorageException("MongoDB configuration is missing");
        }

        try {
            mongoClient = MongoClients.create(mongoConfig.getConnectionUri());
            database = mongoClient.getDatabase(mongoConfig.getDatabase());
            collection = database.getCollection(mongoConfig.getCollection());
            // Ensure index on uuid + vaultNumber for performance?
            // collection.createIndex(Indexes.compoundIndex(Indexes.ascending("uuid"),
            // Indexes.ascending("vault_number")));
        } catch (Exception e) {
            throw new StorageException("Failed to initialize MongoDB connection", e);
        }
    }

    @Override
    public void shutdown() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Override
    public void saveVault(UUID p, int vaultNumber, String serialized) throws StorageException {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", p.toString()), Filters.eq("vault_number", vaultNumber));
            Document doc = collection.find(filter).first();

            if (doc == null) {
                doc = new Document("uuid", p.toString())
                        .append("vault_number", vaultNumber)
                        .append("data", serialized)
                        .append("last_modified", System.currentTimeMillis());
                collection.insertOne(doc);
            } else {
                collection.updateOne(filter, Updates.combine(
                        Updates.set("data", serialized),
                        Updates.set("last_modified", System.currentTimeMillis())));
            }
        } catch (Exception e) {
            throw new StorageException("Failed to save vault to MongoDB", e);
        }
    }

    @Override
    public String loadVault(UUID p, int vaultNumber) throws StorageException {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", p.toString()), Filters.eq("vault_number", vaultNumber));
            Document doc = collection.find(filter).first();
            return doc != null ? doc.getString("data") : null;
        } catch (Exception e) {
            throw new StorageException("Failed to load vault from MongoDB", e);
        }
    }

    @Override
    public boolean vaultExists(UUID p, int vaultNumber) throws StorageException {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", p.toString()), Filters.eq("vault_number", vaultNumber));
            return collection.countDocuments(filter) > 0;
        } catch (Exception e) {
            throw new StorageException("Failed to check if vault exists in MongoDB", e);
        }
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID p) throws StorageException {
        Set<Integer> vaults = new HashSet<>();
        try {
            Bson filter = Filters.eq("uuid", p.toString());
            for (Document doc : collection.find(filter)) {
                Integer num = doc.getInteger("vault_number");
                if (num != null) {
                    vaults.add(num);
                }
            }
        } catch (Exception e) {
            throw new StorageException("Failed to get vault numbers from MongoDB", e);
        }
        return vaults;
    }

    @Override
    public void deleteVault(UUID p, int vaultNumber) throws StorageException {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", p.toString()), Filters.eq("vault_number", vaultNumber));
            collection.deleteOne(filter);
        } catch (Exception e) {
            throw new StorageException("Failed to delete vault from MongoDB", e);
        }
    }

    @Override
    public void deleteAllVaults(UUID p) throws StorageException {
        try {
            Bson filter = Filters.eq("uuid", p.toString());
            collection.deleteMany(filter);
        } catch (Exception e) {
            throw new StorageException("Failed to delete all vaults from MongoDB", e);
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() {
        Set<UUID> uuids = new HashSet<>();
        try {
            for (String uuidStr : collection.distinct("uuid", String.class)) {
                try {
                    uuids.add(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to get all player UUIDs", e);
        }
        return uuids;
    }

    @Override
    public void cleanup(long olderThanTimestamp) {
        try {
            collection.deleteMany(Filters.lt("last_modified", olderThanTimestamp));
        } catch (Exception e) {
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to cleanup old vaults", e);
        }
    }

    @Override
    public void saveVaults(java.util.Map<UUID, java.util.Map<Integer, String>> vaults) {
        if (vaults == null || vaults.isEmpty())
            return;

        List<com.mongodb.client.model.WriteModel<Document>> operations = new java.util.ArrayList<>();

        for (java.util.Map.Entry<UUID, java.util.Map<Integer, String>> entry : vaults.entrySet()) {
            String uuid = entry.getKey().toString();
            for (java.util.Map.Entry<Integer, String> vaultEntry : entry.getValue().entrySet()) {
                int vaultId = vaultEntry.getKey();
                String data = vaultEntry.getValue();

                Bson filter = Filters.and(Filters.eq("uuid", uuid), Filters.eq("vault_number", vaultId));

                operations.add(new com.mongodb.client.model.UpdateOneModel<>(
                        filter,
                        Updates.combine(
                                Updates.set("data", data),
                                Updates.set("last_modified", System.currentTimeMillis()),
                                Updates.setOnInsert("uuid", uuid),
                                Updates.setOnInsert("vault_number", vaultId)),
                        new com.mongodb.client.model.UpdateOptions().upsert(true)));
            }
        }

        if (!operations.isEmpty()) {
            try {
                collection.bulkWrite(operations);
            } catch (Exception e) {
                PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to bulk save vaults", e);
            }
        }
    }

    @Override
    public void saveVaultIcon(UUID p, int vaultId, String iconData) throws StorageException {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", p.toString()), Filters.eq("vault_number", vaultId));
            Document doc = collection.find(filter).first();

            if (doc == null) {
                // If vault doesn't exist, we probably shouldn't set an icon, or we create a
                // hollow entry?
                // Let's create it with null data but valid icon.
                doc = new Document("uuid", p.toString())
                        .append("vault_number", vaultId)
                        .append("icon", iconData)
                        .append("last_modified", System.currentTimeMillis());
                collection.insertOne(doc);
            } else {
                collection.updateOne(filter, Updates.set("icon", iconData));
            }
        } catch (Exception e) {
            throw new StorageException("Failed to save vault icon to MongoDB", e);
        }
    }

    @Override
    public String loadVaultIcon(UUID p, int vaultId) throws StorageException {
        try {
            Bson filter = Filters.and(Filters.eq("uuid", p.toString()), Filters.eq("vault_number", vaultId));
            Document doc = collection.find(filter).first();
            return doc != null ? doc.getString("icon") : null;
        } catch (Exception e) {
            throw new StorageException("Failed to load vault icon from MongoDB", e);
        }
    }
}
