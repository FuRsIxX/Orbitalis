package com.orbitalis.phase2.survival;

import com.orbitalis.platform.PlayerData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Réapparition des équipages. Qui s'est reposé en dernier dans une couchette de station, ou allongé dans un caisson du
 * vaisseau, se réveille devant elle, tant que sa pièce est close et bordée d'un générateur d'air ; sinon, la place est
 * oubliée. Pour le reste, qui meurt en orbite, sur la Lune ou sur Mars se réveille sur la planète : à son lit de l'Overworld,
 * ou, s'il n'en a pas, au point d'apparition du monde. Aucun point de réapparition du jeu ne reste dans l'espace.
 * <p>
 * En 1.21.1, le jeu fait d'abord réapparaître le joueur comme d'habitude (lit, ou point d'apparition), puis
 * {@link #afterRespawn} le déplace à sa couchette, ou le ramène sur Terre s'il est réapparu dans l'espace.
 */
public final class RespawnRules {
    /** La couchette de station ou le caisson où le joueur s'est reposé en dernier. */
    private static final String BERTH = "berth";
    /** Le dernier point de réapparition vu dans l'Overworld : un lit sur Terre fait oublier la couchette. */
    private static final String HOME = "home";

    private RespawnRules() {
    }

    /** Vrai pour les mondes du mod : l'orbite, la Lune, Mars, Titan, Europe. */
    public static boolean isSpaceWorld(ResourceKey<Level> dimension) {
        return dimension == SpaceRules.ORBIT || dimension == SpaceRules.MOON || dimension == SpaceRules.MARS || dimension == SpaceRules.TITAN
                || dimension == SpaceRules.EUROPA;
    }

    /**
     * À chaque tick du joueur : s'il vient de se donner un nouveau point de réapparition dans l'Overworld (un lit), on le
     * retient, et sa couchette de station est oubliée — c'est le dernier endroit choisi qui compte.
     */
    public static void onPlayerTick(ServerPlayer player) {
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null || player.getRespawnDimension() != Level.OVERWORLD) {
            return;
        }
        CompoundTag data = PlayerData.of(player);
        if (data.getCompound(HOME).getLong("pos") != spawn.asLong() || !data.contains(HOME)) {
            CompoundTag home = new CompoundTag();
            home.putLong("pos", spawn.asLong());
            data.put(HOME, home);
            data.remove(BERTH);
        }
    }

    /**
     * Le joueur vient de réapparaître : à sa couchette ou à son caisson s'il en a un, sinon sur Terre s'il est réapparu dans
     * l'espace. Le retour de l'End ne change rien.
     */
    public static void afterRespawn(ServerPlayer player, boolean fromEnd) {
        if (fromEnd) {
            return;
        }
        DimensionTransition berth = wakeUp(player);
        if (berth != null) {
            player.teleportTo(berth.newLevel(), berth.pos().x, berth.pos().y, berth.pos().z, berth.yRot(), berth.xRot());
            return;
        }
        if (isSpaceWorld(player.level().dimension())) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, overworld.getSharedSpawnAngle(), 0.0F);
        }
    }

    /** Le joueur s'est reposé dans cette couchette de station (sa moitié basse) : il s'y réveillera. */
    public static void setBerth(ServerPlayer player, BlockPos bunk) {
        CompoundTag berth = new CompoundTag();
        berth.putString("dimension", player.level().dimension().location().toString());
        berth.putLong("pos", bunk.asLong());
        PlayerData.of(player).put(BERTH, berth);
    }

    /**
     * Le joueur s'est allongé dans ce caisson d'hibernation (sa moitié basse) : il s'y réveillera, où que soit le vaisseau.
     * On retient, en plus de sa place, l'agence du vaisseau et la place du caisson par rapport à sa poupe : quand le
     * vaisseau a sauté, on le retrouve par son voyage.
     *
     * @param origin centre de la poupe du vaisseau
     * @param out    sens de sa proue
     */
    public static void setPodBerth(ServerPlayer player, BlockPos pod, String agency, BlockPos origin, Direction out) {
        setBerth(player, pod);
        CompoundTag berth = PlayerData.of(player).getCompound(BERTH);
        berth.putString("agency", agency);
        berth.putLong("offset", pod.subtract(origin).asLong());
        berth.putInt("out", out.get3DDataValue());
        PlayerData.of(player).put(BERTH, berth);
    }

    /** Vrai si le joueur se réveillera dans cette couchette. */
    public static boolean hasBerth(ServerPlayer player, BlockPos bunk) {
        CompoundTag berth = PlayerData.of(player).getCompound(BERTH);
        return berth.contains("pos") && berth.getLong("pos") == bunk.asLong()
                && player.level().dimension().location().toString().equals(berth.getString("dimension"));
    }

    /**
     * Le réveil dans la couchette de la station ou le caisson du vaisseau, ou {@code null} : rien de retenu, ou ce n'est
     * plus là, ou sa pièce n'est plus close et pourvue d'un générateur d'air. Alors la place est oubliée, et le joueur le sait.
     */
    public static DimensionTransition wakeUp(ServerPlayer player) {
        CompoundTag berth = PlayerData.of(player).getCompound(BERTH);
        if (!berth.contains("pos")) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(berth.getString("dimension"));
        ServerLevel level = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        BlockPos bunk = BlockPos.of(berth.getLong("pos"));
        boolean pod = berth.contains("agency", Tag.TAG_STRING);
        BlockState state = level == null ? null : level.getBlockState(bunk);
        if (pod && !isBerth(state)) {
            // Le vaisseau a sauté depuis : on le cherche là où son voyage l'a mené, à son berceau ou en orbite d'un autre monde.
            var voyage = com.orbitalis.phase3.interstellar.ShipVoyages.of(player.server).of(berth.getString("agency"));
            if (voyage != null) {
                Direction out = Direction.from3DDataValue(berth.contains("out") ? berth.getInt("out") : 2);
                BlockPos origin = voyage.away() ? voyage.awayOrigin() : voyage.home();
                if (out == voyage.out()) {
                    level = player.server.getLevel(voyage.away() ? voyage.destination().orbit() : SpaceRules.ORBIT);
                    bunk = origin.offset(BlockPos.of(berth.getLong("offset")));
                    state = level == null ? null : level.getBlockState(bunk);
                }
            }
        }
        if (!isBerth(state)) {
            return lostBerth(player, pod ? "ship.orbitalis.pod.gone" : "station.orbitalis.bunk.gone");
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos front = bunk.relative(facing);
        // La pièce de la couchette doit être close, et un générateur d'air doit la border : on s'y réveille alors à l'air.
        java.util.Set<Long> cells = com.orbitalis.phase2.station.StationAir.scan(level, bunk);
        BlockPos generator = cells.contains(front.asLong()) ? generatorOf(level, cells) : null;
        if (generator == null) {
            return lostBerth(player, pod ? "ship.orbitalis.pod.lost" : "station.orbitalis.bunk.lost");
        }
        com.orbitalis.phase2.station.StationAir.setRoom(level, generator, cells);
        return new DimensionTransition(level, Vec3.atBottomCenterOf(front), Vec3.ZERO, facing.toYRot(), 0.0F, DimensionTransition.DO_NOTHING);
    }

    /** Vrai pour la moitié basse d'une couchette de station ou d'un caisson d'hibernation. */
    private static boolean isBerth(BlockState state) {
        return state != null && (state.is(com.orbitalis.core.registry.ModBlocks.CREW_BUNK.get())
                || state.is(com.orbitalis.core.registry.ModBlocks.HIBERNATION_POD.get()))
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
    }

    /** Un générateur d'air qui borde ce volume, ou {@code null}. */
    private static BlockPos generatorOf(ServerLevel level, java.util.Set<Long> cells) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long cell : cells) {
            for (Direction direction : Direction.values()) {
                pos.set(cell).move(direction);
                if (level.getBlockState(pos).is(com.orbitalis.core.registry.ModBlocks.LIFE_SUPPORT.get())) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    private static DimensionTransition lostBerth(ServerPlayer player, String key) {
        PlayerData.of(player).remove(BERTH);
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
        return null;
    }
}
