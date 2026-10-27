package com.orbitalis.phase2.survival;

import com.orbitalis.Orbitalis;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * Réapparition des équipages. Qui meurt en orbite, sur la Lune ou sur Mars se réveille sur la planète : à son
 * dernier point de réapparition connu dans l'Overworld (son lit), ou, s'il n'en a pas, au point d'apparition du
 * monde. Aucun point de réapparition ne reste dans l'espace ou sur un autre monde.
 * <p>
 * Le dernier point de réapparition de l'Overworld est retenu à part, dans les données du joueur : rien de ce qui se
 * passe ailleurs ne peut l'effacer.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class RespawnRules {
    private static final String KEY = "orbitalis_home_spawn";

    private RespawnRules() {
    }

    /** Vrai pour les mondes du mod : l'orbite, la Lune, Mars, Titan. */
    public static boolean isSpaceWorld(ResourceKey<Level> dimension) {
        return dimension == SpaceRules.ORBIT || dimension == SpaceRules.MOON || dimension == SpaceRules.MARS || dimension == SpaceRules.TITAN
                || dimension == SpaceRules.EUROPA;
    }

    /** Le joueur se donne un point de réapparition : s'il est dans l'Overworld, on le retient. */
    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSpawnLevel() == Level.OVERWORLD && event.getNewSpawn() != null) {
            CompoundTag home = new CompoundTag();
            home.putLong("pos", event.getNewSpawn().asLong());
            home.putFloat("yaw", player.getYRot());
            home.putBoolean("forced", event.isForced());
            player.getPersistentData().put(KEY, home);
        }
    }

    /**
     * Le joueur réapparaît : s'il est mort dans l'espace ou sur un autre monde, ou si son point de réapparition y est,
     * il revient dans l'Overworld, à son dernier point connu.
     */
    @SubscribeEvent
    static void onRespawn(PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isFromEndFight()) {
            return;
        }
        TeleportTransition target = event.getTeleportTransition();
        boolean diedInSpace = isSpaceWorld(player.level().dimension());
        boolean spawnInSpace = target.newLevel() != null && isSpaceWorld(target.newLevel().dimension());
        if (!diedInSpace && !spawnInSpace) {
            return;
        }
        ServerLevel overworld = player.level().getServer().overworld();
        if (target.newLevel() == overworld) {
            // Déjà prévu dans l'Overworld (son lit, ou le point d'apparition du monde) : rien à changer.
            return;
        }
        // On lui rend son dernier point de l'Overworld (ou aucun), et le jeu calcule la réapparition comme d'habitude :
        // devant son lit s'il est toujours là, sinon au point d'apparition du monde.
        player.setRespawnPosition(homeSpawn(player), false);
        event.setTeleportTransition(player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING));
    }

    /** Le dernier point de réapparition connu du joueur dans l'Overworld, ou {@code null}. */
    private static ServerPlayer.RespawnConfig homeSpawn(ServerPlayer player) {
        CompoundTag home = player.getPersistentData().getCompoundOrEmpty(KEY);
        if (!home.contains("pos")) {
            ServerPlayer.RespawnConfig current = player.getRespawnConfig();
            return current != null && current.respawnData().dimension() == Level.OVERWORLD ? current : null;
        }
        BlockPos pos = BlockPos.of(home.getLongOr("pos", 0L));
        return new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, pos, home.getFloatOr("yaw", 0.0F), 0.0F),
                home.getBooleanOr("forced", false));
    }
}
