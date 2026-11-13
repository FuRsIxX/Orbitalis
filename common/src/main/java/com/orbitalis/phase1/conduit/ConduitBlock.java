package com.orbitalis.phase1.conduit;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.orbitalis.core.registry.ModFluids;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.orbitalis.platform.capability.BlockCapability;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.ResourceHandlerUtil;
import com.orbitalis.platform.transfer.fluid.FluidResource;

/**
 * Conduit (câble d'énergie, tuyau de fluide ou câble de données) : un bloc fin qui se raccorde à ses voisins.
 * <p>
 * Il se raccorde aux conduits du même type (pour les tuyaux, s'ils n'ont pas deux couleurs différentes, voir
 * {@link PipeColor}) et à tout bloc qui expose ce qu'il transporte. De chaque côté, il
 * retient à quoi il est raccordé ({@link Link}) : vers un autre conduit, le tube continue tel quel ; vers une
 * machine, il se termine par un raccord. Un conduit qui ne fait que passer tout droit n'a pas de nœud en son milieu.
 * <p>
 * Le conduit ne stocke rien : le transport est assuré par {@link ConduitNetwork}.
 */
public class ConduitBlock extends Block {
    /** Ce à quoi un côté du conduit est raccordé. */
    public enum Link implements StringRepresentable {
        NONE,
        /** Un conduit du même type. */
        CONDUIT,
        /** Une machine, un réservoir, un écran : le tube se termine par un raccord. */
        MACHINE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Link> NORTH = EnumProperty.create("north", Link.class);
    public static final EnumProperty<Link> EAST = EnumProperty.create("east", Link.class);
    public static final EnumProperty<Link> SOUTH = EnumProperty.create("south", Link.class);
    public static final EnumProperty<Link> WEST = EnumProperty.create("west", Link.class);
    public static final EnumProperty<Link> UP = EnumProperty.create("up", Link.class);
    public static final EnumProperty<Link> DOWN = EnumProperty.create("down", Link.class);
    /** Raccordement de chaque côté. */
    public static final Map<Direction, EnumProperty<Link>> LINKS = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
            Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));

    /** Lueur la plus vive d'un câble en service. */
    public static final int MAX_GLOW = 3;
    /** Intensité de la lueur d'un câble d'énergie : 0 au repos, jusqu'à {@link #MAX_GLOW} quand le courant passe. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, MAX_GLOW);
    /** Plus grand numéro de contenu d'un tuyau. */
    public static final int MAX_CONTENT = 4;
    /**
     * Ce qui circule dans un tuyau : 0 s'il est vide, sinon le numéro du liquide ou du gaz (voir {@link #contentOf}).
     * Le tuyau en prend la couleur.
     */
    public static final IntegerProperty CONTENT = IntegerProperty.create("content", 0, MAX_CONTENT);
    /**
     * Tuyau en extraction : il aspire le contenu des machines et des réservoirs auxquels il est raccordé, et
     * l'envoie dans le réseau. Rien ne leur est livré par ce tuyau.
     */
    public static final BooleanProperty PULL = BooleanProperty.create("pull");

    /** Ce que transporte un conduit. */
    public enum Kind {
        ENERGY,
        /** Liquides : eau, RP-1, oxygène et hydrogène liquides, et les liquides des autres mods. */
        LIQUID,
        /** Gaz : hydrogène et oxygène gazeux. */
        GAS,
        /** Données : relie les écrans de contrôle aux ordinateurs et aux réservoirs qu'ils affichent. */
        DATA;

        public boolean isFluid() {
            return this == LIQUID || this == GAS;
        }
    }

    private final BlockCapability<?, Direction> capability;
    private final Kind kind;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    /**
     * @param capability capacité transportée : les blocs voisins qui l'exposent sont raccordés
     */
    public ConduitBlock(BlockBehaviour.Properties properties, BlockCapability<?, Direction> capability, Kind kind) {
        super(properties);
        this.capability = capability;
        this.kind = kind;

        BlockState state = stateDefinition.any();
        for (EnumProperty<Link> property : LINKS.values()) {
            state = state.setValue(property, Link.NONE);
        }
        registerDefaultState(withDefaults(state));

        // Forme de chaque état : un cœur central, plus un bras par raccordement. Câbles et tuyaux ont la même carrure.
        double lo = 5.0;
        Map<Integer, VoxelShape> byLinks = new HashMap<>();
        for (BlockState possible : stateDefinition.getPossibleStates()) {
            int mask = 0;
            for (Direction direction : Direction.values()) {
                if (possible.getValue(LINKS.get(direction)) != Link.NONE) {
                    mask |= 1 << direction.ordinal();
                }
            }
            shapes.put(possible, byLinks.computeIfAbsent(mask, key -> createShape(key, lo, 16 - lo)));
        }
    }

    /** Valeurs de départ des propriétés propres à un type de conduit. */
    protected BlockState withDefaults(BlockState state) {
        return state;
    }

    private static VoxelShape createShape(int mask, double lo, double hi) {
        VoxelShape shape = Block.box(lo, lo, lo, hi, hi, hi);
        for (Direction direction : Direction.values()) {
            if ((mask & 1 << direction.ordinal()) != 0) {
                shape = Shapes.or(shape, switch (direction) {
                    case NORTH -> Block.box(lo, lo, 0, hi, hi, lo);
                    case SOUTH -> Block.box(lo, lo, hi, hi, hi, 16);
                    case WEST -> Block.box(0, lo, lo, lo, hi, hi);
                    case EAST -> Block.box(hi, lo, lo, 16, hi, hi);
                    case DOWN -> Block.box(lo, 0, lo, hi, lo, hi);
                    case UP -> Block.box(lo, hi, lo, hi, 16, hi);
                });
            }
        }
        return shape;
    }

    public BlockCapability<?, Direction> getCapability() {
        return capability;
    }

    public Kind kind() {
        return kind;
    }

    /** Vrai si ce conduit transporte ce fluide : les gaz dans les tuyaux à gaz, tout le reste dans les tuyaux à liquides. */
    public boolean accepts(Fluid fluid) {
        boolean gas = fluid == ModFluids.HYDROGEN.get() || fluid == ModFluids.OXYGEN.get();
        return kind == Kind.GAS ? gas : kind == Kind.LIQUID && !gas;
    }

    /** Numéro de contenu de ce fluide dans ce conduit : il choisit la couleur du tuyau quand le fluide y circule. */
    public int contentOf(Fluid fluid) {
        if (kind == Kind.GAS) {
            return fluid == ModFluids.OXYGEN.get() ? 2 : 1;
        }
        if (kind != Kind.LIQUID) {
            return 0;
        }
        if (fluid == ModFluids.RP1.get()) {
            return 2;
        }
        if (fluid == ModFluids.LIQUID_OXYGEN.get()) {
            return 3;
        }
        return fluid == ModFluids.LIQUID_HYDROGEN.get() ? 4 : 1;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(LINKS.get(direction),
                    linkTo(context.getLevel(), context.getClickedPos(), context.getClickedPos().relative(direction), direction));
        }
        return state;
    }

    /** Un voisin a changé : le raccordement de ce côté est recalculé. */
    @Override
    protected BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        net.minecraft.world.level.LevelAccessor ticks = level;
        RandomSource random = level.getRandom();
        if (level instanceof Level realLevel) {
            EnumProperty<Link> property = LINKS.get(directionToNeighbour);
            Link link = linkTo(realLevel, pos, neighbourPos, directionToNeighbour);
            if (link != state.getValue(property)) {
                ConduitNetwork.invalidate(realLevel);
                return state.setValue(property, link);
            }
        }
        return state;
    }

    /** Ce à quoi le conduit se raccorde de ce côté : un conduit du même type, un bloc qui expose ce qu'il transporte, ou rien. */
    private Link linkTo(Level level, BlockPos pos, BlockPos neighbourPos, Direction directionToNeighbour) {
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.is(this)) {
            return joins(level, pos, neighbourPos) ? Link.CONDUIT : Link.NONE;
        }
        // Un conduit d'un autre type n'est pas un raccord : un tuyau à gaz ne se branche pas sur un tuyau à liquides.
        if (neighbour.getBlock() instanceof ConduitBlock) {
            return Link.NONE;
        }
        return com.orbitalis.platform.capability.Caps.get(level, capability, neighbourPos, directionToNeighbour.getOpposite()) != null ? Link.MACHINE : Link.NONE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Un simple changement de lueur ou de raccordement ne compte pas : seul un conduit qui vient d'être posé
        // change la forme du réseau et rend les recherches mémorisées caduques.
        if (!oldState.is(this)) {
            ConduitNetwork.invalidate(level);
            com.orbitalis.platform.capability.Caps.invalidate(level, pos);
            // Posé autrement que par un joueur (commande, structure), le conduit n'a pas encore regardé ses voisins :
            // il le fera au tick suivant. Pas tout de suite : changer d'état pendant la pose couperait court aux
            // mises à jour des voisins.
            if (withLinks(state, level, pos) != state) {
                level.scheduleTick(pos, this, 1);
            }
        }
    }

    /** Le même état, avec les raccordements que ses voisins lui donnent. */
    private BlockState withLinks(BlockState state, Level level, BlockPos pos) {
        BlockState linked = state;
        for (Direction direction : Direction.values()) {
            linked = linked.setValue(LINKS.get(direction), linkTo(level, pos, pos.relative(direction), direction));
        }
        return linked;
    }

    /** Vrai si deux conduits voisins du même type se raccordent : pour des tuyaux, leurs couleurs doivent s'accorder. */
    public static boolean joins(BlockGetter level, BlockPos a, BlockPos b) {
        return PipeBlockEntity.colorAt(level, a).joins(PipeBlockEntity.colorAt(level, b));
    }

    /** Recalcule les raccordements de ce conduit et de ses voisins du même type, après un changement de couleur. */
    public void refreshLinks(Level level, BlockPos pos) {
        for (BlockPos at : List.of(pos, pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below())) {
            BlockState state = level.getBlockState(at);
            if (state.is(this)) {
                BlockState linked = withLinks(state, level, at);
                if (linked != state) {
                    level.setBlock(at, linked, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        ConduitNetwork.invalidate(level);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState linked = withLinks(state, level, pos);
        if (linked != state) {
            level.setBlock(pos, linked, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ConduitNetwork.invalidate(level);
        }
    }

    /** Un conduit allumé est vérifié de temps en temps : il s'éteint s'il est resté allumé sans raison. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.hasProperty(GLOW) && state.getValue(GLOW) > 0 || state.hasProperty(CONTENT) && state.getValue(CONTENT) > 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ConduitNetwork.watch(level, pos, this);
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level$, BlockPos pos, BlockState newState$, boolean movedByPiston) {
        super.onRemove(state, level$, pos, newState$, movedByPiston);
        if (state.is(newState$.getBlock()) || !(level$ instanceof ServerLevel level)) {
            return;
        }
        ConduitNetwork.invalidate(level);
        com.orbitalis.platform.capability.Caps.invalidate(level, pos);
    }

    /** Câble d'énergie : il s'illumine progressivement quand le courant passe. */
    public static class Cable extends ConduitBlock {
        public Cable(BlockBehaviour.Properties properties, BlockCapability<?, Direction> capability, Kind kind) {
            super(properties, capability, kind);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(GLOW);
        }

        @Override
        protected BlockState withDefaults(BlockState state) {
            return state.setValue(GLOW, 0);
        }
    }

    /**
     * Tuyau à liquides ou à gaz : il prend la couleur de ce qui y circule.
     * <p>
     * Accroupi, un clic droit à main nue le fait passer en extraction : il aspire alors, deux fois par seconde, le
     * contenu des machines et des réservoirs qu'il touche, et le livre au reste du réseau. C'est ce qui vide un
     * réservoir, qui ne pousse rien de lui-même.
     * <p>
     * Debout, un clic droit à main nue change sa couleur (voir {@link PipeColor}). Posé contre un port de pas de tir,
     * il prend d'office la couleur de son ergol ; posé sur un tuyau coloré, il en prolonge la couleur.
     */
    public static class Pipe extends ConduitBlock implements net.minecraft.world.level.block.EntityBlock {
        /** Couleur choisie pendant la pose, appliquée une fois l'entité du tuyau créée (voir {@link #setPlacedBy}). */
        private static final ThreadLocal<Map.Entry<BlockPos, PipeColor>> PLACING = new ThreadLocal<>();

        /** Intervalle entre deux aspirations, en ticks, et quantité aspirée chaque fois par raccord, en mB. */
        public static final int PULL_INTERVAL = 10;
        public static final int PULL_AMOUNT = 1_000;

        public Pipe(BlockBehaviour.Properties properties, BlockCapability<?, Direction> capability, Kind kind) {
            super(properties, capability, kind);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(CONTENT, PULL);
        }

        @Override
        protected BlockState withDefaults(BlockState state) {
            return state.setValue(CONTENT, 0).setValue(PULL, false);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new PipeBlockEntity(pos, state);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            PLACING.set(Map.entry(context.getClickedPos().immutable(), placementColor(context)));
            return super.getStateForPlacement(context);
        }

        /**
         * Couleur d'un tuyau qu'on pose : celle du tuyau sur lequel on l'a posé, ou celle de l'ergol du port de pas de
         * tir qu'il touche (s'il n'en touche qu'une sorte), sinon aucune.
         */
        private PipeColor placementColor(BlockPlaceContext context) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos();
            BlockPos clicked = pos.relative(context.getClickedFace().getOpposite());
            if (level.getBlockState(clicked).is(this)) {
                PipeColor color = PipeBlockEntity.colorAt(level, clicked);
                if (color != PipeColor.NONE) {
                    return color;
                }
            }
            PipeColor found = portColor(level, clicked);
            if (found != PipeColor.NONE) {
                return found;
            }
            for (Direction direction : Direction.values()) {
                PipeColor color = portColor(level, pos.relative(direction));
                if (color != PipeColor.NONE) {
                    if (found != PipeColor.NONE && found != color) {
                        return PipeColor.NONE;
                    }
                    found = color;
                }
            }
            return found;
        }

        private static PipeColor portColor(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.getBlock() instanceof com.orbitalis.phase1.spaceport.FuelPortBlock
                    ? PipeColor.forPort(state.getValue(com.orbitalis.phase1.spaceport.FuelPortBlock.KIND)) : PipeColor.NONE;
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer,
                net.minecraft.world.item.ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            Map.Entry<BlockPos, PipeColor> placing = PLACING.get();
            PLACING.remove();
            if (!level.isClientSide() && placing != null && placing.getKey().equals(pos) && placing.getValue() != PipeColor.NONE
                    && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.setColor(placing.getValue());
                refreshLinks(level, pos);
            }
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
            if (!player.isShiftKeyDown()) {
                // Debout, main vide : la couleur suivante. Un objet en main se pose normalement.
                if (!player.getMainHandItem().isEmpty()) {
                    return InteractionResult.PASS;
                }
                if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                        && level.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                    PipeColor color = pipe.color().next();
                    pipe.setColor(color);
                    refreshLinks(level, pos);
                    serverPlayer.displayClientMessage(Component.translatable("conduit.orbitalis.color", color.displayName()), true);
                }
                return InteractionResult.SUCCESS;
            }
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                boolean pull = !state.getValue(PULL);
                level.setBlock(pos, state.setValue(PULL, pull), Block.UPDATE_CLIENTS);
                ConduitNetwork.invalidate(level);
                serverPlayer.displayClientMessage(Component.translatable(pull ? "conduit.orbitalis.pull.on" : "conduit.orbitalis.pull.off")
                        .withStyle(pull ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            super.onPlace(state, level, pos, oldState, movedByPiston);
            // Un tuyau qui passe en extraction (ou qu'on pose déjà réglé ainsi) commence à aspirer.
            if (state.getValue(PULL) && !(oldState.is(this) && oldState.getValue(PULL))) {
                level.scheduleTick(pos, this, PULL_INTERVAL);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            super.tick(state, level, pos, random);
            state = level.getBlockState(pos);
            if (!state.is(this) || !state.getValue(PULL)) {
                return;
            }
            pull(level, pos, state);
            level.scheduleTick(pos, this, PULL_INTERVAL);
        }

        /** Aspire le contenu de chaque machine raccordée et le livre aux autres blocs du réseau. */
        private void pull(ServerLevel level, BlockPos pos, BlockState state) {
            List<ConduitNetwork.Endpoint> endpoints = ConduitNetwork.endpoints(level, pos, this, Capabilities.Fluid.BLOCK);
            for (Direction direction : Direction.values()) {
                if (state.getValue(LINKS.get(direction)) != Link.MACHINE) {
                    continue;
                }
                ResourceHandler<FluidResource> source = com.orbitalis.platform.capability.Caps.get(level, Capabilities.Fluid.BLOCK, pos.relative(direction),
                        direction.getOpposite());
                if (source == null) {
                    continue;
                }
                // Ce qui va partir : le premier contenu que ce tuyau transporte.
                Fluid fluid = null;
                for (int index = 0; index < source.size() && fluid == null; index++) {
                    FluidResource resource = source.getResource(index);
                    if (!resource.isEmpty() && source.getAmountAsLong(index) > 0 && accepts(resource.getFluid())) {
                        fluid = resource.getFluid();
                    }
                }
                if (fluid == null) {
                    continue;
                }
                Fluid moved = fluid;
                int remaining = PULL_AMOUNT;
                for (ConduitNetwork.Endpoint endpoint : endpoints) {
                    if (remaining <= 0) {
                        break;
                    }
                    if (isPulling(level, endpoint.conduit())) {
                        continue;
                    }
                    ResourceHandler<FluidResource> target = com.orbitalis.platform.capability.Caps.get(level, Capabilities.Fluid.BLOCK, endpoint.pos(), endpoint.side());
                    // Le même réservoir, touché ailleurs par le réseau : on ne lui rend pas ce qu'on vient de lui prendre.
                    if (target == null || target == source) {
                        continue;
                    }
                    remaining -= ResourceHandlerUtil.move(source, target, resource -> resource.getFluid() == moved, remaining, null);
                }
                if (remaining < PULL_AMOUNT) {
                    ConduitNetwork.markActive(level, pos, this, Capabilities.Fluid.BLOCK, contentOf(fluid));
                }
            }
        }
    }

    /** Vrai si le conduit situé là est un tuyau en extraction : les blocs qu'il touche ne reçoivent rien du réseau. */
    public static boolean isPulling(Level level, BlockPos conduit) {
        BlockState state = level.getBlockState(conduit);
        return state.hasProperty(PULL) && state.getValue(PULL);
    }
}
