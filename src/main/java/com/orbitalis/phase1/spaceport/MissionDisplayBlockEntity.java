package com.orbitalis.phase1.spaceport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.conduit.ConduitNetwork;
import com.orbitalis.phase1.conduit.DataLink;
import com.orbitalis.phase1.fuel.CryogenicTankBlockEntity;
import com.orbitalis.phase1.fuel.FluidTankBlockEntity;
import com.orbitalis.phase1.satellite.AgencySatellites;
import com.orbitalis.phase1.satellite.Satellite;
import com.orbitalis.phase1.satellite.SatelliteManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Écran de contrôle. Chaque bloc est une dalle : posées côte à côte et tournées du même côté, elles forment un
 * seul écran, qui affiche une seule image. C'est la dalle du coin en haut à gauche, vue de face, qui tient ce que
 * l'écran affiche et qui dessine l'image entière.
 * <p>
 * L'écran montre ce que lui fournissent ses <em>sources</em> : les blocs qui le touchent, et ceux qu'un câble de
 * données, branché au dos de l'une de ses dalles, relie à lui.
 * <ul>
 * <li>un pupitre de lancement : le lanceur et son pas de tir (vue d'ensemble, carte, fusée, télémesure, trajectoire) ;</li>
 * <li>une station de poursuite : les satellites de l'agence de celui qui a posé l'écran ;</li>
 * <li>des réservoirs, modulables ou cryogéniques : leurs niveaux.</li>
 * </ul>
 * Une source trop éloignée pour un câble peut aussi être désignée à la main ; cette liaison-là est gardée tant
 * qu'aucune source câblée du même genre ne la remplace.
 */
public class MissionDisplayBlockEntity extends BlockEntity {
    private static final int MAX_WIDTH = 16;
    private static final int MAX_HEIGHT = 9;
    /** Intervalle entre deux mesures de l'écran, en ticks. */
    private static final int SCAN_INTERVAL = 20;

    /**
     * Un écran.
     *
     * @param origin dalle du coin en haut à gauche, vue de face
     * @param right  direction qui va vers la droite de l'écran, vu de face
     */
    public record Wall(BlockPos origin, Direction right, int width, int height) {
        /** Toutes les dalles de l'écran. */
        public List<BlockPos> blocks() {
            List<BlockPos> blocks = new ArrayList<>();
            for (int column = 0; column < width; column++) {
                for (int row = 0; row < height; row++) {
                    blocks.add(origin.relative(right, column).below(row));
                }
            }
            return blocks;
        }
    }

    /**
     * Affichages d'un écran. La vue d'ensemble montre tout ce qui tient ; sur un petit écran, où tout ne tient pas,
     * on passe d'un affichage à l'autre (accroupi + clic droit) pour voir chacun en grand.
     */
    public enum Page {
        OVERVIEW,
        MAP,
        ROCKET,
        TELEMETRY,
        TRAJECTORY,
        SATELLITES,
        TANKS;

        public String nameKey() {
            return "display.orbitalis.page." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Nombre de valeurs décrivant un satellite dans {@link #satellites()} : numéro, type, en service, jours restants. */
    public static final int SATELLITE_FIELDS = 4;
    /** Nombre maximal de satellites affichés sur un écran. */
    private static final int MAX_SATELLITES = 24;
    /** Nombre de valeurs décrivant un réservoir dans {@link #tanks()} : numéro du fluide, quantité, contenance (mB). */
    public static final int TANK_FIELDS = 3;
    /** Nombre maximal de réservoirs affichés sur un écran. */
    private static final int MAX_TANKS = 12;

    /** Table de lancement désignée à la main, ou {@code null}. */
    private BlockPos link;
    /** Table de lancement du pupitre qui touche l'écran ou qu'un câble relie à lui, ou {@code null}. */
    private BlockPos wired;
    /** Agence dont l'écran suit les satellites, désignée à la main, ou {@code null}. */
    private String satelliteAgency;
    /** Vrai si une station de poursuite touche l'écran ou qu'un câble la relie à lui. */
    private boolean wiredStation;
    /** Agence de celui qui a posé l'écran : celle dont une station de poursuite câblée montre les satellites. */
    private String owner;
    /** Satellites de l'agence suivie, relevés par le serveur et envoyés aux clients. */
    private int[] satellites = new int[0];
    /** Réservoirs reliés à l'écran, relevés par le serveur et envoyés aux clients. */
    private int[] tanks = new int[0];
    /** Nombre de sources trouvées au dernier relevé. */
    private int sources;
    private int page;
    private Wall wall;
    private int ticks;
    /** Écran d'un centre spatial trouvé dans le monde : sans source, il montre un écran de veille plutôt que la marche à suivre. */
    private boolean standby;

    public MissionDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSION_DISPLAY.get(), pos, state);
    }

    /** Écran dont fait partie la dalle située en {@code pos} ; les mêmes règles valent côté serveur et côté client. */
    public static Wall wallOf(Level level, BlockPos pos) {
        Direction facing = level.getBlockState(pos).getValue(MissionDisplayBlock.FACING);
        Direction right = MissionDisplayBlock.rightOf(facing);
        Direction left = right.getOpposite();
        BlockPos origin = pos;
        for (int i = 0; i < MAX_WIDTH + MAX_HEIGHT; i++) {
            if (isDisplay(level, origin.above(), facing)) {
                origin = origin.above();
            } else if (isDisplay(level, origin.relative(left), facing)) {
                origin = origin.relative(left);
            } else {
                break;
            }
        }
        int width = 1;
        while (width < MAX_WIDTH && isDisplay(level, origin.relative(right, width), facing)) {
            width++;
        }
        int height = 1;
        while (height < MAX_HEIGHT && isDisplay(level, origin.below(height), facing)) {
            height++;
        }
        return new Wall(origin, right, width, height);
    }

    private static boolean isDisplay(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.MISSION_DISPLAY.get()) && state.getValue(MissionDisplayBlock.FACING) == facing;
    }

    public void clientTick() {
        // Le tronçon qui la porte peut déjà être déchargé (le joueur s'éloigne d'un coup) : sa dalle n'est alors plus là.
        if (level != null && ticks++ % SCAN_INTERVAL == 0 && level.getBlockState(worldPosition).is(ModBlocks.MISSION_DISPLAY.get())) {
            wall = wallOf(level, worldPosition);
        }
    }

    /** Vrai si cette dalle dessine l'image : elle est le coin de l'écran. */
    public boolean drawsWall() {
        return wall != null && wall.origin().equals(worldPosition);
    }

    public int wallWidth() {
        return wall == null ? 1 : wall.width();
    }

    public int wallHeight() {
        return wall == null ? 1 : wall.height();
    }

    // ------------------------------------------------------------------
    // Sources
    // ------------------------------------------------------------------

    public void setOwner(String agencyKey) {
        owner = agencyKey;
        sync();
    }

    /** Donne une agence à un écran qui n'en a pas encore. */
    public void adopt(String agencyKey) {
        if (owner == null) {
            setOwner(agencyKey);
        }
    }

    /**
     * Côté serveur, une fois par seconde, sur la dalle du coin : relève les blocs qui touchent l'écran et ceux
     * qu'un câble de données relie à lui, et en tire ce que l'écran peut afficher.
     */
    public void scan(ServerLevel serverLevel, Wall around) {
        Set<BlockPos> found = new LinkedHashSet<>();
        String inherited = owner;
        Direction back = around.right().getCounterClockWise();
        for (BlockPos slab : around.blocks()) {
            if (inherited == null && serverLevel.getBlockEntity(slab) instanceof MissionDisplayBlockEntity other) {
                inherited = other.owner;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = slab.relative(direction);
                BlockState state = serverLevel.getBlockState(neighbour);
                if (state.is(ModBlocks.MISSION_DISPLAY.get())) {
                    continue;
                }
                if (state.is(ModBlocks.DATA_CABLE.get())) {
                    // Un câble de données ne se branche qu'au dos de l'écran.
                    if (direction != back) {
                        continue;
                    }
                    for (ConduitNetwork.Endpoint endpoint : ConduitNetwork.endpoints(serverLevel, neighbour, state.getBlock(), DataLink.BLOCK)) {
                        found.add(endpoint.pos());
                    }
                } else if (!state.isAir()) {
                    found.add(neighbour);
                }
            }
        }

        BlockPos table = null;
        boolean station = false;
        int count = 0;
        Set<BlockPos> seen = new HashSet<>();
        List<int[]> levels = new ArrayList<>();
        for (BlockPos pos : found) {
            BlockState state = serverLevel.getBlockState(pos);
            // L'aile d'un pupitre ou d'une station vaut le poste lui-même.
            if (state.is(ModBlocks.WORKSTATION_PART.get())) {
                pos = Workstation.findHost(serverLevel, pos);
                if (pos == null) {
                    continue;
                }
                state = serverLevel.getBlockState(pos);
            }
            if (!seen.add(pos)) {
                continue;
            }
            if (state.is(ModBlocks.LAUNCH_CONSOLE.get())) {
                count++;
                LaunchTableBlockEntity linked = LaunchConsoleBlock.linkedTable(serverLevel, pos);
                if (table == null && linked != null) {
                    table = linked.getBlockPos().immutable();
                }
            } else if (state.is(ModBlocks.TRACKING_STATION.get())) {
                count++;
                station = true;
            } else if (serverLevel.getBlockEntity(pos) instanceof FluidTankBlockEntity block) {
                FluidTankBlockEntity tank = block.master();
                if (tank != null && (tank == block || seen.add(tank.getBlockPos())) && levels.size() < MAX_TANKS) {
                    seen.add(tank.getBlockPos());
                    count++;
                    levels.add(snapshot(tank.fluid(), tank.capacity()));
                }
            } else if (serverLevel.getBlockEntity(pos) instanceof CryogenicTankBlockEntity tank && levels.size() < MAX_TANKS) {
                count++;
                levels.add(snapshot(tank.getTank().getFluid(0), CryogenicTankBlockEntity.CAPACITY));
            }
        }
        int[] snapshot = new int[levels.size() * TANK_FIELDS];
        for (int i = 0; i < levels.size(); i++) {
            System.arraycopy(levels.get(i), 0, snapshot, i * TANK_FIELDS, TANK_FIELDS);
        }
        boolean changed = !java.util.Objects.equals(table, wired) || station != wiredStation || count != sources
                || !Arrays.equals(snapshot, tanks) || !java.util.Objects.equals(inherited, owner);
        if (changed) {
            wired = table;
            wiredStation = station;
            sources = count;
            tanks = snapshot;
            owner = inherited;
            sync();
        }
    }

    private static int[] snapshot(FluidStack content, int capacity) {
        return new int[] {content.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(content.getFluid()), content.getAmount(), capacity};
    }

    /** Vrai si l'écran a de quoi afficher quelque chose. */
    public boolean hasSource() {
        return wired != null || link != null || agency() != null || tanks.length > 0;
    }

    /** Nombre d'ordinateurs et de réservoirs trouvés au dernier relevé, au contact ou par câble. */
    public int sourceCount() {
        return sources;
    }

    /** Relie l'écran à distance à une table de lancement, et l'annonce aux clients. */
    public void setLink(BlockPos table) {
        link = table == null ? null : table.immutable();
        sync();
    }

    /** Agence dont l'écran suit les satellites : celle de son propriétaire s'il est câblé à une station, sinon celle désignée à la main. */
    private String agency() {
        return wiredStation && owner != null ? owner : satelliteAgency;
    }

    // ------------------------------------------------------------------
    // Affichages
    // ------------------------------------------------------------------

    private boolean isAvailable(Page candidate) {
        return switch (candidate) {
            case SATELLITES -> agency() != null;
            case TANKS -> tanks.length > 0;
            default -> wired != null || link != null;
        };
    }

    /** Affichage en cours : celui qui a été choisi s'il a de quoi s'afficher, sinon le premier qui convient. */
    public Page page() {
        Page stored = Page.values()[Math.floorMod(page, Page.values().length)];
        if (isAvailable(stored)) {
            return stored;
        }
        for (Page candidate : Page.values()) {
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        return Page.OVERVIEW;
    }

    /** Affichage suivant, parmi ceux que les sources de l'écran permettent. */
    public Page nextPage() {
        Page[] pages = Page.values();
        Page current = page();
        for (int step = 1; step <= pages.length; step++) {
            Page candidate = pages[(current.ordinal() + step) % pages.length];
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        return current;
    }

    public void setPage(Page newPage) {
        page = newPage.ordinal();
        sync();
    }

    /** Relie l'écran à distance aux satellites d'une agence, et passe sur leur affichage. */
    public void setSatelliteLink(String agencyKey) {
        satelliteAgency = agencyKey;
        page = Page.SATELLITES.ordinal();
        sync();
    }

    /** Satellites suivis : {@link #SATELLITE_FIELDS} valeurs par satellite, du plus ancien au plus récent. */
    public int[] satellites() {
        return satellites;
    }

    /** Réservoirs reliés : {@link #TANK_FIELDS} valeurs par réservoir. Le numéro du fluide vaut -1 pour un réservoir vide. */
    public int[] tanks() {
        return tanks;
    }

    /** Côté serveur, une fois par seconde : relève les satellites de l'agence suivie et les envoie s'ils ont changé. */
    public void refreshSatellites(MinecraftServer server) {
        String agencyKey = agency();
        if (agencyKey == null) {
            return;
        }
        long now = SatelliteManager.now(server);
        boolean limited = SatelliteManager.limited();
        List<Satellite> fleet = SatelliteManager.of(server, agencyKey).all();
        int count = Math.min(fleet.size(), MAX_SATELLITES);
        int[] snapshot = new int[count * SATELLITE_FIELDS];
        for (int i = 0; i < count; i++) {
            // Les plus récents d'abord s'il y en a trop : ce sont ceux qu'on suit.
            Satellite satellite = fleet.get(fleet.size() - count + i);
            boolean active = AgencySatellites.isActive(satellite, now, limited);
            long remaining = limited ? Math.max(0, AgencySatellites.endOfLife(satellite, true) - now) : -1;
            snapshot[i * SATELLITE_FIELDS] = satellite.number();
            snapshot[i * SATELLITE_FIELDS + 1] = satellite.type().ordinal();
            snapshot[i * SATELLITE_FIELDS + 2] = active ? 1 : 0;
            snapshot[i * SATELLITE_FIELDS + 3] = remaining < 0 ? -1 : (int) ((remaining + AgencySatellites.DAY - 1) / AgencySatellites.DAY);
        }
        if (!Arrays.equals(snapshot, satellites)) {
            satellites = snapshot;
            sync();
        }
    }

    private void sync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Vrai si l'écran a été relié à un pas de tir (même si la table n'existe plus). */
    public boolean hasLink() {
        return wired != null || link != null;
    }

    /** Table de lancement reliée, si elle existe encore : celle du pupitre câblé d'abord, sinon celle désignée à la main. */
    public LaunchTableBlockEntity linkedTable() {
        BlockPos table = wired != null ? wired : link;
        return level != null && table != null && level.getBlockEntity(table) instanceof LaunchTableBlockEntity found ? found : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (link != null) {
            output.putLong("link", link.asLong());
        }
        if (wired != null) {
            output.putLong("wired", wired.asLong());
        }
        output.putBoolean("wired_station", wiredStation);
        output.putInt("sources", sources);
        output.putInt("page", page);
        if (owner != null) {
            output.putString("owner", owner);
        }
        if (satelliteAgency != null) {
            output.putString("satellite_agency", satelliteAgency);
        }
        output.putIntArray("satellites", satellites);
        output.putIntArray("tanks", tanks);
        output.putBoolean("standby", standby);
    }

    /** Fait de cet écran celui d'un centre spatial : en veille tant que rien ne l'alimente. */
    public void setStandby(boolean standby) {
        this.standby = standby;
        setChanged();
    }

    public boolean isStandby() {
        return standby;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long packed = input.getLongOr("link", Long.MIN_VALUE);
        link = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
        packed = input.getLongOr("wired", Long.MIN_VALUE);
        wired = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
        wiredStation = input.getBooleanOr("wired_station", false);
        sources = input.getIntOr("sources", 0);
        page = input.getIntOr("page", 0);
        String stored = input.getStringOr("owner", "");
        owner = stored.isEmpty() ? null : stored;
        String agency = input.getStringOr("satellite_agency", "");
        satelliteAgency = agency.isEmpty() ? null : agency;
        satellites = input.getIntArray("satellites").orElse(new int[0]);
        tanks = input.getIntArray("tanks").orElse(new int[0]);
        standby = input.getBooleanOr("standby", false);
    }
}
