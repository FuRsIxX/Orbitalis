package com.orbitalis.core.world;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.core.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Un centre spatial, posé sur la surface du monde : plusieurs bâtiments autour d'une esplanade, reliés par des
 * allées, dans une enceinte grillagée. Il est bâti normal ou abandonné (un sur trois environ : béton fissuré, bardage
 * rouillé, vitres brisées, toiles d'araignée, coffres pillés), en petit ou en grand.
 * <p>
 * Le terrain s'aplanit autour de chaque bâtiment ; un centre ne se pose que sur un sol à peu près plat et sec.
 */
public class SpaceFacilityStructure extends Structure {
    public static final MapCodec<SpaceFacilityStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(settingsCodec(instance),
            FacilityKind.CODEC.fieldOf("kind").forGetter(structure -> structure.kind)).apply(instance, SpaceFacilityStructure::new));

    /** Part des centres abandonnés, et des grands centres. */
    private static final float ABANDONED = 0.35F;
    private static final float LARGE = 0.45F;

    /** Pour la carte de test : impose la taille et l'état du prochain centre posé ({@code null} : au hasard). */
    public static Boolean forcedLarge;
    public static Boolean forcedAbandoned;

    private final FacilityKind kind;

    public SpaceFacilityStructure(StructureSettings settings, FacilityKind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        WorldgenRandom random = context.random();
        boolean rolledLarge = random.nextFloat() < LARGE;
        boolean rolledAbandoned = random.nextFloat() < ABANDONED;
        boolean large = kind == FacilityKind.CONTROL_CENTER || (forcedLarge != null ? forcedLarge : rolledLarge);
        boolean abandoned = forcedAbandoned != null ? forcedAbandoned : rolledAbandoned;
        int turns = random.nextInt(4);
        long seed = random.nextLong();
        List<FacilityLayout.Placement> plan = FacilityLayout.plan(kind, large, random);
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        // Le sol, au centre de chaque bâtiment et aux coins de son emprise : à peu près plat, et pas sous l'eau.
        List<int[]> samples = FacilityLayout.samplePoints(plan, cx, cz, turns);
        int[] heights = new int[samples.size()];
        for (int k = 0; k < samples.size(); k++) {
            int x = samples.get(k)[0];
            int z = samples.get(k)[1];
            int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                    context.randomState());
            int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                    context.randomState());
            if (surface > floor) {
                return Optional.empty();
            }
            heights[k] = surface;
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        int tolerance = kind == FacilityKind.CONTROL_CENTER ? 12 : 9;
        if (sorted[sorted.length - 1] - sorted[0] > tolerance || sorted[0] <= context.chunkGenerator().getSeaLevel()) {
            return Optional.empty();
        }
        // Le niveau du sol fini : la médiane, pour que le terrain soit un peu creusé ici, un peu remblayé là.
        int ground = sorted[sorted.length / 2];
        return Optional.of(new GenerationStub(new BlockPos(cx, ground, cz),
                builder -> FacilityLayout.build(builder, kind, plan, cx, ground, cz, turns, abandoned, seed)));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SPACE_FACILITY.get();
    }
}
