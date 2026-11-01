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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
 * La foreuse au travail. Une fois sa tour debout ({@link DerrickPlan}), elle fore la zone qui est devant elle, centrée
 * sur son axe, couche par couche, de la surface jusqu'à la profondeur que permet sa tour :
 * <ul>
 * <li>en mode « Filons », elle n'extrait que les sortes cochées ({@link QuarryKinds}) ; elle perce un puits au milieu de
 * la zone, où descend son train de tiges, suspendu à une flèche qui part du haut de la tour ;</li>
 * <li>en mode « Tout miner », elle extrait tout ce qui se casse (sauf les fluides et les blocs qui ont un contenu, comme
 * un coffre ou une machine) ;</li>
 * <li>la zone est un carré dont on règle le côté, dans la limite de la tour, ou un tronçon entier du monde, celui qui
 * est devant elle ;</li>
 * <li>avec le remplacement, chaque bloc extrait est remplacé par le bloc posé dans la case prévue, sinon par la roche
 * du monde. Cela coûte de l'électricité en plus ;</li>
 * <li>la tête chauffe à chaque bloc ; l'eau la refroidit ;</li>
 * <li>un carottage révèle les sortes présentes, couche par couche, et rapporte des points de recherche ;</li>
 * <li>les satellites d'observation de l'agence dressent la carte des filons de la zone.</li>
 * </ul>
 * Le butin attend dans la réserve (dix-huit cases) et part dans les coffres et machines qui touchent la tour.
 */
public class DerrickBlockEntity extends BlockEntity implements MenuProvider {
    public static final int ENERGY_CAPACITY = 200_000;
    private static final int ENERGY_INPUT = 4_000;
    /** Consommation en marche, par bloc extrait ou percé, et en plus par bloc remplacé, en FE. */
    public static final int RUN_DRAW = 40;
    public static final int BLOCK_ENERGY = 400;
    public static final int REPLACE_ENERGY = 300;
    public static final int WATER_CAPACITY = 16_000;
    /** Eau consommée par tick pour refroidir la tête, en mB, et chaleur qu'elle retire. */
    public static final int COOLANT_PER_TICK = 10;
    private static final int COOLING = 15;
    private static final int PASSIVE_COOLING = 1;
    /** Chaleur, en millièmes : ce qu'ajoute un bloc, et le seuil où la tête repart après une surchauffe. */
    public static final int HEAT_MAX = 1000;
    private static final int HEAT_PER_BLOCK = 45;
    private static final int HEAT_RESUME = 600;
    /** Cases examinées par tick. */
    private static final int SCAN_BUDGET = 512;
    /** Durée et coût d'un carottage. */
    public static final int CORE_TICKS = 200;
    public static final int CORE_ENERGY = 20_000;
    /** Épaisseur d'une couche du carottage, en blocs. */
    public static final int BAND = 16;
    /** Points de recherche du premier filon de chaque sorte trouvé dans un monde (le double pour un minerai rare). */
    public static final int CORE_POINTS = 15;
    private static final int SURVEY_AGE = 200;
    public static final int OUTPUT_SLOTS = 18;
    /** Côté d'un tronçon du monde. */
    public static final int CHUNK = 16;

    public enum Status {
        UNFORMED, STOPPED, WORKING, CORING, NO_POWER, OVERHEATED, FULL, FINISHED
    }

    public static final int MODE_ORES = 0;
    public static final int MODE_ALL = 1;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(ENERGY_CAPACITY, ENERGY_INPUT, 0, this::setChanged);
    private final MachineTanks coolant = new MachineTanks(1, WATER_CAPACITY, (tank, fluid) -> fluid == Fluids.WATER, tank -> false, this::setChanged);
    private final MachineInventory output = new MachineInventory(OUTPUT_SLOTS, (slot, stack) -> false, slot -> true, this::setChanged);
    /** La case du bloc de remplacement : seul un bloc y entre. */
    private final MachineInventory replacement = new MachineInventory(1, (slot, stack) -> stack.getItem() instanceof BlockItem, slot -> true,
            this::setChanged);

