package com.drtshock.playervaults.vaultmanagement;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Base64;

public class NBTSerialization {

    private static Class<?> nbtItemClass;
    private static Class<?> nbtContainerClass;
    private static Constructor<?> nbtItemConstructor;
    private static Constructor<?> nbtContainerConstructor;
    private static Method nbtItemToStringMethod;
    private static Method nbtItemConvertMethod;

    static {
        try {
            nbtItemClass = Class.forName("com.drtshock.playervaults.lib.de.tr7zw.nbtapi.NBTItem");
            nbtContainerClass = Class.forName("com.drtshock.playervaults.lib.de.tr7zw.nbtapi.NBTContainer");

            nbtItemConstructor = nbtItemClass.getConstructor(ItemStack.class);
            nbtContainerConstructor = nbtContainerClass.getConstructor(String.class);

            nbtItemToStringMethod = nbtItemClass.getMethod("toString");
            nbtItemConvertMethod = nbtItemClass.getMethod("convertNBTtoItemStack", nbtContainerClass);

        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            // Handle the error, perhaps disable NBT features or log a severe error
        }
    }

    public static String toStorage(ItemStack[] contents) {
        if (nbtItemClass == null) return null; // NBTAPI not available

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(contents.length);

            for (ItemStack stack : contents) {
                if (stack != null) {
                    Object nbtItem = nbtItemConstructor.newInstance(stack);
                    dataOutput.writeUTF((String) nbtItemToStringMethod.invoke(nbtItem));
                } else {
                    dataOutput.writeUTF(""); // Empty string for null items
                }
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException | ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack[] fromStorage(String data) {
        if (nbtContainerClass == null) return null; // NBTAPI not available

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            ItemStack[] contents = new ItemStack[dataInput.readInt()];

            for (int i = 0; i < contents.length; i++) {
                String nbtString = dataInput.readUTF();
                if (!nbtString.isEmpty()) {
                    Object nbtContainer = nbtContainerConstructor.newInstance(nbtString);
                    contents[i] = (ItemStack) nbtItemConvertMethod.invoke(null, nbtContainer);
                }
            }
            return contents;
        } catch (IOException | ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }
}