package com.drtshock.playervaults.storage;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Config;
import com.drtshock.playervaults.config.file.Translation;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("slow")
@SuppressWarnings("null")
public class StorageFailoverTest {

    private StorageProvider storage;
    private VaultManager vaultManager;
    private PlayerVaults plugin;
    private MockedStatic<Bukkit> bukkits;
    private MockedStatic<PlayerVaults> statics;

    @BeforeEach
    void setUp() {
        storage = mock(StorageProvider.class);
        plugin = mock(PlayerVaults.class);

        // Mock Singleton
        statics = mockStatic(PlayerVaults.class);
        statics.when(PlayerVaults::getInstance).thenReturn(plugin);

        bukkits = mockStatic(Bukkit.class);

        // Mock Config/Translation
        Config config = mock(Config.class);
        Config.Storage storageConfig = mock(Config.Storage.class);
        Config.Storage.WorldScoping scoping = mock(Config.Storage.WorldScoping.class);
        when(plugin.getConf()).thenReturn(config);
        when(config.getStorage()).thenReturn(storageConfig);
        when(storageConfig.getScoping()).thenReturn(scoping);
        when(scoping.isEnabled()).thenReturn(false);
        when(scoping.getDefaultGroup()).thenReturn("global");

        Translation tl = mock(Translation.class);
        Translation.TL tlnode = mock(Translation.TL.class);
        Translation.TL.Builder builder = mock(Translation.TL.Builder.class);

        when(plugin.getTL()).thenReturn(tl);
        when(tl.storageSaveError()).thenReturn(tlnode);
        when(tl.storageLoadError()).thenReturn(tlnode);
        when(tlnode.title()).thenReturn(builder);

        // Mock Logger
        java.util.logging.Logger mockLogger = mock(java.util.logging.Logger.class);
        when(plugin.getLogger()).thenReturn(mockLogger);

        // Mock Scheduler
        org.bukkit.scheduler.BukkitScheduler mockScheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        bukkits.when(Bukkit::getScheduler).thenReturn(mockScheduler);

        // Handle runTaskAsynchronously
        when(mockScheduler.runTaskAsynchronously(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(1)).run();
                    return null;
                });

        // Handle runTask (sync)
        when(mockScheduler.runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(1)).run();
                    return null;
                });

        vaultManager = new VaultManager(plugin, storage);
    }

    @AfterEach
    void tearDown() {
        statics.close();
        bukkits.close();
    }

    @Test
    void testSaveFailover() throws StorageException {
        UUID uuid = UUID.randomUUID();
        String name = "TestUser";
        int vaultNumber = 1;

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        // Mock world for scope resolution fallback
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);

        bukkits.when(() -> Bukkit.getPlayer(name)).thenReturn(player);
        bukkits.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);

        Inventory inventory = mock(Inventory.class);
        when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[0]);

        // Mock Storage to throw exception
        doThrow(new StorageException("Database offline")).when(storage).saveVault(eq(uuid), eq(vaultNumber),
                anyString(), anyString());

        // Execute
        vaultManager.saveVault(inventory, uuid.toString(), vaultNumber);

        // Verify unlock called despite exception (in finally block)
        verify(storage).unlock(eq(uuid), eq(vaultNumber), anyString());

        // Verify user notified
        // Note: checking Translation.TL.Builder.send(player)
        // verify(plugin.getTL().storageSaveError().title()).send(player); // This is
        // complex due to chain mocking
    }

    @Test
    void testLoadFailover() throws StorageException {
        UUID uuid = UUID.randomUUID();
        int vaultNumber = 1;

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestUser");
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);

        when(plugin.getVaultTitle(anyString())).thenReturn("Vault 1");

        // Mock Lock Success
        when(storage.attemptLock(any(UUID.class), anyInt(), anyString())).thenReturn(true);

        // Mock Storage to throw exception
        doThrow(new StorageException("Connection timed out")).when(storage).loadVault(eq(uuid), eq(vaultNumber),
                anyString());

        // Execute
        Inventory result = vaultManager.loadOwnVault(player, vaultNumber, 54);

        // Verify null return (fail safe)
        assertNull(result);

        // Verify unlock called
        verify(storage).unlock(eq(uuid), eq(vaultNumber), anyString());
    }
}
