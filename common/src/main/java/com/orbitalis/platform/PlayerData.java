package com.orbitalis.platform;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.orbitalis.Orbitalis;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Ce que le mod retient de chaque joueur d'une partie à l'autre (sa couchette, son dernier lit sur Terre), gardé avec le monde.
 * Remplace les données persistantes des joueurs de NeoForge, qui n'existent pas sous Fabric.
 */
public final class PlayerData extends SavedData {
    private static final String NAME = Orbitalis.MODID + "_players";
    private static final SavedData.Factory<PlayerData> FACTORY = new SavedData.Factory<>(PlayerData::new, PlayerData::load, null);

    private final Map<UUID, CompoundTag> players = new HashMap<>();

    private PlayerData() {
    }

    private static PlayerData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerData data = new PlayerData();
        for (String key : tag.getAllKeys()) {
            try {
                data.players.put(UUID.fromString(key), tag.getCompound(key));
            } catch (IllegalArgumentException ignored) {
                // Une clé qui n'est pas un joueur : on l'ignore.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        players.forEach((uuid, data) -> tag.put(uuid.toString(), data));
        return tag;
    }

    private static PlayerData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Les données de ce joueur, à lire et à changer ; elles seront sauvegardées avec le monde. */
    public static CompoundTag of(ServerPlayer player) {
        PlayerData data = of(player.server);
        data.setDirty();
        return data.players.computeIfAbsent(player.getUUID(), uuid -> new CompoundTag());
    }
}
