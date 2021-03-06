/**
 * HiveJumpPads, a Bukkit plugin to create 'Jump-Pads' on your Minecraft server.
 * TranslateAlternateColors.java - {CLASS_DESCRIPTION}
 * Copyright (C) 2013-2019 Sendarox 
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 **/

package com.Sendarox.HiveJumpPads.Utils.Handler;

import org.bukkit.ChatColor;

/** TranslateAlternateColors.java - Translate Alternate Colors. */
public class TranslateAlternateColors {

	/** Transalte ColorCodes within the given string. **/
	public static String translateColorCodes(String _msg) {
		return ChatColor.translateAlternateColorCodes('&', _msg);
	}
	
}
