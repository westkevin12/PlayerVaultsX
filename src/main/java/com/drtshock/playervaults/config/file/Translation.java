package com.drtshock.playervaults.config.file;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.annotation.Comment;
import com.drtshock.playervaults.util.ComponentDispatcher;
import com.google.common.collect.ImmutableMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
public class Translation {
    public static class TL extends ArrayList<String> {
        private static transient PlayerVaults plugin;

        private static @NonNull TL of(@NonNull String... strings) {
            TL list = new TL();
            Collections.addAll(list, strings);
            return list;
        }

        public static @NonNull TL copyOf(@NonNull Collection<String> collection) {
            TL list = new TL();
            list.addAll(collection);
            return list;
        }

        public class Builder {
            private transient ImmutableMap.Builder<String, String> map;
            private transient TL title;

            private Builder(@NonNull TL title) {
                this.title = title;
            }

            private Builder(@NonNull String key, @Nullable String value) {
                this.with(key, value);
            }

            public @NonNull Builder with(@NonNull String key, @Nullable String value) {
                if (this.map == null) {
                    this.map = ImmutableMap.builder();
                }
                this.map.put(key, value);
                return this;
            }

            public void send(@NonNull CommandSender sender) {
                TL.this.send(sender, this.map == null ? Collections.emptyMap() : this.map.build(), this.title);
            }

            public @NonNull String getLegacy() {
                return TL.this.getLegacy(this.map == null ? Collections.emptyMap() : this.map.build(), this.title);
            }
        }

        public @NonNull Builder with(@NonNull String key, @Nullable String value) {
            return new Builder(key, value);
        }

        public @NonNull Builder title() {
            return new Builder(TL.plugin.getTL().title());
        }

        public @NonNull Builder title(@NonNull TL title) {
            return new Builder(title);
        }

        public void send(@NonNull CommandSender sender) {
            this.send(sender, Collections.emptyMap(), null);
        }

        private void send(@NonNull CommandSender sender, @NonNull Map<String, String> map, @Nullable TL title) {
            this.forEach(line -> {
                if (line == null || line.isEmpty()) {
                    return;
                }
                ComponentDispatcher.send(sender, this.getComponent(line, map, title));
            });
        }

        private @NonNull Component getComponent(@NonNull String line, @NonNull Map<String, String> map, @Nullable TL title) {
            if (title != null && !title.isEmpty()) {
                line = title.get(0) + line;
            }
            TagResolver.Builder tagResolverBuilder = TagResolver.builder();
            TL.plugin.getTL().colorMappings().forEach((k, v) -> {
                TextColor color = v.startsWith("#") ? TextColor.fromHexString(v) : NamedTextColor.NAMES.value(v);
                tagResolverBuilder.tag(k, Tag.styling(color == null ? NamedTextColor.WHITE : color));
            });
            map.forEach((k, v) -> tagResolverBuilder.resolver(Placeholder.unparsed(k, v)));
            return MiniMessage.miniMessage().deserialize(line, tagResolverBuilder.build());
        }

        public @NonNull String getLegacy() {
            return this.getLegacy(Collections.emptyMap(), null);
        }

        public @NonNull String getLegacy(@NonNull Map<String, String> map) {
            return this.getLegacy(map, null);
        }

        public @NonNull String getLegacy(@NonNull Map<String, String> map, @Nullable TL title) {
            return this.stream()
                    .map(line -> this.getComponent(line, map, title))
                    .filter(Objects::nonNull)
                    .map(component -> LegacyComponentSerializer.legacySection().serialize(component))
                    .collect(Collectors.joining("\n"));
        }