    private boolean formed;
    private int missing;
    private int levels;
    private boolean running;
    private int mode = MODE_ORES;
    private boolean replace;
    private boolean chunk;
    private int filter = QuarryKinds.ALL_ORES;
    /** Côté du carré foré, impair ; borné par la tour. */
    private int side = 9;
    private int heat;
    private boolean overheated;
    private int layer = Integer.MIN_VALUE;
    private int cursor;
    private int cooldown;
    private int mined;
    private boolean finished;
    private int ticks;
    private long coreUntil;
    private java.util.UUID coreBy;
    private int[] coreBands = new int[0];
    private long surveyAt = Long.MIN_VALUE;
    private String surveyKey = "";
    private int surveyObservers;
    private int[] surveyCells = new int[0];
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

    public MachineInventory getReplacement() {
        return replacement;
    }

    public boolean isFormed() {
        return formed;
    }

    public int levels() {
        return levels;
    }

    public int heat() {
        return heat;
    }

    public int mined() {
        return mined;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Couche en cours, pour le train de tiges ; la surface si la foreuse n'a pas commencé. */
    public int boreBottom() {
        return layer == Integer.MIN_VALUE ? worldPosition.getY() - 1 : layer;
    }

    // ------------------------------------------------------------------
    // La zone forée
    // ------------------------------------------------------------------

    /** La zone forée : {x0, z0, x1, z1}, bornes comprises. */
    public int[] area() {
        Direction facing = facing();
        if (chunk && DerrickPlan.maxSide(levels) > CHUNK) {
            // Le tronçon du monde qui est devant la tour, à mi-chemin d'un tronçon de profondeur.
            BlockPos ahead = worldPosition.relative(facing, DerrickPlan.GAP + CHUNK / 2);
            int x0 = (ahead.getX() >> 4) << 4;
            int z0 = (ahead.getZ() >> 4) << 4;
            return new int[] {x0, z0, x0 + CHUNK - 1, z0 + CHUNK - 1};
        }
        int size = effectiveSide();
        BlockPos centre = worldPosition.relative(facing, DerrickPlan.GAP + size / 2);
        return new int[] {centre.getX() - size / 2, centre.getZ() - size / 2, centre.getX() + size / 2, centre.getZ() + size / 2};
    }

    /** Côté réellement foré : celui demandé, dans la limite de la tour. */
    public int effectiveSide() {
        int max = Math.max(3, DerrickPlan.maxSide(levels));
        return Math.clamp(side, 3, max) | 1;
    }

    /** Fond de la zone : la profondeur que permet la tour, ou le fond du monde. */
    private int bottom(ServerLevel level) {
        int depth = DerrickPlan.depth(levels);
        int floor = level.getMinY() + 1;
        return depth == 0 ? floor : Math.max(floor, worldPosition.getY() - depth);
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
            checkStructure();
        }
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
            working = dig(level);
        }
        if (state.getValue(BlockStateProperties.LIT) != working) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, working), Block.UPDATE_ALL);
        }
        if (ticks % 10 == 0) {
            pushOutput(level, pos);
        }
    }

    /** Relève la tour : son socle, ses étages. */
    public void checkStructure() {
        if (level == null) {
            return;
        }
        Direction facing = facing();
        int nowMissing = DerrickPlan.missing(level, worldPosition, facing).size();
        int nowLevels = DerrickPlan.levels(level, worldPosition, facing);
        boolean nowFormed = nowMissing == 0 && nowLevels >= DerrickPlan.MIN_LEVELS;
        if (nowFormed != formed || nowMissing != missing || nowLevels != levels) {
            formed = nowFormed;
            missing = nowMissing;
            levels = nowLevels;
            if (!formed) {
                running = false;
            }
            sync();
        }
        applyLooks(level, worldPosition, facing, formed ? levels : -1);
    }

    /**
     * Donne à la tour son aspect : assemblé pour le socle et les {@code levels} premiers étages (l'étage du haut en
     * couronne), à bâtir pour le reste ; {@code levels} négatif rend à toute la tour son aspect à bâtir.
     */
    static void applyLooks(Level level, BlockPos origin, Direction facing, int levels) {
        for (BlockPos cell : DerrickPlan.base(facing)) {
            look(level, origin.offset(cell), ModBlocks.DRILL_BASE.get(), levels >= 0 ? DerrickPlan.role(facing, cell) : null, false);
        }
        for (int y = 1; y <= DerrickPlan.MAX_LEVELS; y++) {
            for (BlockPos cell : DerrickPlan.layer(facing)) {
                look(level, origin.offset(cell).above(y), ModBlocks.DRILL_MAST.get(), y <= levels ? DerrickPlan.role(facing, cell) : null,
                        y == levels);
            }
        }
    }

    private static void look(Level level, BlockPos pos, Block block, DerrickPlan.Role role, boolean top) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(block)) {
            return;
        }
        BlockState wanted = state.setValue(DrillTowerBlock.PART, role == null ? DrillTowerBlock.Part.LOOSE : role.part())
                .setValue(DrillTowerBlock.TOP, role != null && top)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, role == null ? Direction.NORTH : role.facing());
        if (wanted != state) {
            level.setBlock(pos, wanted, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Un bloc de tour posé ou cassé près d'une foreuse : elle relève sa tour à l'instant. */
    public static void notifyTowerChanged(ServerLevel level, BlockPos pos) {
        for (int dy = -DerrickPlan.MAX_LEVELS; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (level.getBlockEntity(pos.offset(dx, dy, dz)) instanceof DerrickBlockEntity derrick) {
                        derrick.checkStructure();
                    }
                }
            }
        }
    }

    /** La foreuse est cassée : sa tour reprend son aspect à bâtir. */
    public static void releaseTower(Level level, BlockPos origin, Direction facing) {
        applyLooks(level, origin, facing, -1);
    }

    /** Un tick de forage. Renvoie vrai si la tête a travaillé. */
    private boolean dig(ServerLevel level) {
        if (!energy.consume(RUN_DRAW) || isFull()) {
            return false;
        }
        int[] area = area();
        int width = area[2] - area[0] + 1;
        int cells = width * (area[3] - area[1] + 1);
        if (layer == Integer.MIN_VALUE) {
            layer = worldPosition.getY() - 1;
            cursor = 0;
            bore(level, area);
        }
        if (cooldown > 0) {
            cooldown--;
            return true;
        }
        Direction facing = facing();
        for (int scanned = 0; scanned < SCAN_BUDGET; scanned++) {
            if (cursor >= cells) {
                layer--;
                cursor = 0;
                if (layer < bottom(level)) {
                    running = false;
                    finished = true;
                    layer = bottom(level);
                    sync();
                    return false;
                }
                bore(level, area);
                sync();
                return true;
            }
            int x = area[0] + cursor % width;
            int z = area[1] + cursor / width;
            BlockPos target = new BlockPos(x, layer, z);
            if (DerrickPlan.inFootprint(worldPosition, facing, x, z)) {
                cursor++;
                continue;
            }
            BlockState state = level.getBlockState(target);
            int kind = QuarryKinds.of(state);
            if (!wanted(level, target, state, kind)) {
                cursor++;
                continue;
            }
            if (energy.getEnergy() < BLOCK_ENERGY + (replace ? REPLACE_ENERGY : 0)) {
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

    /** Vrai si ce bloc est à extraire, selon le mode. */
    private boolean wanted(ServerLevel level, BlockPos target, BlockState state, int kind) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, target) < 0) {
            return false;
        }
        if (mode == MODE_ALL) {
            // Un coffre, une machine : on ne les casse pas, leur contenu serait perdu.
            return !state.hasBlockEntity() && !state.is(ModBlocks.DRILL_BASE.get()) && !state.is(ModBlocks.DRILL_MAST.get());
        }
        return kind >= 0 && (filter & (1 << kind)) != 0;
    }

    /** En mode « Filons », perce le puits au milieu de la zone : ce qu'il traverse n'est pas gardé, sauf un minerai voulu. */
    private void bore(ServerLevel level, int[] area) {
        if (mode != MODE_ORES) {
            return;
        }
        BlockPos shaft = new BlockPos((area[0] + area[2]) / 2, layer, (area[1] + area[3]) / 2);
        BlockState state = level.getBlockState(shaft);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, shaft) < 0 || state.hasBlockEntity()) {
            return;
        }
        int kind = QuarryKinds.of(state);
        if (kind >= 0 && (filter & (1 << kind)) != 0) {
            boolean wasReplacing = replace;
            replace = false;
            extract(level, shaft, state, kind);
            replace = wasReplacing;
            return;
        }
        if (energy.consume(BLOCK_ENERGY)) {
            level.levelEvent(2001, shaft, Block.getId(state));
            level.setBlock(shaft, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Extrait un bloc : son butin dans la réserve, son remplaçant ou l'air à sa place, et la tête qui chauffe. */
    private boolean extract(ServerLevel level, BlockPos target, BlockState state, int kind) {
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), null, new ItemStack(Items.NETHERITE_PICKAXE));
        if (!fits(drops)) {
            return false;
        }
        drops.forEach(this::store);
        energy.consume(BLOCK_ENERGY);
        level.levelEvent(2001, target, Block.getId(state));
        BlockState after = Blocks.AIR.defaultBlockState();
        if (replace) {
            energy.consume(REPLACE_ENERGY);
            after = replacementFor(level, state, kind);
        }
        level.setBlock(target, after, Block.UPDATE_ALL);
        mined++;
        heat = Math.min(HEAT_MAX, heat + HEAT_PER_BLOCK);
        if (heat >= HEAT_MAX) {
            overheated = true;
        }
        cooldown = heat < 500 ? 2 : heat < 800 ? 4 : 10;
        setChanged();
        return true;
    }

    /**
     * Le bloc remis à la place d'un bloc extrait : celui de la case de remplacement, qu'on regarnit avec ce que la réserve
     * en contient ; la case vide, la roche du monde.
     */
    private BlockState replacementFor(ServerLevel level, BlockState mined, int kind) {
        ItemStack held = replacement.getStack(0);
        if (!held.isEmpty() && held.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            held.shrink(1);
            if (held.isEmpty()) {
                // La réserve regarnit la case, si elle en a : de la pierre extraite pour remettre de la pierre.
                for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
                    ItemStack stored = output.getStack(slot);
                    if (stored.is(blockItem)) {
                        held = stored.copy();
                        output.setStack(slot, ItemStack.EMPTY);
                        break;
                    }
                }
            }
            replacement.setStack(0, held);
            return block.defaultBlockState();
        }
        return filler(level, mined, kind);
    }

    /** La roche du monde : celle de la Lune, de Mars, de Titan ou d'Europe, sinon la pierre, l'ardoise ou la netherrack. */
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
        if (state.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK) || state.is(Blocks.NETHERRACK)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        if (state.is(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE) || state.is(Blocks.DEEPSLATE)) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private boolean isFull() {
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            if (output.getStack(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Vrai si tout ce butin trouve sa place dans la réserve. */
    private boolean fits(List<ItemStack> drops) {
        int free = 0;
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            if (output.getStack(slot).isEmpty()) {
                free++;
            }
        }
        return free >= drops.size();
    }

    private void store(ItemStack stack) {
        for (int slot = 0; slot < OUTPUT_SLOTS && !stack.isEmpty(); slot++) {
            ItemStack held = output.getStack(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(moved);
                stack.shrink(moved);
                output.setStack(slot, held);
            }
        }
        for (int slot = 0; slot < OUTPUT_SLOTS && !stack.isEmpty(); slot++) {
            if (output.getStack(slot).isEmpty()) {
                output.setStack(slot, stack.copy());
                stack.setCount(0);
            }
        }
    }

    /** Le butin part dans les coffres et machines qui touchent le bloc de commande ou le socle. */
    private void pushOutput(ServerLevel level, BlockPos pos) {
        List<BlockPos> sources = new ArrayList<>();
        sources.add(pos);
        for (BlockPos cell : DerrickPlan.base(facing())) {
            sources.add(pos.offset(cell));
        }
        for (BlockPos source : sources) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = source.relative(direction);
                BlockState state = level.getBlockState(neighbour);
                if (neighbour.equals(pos) || state.is(ModBlocks.DRILL_BASE.get()) || state.is(ModBlocks.DRILL_MAST.get())) {
                    continue;
                }
                ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, neighbour, direction.getOpposite());
                if (target != null) {
                    ResourceHandlerUtil.move(output.automationView(), target, resource -> true, 64, null);
                }
            }
        }
    }

    /** La foreuse dont ce bloc de socle fait partie, ou {@code null}. */
    public static DerrickBlockEntity ownerOfBase(net.minecraft.world.level.Level level, BlockPos basePos) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (BlockPos cell : DerrickPlan.base(facing)) {
                BlockPos origin = basePos.subtract(cell);
                if (level.getBlockEntity(origin) instanceof DerrickBlockEntity derrick && derrick.facing() == facing) {
                    return derrick;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Carottage et relevé satellite
    // ------------------------------------------------------------------

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
     * Fin du carottage : les sortes présentes dans chaque couche de seize blocs, sous toute la zone. Le premier filon de
     * chaque sorte trouvé dans ce monde rapporte des points de recherche à l'agence de celui qui l'a demandé.
     */
    private void finishCore(ServerLevel level) {
        coreUntil = 0;
        int top = worldPosition.getY() - 1;
        int bands = Math.max(1, (top - bottom(level)) / BAND + 1);
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

    /** Parcourt toutes les sortes reconnues sous la zone, de la surface au fond foré. */
    private void scan(ServerLevel level, Visitor visitor) {
        int[] area = area();
        int top = worldPosition.getY() - 1;
        int floor = bottom(level);
        for (int cx = area[0] >> 4; cx <= area[2] >> 4; cx++) {
            for (int cz = area[1] >> 4; cz <= area[3] >> 4; cz++) {
                LevelChunk chunkAt = level.getChunkSource().getChunkNow(cx, cz);
                if (chunkAt == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunkAt.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int baseY = chunkAt.getSectionYFromSectionIndex(index) << 4;
                    // La plupart des tranches n'ont rien à extraire : leur palette le dit sans les parcourir.
                    if (baseY > top || baseY + 15 < floor || section.hasOnlyAir() || !section.maybeHas(state -> QuarryKinds.of(state) >= 0)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        int worldY = baseY + y;
                        if (worldY > top || worldY < floor) {
                            continue;
                        }
                        for (int z = 0; z < 16; z++) {
                            int worldZ = (cz << 4) + z;
                            if (worldZ < area[1] || worldZ > area[3]) {
                                continue;
                            }
                            for (int x = 0; x < 16; x++) {
                                int worldX = (cx << 4) + x;
                                if (worldX < area[0] || worldX > area[2]) {
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

    /** Refait le relevé satellite s'il est trop ancien, ou si la zone a changé. */
    private void survey(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        int[] area = area();
        String key = area[0] + "," + area[1] + "," + area[2] + "," + area[3] + "," + levels;
        if (key.equals(surveyKey) && now - surveyAt < SURVEY_AGE) {
            return;
        }
        surveyObservers = SatelliteManager.of(level.getServer(), ResearchManager.agencyKey(player))
                .active(SatelliteType.OBSERVATION, SatelliteManager.now(level.getServer()), SatelliteManager.limited());
        surveyAt = now;
        surveyKey = key;
        if (surveyObservers <= 0 || !formed) {
            surveyCells = new int[0];
            surveyCounts = new int[0];
            return;
        }
        int width = area[2] - area[0] + 1;
        int depth = area[3] - area[1] + 1;
        int[][] perColumn = new int[width * depth][QuarryKinds.COUNT];
        int[] counts = new int[QuarryKinds.COUNT];
        scan(level, (x, y, z, kind) -> {
            perColumn[(z - area[1]) * width + (x - area[0])][kind]++;
            counts[kind]++;
        });
        int[] cells = new int[width * depth];
        for (int cell = 0; cell < cells.length; cell++) {
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
            cells[cell] = best + 1;
        }
        surveyCells = cells;
        surveyCounts = counts;
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    public static final int ACTION_REFRESH = 0;
    public static final int ACTION_RUN = 1;
    public static final int ACTION_SIDE = 2;
    public static final int ACTION_REPLACE = 3;
    public static final int ACTION_FILTER = 4;
    public static final int ACTION_CORE = 5;
    public static final int ACTION_MODE = 6;
    public static final int ACTION_CHUNK = 7;

    /** Valeurs synchronisées vers l'écran à chaque tick : énergie (en deux moitiés), capacité, marche, eau, chaleur, état. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergy() & 0xFFFF;
                case 1 -> energy.getEnergy() >>> 16;
                case 2 -> ENERGY_CAPACITY & 0xFFFF;
                case 3 -> ENERGY_CAPACITY >>> 16;
                case 4 -> getBlockState().getValue(BlockStateProperties.LIT) ? 1 : 0;
                case 5 -> coolant.getFluid(0).getAmount();
                case 6 -> heat;
                case 7 -> status().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DerrickMenu.DATA_COUNT;
        }
    };

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DerrickMenu(containerId, playerInventory, this, output, replacement, data);
    }

    /** Ouvre l'écran du joueur, puis lui envoie l'état complet (carte, carottage, réglages). */
    public void open(ServerPlayer player) {
        checkStructure();
        player.openMenu(this, (RegistryFriendlyByteBuf buffer) -> buffer.writeBlockPos(worldPosition));
        ModNetwork.send(player, status(player));
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
                        finished = false;
                        layer = Integer.MIN_VALUE;
                    }
                }
            }
            case ACTION_SIDE -> {
                int max = Math.max(3, DerrickPlan.maxSide(levels));
                side = Math.clamp(effectiveSide() + 2 * Integer.signum(argument), 3, max);
                chunk = false;
                cursor = 0;
            }
            case ACTION_CHUNK -> {
                if (DerrickPlan.maxSide(levels) > CHUNK) {
                    chunk = !chunk;
                    cursor = 0;
                } else {
                    refused = Component.translatable("derrick.orbitalis.chunk.too_small");
                }
            }
            case ACTION_MODE -> {
                mode = mode == MODE_ORES ? MODE_ALL : MODE_ORES;
                cursor = 0;
            }
            case ACTION_REPLACE -> replace = !replace;
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
        ModNetwork.send(player, status(player));
    }

    private DerrickStatusPayload status(ServerPlayer player) {
        ServerLevel serverLevel = (ServerLevel) level;
        survey(serverLevel, player);
        int stored = 0;
        for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
            stored += output.getStack(slot).getCount();
        }
        long now = serverLevel.getGameTime();
        int core = coreUntil <= 0 ? -1 : (int) Math.clamp(100 - (coreUntil - now) * 100 / CORE_TICKS, 0, 100);
        int[] area = area();
        boolean chunked = chunk && DerrickPlan.maxSide(levels) > CHUNK;
        return new DerrickStatusPayload(worldPosition, status().ordinal(), missing, running, mode, chunked, levels,
                chunked ? CHUNK : effectiveSide(), DerrickPlan.maxSide(levels), DerrickPlan.depth(levels), replace, filter, boreBottom(),
                worldPosition.getY() - 1, bottom(serverLevel), mined, stored, core, coreBands, surveyObservers,
                surveyCells.length == 0 ? 0 : area[2] - area[0] + 1, surveyCells, surveyCounts);
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

    /** Cassée, la foreuse garde son énergie, son eau, son butin et son bloc de remplacement sur son objet. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
        coolant.storeIn(components);
        List<ItemStack> all = new ArrayList<>(output.copyToList());
        all.add(replacement.getStack(0).copy());
        if (all.stream().anyMatch(stack -> !stack.isEmpty())) {
            components.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(all));
        }
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components);
        coolant.restoreFrom(components);
        var contents = components.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents != null) {
            net.minecraft.core.NonNullList<ItemStack> stacks = net.minecraft.core.NonNullList.withSize(OUTPUT_SLOTS + 1, ItemStack.EMPTY);
            contents.copyInto(stacks);
            for (int slot = 0; slot < OUTPUT_SLOTS; slot++) {
                output.setStack(slot, stacks.get(slot));
            }
            replacement.setStack(0, stacks.get(OUTPUT_SLOTS));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
        coolant.serialize(out.child("coolant"));
        output.serialize(out.child("output"));
        replacement.serialize(out.child("replacement"));
        out.putBoolean("formed", formed);
        out.putInt("levels", levels);
        out.putBoolean("running", running);
        out.putInt("mode", mode);
        out.putBoolean("replace", replace);
        out.putBoolean("chunk", chunk);
        out.putInt("filter", filter);
        out.putInt("side", side);
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
        replacement.deserialize(in.childOrEmpty("replacement"));
        formed = in.getBooleanOr("formed", false);
        levels = in.getIntOr("levels", 0);
        running = in.getBooleanOr("running", false);
        mode = in.getIntOr("mode", MODE_ORES);
        replace = in.getBooleanOr("replace", false);
        chunk = in.getBooleanOr("chunk", false);
        filter = in.getIntOr("filter", QuarryKinds.ALL_ORES);
        side = in.getIntOr("side", 9);
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

    // ------------------------------------------------------------------
    // Pour les tests et les bâtisseurs
    // ------------------------------------------------------------------

    public void setRunning(boolean running) {
        this.running = running && formed;
    }

    public void setReplace(boolean replace) {
        this.replace = replace;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setSide(int side) {
        this.side = side;
        this.chunk = false;
    }

    public void setChunk(boolean chunk) {
        this.chunk = chunk;
    }

    public int[] coreBands() {
        return coreBands;
    }

    /** Lance un carottage et le termine aussitôt. */
    public void coreNow(ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel && startCore(serverLevel, player) == null) {
            finishCore(serverLevel);
        }
    }
}
