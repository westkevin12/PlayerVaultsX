package com.drtshock.playervaults.concurrency;

import com.drtshock.playervaults.storage.DefaultFileOperations;
import com.drtshock.playervaults.storage.FileStorageProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.logging.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class LoadTest {

    @TempDir
    Path tempDir;

    private FileStorageProvider storage;
    private File directory;

    @BeforeEach
    void setUp() {
        directory = tempDir.toFile();
        // Use constructor injection
        storage = new FileStorageProvider(directory, new DefaultFileOperations(), mock(Logger.class));
        storage.initialize();
    }

    @AfterEach
    void tearDown() {
        storage.shutdown();
    }

    @Test
    void testHighConcurrencyLoad() throws InterruptedException {
        int threads = 50;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            tasks.add(() -> {
                UUID uuid = UUID.randomUUID(); // Unique vault per thread
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String data = "thread-" + threadId + "-op-" + j;
                        int vaultNumber = 1;

                        // Save
                        storage.saveVault(uuid, vaultNumber, data, "global");

                        // Load and verify
                        String loaded = storage.loadVault(uuid, vaultNumber, "global");

                        // Basic consistency check (though not strictly race condition if unique UUID)
                        if (loaded == null || !loaded.equals(data)) {
                            System.err.println("Data mismatch for " + uuid + ": " + loaded);
                            failures.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    failures.incrementAndGet();
                }
                return null;
            });
        }

        executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals(0, failures.get(), "Load test caused failures or data mismatches");
    }
}
