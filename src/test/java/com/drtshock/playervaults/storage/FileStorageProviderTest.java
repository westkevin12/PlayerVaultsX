package com.drtshock.playervaults.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;
import java.util.logging.Logger;
import org.mockito.ArgumentMatcher;

public class FileStorageProviderTest {

    private File testDirectory;
    private FileStorageProvider fileStorageProvider;
    private FileOperations mockFileOperations;
    private java.util.Map<String, String> fileContentMap;
    private MockedStatic<com.drtshock.playervaults.PlayerVaults> mockedPlayerVaults;

    // Custom ArgumentMatcher for File objects to compare by absolute path
    private record FilePathMatcher(String expectedPath) implements ArgumentMatcher<File> {

        public FilePathMatcher(File expectedFile) {
            this(expectedFile.getAbsolutePath());
        }

        @Override
        public boolean matches(File argument) {
            return argument != null && argument.getAbsolutePath().equals(expectedPath);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory for testing
        testDirectory = Files.createTempDirectory("playervaults_test").toFile();
        fileContentMap = new java.util.HashMap<>();

        mockFileOperations = mock(FileOperations.class);
        // Default behavior for mockFileOperations.exists()
        when(mockFileOperations.exists(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            return fileContentMap.containsKey(file.getAbsolutePath()) || file.equals(testDirectory);
        });
        // Default behavior for mockFileOperations.load()
        when(mockFileOperations.load(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            YamlConfiguration config = new YamlConfiguration();
            if (fileContentMap.containsKey(file.getAbsolutePath())) {
                // Load the string content into a new YamlConfiguration
                config.loadFromString(fileContentMap.get(file.getAbsolutePath()));
            }
            return config;
        });
        // Default behavior for mockFileOperations.save()
        doAnswer(invocation -> {
            YamlConfiguration yaml = invocation.getArgument(0);
            File file = invocation.getArgument(1);
            fileContentMap.put(file.getAbsolutePath(), yaml.saveToString());
            return null;
        }).when(mockFileOperations).save(any(YamlConfiguration.class), any(File.class));

        when(mockFileOperations.delete(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            return fileContentMap.remove(file.getAbsolutePath()) != null;
        });
        // Generic renameTo mock for successful operations in other tests
        when(mockFileOperations.renameTo(any(File.class), any(File.class))).thenAnswer(invocation -> {
            File source = invocation.getArgument(0);
            File destination = invocation.getArgument(1);
            String content = fileContentMap.remove(source.getAbsolutePath());
            if (content != null) {
                fileContentMap.put(destination.getAbsolutePath(), content);
                return true;
            }
            return false;
        });
        // Default behavior for mockFileOperations.listFiles()
        when(mockFileOperations.listFiles(any(File.class), any(java.io.FilenameFilter.class))).thenAnswer(invocation -> {
            File directory = invocation.getArgument(0);
            java.io.FilenameFilter filter = invocation.getArgument(1);
            return fileContentMap.keySet().stream()
                    .map(File::new) // Convert path string back to File object
                    .filter(file -> file.getParentFile() != null && file.getParentFile().equals(directory) && filter.accept(file.getParentFile(), file.getName()))
                    .toArray(File[]::new);
        });

        // Mock PlayerVaults.getInstance() and its logger
        mockedPlayerVaults = mockStatic(com.drtshock.playervaults.PlayerVaults.class);
        com.drtshock.playervaults.PlayerVaults mockPlayerVaultsInstance = mock(com.drtshock.playervaults.PlayerVaults.class);
        Logger mockCustomLogger = mock(Logger.class);

        mockedPlayerVaults.when(com.drtshock.playervaults.PlayerVaults::getInstance).thenReturn(mockPlayerVaultsInstance);
        when(mockPlayerVaultsInstance.getLogger()).thenReturn(mockCustomLogger);

        fileStorageProvider = new FileStorageProvider(testDirectory, mockFileOperations);
        fileStorageProvider.initialize();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up the temporary directory after each test
        try (var walk = Files.walk(testDirectory.toPath())) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete()) {
                            System.err.println("Failed to delete file: " + file.getAbsolutePath());
                        }
                    });
        }
        fileContentMap.clear();
        mockedPlayerVaults.close(); // Close the mocked static
    }

    @Test
    void testSaveAndLoadVault() {
        UUID playerUUID = UUID.randomUUID();
        int vaultId = 1;
        String inventoryData = "test_inventory_data";

        fileStorageProvider.saveVault(playerUUID, vaultId, inventoryData);

        String loadedData = fileStorageProvider.loadVault(playerUUID, vaultId);
        assertEquals(inventoryData, loadedData);
    }

    @Test
    void testLoadNonExistentVault() {
        UUID playerUUID = UUID.randomUUID();
        int vaultId = 1;

        String loadedData = fileStorageProvider.loadVault(playerUUID, vaultId);
        assertNull(loadedData);
    }

    @Test
    void testDeleteVault() {
        UUID playerUUID = UUID.randomUUID();
        int vaultId = 1;
        String inventoryData = "test_inventory_data";

        fileStorageProvider.saveVault(playerUUID, vaultId, inventoryData);
        String loadedData = fileStorageProvider.loadVault(playerUUID, vaultId);
        assertNotNull(loadedData);

        fileStorageProvider.deleteVault(playerUUID, vaultId);
        loadedData = fileStorageProvider.loadVault(playerUUID, vaultId);
        assertNull(loadedData);
    }

    @Test
    void testVaultExists() {
        UUID playerUUID = UUID.randomUUID();
        int vaultId = 1;
        String inventoryData = "test_inventory_data";

        assertFalse(fileStorageProvider.vaultExists(playerUUID, vaultId));

        fileStorageProvider.saveVault(playerUUID, vaultId, inventoryData);
        assertTrue(fileStorageProvider.vaultExists(playerUUID, vaultId));

        fileStorageProvider.deleteVault(playerUUID, vaultId);
        assertFalse(fileStorageProvider.vaultExists(playerUUID, vaultId)); // Corrected assertion
    }

    @Test
    void testGetVaultNumbers() {
        UUID playerUUID = UUID.randomUUID();
        fileStorageProvider.saveVault(playerUUID, 1, "data1");
        fileStorageProvider.saveVault(playerUUID, 2, "data2");
        fileStorageProvider.saveVault(playerUUID, 3, "data3");

        Set<Integer> vaultNumbers = fileStorageProvider.getVaultNumbers(playerUUID);
        assertEquals(3, vaultNumbers.size());
        assertTrue(vaultNumbers.contains(1));
        assertTrue(vaultNumbers.contains(2));
        assertTrue(vaultNumbers.contains(3));
    }

    @Test
    void testDeleteAllVaults() {
        UUID playerUUID = UUID.randomUUID();
        fileStorageProvider.saveVault(playerUUID, 1, "data1");
        fileStorageProvider.saveVault(playerUUID, 2, "data2");

        assertTrue(fileStorageProvider.vaultExists(playerUUID, 1));
        assertTrue(fileStorageProvider.vaultExists(playerUUID, 2));

        fileStorageProvider.deleteAllVaults(playerUUID);

        assertFalse(mockFileOperations.exists(new File(testDirectory, playerUUID + ".yml")));
    }

    @Test
    void testGetAllPlayerUUIDs() {
        UUID player1UUID = UUID.randomUUID();
        UUID player2UUID = UUID.randomUUID();

        fileStorageProvider.saveVault(player1UUID, 1, "data1");
        fileStorageProvider.saveVault(player2UUID, 1, "data2");

        Set<UUID> allUUIDs = fileStorageProvider.getAllPlayerUUIDs();
        assertEquals(2, allUUIDs.size());
        assertTrue(allUUIDs.contains(player1UUID));
        assertTrue(allUUIDs.contains(player2UUID));
    }

    @Test
    void testSafeSaveIOExceptionHandling() throws IOException {
        UUID playerUUID = UUID.randomUUID();
        int vaultId = 1;
        String initialInventoryData = "initial_data";
        String newInventoryData = "new_data";

        File playerFile = new File(testDirectory, playerUUID + ".yml");
        File tempFile = new File(testDirectory, playerUUID + ".yml.tmp");
        File backupFile = new File(testDirectory, playerUUID + ".yml.bak");

        // Create a YamlConfiguration that represents the initial state of the player file
        YamlConfiguration initialConfigForMap = new YamlConfiguration();
        initialConfigForMap.set("vault" + vaultId, initialInventoryData);
        fileContentMap.put(playerFile.getAbsolutePath(), initialConfigForMap.saveToString()); // Populate the map directly with string

        // ArgumentMatchers for File objects
        FilePathMatcher playerFileMatcher = new FilePathMatcher(playerFile);
        FilePathMatcher backupFileMatcher = new FilePathMatcher(backupFile);
        FilePathMatcher tempFileMatcher = new FilePathMatcher(tempFile);

        // When load is called for the playerFile, return a new YamlConfiguration populated with initialConfigForMap's data
        when(mockFileOperations.load(argThat(playerFileMatcher))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            String configToLoadString = fileContentMap.get(file.getAbsolutePath());
            YamlConfiguration newConfig = new YamlConfiguration();
            if (configToLoadString != null) {
                newConfig.loadFromString(configToLoadString);
            }
            return newConfig;
        });

        // Mock the first rename: playerFile to backupFile
        doAnswer(invocation -> {
            File source = invocation.getArgument(0);
            File destination = invocation.getArgument(1);
            String content = fileContentMap.remove(source.getAbsolutePath());
            if (content != null) {
                fileContentMap.put(destination.getAbsolutePath(), content);
                return true;
            }
            return false;
        }).when(mockFileOperations).renameTo(argThat(playerFileMatcher), argThat(backupFileMatcher));

        // Mock the second rename: tempFile to playerFile (this is the one that fails)
        doThrow(new IOException("Simulated rename failure")).when(mockFileOperations).renameTo(argThat(tempFileMatcher), argThat(playerFileMatcher));

        // Explicitly mock the restore operation from backup to playerFile
        doAnswer(invocation -> {
            File source = invocation.getArgument(0);
            File destination = invocation.getArgument(1);
            String contentToRestore = fileContentMap.remove(source.getAbsolutePath());
            if (contentToRestore != null) {
                fileContentMap.put(destination.getAbsolutePath(), contentToRestore); // Add playerFile back
                return true;
            }
            return false;
        }).when(mockFileOperations).renameTo(argThat(backupFileMatcher), argThat(playerFileMatcher));

        // Mock save operation for YamlConfiguration
        doAnswer(invocation -> {
            YamlConfiguration yaml = invocation.getArgument(0);
            File file = invocation.getArgument(1);
            fileContentMap.put(file.getAbsolutePath(), yaml.saveToString());
            return null;
        }).when(mockFileOperations).save(any(org.bukkit.configuration.file.YamlConfiguration.class), argThat(tempFileMatcher));


        // Mock delete operation for other files (e.g., tempFile, backupFile after successful save)
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            return fileContentMap.remove(file.getAbsolutePath()) != null;
        }).when(mockFileOperations).delete(any(File.class));

        // Expect a StorageException due to the simulated IOException
        assertThrows(StorageException.class, () -> fileStorageProvider.saveVault(playerUUID, vaultId, newInventoryData));

        // Verify that the original file was restored from backup
        // We check the fileContentMap directly now, as the actual file system is not being used for this test
        assertTrue(fileContentMap.containsKey(playerFile.getAbsolutePath()));
        YamlConfiguration restoredConfig = new YamlConfiguration();
        try {
            restoredConfig.loadFromString(fileContentMap.get(playerFile.getAbsolutePath()));
        } catch (InvalidConfigurationException e) {
            fail("InvalidConfigurationException during restore: " + e.getMessage());
        }
        assertEquals(initialInventoryData, restoredConfig.getString("vault" + vaultId));

        // Verify interactions with mock
        verify(mockFileOperations, times(1)).renameTo(argThat(playerFileMatcher), argThat(backupFileMatcher));
        verify(mockFileOperations, times(1)).renameTo(argThat(tempFileMatcher), argThat(playerFileMatcher));
        verify(mockFileOperations, times(1)).renameTo(argThat(backupFileMatcher), argThat(playerFileMatcher)); // Should be called to restore
        verify(mockFileOperations, never()).delete(argThat(backupFileMatcher)); // Backup should not be deleted if restore failed
        verify(mockFileOperations, times(1)).delete(argThat(tempFileMatcher)); // Temp file should always be cleaned up
    }

    @Test
    void testSaveVaultsBulk() {
        java.util.Map<UUID, java.util.Map<Integer, String>> vaultsToSave = createVaultsToSaveMap();

        fileStorageProvider.saveVaults(vaultsToSave);

        // Extract UUIDs from the map to use in assertions
        UUID player1UUID = null;
        UUID player2UUID = null;
        for (java.util.Map.Entry<UUID, java.util.Map<Integer, String>> entry : vaultsToSave.entrySet()) {
            if (entry.getValue().containsKey(1) && entry.getValue().get(1).equals("player1_vault1_data")) {
                player1UUID = entry.getKey();
            } else if (entry.getValue().containsKey(1) && entry.getValue().get(1).equals("player2_vault1_data")) {
                player2UUID = entry.getKey();
            }
        }

        assertNotNull(player1UUID, "Player 1 UUID should not be null");
        assertNotNull(player2UUID, "Player 2 UUID should not be null");

        assertEquals("player1_vault1_data", fileStorageProvider.loadVault(player1UUID, 1));
        assertEquals("player1_vault2_data", fileStorageProvider.loadVault(player1UUID, 2));
        assertEquals("player2_vault1_data", fileStorageProvider.loadVault(player2UUID, 1));
    }

    private java.util.Map<UUID, java.util.Map<Integer, String>> createVaultsToSaveMap() {
        UUID player1UUID = UUID.randomUUID();
        UUID player2UUID = UUID.randomUUID();

        java.util.Map<UUID, java.util.Map<Integer, String>> vaultsToSave = new java.util.HashMap<>();

        java.util.Map<Integer, String> player1Vaults = new java.util.HashMap<>();
        player1Vaults.put(1, "player1_vault1_data");
        player1Vaults.put(2, "player1_vault2_data");
        vaultsToSave.put(player1UUID, player1Vaults);

        java.util.Map<Integer, String> player2Vaults = new java.util.HashMap<>();
        player2Vaults.put(1, "player2_vault1_data");
        vaultsToSave.put(player2UUID, player2Vaults);
        return vaultsToSave;
    }
}