        public boolean arrContains(@Nullable String[] array, @NonNull String target) {
            if (array == null) {
                return false;
            }
            for (String string : array) {
                if (target.equals(string)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void cleanupMiniMessup() {
        this.cleanupMiniMessup(this.translations.openVault);
        this.cleanupMiniMessup(this.translations.openOtherVault);
        this.cleanupMiniMessup(this.translations.invalidArgs);
        this.cleanupMiniMessup(this.translations.deleteVault);
        this.cleanupMiniMessup(this.translations.deleteOtherVault);
        this.cleanupMiniMessup(this.translations.deleteOtherVaultAll);
        this.cleanupMiniMessup(this.translations.playerOnly);
        this.cleanupMiniMessup(this.translations.mustBeNumber);
        this.cleanupMiniMessup(this.translations.noPerms);
        this.cleanupMiniMessup(this.translations.insufficientFunds);
        this.cleanupMiniMessup(this.translations.refundAmount);
        this.cleanupMiniMessup(this.translations.costToCreate);
        this.cleanupMiniMessup(this.translations.costToOpen);
        this.cleanupMiniMessup(this.translations.vaultDoesNotExist);
        this.cleanupMiniMessup(this.translations.clickASign);
        this.cleanupMiniMessup(this.translations.notASign);
        this.cleanupMiniMessup(this.translations.setSign);
        this.cleanupMiniMessup(this.translations.existingVaults);
        this.cleanupMiniMessup(this.translations.vaultTitle);
        this.cleanupMiniMessup(this.translations.openWithSign);
        this.cleanupMiniMessup(this.translations.noOwnerFound);
        this.cleanupMiniMessup(this.translations.convertPluginNotFound);
        this.cleanupMiniMessup(this.translations.convertComplete);
        this.cleanupMiniMessup(this.translations.convertBackground);
        this.cleanupMiniMessup(this.translations.locked);
        this.cleanupMiniMessup(this.translations.help);
        this.cleanupMiniMessup(this.translations.blockedItem);
        this.cleanupMiniMessup(this.translations.blockedItemWithModelData);
        this.cleanupMiniMessup(this.translations.blockedItemWithoutModelData);
        this.cleanupMiniMessup(this.translations.blockedItemWithEnchantments);
        this.cleanupMiniMessup(this.translations.signsDisabled);
        this.cleanupMiniMessup(this.translations.deleteHelp);
        this.cleanupMiniMessup(this.translations.storageSaveError);
        this.cleanupMiniMessup(this.translations.storageLoadError);
        this.cleanupMiniMessup(this.placeholders.title);
        for (Map.Entry<String, String> entry : this.colorMappings.entrySet()) {
            if (entry.getValue().contains("§")) {
                this.cleanupMiniMessupAlert(entry.getValue());
                entry.setValue(entry.getValue().replace('§', '&'));
            }
        }
    }

    private void cleanupMiniMessup(List<String> tl) {
        for (int i = 0; i < tl.size(); i++) {
            String line = tl.get(i);
            if (line.contains("§")) {
                this.cleanupMiniMessupAlert(line);
                tl.set(i, line.replace('§', '&'));
            }
        }
    }

    private void cleanupMiniMessupAlert(String line) {
        PlayerVaults.getInstance().getLogger().severe("Found section sign at lang.conf '" + line + "' - replacing with & in-game so you can notice and go fix it.");
    }

    private static class Placeholders {
        private TL title = TL.of("<dark_red>[<normal>PlayerVaults<dark_red>]: ");
    }

    private static class Translations {
        // Be sure to add anything new to the cleanupMiniMessup list.
        private TL openVault = TL.of("<normal>Opening vault <info><vault></info>");
        private TL openOtherVault = TL.of("<normal>Opening vault <info><vault></info> of <info><player></info>");
        private TL invalidArgs = TL.of("<error>Invalid args!");
        private TL deleteVault = TL.of("<normal>Deleted vault <info><vault></info>");
        private TL deleteOtherVault = TL.of("<normal>Deleted vault <info><vault></info> <normal>of <info><player></info>");
        private TL deleteOtherVaultAll = TL.of("<dark_red>Deleted all vaults belonging to <info><player></info>");
        private TL playerOnly = TL.of("<error>Sorry but that can only be run by a player!");
        private TL mustBeNumber = TL.of("<error>You need to specify a valid number.");
        private TL noPerms = TL.of("<error>You don't have permission for that!");
        private TL insufficientFunds = TL.of("<error>You don't have enough money for that!");
        private TL refundAmount = TL.of("<normal>You were refunded <info><price></info> for deleting that vault.");
        private TL costToCreate = TL.of("<normal>You were charged <info><price></info> for creating a vault.");
        private TL costToOpen = TL.of("<normal>You were charged <info><price></info> for opening that vault.");
        private TL vaultDoesNotExist = TL.of("<error>That vault does not exist!");
        private TL clickASign = TL.of("<normal>Now click a sign!");
        private TL notASign = TL.of("<error>You must click a sign!");
        private TL setSign = TL.of("<normal>You have successfully set a PlayerVault access sign!");
        private TL existingVaults = TL.of("<normal><player> has vaults: <info><vault></info>");
        private TL vaultTitle = TL.of("<dark_red>Vault #<vault>");
        private TL openWithSign = TL.of("<normal>Opening vault <info><vault></info> of <info><player></info>");
        private TL noOwnerFound = TL.of("<error>Cannot find vault owner: <info><player></info>");
        private TL convertPluginNotFound = TL.of("<error>No converter found for that plugin.");
        private TL convertComplete = TL.of("<normal>Converted <info><count></info> players to PlayerVaults.");
        private TL convertBackground = TL.of("<normal>Conversion has been forked to the background. See console for updates.");
        private TL locked = TL.of("<error>Vaults are currently locked while conversion occurs. Please try again in a moment!");
        private TL help = TL.of("/pv <number>");
        private TL blockedItem = TL.of("<gold><item></gold> <error>is blocked from vaults.");
        private TL blockedItemWithModelData = TL.of("<error>This item is blocked from vaults.");
        private TL blockedItemWithoutModelData = TL.of("<error>This item is blocked from vaults.");
        private TL blockedItemWithEnchantments = TL.of("<error>This item's enchantments are blocked from vaults.");
        private TL signsDisabled = TL.of("<error>Vault signs are currently disabled.");
        private TL storageSaveError = TL.of("<error>Failed to save your vault. Please contact an administrator.");
        private TL storageLoadError = TL.of("<error>Failed to load your vault. Please contact an administrator.");
        private TL deleteHelp = TL.of(
                "<normal>/pvdel <number>",
                "<normal>/pvdel <player> <number>",
                "<normal>/pvdel <player> all"
        );
    }

    private Placeholders placeholders = new Placeholders();
    private Translations translations = new Translations();

    public Translation(@NonNull PlayerVaults plugin) {
        TL.plugin = plugin;
    }

    @Comment("https://docs.adventure.kyori.net/minimessage.html#format")
    private Map<String, String> colorMappings = new HashMap<>() {
        {
            this.put("error", "red");
            this.put("normal", "white");
            this.put("info", "green");
        }
    };

    public @NonNull TL title() {
        return this.placeholders.title;
    }

    public @NonNull TL openVault() {
        return this.translations.openVault;
    }

    public @NonNull TL openOtherVault() {
        return this.translations.openOtherVault;
    }

    public @NonNull TL invalidArgs() {
        return this.translations.invalidArgs;
    }

    public @NonNull TL deleteVault() {
        return this.translations.deleteVault;
    }

    public @NonNull TL deleteOtherVault() {
        return this.translations.deleteOtherVault;
    }

    public @NonNull TL deleteOtherVaultAll() {
        return this.translations.deleteOtherVaultAll;
    }

    public @NonNull TL playerOnly() {
        return this.translations.playerOnly;
    }

    public @NonNull TL mustBeNumber() {
        return this.translations.mustBeNumber;
    }

    public @NonNull TL noPerms() {
        return this.translations.noPerms;
    }

    public @NonNull TL insufficientFunds() {
        return this.translations.insufficientFunds;
    }

    public @NonNull TL refundAmount() {
        return this.translations.refundAmount;
    }

    public @NonNull TL costToCreate() {
        return this.translations.costToCreate;
    }

    public @NonNull TL costToOpen() {
        return this.translations.costToOpen;
    }

    public @NonNull TL vaultDoesNotExist() {
        return this.translations.vaultDoesNotExist;
    }

    public @NonNull TL clickASign() {
        return this.translations.clickASign;
    }

    public @NonNull TL notASign() {
        return this.translations.notASign;
    }

    public @NonNull TL setSign() {
        return this.translations.setSign;
    }

    public @NonNull TL existingVaults() {
        return this.translations.existingVaults;
    }

    public @NonNull TL vaultTitle() {
        return this.translations.vaultTitle;
    }

    public @NonNull TL openWithSign() {
        return this.translations.openWithSign;
    }

    public @NonNull TL noOwnerFound() {
        return this.translations.noOwnerFound;
    }

    public @NonNull TL convertPluginNotFound() {
        return this.translations.convertPluginNotFound;
    }

    public @NonNull TL convertComplete() {
        return this.translations.convertComplete;
    }

    public @NonNull TL convertBackground() {
        return this.translations.convertBackground;
    }

    public @NonNull TL locked() {
        return this.translations.locked;
    }

    public @NonNull TL help() {
        return this.translations.help;
    }

    public @NonNull TL blockedItem() {
        return this.translations.blockedItem;
    }

    public @NonNull TL blockedItemWithModelData() {
        return this.translations.blockedItemWithModelData;
    }

    public @NonNull TL blockedItemWithoutModelData() {
        return this.translations.blockedItemWithoutModelData;
    }

    public @NonNull TL blockedItemWithEnchantments() {return this.translations.blockedItemWithEnchantments;}

    public @NonNull TL signsDisabled() {
        return this.translations.signsDisabled;
    }

    public @NonNull TL storageSaveError() {
        return this.translations.storageSaveError;
    }

    public @NonNull TL storageLoadError() {
        return this.translations.storageLoadError;
    }

    public @NonNull TL deleteHelp() {
        return this.translations.deleteHelp;
    }

    public @NonNull Map<String, String> colorMappings() {
        return Collections.unmodifiableMap(this.colorMappings);
    }
}
