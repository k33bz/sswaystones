/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/
package lol.sylvie.sswaystones.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class PlayerData {
    public ArrayList<String> discoveredWaystones;

    public PlayerData() {
        this(new ArrayList<>());
    }

    // Saves written before 1.3.2+k33bz.10 can hold the same hash twice, so duplicates are dropped on load
    public PlayerData(List<String> discoveredWaystones) {
        this.discoveredWaystones = new ArrayList<>(new LinkedHashSet<>(discoveredWaystones));
    }

    // Each waystone is listed once. Placing a waystone used to add it twice (once on creation, once on the
    // first right-click), and forgetting removed only one copy, so a forgotten waystone stayed usable
    public void discover(String hash) {
        if (!discoveredWaystones.contains(hash))
            discoveredWaystones.add(hash);
    }

    // Removes every copy, in case an old save still has duplicates
    public void forget(String hash) {
        discoveredWaystones.removeIf(hash::equals);
    }

    public List<String> getDiscoveredWaystones() {
        return discoveredWaystones;
    }

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(Codec.STRING.listOf().fieldOf("discovered_waystones").forGetter(PlayerData::getDiscoveredWaystones))
            .apply(instance, PlayerData::new));

}
