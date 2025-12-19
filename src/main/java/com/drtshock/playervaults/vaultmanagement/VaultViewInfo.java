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

package com.drtshock.playervaults.vaultmanagement;

/**
 * A class that stores information about a vault viewing including the holder of
 * the vault, and the vault number.
 */
public class VaultViewInfo {

    final String vaultName;
    final int number;
    final boolean readOnly;

    /**
     * Makes a VaultViewInfo object. Used for opening a vault owned by the opener.
     *
     * @param vaultName vault owner/name.
     * @param i         vault number.
     */
    public VaultViewInfo(String vaultName, int i) {
        this(vaultName, i, false);
    }

    /**
     * Makes a VaultViewInfo object with read-only option.
     *
     * @param vaultName vault owner/name.
     * @param i         vault number.
     * @param readOnly  whether the vault is read-only.
     */
    public VaultViewInfo(String vaultName, int i, boolean readOnly) {
        this.number = i;
        this.vaultName = vaultName;
        this.readOnly = readOnly;
    }

    /**
     * Get the holder of the vault.
     *
     * @return The holder of the vault.
     */
    public String getVaultName() {
        return this.vaultName;
    }

    /**
     * Get the vault number.
     *
     * @return The vault number.
     */
    public int getNumber() {
        return this.number;
    }

    /**
     * Check if the vault is read-only.
     *
     * @return true if read-only.
     */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    @Override
    public String toString() {
        return this.vaultName + " " + this.number;
    }
}