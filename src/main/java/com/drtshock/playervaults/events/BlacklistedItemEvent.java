/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class BlacklistedItemEvent extends Event implements Cancellable {
    public enum Reason {
        ENCHANTMENT,
        HAS_MODEL_DATA,
        HAS_NO_MODEL_DATA,
        TYPE;
    }

    private static final HandlerList handlers = new HandlerList();
    private boolean isCancelled;
    private final ItemStack item;
    private final String owner;
    private final int vaultNumber;
    private final Player player;
    private final List<Reason> reasons;

    public BlacklistedItemEvent(Player player, ItemStack item, List<Reason> reasons, String owner, int vaultNumber) {
        this.player = player;
        this.item = item;
        this.reasons = reasons;
        this.owner = owner;
        this.vaultNumber = vaultNumber;
    }

    /**
     * Gets the player attempting to use this item.
     *
     * @return player
     */
    public Player getActingPlayer() {
        return this.player;
    }

    /**
     * Gets the item in question.
     *
     * @return item
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Gets the vault's owner. Probably UUID, possibly something else due to legacy plugin integrations.
     *
     * @return owner
     */
    public String getOwner() {
        return this.owner;
    }

    /**
     * Gets an immutable list of reasons the item is blacklisted.
     *
     * @return reasons
     */
    public List<Reason> getReasons() {
        return List.copyOf(this.reasons);
    }

    /**
     * Removes a reason for blacklisting. Removing all reasons will cancel the event.
     *
     * @param reason reason to remove
     */
    public void removeReason(Reason reason) {
        this.reasons.remove(reason);
        if (reasons.isEmpty()) this.isCancelled = true;
    }

    /**
     * Gets the vault number.
     *
     * @return vault number
     */
    public int getVaultNumber() {
        return this.vaultNumber;
    }

    @Override
    public boolean isCancelled() {
        return this.isCancelled || this.reasons.isEmpty();
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.isCancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
