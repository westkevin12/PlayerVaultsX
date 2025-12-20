package com.drtshock.playervaults.concurrency;

import com.drtshock.playervaults.storage.DefaultFileOperations;
import com.drtshock.playervaults.storage.FileStorageProvider;
import com.drtshock.playervaults.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.logging.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class RaceConditionTest {

    @TempDir
    Path tempDir;

    private FileStorageProvider storage;
    private File directory;

    @BeforeEach
    void setUp() {
        directory = tempDir.toFile();
        // Use constructor injection to provide a mock logger and real file operations
        storage = new FileStorageProvider(directory, new DefaultFileOperations(), mock(Logger.class));
        storage.initialize();
    }

    @AfterEach
    void tearDown() {
        storage.shutdown();
    }

    @Test
    void testConcurrentSaves() throws InterruptedException, StorageException {
        UUID uuid = UUID.randomUUID();
        int vaultNumber = 1;
        int threads = 10;
        int savesPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger(0);

        // Pre-create file
        storage.saveVault(uuid, vaultNumber, "initial", "global");

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < savesPerThread; j++) {
                        String data = "thread-" + threadId + "-save-" + j;
                        // Race condition likely here
                        try {
                            storage.saveVault(uuid, vaultNumber, data, "global");
                        } catch (StorageException e) {
                            // This might happen if file is locked by another process/handle?
                            // On Linux, file locking isn't mandatory, so writes might interleave or rename
                            // fails.
                            e.printStackTrace();
                            failures.incrementAndGet();
                        } catch (Exception e) {
                            e.printStackTrace();
                            failures.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        // If 'failures' > 0, it means we caught exceptions (maybe file locked).
        // Real race condition might be silent data loss or corruption.
        // For this test, we just want to see if it crashes or throws exceptions.

        System.out.println("Failures: " + failures.get());

        // Load final state
        String finalData = storage.loadVault(uuid, vaultNumber, "global");
        System.out.println("Final data: " + finalData);

        // Ideally we want 0 failures.
        // If FileStorageProvider is not synchronized, renames might fail or content
        // might be mixed.
        assertEquals(0, failures.get(), "Concurrent saves caused exceptions");
    }
}
