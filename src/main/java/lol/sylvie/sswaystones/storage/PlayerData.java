/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

public class PlayerData {
    public ArrayList<String> discoveredWaystones;
    // Landmark waystones this player has already been announced near (drives the
    // one-time discovery title).
    // Separate from discoveredWaystones (the fast-travel unlock set): walking past
    // a waystone must NOT unlock
    // it for teleport. Persisted; optional in the codec so pre-existing player data
    // loads with an empty set.
    public ArrayList<String> seenWaystones;

    public PlayerData() {
        this(new ArrayList<>(), new ArrayList<>());
    }

    public PlayerData(List<String> discoveredWaystones) {
        this(discoveredWaystones, new ArrayList<>());
    }

    public PlayerData(List<String> discoveredWaystones, List<String> seenWaystones) {
        this.discoveredWaystones = new ArrayList<>(discoveredWaystones);
        this.seenWaystones = new ArrayList<>(seenWaystones);
    }

    public List<String> getDiscoveredWaystones() {
        return discoveredWaystones;
    }

    public List<String> getSeenWaystones() {
        return seenWaystones;
    }

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("discovered_waystones").forGetter(PlayerData::getDiscoveredWaystones),
            Codec.STRING.listOf().optionalFieldOf("seen_waystones", List.of()).forGetter(PlayerData::getSeenWaystones))
            .apply(instance, PlayerData::new));

}
