package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.util.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;
import java.util.UUID;

public class RedisCacheLayer implements StorageProvider {

    private final StorageProvider backingStore;
    private JedisPool pool;
    private boolean enabled = false;
    private long ttlSeconds = 1800; // default 30 mins

    public RedisCacheLayer(StorageProvider backingStore) {
        this.backingStore = backingStore;
        initialize();
    }

    @Override
    public void initialize() {
        Config.Storage.Redis config = PlayerVaults.getInstance().getConf().getStorage().getRedis();

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(128);

            this.pool = new JedisPool(poolConfig, config.getHost(), config.getPort(), config.getTimeout(),
                    config.getPassword().isEmpty() ? null : config.getPassword(), config.getDatabase(), config.isSsl());

            // Test connection
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
            this.ttlSeconds = config.getTtl() * 60;
            this.enabled = true;
            Logger.info("Redis caching enabled!");
        } catch (Exception e) {
            Logger.warn("Failed to connect to Redis. Caching will be disabled: " + e.getMessage());
            this.enabled = false;
        }
    }

    @Override
    public void shutdown() {
        if (pool != null) {
            pool.close();
        }
        if (backingStore != null) {
            backingStore.shutdown();
        }
    }

    private String getKey(UUID holder, int number) {
        return "pv:" + holder.toString() + ":" + number;
    }

    @Override
    public void saveVault(UUID holder, int number, String serialized) throws StorageException {
        // Write-through: Save to DB first
        backingStore.saveVault(holder, number, serialized);

        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String key = getKey(holder, number);
                jedis.setex(key, ttlSeconds, serialized);
            } catch (Exception e) {
                Logger.warn("Failed to update Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public String loadVault(UUID holder, int number) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String key = getKey(holder, number);
                String data = jedis.get(key);
                if (data != null) {
                    Logger.debug("Loaded vault from cache: " + key);
                    // Refresh TTL
                    jedis.expire(key, ttlSeconds);
                    return data;
                }
            } catch (Exception e) {
                Logger.warn("Failed to load from Redis cache: " + e.getMessage());
            }
        }

        // Cache miss
        String data = backingStore.loadVault(holder, number);

        if (enabled && data != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.setex(getKey(holder, number), ttlSeconds, data);
            } catch (Exception e) {
                // Ignore cache write errors on read
            }
        }
        return data;
    }

    @Override
    public boolean vaultExists(UUID holder, int number) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                if (jedis.exists(getKey(holder, number))) {
                    return true;
                }
            }
        }
        return backingStore.vaultExists(holder, number);
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID holder) throws StorageException {
        // Complex query, just use backing store for now
        // A full implementation would need a Set in Redis to track vault numbers per
        // player
        return backingStore.getVaultNumbers(holder);
    }

    @Override
    public void deleteVault(UUID holder, int number) throws StorageException {
        backingStore.deleteVault(holder, number);
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(getKey(holder, number));
            } catch (Exception e) {
                Logger.warn("Failed to delete from Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public void deleteAllVaults(UUID holder) throws StorageException {
        backingStore.deleteAllVaults(holder);
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                // Inefficient but safe without a tracking set
                Set<String> keys = jedis.keys("pv:" + holder.toString() + ":*");
                if (!keys.isEmpty()) {
                    jedis.del(keys.toArray(new String[0]));
                }
            } catch (Exception e) {
                Logger.warn("Failed to delete all vaults from Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() throws StorageException {
        return backingStore.getAllPlayerUUIDs();
    }

    @Override
    public void saveVaults(java.util.Map<UUID, java.util.Map<Integer, String>> vaults) throws StorageException {
        backingStore.saveVaults(vaults);
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                for (java.util.Map.Entry<UUID, java.util.Map<Integer, String>> playerEntry : vaults.entrySet()) {
                    UUID uuid = playerEntry.getKey();
                    for (java.util.Map.Entry<Integer, String> vaultEntry : playerEntry.getValue().entrySet()) {
                        String key = getKey(uuid, vaultEntry.getKey());
                        jedis.setex(key, ttlSeconds, vaultEntry.getValue());
                    }
                }
            } catch (Exception e) {
                Logger.warn("Failed to bulk update Redis cache: " + e.getMessage());
            }
        }
    }

    @Override
    public void cleanup(long daysSinceLastEdit) throws StorageException {
        backingStore.cleanup(daysSinceLastEdit);
        // We can't easily purge specific items from Redis based on DB age without
        // richer metadata
        // Rely on TTL for cache cleanup
    }
}
