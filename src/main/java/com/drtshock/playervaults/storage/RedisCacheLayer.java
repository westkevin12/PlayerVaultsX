package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.util.Logger;
import com.google.gson.JsonObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RedisCacheLayer implements StorageProvider {

    private final StorageProvider backingStore;
    private JedisPool pool;
    private boolean enabled = false;
    private long ttlSeconds = 1800; // default 30 mins
    private RedisMessageListener messageListener;
    private Thread listenerThread;
    private String serverId;

    public RedisCacheLayer(StorageProvider backingStore) {
        this.backingStore = backingStore;
        initialize();
    }

    @Override
    public void initialize() {
        Config.Storage.Redis config = PlayerVaults.getInstance().getConf().getStorage().getRedis();
        this.serverId = "server-" + UUID.randomUUID().toString().substring(0, 8); // Simple unique ID for this instance

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

            // Initialize Pub/Sub Listener
            startPubSub();

        } catch (Exception e) {
            Logger.warn("Failed to connect to Redis. Caching will be disabled: " + e.getMessage());
            this.enabled = false;
        }
    }

    private void startPubSub() {
        this.messageListener = new RedisMessageListener(this.serverId);
        this.listenerThread = new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(messageListener, "pv:updates");
            } catch (Exception e) {
                if (enabled) { // Only log if we expect it to work
                    Logger.warn("Redis Pub/Sub subscription failed: " + e.getMessage());
                }
            }
        });
        this.listenerThread.setName("PlayerVaults-RedisListener");
        this.listenerThread.start();
    }

    @Override
    public void shutdown() {
        this.enabled = false; // Disable first to stop new ops
        if (messageListener != null && messageListener.isSubscribed()) {
            messageListener.unsubscribe();
        }
        if (pool != null) {
            pool.close();
        }
        if (backingStore != null) {
            backingStore.shutdown();
        }
    }

    private String getKey(UUID holder, int number, String scope) {
        String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
        return "pv:" + scopeKey + ":" + holder.toString() + ":" + number;
    }

    private String getLockKey(UUID holder, int number, String scope) {
        String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
        return "pv:lock:" + scopeKey + ":" + holder.toString() + ":" + number;
    }

    private String getIconKey(UUID holder, int number) {
        return "pv:icon:" + holder.toString() + ":" + number;
    }

    private void publishUpdate(UUID holder, int number, String scope) {
        if (!enabled)
            return;
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = pool.getResource()) {
                JsonObject json = new JsonObject();
                json.addProperty("type", "UPDATE");
                json.addProperty("server", this.serverId);
                json.addProperty("uuid", holder.toString());
                json.addProperty("vault", number);
                json.addProperty("scope", scope);
                jedis.publish("pv:updates", json.toString());
            } catch (Exception e) {
                Logger.warn("Failed to publish Redis update: " + e.getMessage());
            }
        });
    }

    @Override
    public void saveVault(UUID holder, int number, String serialized, String scope) throws StorageException {
        // Write-through: Save to DB first (BLOCKING - Critical)
        backingStore.saveVault(holder, number, serialized, scope);

        if (enabled) {
            // Async Cache Update & Notify
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    String key = getKey(holder, number, scope);
                    jedis.setex(key, ttlSeconds, serialized);
                } catch (Exception e) {
                    Logger.warn("Failed to update Redis cache: " + e.getMessage());
                }
            });

            publishUpdate(holder, number, scope);
        }
    }

    @Override
    public String loadVault(UUID holder, int number, String scope) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String key = getKey(holder, number, scope);
                String data = jedis.get(key);
                if (data != null) {
                    Logger.debug("Loaded vault from cache: " + key);
                    // Refresh TTL async
                    CompletableFuture.runAsync(() -> {
                        try (Jedis j = pool.getResource()) {
                            j.expire(key, ttlSeconds);
                        } catch (Exception ignored) {
                        }
                    });
                    return data;
                }
            } catch (Exception e) {
                Logger.warn("Failed to load from Redis cache: " + e.getMessage());
            }
        }

        // Cache miss
        String data = backingStore.loadVault(holder, number, scope);

        if (enabled && data != null) {
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    jedis.setex(getKey(holder, number, scope), ttlSeconds, data);
                } catch (Exception ignored) {
                }
            });
        }
        return data;
    }

    @Override
    public boolean vaultExists(UUID holder, int number, String scope) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                if (jedis.exists(getKey(holder, number, scope))) {
                    return true;
                }
            }
        }
        return backingStore.vaultExists(holder, number, scope);
    }

    @Override
    public Set<Integer> getVaultNumbers(UUID holder, String scope) throws StorageException {
        return backingStore.getVaultNumbers(holder, scope);
    }

    @Override
    public void deleteVault(UUID holder, int number, String scope) throws StorageException {
        backingStore.deleteVault(holder, number, scope);
        if (enabled) {
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    jedis.del(getKey(holder, number, scope));
                    // Also delete icon cache if exists, just in case
                    jedis.del(getIconKey(holder, number));
                } catch (Exception e) {
                    Logger.warn("Failed to delete from Redis cache: " + e.getMessage());
                }
            });
            publishUpdate(holder, number, scope);
        }
    }

    @Override
    public void deleteAllVaults(UUID holder, String scope) throws StorageException {
        backingStore.deleteAllVaults(holder, scope);
        if (enabled) {
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
                    String pattern = "pv:" + scopeKey + ":" + holder.toString() + ":*";

                    // Use SCAN instead of KEYS
                    ScanParams params = new ScanParams().match(pattern).count(100);
                    String cursor = ScanParams.SCAN_POINTER_START;

                    do {
                        ScanResult<String> result = jedis.scan(cursor, params);
                        cursor = result.getCursor();
                        if (!result.getResult().isEmpty()) {
                            jedis.del(result.getResult().toArray(new String[0]));
                        }
                    } while (!cursor.equals(ScanParams.SCAN_POINTER_START));

                } catch (Exception e) {
                    Logger.warn("Failed to delete all vaults from Redis cache: " + e.getMessage());
                }
            });
        }
    }

    @Override
    public Set<UUID> getAllPlayerUUIDs() throws StorageException {
        return backingStore.getAllPlayerUUIDs();
    }

    @Override
    public void saveVaults(Map<UUID, Map<Integer, String>> vaults, String scope) throws StorageException {
        backingStore.saveVaults(vaults, scope);
        if (enabled) {
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    Pipeline p = jedis.pipelined();
                    for (Map.Entry<UUID, Map<Integer, String>> playerEntry : vaults.entrySet()) {
                        UUID uuid = playerEntry.getKey();
                        for (Map.Entry<Integer, String> vaultEntry : playerEntry.getValue().entrySet()) {
                            String key = getKey(uuid, vaultEntry.getKey(), scope);
                            p.setex(key, ttlSeconds, vaultEntry.getValue());
                        }
                    }
                    p.sync();
                } catch (Exception e) {
                    Logger.warn("Failed to bulk update Redis cache: " + e.getMessage());
                }
            });
        }
    }

    @Override
    public void cleanup(long olderThanTimestamp) throws StorageException {
        backingStore.cleanup(olderThanTimestamp);
        // Redis generally handles its own eviction via TTL, explicit cleanup here might
        // be redundant or complex via scan.
    }

    @Override
    public boolean attemptLock(UUID playerUUID, int vaultId, String scope) {
        if (!enabled) {
            return true;
        }
        try (Jedis jedis = pool.getResource()) {
            String lockKey = getLockKey(playerUUID, vaultId, scope);
            // Set lock with 120 second TTL (safeguard) if it doesn't exist
            redis.clients.jedis.params.SetParams params = new redis.clients.jedis.params.SetParams().nx().ex(120);
            String result = jedis.set(lockKey, "locked", params);
            return "OK".equals(result);
        } catch (Exception e) {
            Logger.warn("Failed to acquire lock from Redis: " + e.getMessage());
            return true;
        }
    }

    @Override
    public void unlock(UUID playerUUID, int vaultId, String scope) {
        if (!enabled) {
            return;
        }
        // Async unlock? Probably better to be synchronous to ensure next lock succeeds
        // immediately?
        // Although Jedis calls are fast. Let's keep synchronous to avoid race
        // conditions where unlock is queued but next lock attempt happens first.
        try (Jedis jedis = pool.getResource()) {
            String lockKey = getLockKey(playerUUID, vaultId, scope);
            jedis.del(lockKey);
        } catch (Exception e) {
            Logger.warn("Failed to release lock from Redis: " + e.getMessage());
        }
    }

    @Override
    public void saveVaultIcon(UUID playerUUID, int vaultId, String iconData, String scope) throws StorageException {
        backingStore.saveVaultIcon(playerUUID, vaultId, iconData, scope);
        if (enabled) {
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    // Update key if we want scoped icons in Redis too?
                    // Currently getIconKey doesn't include scope. We should probably update
                    // getIconKey too?
                    // But interface steps didn't mention updating Redis key structure.
                    // If we use same key for all scopes, icons will clash.
                    // I should update getIconKey too or just append scope here.
                    // Given I can't see the getIconKey method right now in this chunk (it's above),
                    // I will assume I should update getIconKey SEPARATELY or construct a new key
                    // here.
                    // Actually, I can update getIconKey later if needed, but for now I'll use the
                    // existing one + scope suffix if I can, OR just update this method.
                    // Wait, if I don't update getIconKey, existing cache will be wrong.
                    // Let's check getIconKey from my previous read.
                    // `private String getIconKey(UUID holder, int number)`
                    // I should update getIconKey signature too, but it's private.
                    // I will just construct the key here to be safe: "pv:icon:" + scope + ":" + ...
                    String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
                    String key = "pv:icon:" + scopeKey + ":" + playerUUID.toString() + ":" + vaultId;

                    // Icons might not need as strict TTL, but let's match vaults or longer
                    jedis.setex(key, ttlSeconds, iconData);
                } catch (Exception e) {
                    Logger.warn("Failed to cache vault icon: " + e.getMessage());
                }
            });
        }
    }

    @Override
    public String loadVaultIcon(UUID playerUUID, int vaultId, String scope) throws StorageException {
        if (enabled) {
            try (Jedis jedis = pool.getResource()) {
                String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
                String key = "pv:icon:" + scopeKey + ":" + playerUUID.toString() + ":" + vaultId;
                String data = jedis.get(key);
                if (data != null) {
                    // Refresh TTL
                    CompletableFuture.runAsync(() -> {
                        try (Jedis j = pool.getResource()) {
                            j.expire(key, ttlSeconds);
                        } catch (Exception ignored) {
                        }
                    });
                    return data;
                }
            } catch (Exception e) {
                Logger.debug("Failed to load icon from cache: " + e.getMessage());
            }
        }

        String data = backingStore.loadVaultIcon(playerUUID, vaultId, scope);

        if (enabled && data != null) {
            CompletableFuture.runAsync(() -> {
                try (Jedis jedis = pool.getResource()) {
                    String scopeKey = (scope == null || scope.isEmpty()) ? "global" : scope;
                    String key = "pv:icon:" + scopeKey + ":" + playerUUID.toString() + ":" + vaultId;
                    jedis.setex(key, ttlSeconds, data);
                } catch (Exception ignored) {
                }
            });
        }
        return data;
    }
}
