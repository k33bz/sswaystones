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

/**
 * Builds the marker head shown on publicly-reachable waystones (see
 * {@link AccessMode#headTexture()}). Global and server-owned waystones render a
 * globe head instead of the owner's chosen icon, so a player scanning the viewer
 * can tell public destinations from their own at a glance.
 *
 * <p>The stacks are resolved profiles built from a baked-in texture, so nothing
 * here touches the session service — unlike the owner-head path, this never makes
 * a network call.
 */
public final class AccessIcons {
    private AccessIcons() {
    }

    // Fixed profile UUIDs/names keep the rendered heads identical between calls
    // (and therefore stackable / cacheable) instead of minting a new profile each time.
    private static final UUID GLOBAL_PROFILE_UUID = UUID.fromString("5b9ce8a1-11d4-4c7e-9a51-9a75746f0001");
    private static final UUID SERVER_PROFILE_UUID = UUID.fromString("5b9ce8a1-11d4-4c7e-9a51-9a75746f0002");
    private static final String GLOBAL_PROFILE_NAME = "GlobalWaystone";
    private static final String SERVER_PROFILE_NAME = "ServerWaystone";

    private static ItemStack GLOBAL_ICON;
    private static ItemStack SERVER_ICON;

    /**
     * The marker head for this access mode, or {@code null} if the mode keeps the
     * owner's own icon. Returns a copy so callers can mutate (add lore, names, …)
     * without corrupting the cached template.
     */
    public static @Nullable ItemStack iconFor(AccessMode mode) {
        return switch (mode) {
            case GLOBAL -> globalIcon().copy();
            case SERVER -> serverIcon().copy();
            case PRIVATE, TEAM -> null;
        };
    }

    private static ItemStack globalIcon() {
        if (GLOBAL_ICON == null)
            GLOBAL_ICON = head(GLOBAL_PROFILE_UUID, GLOBAL_PROFILE_NAME, AccessMode.GLOBAL_HEAD_TEXTURE);
        return GLOBAL_ICON;
    }

    private static ItemStack serverIcon() {
        if (SERVER_ICON == null)
            SERVER_ICON = head(SERVER_PROFILE_UUID, SERVER_PROFILE_NAME, AccessMode.SERVER_HEAD_TEXTURE);
        return SERVER_ICON;
    }

    /** A player head carrying a baked base64 skin texture (the minecraft-heads pattern). */
    private static ItemStack head(UUID id, String name, String texture) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, name, properties)));
        return stack;
    }
}
