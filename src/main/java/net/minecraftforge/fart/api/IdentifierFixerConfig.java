/*
 * Forge Auto Renaming Tool
 * Copyright (c) 2021
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fart.api;

/**
 * Identifier transformation strategy for {@link Transformer#identifierFixerFactory(IdentifierFixerConfig)}.
 */
public enum IdentifierFixerConfig {
    /**
     * Checks all Local variables if they are valid java identifiers.
     */
    ALL,
    /**
     * Only replaces snowman character used by Minecraft.
     */
    SNOWMEN;
}
