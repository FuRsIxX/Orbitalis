package com.orbitalis.phase1.quarry;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.network.DerrickStatusPayload;
import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.fuel.MachineTanks;
import com.orbitalis.phase1.machines.MachineInventory;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.satellite.SatelliteType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Le derrick au travail. Une fois sa tour complète ({@link DerrickPlan}), il fore le carré choisi sous lui, couche par
 * couche, de son pied jusqu'au fond du monde, et n'en extrait que les sortes choisies ({@link QuarryKinds}) :
 * <ul>
 * <li>il perce d'abord un puits au centre, où descend son train de tiges, puis cherche dans chaque couche ;</li>
 * <li>chaque bloc extrait part dans sa réserve, puis dans les coffres et machines qui touchent le derrick ;</li>
 * <li>avec le remblai, un minerai extrait est remplacé par la roche qui l'entourait : le paysage reste intact, seuls
 * les minerais disparaissent ;</li>
 * <li>la tête chauffe à chaque bloc. L'eau qu'on lui envoie par tuyau la refroidit : bien refroidie, elle fore vite ;
 * trop chaude, elle ralentit, puis s'arrête le temps de redescendre ;</li>
 * <li>un carottage révèle, couche par couche, les sortes présentes sous le derrick. Le premier filon de chaque sorte
 * trouvé dans chaque monde rapporte des points de recherche ;</li>
 * <li>les satellites d'observation de l'agence dressent la carte des filons de tout le carré.</li>
 * </ul>
 */
public class DerrickBlockEntity extends BlockEntity {
    public static final int ENERGY_CAPACITY = 200_000;
    private static final int ENERGY_INPUT = 4_000;
    /** Consommation en marche, puis par bloc extrait ou percé, en FE. */
    public static final int RUN_DRAW = 40;
    public static final int BLOCK_ENERGY = 400;
    public static final int WATER_CAPACITY = 16_000;
    /** Eau consommée par tick pour refroidir la tête, en mB, et chaleur qu'elle retire. */
    public static final int COOLANT_PER_TICK = 10;
    private static final int COOLING = 15;
    private static final int PASSIVE_COOLING = 1;
    /** Chaleur, en millièmes : ce qu'ajoute un bloc, le seuil où la tête s'arrête, et celui où elle repart. */
    public static final int HEAT_MAX = 1000;
    private static final int HEAT_PER_BLOCK = 45;
    private static final int HEAT_RESUME = 600;
    /** Côté du carré foré : de 5 à 33 blocs. */
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 16;
    /** Cases examinées par tick. */
    private static final int SCAN_BUDGET = 512;
    /** Durée et coût d'un carottage. */
    public static final int CORE_TICKS = 200;
    public static final int CORE_ENERGY = 20_000;
    /** Épaisseur d'une couche du carottage, en blocs. */
    public static final int BAND = 16;
    /** Points de recherche du premier filon de chaque sorte trouvé dans un monde (le double pour un minerai rare). */
    public static final int CORE_POINTS = 15;
    /** Un relevé satellite se refait au plus toutes les dix secondes. */
    private static final int SURVEY_AGE = 200;
    private static final int SLOTS = 18;

    public enum Status {
        UNFORMED, STOPPED, WORKING, CORING, NO_POWER, OVERHEATED, FULL, FINISHED
    }

