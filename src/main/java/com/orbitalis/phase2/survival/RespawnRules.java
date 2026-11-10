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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

/**
 * Réapparition des équipages. Qui s'est reposé en dernier dans une couchette de station se réveille devant elle, tant
 * que sa pièce est close et pourvue d'un générateur d'air ; sinon, la couchette est oubliée. Pour le reste, qui meurt en
 * orbite, sur la Lune ou sur Mars se réveille sur la planète : à son dernier point de réapparition connu dans
 * l'Overworld (son lit), ou, s'il n'en a pas, au point d'apparition du monde. Aucun point de réapparition du jeu ne reste
 * dans l'espace ou sur un autre monde.
 * <p>
 * Le dernier point de réapparition de l'Overworld est retenu à part, dans les données du joueur : rien de ce qui se
 * passe ailleurs ne peut l'effacer.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class RespawnRules {
    private static final String KEY = "orbitalis_home_spawn";
    /** La couchette de station où le joueur s'est reposé en dernier : il s'y réveille, tant qu'elle est pressurisée. */
    private static final String BERTH = "orbitalis_berth";

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
            // Un lit sur Terre remplace la couchette de la station : c'est le dernier endroit choisi qui compte.
            player.getPersistentData().remove(BERTH);
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
        TeleportTransition berth = wakeUp(player);
        if (berth != null) {
            event.setTeleportTransition(berth);
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

    /** Le joueur s'est reposé dans cette couchette de station (sa moitié basse) : il s'y réveillera. */
    public static void setBerth(ServerPlayer player, BlockPos bunk) {
        CompoundTag berth = new CompoundTag();
        berth.putString("dimension", player.level().dimension().identifier().toString());
        berth.putLong("pos", bunk.asLong());
        player.getPersistentData().put(BERTH, berth);
    }

    /** Vrai si le joueur se réveillera dans cette couchette. */
    public static boolean hasBerth(ServerPlayer player, BlockPos bunk) {
        CompoundTag berth = player.getPersistentData().getCompoundOrEmpty(BERTH);
        return berth.contains("pos") && berth.getLongOr("pos", 0L) == bunk.asLong()
                && player.level().dimension().identifier().toString().equals(berth.getStringOr("dimension", ""));
    }

    /**
     * Le réveil dans la couchette de la station, ou {@code null} : pas de couchette retenue, ou elle n'est plus là, ou sa
     * pièce n'est plus close et pourvue d'un générateur d'air. Alors la couchette est oubliée, et le joueur le sait.
     */
    public static TeleportTransition wakeUp(ServerPlayer player) {
        CompoundTag berth = player.getPersistentData().getCompoundOrEmpty(BERTH);
        if (!berth.contains("pos")) {
            return null;
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(berth.getStringOr("dimension", ""));
        ServerLevel level = id == null ? null
                : player.level().getServer().getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        BlockPos bunk = BlockPos.of(berth.getLongOr("pos", 0L));
        net.minecraft.world.level.block.state.BlockState state = level == null ? null : level.getBlockState(bunk);
        if (state == null || !state.is(com.orbitalis.core.registry.ModBlocks.CREW_BUNK.get())
                || state.getValue(com.orbitalis.phase2.station.CrewBunkBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            return lostBerth(player, "station.orbitalis.bunk.gone");
        }
        net.minecraft.core.Direction facing = state.getValue(com.orbitalis.phase2.station.CrewBunkBlock.FACING);
        BlockPos front = bunk.relative(facing);
        // La pièce de la couchette doit être close, et un générateur d'air doit la border : on s'y réveille alors à l'air.
        java.util.Set<Long> cells = com.orbitalis.phase2.station.StationAir.scan(level, bunk);
        BlockPos generator = cells.contains(front.asLong()) ? generatorOf(level, cells) : null;
        if (generator == null) {
            return lostBerth(player, "station.orbitalis.bunk.lost");
        }
        com.orbitalis.phase2.station.StationAir.setRoom(level, generator, cells);
        return new TeleportTransition(level, Vec3.atBottomCenterOf(front), Vec3.ZERO, facing.toYRot(), 0.0F, TeleportTransition.DO_NOTHING);
    }

    /** Un générateur d'air qui borde ce volume, ou {@code null}. */
    private static BlockPos generatorOf(ServerLevel level, java.util.Set<Long> cells) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long cell : cells) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                pos.set(cell).move(direction);
                if (level.getBlockState(pos).is(com.orbitalis.core.registry.ModBlocks.LIFE_SUPPORT.get())) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    private static TeleportTransition lostBerth(ServerPlayer player, String key) {
        player.getPersistentData().remove(BERTH);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GOLD));
        return null;
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
