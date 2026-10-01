/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.gui;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

// Globe heads for global and server-owned waystones. The texture is baked in, so
// unlike owner heads these never go through the session service.
public final class AccessIcons {
    private static final UUID GLOBAL_PROFILE_UUID = UUID.fromString("5b9ce8a1-11d4-4c7e-9a51-9a75746f0001");
    private static final UUID SERVER_PROFILE_UUID = UUID.fromString("5b9ce8a1-11d4-4c7e-9a51-9a75746f0002");

    private static ItemStack globalIcon;
    private static ItemStack serverIcon;

    private AccessIcons() {
    }

    // A copy, so callers can set names and lore on it
    public static @Nullable ItemStack iconFor(AccessMode mode) {
        return switch (mode) {
            case GLOBAL -> globalIcon().copy();
            case SERVER -> serverIcon().copy();
            case PRIVATE, TEAM -> null;
        };
    }

    private static ItemStack globalIcon() {
        if (globalIcon == null)
            globalIcon = head(GLOBAL_PROFILE_UUID, "GlobalWaystone", AccessMode.GLOBAL_HEAD_TEXTURE);
        return globalIcon;
    }

    private static ItemStack serverIcon() {
        if (serverIcon == null)
            serverIcon = head(SERVER_PROFILE_UUID, "ServerWaystone", AccessMode.SERVER_HEAD_TEXTURE);
        return serverIcon;
    }

    private static ItemStack head(UUID id, String name, String texture) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, name, properties)));
        return stack;
    }
}