    private final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_INPUT, 0, this::setChanged);
    private final MachineTanks coolant = new MachineTanks(1, WATER_CAPACITY, (tank, fluid) -> fluid == Fluids.WATER, tank -> false, this::setChanged);
    private final MachineInventory output = new MachineInventory(SLOTS, (slot, stack) -> false, slot -> true, this::setChanged);

    private boolean formed;
    private int missing;
    private boolean running;
    private boolean backfill = true;
    private int filter = QuarryKinds.ALL_ORES;
    private int radius = 8;
    private int heat;
    private boolean overheated;
    /** Couche en cours et case suivante à examiner dans cette couche. */
    private int layer = Integer.MIN_VALUE;
    private int cursor;
    private int cooldown;
    private int mined;
    private boolean finished;
    private int ticks;
    /** Carottage : tick de fin (0 sans carottage en cours), joueur qui l'a demandé, et résultat par couche. */
    private long coreUntil;
    private java.util.UUID coreBy;
    private int[] coreBands = new int[0];
    /** Dernier relevé satellite. */
    private long surveyAt = Long.MIN_VALUE;
    private int surveyRadius = -1;
    private int surveyObservers;
    private int[] surveyColumns = new int[0];
    private int[] surveyCounts = new int[0];

    public DerrickBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL_DERRICK.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public MachineTanks getCoolant() {
        return coolant;
    }

    public MachineInventory getOutput() {
        return output;
    }

    public boolean isFormed() {
        return formed;
    }

    public int heat() {
        return heat;
    }

    public int mined() {
        return mined;
    }

    /** Fond actuel du puits central, pour le train de tiges ; le pied du derrick s'il n'a pas commencé. */
    public int boreBottom() {
        return layer == Integer.MIN_VALUE ? worldPosition.getY() : layer;
    }

    public Status status() {
        if (!formed) {
            return Status.UNFORMED;
        }
        if (coreUntil > 0) {
            return Status.CORING;
        }
        if (!running) {
            return finished ? Status.FINISHED : Status.STOPPED;
        }
        if (overheated) {
            return Status.OVERHEATED;
        }
        if (energy.getEnergy() < BLOCK_ENERGY + RUN_DRAW) {
            return Status.NO_POWER;
        }
        if (isFull()) {
            return Status.FULL;
        }
        return Status.WORKING;
    }

    // ------------------------------------------------------------------
    // Travail
    // ------------------------------------------------------------------

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % 20 == 0) {
            List<DerrickPlan.Piece> gaps = DerrickPlan.missing(level, pos);
            boolean nowFormed = gaps.isEmpty();
            if (nowFormed != formed || gaps.size() != missing) {
                formed = nowFormed;
                missing = gaps.size();
                if (!formed) {
                    running = false;
                }
                sync();
            }
        }
        // La tête refroidit : vite avec de l'eau, lentement sans.
        if (heat > 0) {
            FluidStack water = coolant.getFluid(0);
            if (water.getAmount() >= COOLANT_PER_TICK) {
                coolant.setFluid(0, water.getAmount() == COOLANT_PER_TICK ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, water.getAmount() - COOLANT_PER_TICK));
                heat = Math.max(0, heat - COOLING);
            } else {
                heat = Math.max(0, heat - PASSIVE_COOLING);
            }
            if (overheated && heat <= HEAT_RESUME) {
                overheated = false;
            }
        }
        boolean working = false;
        if (coreUntil > 0) {
            working = true;
            if (level.getGameTime() >= coreUntil) {
                finishCore(level);
            }
        } else if (formed && running && !overheated) {
            working = dig(level, pos);
        }
        if (state.getValue(BlockStateProperties.LIT) != working) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, working), Block.UPDATE_ALL);
        }
        if (ticks % 10 == 0) {
            pushOutput(level, pos);
        }
    }

    /** Un tick de forage. Renvoie vrai si la tête a travaillé. */
    private boolean dig(ServerLevel level, BlockPos pos) {
        if (!energy.consume(RUN_DRAW) || isFull()) {
            return false;
        }
        if (layer == Integer.MIN_VALUE) {
            layer = pos.getY() - 1;
            cursor = 0;
            bore(level, pos);
        }
        if (cooldown > 0) {
            cooldown--;
            return true;
        }
        int side = 2 * radius + 1;
        int cells = side * side;
        for (int scanned = 0; scanned < SCAN_BUDGET; scanned++) {
            if (cursor >= cells) {
                // Couche suivante, jusqu'au fond du monde.
                layer--;
                cursor = 0;
                if (layer <= level.getMinY()) {
                    running = false;
                    finished = true;
                    layer = level.getMinY() + 1;
                    sync();
                    return false;
                }
                bore(level, pos);
                sync();
                return true;
            }
            BlockPos target = new BlockPos(pos.getX() - radius + cursor % side, layer, pos.getZ() - radius + cursor / side);
            BlockState state = level.getBlockState(target);
            int kind = QuarryKinds.of(state);
            if (kind < 0 || (filter & (1 << kind)) == 0 || state.getDestroySpeed(level, target) < 0) {
                cursor++;
                continue;
            }
            if (energy.getEnergy() < BLOCK_ENERGY) {
                return false;
            }
            if (!extract(level, target, state, kind)) {
                return false;
            }
            cursor++;
            return true;
        }
        return true;
    }

    /** Perce le puits central à la couche en cours : ce qu'il traverse n'est pas gardé, sauf un minerai voulu. */
    private void bore(ServerLevel level, BlockPos pos) {
        BlockPos shaft = new BlockPos(pos.getX(), layer, pos.getZ());
        BlockState state = level.getBlockState(shaft);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, shaft) < 0) {
            return;
        }
        int kind = QuarryKinds.of(state);
        if (kind >= 0 && (filter & (1 << kind)) != 0) {
            extract(level, shaft, state, kind);
            level.setBlock(shaft, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (energy.consume(BLOCK_ENERGY)) {
            level.levelEvent(2001, shaft, Block.getId(state));
            level.setBlock(shaft, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Extrait un bloc : son butin dans la réserve, le remblai ou l'air à sa place, et la tête qui chauffe. */
    private boolean extract(ServerLevel level, BlockPos target, BlockState state, int kind) {
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), null, new ItemStack(Items.NETHERITE_PICKAXE));
        if (!fits(drops)) {
            return false;
        }
        drops.forEach(this::store);
        energy.consume(BLOCK_ENERGY);
        level.levelEvent(2001, target, Block.getId(state));
        level.setBlock(target, backfill ? filler(level, state, kind) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        mined++;
        heat = Math.min(HEAT_MAX, heat + HEAT_PER_BLOCK);
        if (heat >= HEAT_MAX) {
            overheated = true;
        }
        cooldown = heat < 500 ? 2 : heat < 800 ? 4 : 10;
        setChanged();
        return true;
    }

    /** La roche qui entourait le minerai : celle du monde, et l'ardoise des profondeurs ou la netherrack s'il le faut. */
    static BlockState filler(ServerLevel level, BlockState state, int kind) {
        String dimension = level.dimension().identifier().getPath();
        if (dimension.contains("moon")) {
            return ModBlocks.MOON_ROCK.get().defaultBlockState();
        }
        if (dimension.contains("mars")) {
            return ModBlocks.MARS_ROCK.get().defaultBlockState();
        }
        if (dimension.contains("titan")) {
            return ModBlocks.TITAN_ICE.get().defaultBlockState();
        }
        if (dimension.contains("europa")) {
            return ModBlocks.EUROPA_ICE.get().defaultBlockState();
        }
        if (state.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (state.is(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE)) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private boolean isFull() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (output.getStack(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Vrai si tout ce butin trouve sa place dans la réserve. */
    private boolean fits(List<ItemStack> drops) {
        int free = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (output.getStack(slot).isEmpty()) {
                free++;
            }
        }
        return free >= drops.size();
    }

    private void store(ItemStack stack) {
        for (int slot = 0; slot < SLOTS && !stack.isEmpty(); slot++) {
            ItemStack held = output.getStack(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                stack.shrink(moved);
                output.setStack(slot, held);
            }
        }
        for (int slot = 0; slot < SLOTS && !stack.isEmpty(); slot++) {
            if (output.getStack(slot).isEmpty()) {
                output.setStack(slot, stack.copy());
                stack.setCount(0);
            }
        }
    }

    /** Le butin part dans les coffres et machines qui touchent le derrick (sauf par le dessous, où est le puits). */
    private void pushOutput(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }
            ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, pos.relative(direction), direction.getOpposite());
            if (target != null) {
                ResourceHandlerUtil.move(output.automationView(), target, resource -> true, 64, null);
            }
        }
    }

    // ------------------------------------------------------------------
    // Carottage et relevé satellite
    // ------------------------------------------------------------------

    /** Lance un carottage, demandé par ce joueur. Renvoie ce qui l'empêche, ou {@code null}. */
    private Component startCore(ServerLevel level, ServerPlayer player) {
        if (!formed) {
            return Component.translatable("derrick.orbitalis.status.unformed");
        }
        if (coreUntil > 0) {
            return null;
        }
        if (!energy.consume(CORE_ENERGY)) {
            return Component.translatable("derrick.orbitalis.core.no_power", CORE_ENERGY);
        }
        coreUntil = level.getGameTime() + CORE_TICKS;
        coreBy = player.getUUID();
        setChanged();
        return null;
    }

    /**
     * Fin du carottage : les sortes présentes dans chaque couche de seize blocs, sous tout le carré. Le premier filon de
     * chaque sorte trouvé dans ce monde rapporte des points de recherche à l'agence de celui qui l'a demandé.
     */
    private void finishCore(ServerLevel level) {
        coreUntil = 0;
        int top = worldPosition.getY() - 1;
        int bands = Math.max(1, (top - level.getMinY()) / BAND + 1);
        int[] result = new int[bands];
        scan(level, (x, y, z, kind) -> result[Math.min(bands - 1, (top - y) / BAND)] |= 1 << kind);
        coreBands = result;
        setChanged();
        ServerPlayer player = coreBy == null ? null : level.getServer().getPlayerList().getPlayer(coreBy);
        if (player == null) {
            return;
        }
        int found = 0;
        for (int band : result) {
            found |= band;
        }
        AgencyResearch research = ResearchManager.of(player);
        String dimension = level.dimension().identifier().getPath();
        int points = 0;
        for (int kind = 0; kind < QuarryKinds.COUNT; kind++) {
            if ((found & (1 << kind)) != 0 && research.completeMilestone("core_" + dimension + "_" + kind)) {
                int reward = QuarryKinds.rare(kind) ? CORE_POINTS * 2 : CORE_POINTS;
                points += reward;
                research.addPoints(reward);
                player.sendSystemMessage(Component.translatable("derrick.orbitalis.core.discovery",
                        Component.translatable(QuarryKinds.nameKey(kind)), reward).withStyle(ChatFormatting.GREEN));
            }
        }
        player.sendSystemMessage(Component.translatable(points > 0 ? "derrick.orbitalis.core.done_points" : "derrick.orbitalis.core.done", points)
                .withStyle(ChatFormatting.AQUA), true);
    }

    @FunctionalInterface
    private interface Visitor {
        void visit(int x, int y, int z, int kind);
    }

    /** Parcourt toutes les sortes reconnues sous le carré, du pied du derrick au fond du monde. */
    private void scan(ServerLevel level, Visitor visitor) {
        int x0 = worldPosition.getX() - radius;
        int x1 = worldPosition.getX() + radius;
        int z0 = worldPosition.getZ() - radius;
        int z1 = worldPosition.getZ() + radius;
        int top = worldPosition.getY() - 1;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
                    // La plupart des tranches n'ont rien à extraire : leur palette le dit sans les parcourir.
                    if (baseY > top || section.hasOnlyAir() || !section.maybeHas(state -> QuarryKinds.of(state) >= 0)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        if (worldY > top) {
                            break;
                        }
                        for (int z = 0; z < 16; z++) {
                            int worldZ = (cz << 4) + z;
                            if (worldZ < z0 || worldZ > z1) {
                                continue;
                            }
                            for (int x = 0; x < 16; x++) {
                                int worldX = (cx << 4) + x;
                                if (worldX < x0 || worldX > x1) {
                                    continue;
                                }
                                int kind = QuarryKinds.of(section.getBlockState(x, y, z));
                                if (kind >= 0) {
                                    visitor.visit(worldX, worldY, worldZ, kind);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Refait le relevé satellite s'il est trop ancien, ou si le carré a changé. */
    private void survey(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        if (surveyRadius == radius && now - surveyAt < SURVEY_AGE) {
            return;
        }
        surveyObservers = SatelliteManager.of(level.getServer(), ResearchManager.agencyKey(player))
                .active(SatelliteType.OBSERVATION, SatelliteManager.now(level.getServer()), SatelliteManager.limited());
        surveyAt = now;
        surveyRadius = radius;
        if (surveyObservers <= 0) {
            surveyColumns = new int[0];
            surveyCounts = new int[0];
            return;
        }
        int side = 2 * radius + 1;
        int[][] perColumn = new int[side * side][QuarryKinds.COUNT];
        int[] counts = new int[QuarryKinds.COUNT];
        int x0 = worldPosition.getX() - radius;
        int z0 = worldPosition.getZ() - radius;
        scan(level, (x, y, z, kind) -> {
            perColumn[(z - z0) * side + (x - x0)][kind]++;
            counts[kind]++;
        });
        int[] columns = new int[side * side];
        for (int cell = 0; cell < columns.length; cell++) {
            int best = -1;
            int bestCount = 0;
            for (int kind = 0; kind < QuarryKinds.COUNT; kind++) {
                // Le régolithe couvre tout le sol des autres mondes : la carte montre d'abord les minerais.
                int weight = kind == QuarryKinds.REGOLITH ? perColumn[cell][kind] / 8 : perColumn[cell][kind];
                if (weight > bestCount) {
                    best = kind;
                    bestCount = weight;
                }
            }
            columns[cell] = best + 1;
        }
        surveyColumns = columns;
        surveyCounts = counts;
    }

    // ------------------------------------------------------------------
    // Écran
    // ------------------------------------------------------------------

    public static final int ACTION_REFRESH = 0;
    public static final int ACTION_RUN = 1;
    public static final int ACTION_RADIUS = 2;
    public static final int ACTION_BACKFILL = 3;
    public static final int ACTION_FILTER = 4;
    public static final int ACTION_CORE = 5;

    /** Ouvre l'écran du joueur. */
    public void open(ServerPlayer player) {
        ModNetwork.send(player, status(player, true));
    }

    /** Une commande de l'écran ; la réponse est l'état à jour, et, s'il y en a, ce qui a empêché la commande. */
    public void command(ServerPlayer player, int action, int argument) {
        if (!(level instanceof ServerLevel serverLevel) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) > 64 * 64) {
            return;
        }
        Component refused = null;
        switch (action) {
            case ACTION_RUN -> {
                if (!formed) {
                    refused = Component.translatable("derrick.orbitalis.status.unformed");
                } else {
                    running = !running;
                    if (running && finished) {
                        // Un nouveau départ reprend tout le carré, du haut.
                        finished = false;
                        layer = Integer.MIN_VALUE;
                    }
                }
            }
            case ACTION_RADIUS -> {
                int wanted = Math.clamp(radius + Integer.signum(argument), MIN_RADIUS, MAX_RADIUS);
                if (wanted != radius) {
                    radius = wanted;
                    // Un autre carré : on reprend la couche en cours depuis son début.
                    cursor = 0;
                }
            }
            case ACTION_BACKFILL -> backfill = !backfill;
            case ACTION_FILTER -> {
                if (argument >= 0 && argument < QuarryKinds.COUNT) {
                    filter ^= 1 << argument;
                }
            }
            case ACTION_CORE -> refused = startCore(serverLevel, player);
            default -> {
            }
        }
        if (action != ACTION_REFRESH) {
            setChanged();
            sync();
        }
        if (refused != null) {
            player.sendSystemMessage(refused.copy().withStyle(ChatFormatting.GOLD), true);
        }
        ModNetwork.send(player, status(player, false));
    }

    private DerrickStatusPayload status(ServerPlayer player, boolean open) {
        ServerLevel serverLevel = (ServerLevel) level;
        survey(serverLevel, player);
        int stored = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            stored += output.getStack(slot).getCount();
        }
        long now = serverLevel.getGameTime();
        int core = coreUntil <= 0 ? -1 : (int) Math.clamp(100 - (coreUntil - now) * 100 / CORE_TICKS, 0, 100);
        return new DerrickStatusPayload(worldPosition, open, status().ordinal(), missing, running, energy.getEnergy(), ENERGY_CAPACITY,
                coolant.getFluid(0).getAmount(), WATER_CAPACITY, heat, radius, backfill, filter, boreBottom(), worldPosition.getY() - 1,
                serverLevel.getMinY(), mined, stored, core, coreBands, surveyObservers, surveyColumns.length == 0 ? 0 : 2 * surveyRadius + 1,
                surveyColumns, surveyCounts);
    }

    // ------------------------------------------------------------------
    // Synchronisation et sauvegarde
    // ------------------------------------------------------------------

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /** Cassé, le derrick garde son énergie, son eau et son butin sur son objet ; reposé, il les retrouve. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
        coolant.storeIn(components);
        output.storeIn(components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components);
        coolant.restoreFrom(components);
        output.restoreFrom(components);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
        coolant.serialize(out.child("coolant"));
        output.serialize(out.child("output"));
        out.putBoolean("formed", formed);
        out.putBoolean("running", running);
        out.putBoolean("backfill", backfill);
        out.putInt("filter", filter);
        out.putInt("radius", radius);
        out.putInt("heat", heat);
        out.putBoolean("overheated", overheated);
        out.putInt("layer", layer);
        out.putInt("cursor", cursor);
        out.putInt("mined", mined);
        out.putBoolean("finished", finished);
        out.putLong("core_until", coreUntil);
        if (coreBy != null) {
            out.putString("core_by", coreBy.toString());
        }
        out.putIntArray("core_bands", coreBands);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
        coolant.deserialize(in.childOrEmpty("coolant"));
        output.deserialize(in.childOrEmpty("output"));
        formed = in.getBooleanOr("formed", false);
        running = in.getBooleanOr("running", false);
        backfill = in.getBooleanOr("backfill", true);
        filter = in.getIntOr("filter", QuarryKinds.ALL_ORES);
        radius = Math.clamp(in.getIntOr("radius", 8), MIN_RADIUS, MAX_RADIUS);
        heat = in.getIntOr("heat", 0);
        overheated = in.getBooleanOr("overheated", false);
        layer = in.getIntOr("layer", Integer.MIN_VALUE);
        cursor = in.getIntOr("cursor", 0);
        mined = in.getIntOr("mined", 0);
        finished = in.getBooleanOr("finished", false);
        coreUntil = in.getLongOr("core_until", 0L);
        coreBy = in.getString("core_by").map(java.util.UUID::fromString).orElse(null);
        coreBands = in.getIntArray("core_bands").orElse(new int[0]);
    }

    /** Pour les tests : le derrick a fini de se relever, sans attendre son tick. */
    public void checkStructure() {
        if (level != null) {
            List<DerrickPlan.Piece> gaps = DerrickPlan.missing(level, worldPosition);
            formed = gaps.isEmpty();
            missing = gaps.size();
        }
    }

    /** Pour les tests : lance ou arrête le forage. */
    public void setRunning(boolean running) {
        this.running = running && formed;
    }

    public void setBackfill(boolean backfill) {
        this.backfill = backfill;
    }

    public void setRadius(int radius) {
        this.radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
    }

    public int[] coreBands() {
        return coreBands;
    }

    /** Pour les tests : lance un carottage et le termine aussitôt. */
    public void coreNow(ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel && startCore(serverLevel, player) == null) {
            finishCore(serverLevel);
        }
    }

    /** Liste des sortes qu'on voit sur la carte du relevé, pour les tests. */
    public List<Integer> surveyedKinds() {
        List<Integer> kinds = new ArrayList<>();
        for (int kind = 0; kind < surveyCounts.length; kind++) {
            if (surveyCounts[kind] > 0) {
                kinds.add(kind);
            }
        }
        return kinds;
    }
}
