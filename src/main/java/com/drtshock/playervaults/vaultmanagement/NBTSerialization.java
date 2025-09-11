package com.drtshock.playervaults.vaultmanagement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.inventory.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class NBTSerialization {

    private static Class<?> nbtItemClass;
    private static Class<?> nbtContainerClass;
    private static Constructor<?> nbtItemConstructor;
    private static Constructor<?> nbtContainerConstructor;
    private static Method nbtItemToStringMethod;
    private static Method nbtItemConvertMethod;

    static {
        try {
            nbtItemClass = Class.forName("de.tr7zw.nbtapi.NBTItem");
            nbtContainerClass = Class.forName("de.tr7zw.nbtapi.NBTContainer");

            nbtItemConstructor = nbtItemClass.getConstructor(ItemStack.class);
            nbtContainerConstructor = nbtContainerClass.getConstructor(String.class);

            nbtItemToStringMethod = nbtItemClass.getMethod("toString");
            nbtItemConvertMethod = nbtItemClass.getMethod("convertNBTtoItemStack", nbtContainerClass);

        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            // Handle the error, perhaps disable NBT features or log a severe error
        }
    }

    private static final Gson gson = new Gson();
    private static final Type LIST_STRING_TYPE = new TypeToken<List<String>>() {}.getType();

    public static String toStorage(ItemStack[] contents) {
        if (nbtItemClass == null) return null; // NBTAPI not available

        try {
            List<String> nbtStrings = new ArrayList<>();
            for (ItemStack stack : contents) {
                if (stack != null) {
                    Object nbtItem = nbtItemConstructor.newInstance(stack);
                    nbtStrings.add((String) nbtItemToStringMethod.invoke(nbtItem));
                } else {
                    nbtStrings.add(""); // Empty string for null items
                }
            }
            return Base64.getEncoder().encodeToString(gson.toJson(nbtStrings).getBytes());
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack[] fromStorage(String data) {
        if (nbtContainerClass == null) return null; // NBTAPI not available

        try {
            String decodedString = new String(Base64.getDecoder().decode(data));
            List<String> nbtStrings = gson.fromJson(decodedString, LIST_STRING_TYPE);

            ItemStack[] contents = new ItemStack[nbtStrings.size()];
            for (int i = 0; i < nbtStrings.size(); i++) {
                String nbtString = nbtStrings.get(i);
                if (!nbtString.isEmpty()) {
                    Object nbtContainer = nbtContainerConstructor.newInstance(nbtString);
                    contents[i] = (ItemStack) nbtItemConvertMethod.invoke(null, nbtContainer);
                } else {
                    contents[i] = null;
                }
            }
            return contents;
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return null;
        }
    }
}
