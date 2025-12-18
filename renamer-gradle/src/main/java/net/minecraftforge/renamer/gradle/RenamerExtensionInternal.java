/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.renamer.gradle;

import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

non-sealed interface RenamerExtensionInternal extends RenamerExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(RenamerExtension.class);
    }
}
