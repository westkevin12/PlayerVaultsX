package com.drtshock.playervaults.util;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Utility class to dispatch Adventure components to CommandSenders.
 * Handles Paper's native Adventure support and provides a tiered fallback for
 * Spigot.
 */
public class ComponentDispatcher {
    private static boolean isPaper;
    private static MethodHandle sendMessage;
    private static MethodHandle deserialize;
    private static Object gsonSerializer;

    // Spigot/Bungee Fallback via reflection to avoid deprecation warnings and hard
    // dependencies.
    private static MethodHandle bungeeParse;
    private static MethodHandle spigotSendMessage;

    static {
        MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
        try {
            // Tier 0: Paper Native Adventure
            // We use .replace to obfuscate the class name slightly for older plugins, but
            // full path is fine here.
            Class<?> audienceClass = Class.forName("net.kyori.adventure.Audience");
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            MethodType sendMessageType = MethodType.methodType(void.class, componentClass);
            sendMessage = publicLookup.findVirtual(audienceClass, "sendMessage", sendMessageType);

            Class<?> gsonSerializerClass = Class
                    .forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
            gsonSerializer = publicLookup
                    .findStatic(gsonSerializerClass, "gson", MethodType.methodType(gsonSerializerClass)).invokeExact();
            deserialize = publicLookup.findVirtual(gsonSerializerClass, "deserializeFromTree",
                    MethodType.methodType(componentClass, JsonElement.class));

            isPaper = true;
        } catch (Throwable e) {
            // Tier 1 & 2: Spigot Fallbacks
            try {
                Class<?> bungeeSerializer = Class.forName("net.md_5.bungee.chat.ComponentSerializer");
                Class<?> baseComponentArray = Class.forName("[Lnet.md_5.bungee.api.chat.BaseComponent;");
                bungeeParse = publicLookup.findStatic(bungeeSerializer, "parse",
                        MethodType.methodType(baseComponentArray, String.class));

                Class<?> spigotClass = Class.forName("org.bukkit.command.CommandSender$Spigot");
                spigotSendMessage = publicLookup.findVirtual(spigotClass, "sendMessage",
                        MethodType.methodType(void.class, baseComponentArray));
            } catch (Throwable ignored) {
                // Completely unsupported environment, will fallback to legacy strings only.
            }
        }
    }

    /**
     * Sends a component to a CommandSender.
     *
     * @param commandSender The sender to receive the message.
     * @param componentLike The component to send.
     */
    public static void send(CommandSender commandSender, ComponentLike componentLike) {
        Component component = componentLike.asComponent();

        if (isPaper) {
            try {
                Object comp = deserialize.invokeExact(gsonSerializer,
                        GsonComponentSerializer.gson().serializeToTree(component));
                sendMessage.invoke(commandSender, comp);
                return;
            } catch (Throwable ignored) {
            }
        }

        // Tier 1: Legacy String Fallback (No Click/Hover Events)
        // This is the preferred non-deprecated way for Spigot.
        if (!hasComplexEvents(component)) {
            commandSender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
            return;
        }

        // Tier 2: Reflected Bungee Fallback (For Click/Hover support on Spigot)
        // Only used when "must be used" for complex event support.
        if (bungeeParse != null && spigotSendMessage != null) {
            try {
                String json = GsonComponentSerializer.gson().serialize(component);
                Object components = bungeeParse.invokeExact(json);
                spigotSendMessage.invoke(commandSender.spigot(), components);
                return;
            } catch (Throwable ignored) {
            }
        }

        // Tier 3: absolute fallback to legacy string if Bungee is missing
        commandSender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
    }

    /**
     * Checks if a component or any of its children have click or hover events.
     */
    private static boolean hasComplexEvents(Component component) {
        if (component.clickEvent() != null || component.hoverEvent() != null)
            return true;
        for (Component child : component.children()) {
            if (hasComplexEvents(child))
                return true;
        }
        return false;
    }
}