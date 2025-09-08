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

package com.drtshock.playervaults.tasks;

import com.drtshock.playervaults.PlayerVaults;

public class Cleanup implements Runnable {

    private final long diff;
    private final PlayerVaults plugin;

    public Cleanup(PlayerVaults plugin, long diff) {
        this.plugin = plugin;
        this.diff = diff * 86400000L;
    }

    @Override
    public void run() {
        long time = System.currentTimeMillis();
        long olderThan = time - diff;
        plugin.getStorageProvider().cleanup(olderThan);
    }
}
