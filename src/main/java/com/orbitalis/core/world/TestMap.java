package com.orbitalis.core.world;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase2.station.ModuleType;
import com.orbitalis.phase2.station.StationLayout;
import com.orbitalis.phase2.station.StationManager;
import com.orbitalis.phase2.station.StationModules;
import com.orbitalis.phase2.survival.SpaceRules;
import com.orbitalis.phase2.survival.SpaceSuits;
import com.orbitalis.phase2.survival.SuitModule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Outil de développement : bâtit la carte de test d'Orbitalis, puis arrête le serveur.
 * <p>
 * Inactif en jeu normal. Il ne se déclenche que si le serveur est lancé avec {@code gradlew deployTestMap}, qui crée
 * un monde plat neuf, le fait bâtir ici, puis le copie dans les mondes de l'instance CurseForge sous le nom
 * « Orbitalis - Carte de test ». La carte est refaite à chaque nouvelle version du mod : on y trouve tout ce que le
 * mod ajoute, au même endroit d'une fois sur l'autre.
 * <ul>
 * <li>l'accueil, au point d'apparition : la version, et des panneaux qui téléportent d'un clic vers chaque zone ;</li>
 * <li>la galerie des blocs : chaque bloc du mod sur son socle, son nom sur un panneau ;</li>
 * <li>la réserve : tous les objets du mod, rangés dans des coffres ;</li>
 * <li>les combinaisons, sur des mannequins ;</li>
 * <li>les machines en marche : le réacteur à fusion et ce qu'il alimente, nourris par des réservoirs créatifs ;</li>
 * <li>les fusées : chaque modèle du catalogue deux fois, ses blocs de construction tous posés (pas encore assemblée),
 * puis assemblée comme sur le pas de tir ;</li>
 * <li>le pas de tir d'essai, en service, une fusée-sonde sur sa table et son pupitre à distance de sécurité ;</li>
 * <li>les centres spatiaux : chaque sorte, normale et en grand, puis abandonnée et en petit ;</li>
 * <li>la station complète, en orbite.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class TestMap {
    private static final boolean ENABLED = Boolean.getBoolean("orbitalis.testMap");
    /** Hauteur du sol du monde plat (le dessus de l'herbe est un bloc plus bas). */
    private static final int Y = -60;
    /** Blocs techniques qui ne se posent pas seuls (parties invisibles d'un ensemble, fusée assemblée). */
    private static final Set<String> TECHNICAL = Set.of("workstation_part", "structure_part", "airlock_leaf", "glass_door_leaf", "rover_model", "rotor_drone_model", "rocket", "tower_elevator");
    /** Où sont exposées les fusées : la rangée des fusées en blocs, puis, derrière, celle des fusées assemblées. */
    private static final int ROCKETS_X = 0;
    private static final int ROCKETS_Z = 40;
    /** Écart entre deux fusées d'une rangée, et entre les deux rangées. */
    private static final int ROCKET_SPACING = 12;
    private static final int ROCKET_ROWS = 16;
    /** Où est bâti le pas de tir d'essai (sa table), à l'ouest des fusées. */
    private static final int TEST_PAD_X = -20;
    private static final int TEST_PAD_Z = 40;
    /** Où est bâtie la station en orbite. */
    private static final BlockPos STATION = new BlockPos(0, 100, 0);

    private TestMap() {
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        if (!ENABLED) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        Orbitalis.LOGGER.info("Carte de test : construction…");
        if (server.getWorldData() instanceof PrimaryLevelData data) {
            // Les commandes sont permises : les panneaux de téléportation et les essais en ont besoin.
            data.setAllowCommands(true);
            // Un monde bâti par un serveur, avec les dimensions du mod, passe pour « expérimental » : le jeu demanderait
            // de le confirmer (et proposerait une sauvegarde) à chaque première ouverture. La carte est faite pour ça.
            data.withConfirmedWarning(true);
        }
        run(server, "time set 6000");
        run(server, "weather clear 1000000");
        for (String rule : new String[] {"doDaylightCycle false", "doWeatherCycle false", "doMobSpawning false", "advance_time false",
                "advance_weather false", "spawn_mobs false"}) {
            run(server, "gamerule " + rule);
        }
        run(server, "setworldspawn 0 " + Y + " 0");

        List<String[]> destinations = new ArrayList<>();
        int galleryEnd = gallery(level, 24, -40);
        destinations.add(new String[] {"testmap.orbitalis.zone.blocks", "tp @s 24 " + (Y + 1) + " -44 180 20"});
        storage(level, -6, -14);
        suits(level, -14, -4);
        machines(level, -24, 10);
        destinations.add(new String[] {"testmap.orbitalis.zone.machines", "tp @s -23 " + (Y + 1) + " 15 180 20"});
        rockets(level, ROCKETS_X, ROCKETS_Z);
        destinations.add(new String[] {"testmap.orbitalis.zone.rockets", "tp @s " + (ROCKETS_X - 6) + " " + (Y + 6) + " " + (ROCKETS_Z - 14)
                + " -45 20"});
        testPad(level, TEST_PAD_X, TEST_PAD_Z);
        destinations.add(new String[] {"testmap.orbitalis.zone.test_pad", "tp @s " + TEST_PAD_X + " " + Y + " " + (TEST_PAD_Z + 12) + " 180 10"});
        int[] sites = facilities(server, level, destinations);
        station(server);
        destinations.add(new String[] {"testmap.orbitalis.zone.station", "execute in orbitalis:orbit run tp @s " + STATION.getX() + " "
                + (STATION.getY() + 1) + " " + (STATION.getZ() - 4)});
        destinations.add(new String[] {"testmap.orbitalis.zone.moon", "execute in orbitalis:moon run tp @s 0 140 0"});
        destinations.add(new String[] {"testmap.orbitalis.zone.mars", "execute in orbitalis:mars run tp @s 0 160 0"});
        destinations.add(new String[] {"testmap.orbitalis.zone.titan", "execute in orbitalis:titan run tp @s 0 150 0"});
        destinations.add(new String[] {"testmap.orbitalis.zone.europa", "execute in orbitalis:europa run tp @s 0 80 0"});
        // Le voyage interstellaire n'est sur la carte que s'il est montré (réglage de démarrage).
        if (com.orbitalis.core.Features.interstellar()) {
            destinations.add(new String[] {"testmap.orbitalis.zone.proxima", "execute in orbitalis:proxima_b run tp @s 0 180 0"});
            destinations.add(new String[] {"testmap.orbitalis.zone.proxima_orbit", "execute in orbitalis:proxima_orbit run tp @s 0 100 0"});
        }
        welcome(level, destinations);
        Orbitalis.LOGGER.info("Carte de test : galerie jusqu'à x = {}, {} centres posés", galleryEnd, sites.length);

        server.saveEverything(false, true, true);
        Orbitalis.LOGGER.info("Carte de test : terminée");
        server.halt(false);
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void loadAround(ServerLevel level, int x, int z, int radius) {
        for (int cx = (x >> 4) - radius; cx <= (x >> 4) + radius; cx++) {
            for (int cz = (z >> 4) - radius; cz <= (z >> 4) + radius; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** Un panneau planté, son texte tourné vers le sud ; une commande s'il en a une, lancée d'un clic. */
    private static void sign(ServerLevel level, BlockPos pos, Component title, Component subtitle, String command) {
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            Style style = command == null ? Style.EMPTY : Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command));
            var text = sign.getFrontText()
                    .setMessage(0, title.copy().withStyle(style.withBold(true)))
                    .setMessage(1, subtitle == null ? Component.empty() : subtitle.copy().withStyle(style))
                    .setColor(command == null ? DyeColor.BLACK : DyeColor.BLUE);
            sign.setText(text, true);
            sign.setWaxed(true);
        }
    }

    // ------------------------------------------------------------------
    // Les zones
    // ------------------------------------------------------------------

    /** L'accueil : une esplanade, la version du mod, et un panneau par destination. */
    private static void welcome(ServerLevel level, List<String[]> destinations) {
        loadAround(level, 0, 0, 2);
        BlockState concrete = ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
        BlockState tiles = ModBlocks.FACILITY_TILES.get().defaultBlockState();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Math.abs(x) == 8 || Math.abs(z) == 8 ? concrete : tiles, Block.UPDATE_ALL);
            }
        }
        String version = ModList.get().getModContainerById(Orbitalis.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        sign(level, new BlockPos(0, Y, -3), Component.translatable("testmap.orbitalis.title"),
                Component.literal(version + " · " + LocalDate.now()), null);
        for (int k = 0; k < destinations.size(); k++) {
            String[] destination = destinations.get(k);
            int x = -6 + (k % 7) * 2;
            int z = 1 + (k / 7) * 2;
            sign(level, new BlockPos(x, Y, z), Component.translatable(destination[0]), Component.translatable("testmap.orbitalis.click"),
                    "/" + destination[1]);
        }
    }

    /** La galerie : chaque bloc du mod sur un socle, son nom au pied. Renvoie l'abscisse de sa fin. */
    private static int gallery(ServerLevel level, int x0, int z0) {
        List<Block> blocks = new ArrayList<>();
        for (var entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (!TECHNICAL.contains(entry.getId().getPath()) && !(block instanceof LiquidBlock) && !com.orbitalis.core.Features.hidden(block)) {
                blocks.add(block);
            }
        }
        int perRow = 12;
        int lastX = x0;
        BlockState pedestal = ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
        for (int k = 0; k < blocks.size(); k++) {
            int x = x0 + (k % perRow) * 6;
            int z = z0 - (k / perRow) * 7;
            lastX = Math.max(lastX, x);
            loadAround(level, x, z, 1);
            BlockPos base = new BlockPos(x, Y, z);
            level.setBlock(base.below(), pedestal, Block.UPDATE_ALL);
            BlockState state = blocks.get(k).defaultBlockState();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            }
            if (state.getBlock() instanceof com.orbitalis.phase2.survival.SuitWorkbenchBlock) {
                level.setBlock(base, state, Block.UPDATE_ALL);
                com.orbitalis.phase2.survival.SuitWorkbenchBlock.place(level, base, state);
            } else if (state.getBlock() instanceof com.orbitalis.phase2.station.GlassDoorBlock) {
                // La porte vitrée tient sur deux colonnes.
                level.setBlock(base, state, Block.UPDATE_ALL);
                com.orbitalis.phase2.station.GlassDoorBlock.place(level, base, state);
            } else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                level.setBlock(base, state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
                level.setBlock(base.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
            } else {
                level.setBlock(base, state, Block.UPDATE_ALL);
            }
            sign(level, base.south(2), blocks.get(k).getName(), null, null);
        }
        return lastX;
    }

    /** La réserve : tous les objets du mod, rangés dans des coffres alignés. */
    private static void storage(ServerLevel level, int x0, int z0) {
        loadAround(level, x0, z0, 2);
        List<Item> items = new ArrayList<>();
        for (var entry : ModItems.ITEMS.getEntries()) {
            if (!com.orbitalis.core.Features.hidden(entry.get())) {
                items.add(entry.get());
            }
        }
        int chest = 0;
        for (int start = 0; start < items.size(); start += 27, chest++) {
            BlockPos pos = new BlockPos(x0 + chest, Y, z0);
            level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity box) {
                for (int slot = 0; slot < 27 && start + slot < items.size(); slot++) {
                    box.setItem(slot, new ItemStack(items.get(start + slot)));
                }
            }
        }
        sign(level, new BlockPos(x0, Y, z0 + 2), Component.translatable("testmap.orbitalis.zone.items"), null, null);
    }

    /** Les combinaisons, sur des mannequins : l'orange, la blanche, et la blanche avec tous ses modules. */
    private static void suits(ServerLevel level, int x0, int z0) {
        loadAround(level, x0, z0, 1);
        for (int k = 0; k < 3; k++) {
            boolean advanced = k > 0;
            ArmorStand stand = new ArmorStand(level, x0 + k * 2 + 0.5, Y, z0 + 0.5);
            stand.setShowArms(true);
            stand.setNoBasePlate(true);
            ItemStack head = new ItemStack(advanced ? ModItems.ADVANCED_SPACE_HELMET.get() : ModItems.SPACE_HELMET.get());
            ItemStack chest = new ItemStack(advanced ? ModItems.ADVANCED_SPACESUIT.get() : ModItems.SPACESUIT.get());
            ItemStack legs = new ItemStack(advanced ? ModItems.ADVANCED_SPACE_LEGGINGS.get() : ModItems.SPACE_LEGGINGS.get());
            ItemStack feet = new ItemStack(advanced ? ModItems.ADVANCED_SPACE_BOOTS.get() : ModItems.SPACE_BOOTS.get());
            if (k == 2) {
                for (SuitModule module : SuitModule.values()) {
                    SpaceSuits.install(module.slot() == EquipmentSlot.HEAD ? head : module.slot() == EquipmentSlot.CHEST ? chest : legs, module);
                }
            }
            stand.setItemSlot(EquipmentSlot.HEAD, head);
            stand.setItemSlot(EquipmentSlot.CHEST, chest);
            stand.setItemSlot(EquipmentSlot.LEGS, legs);
            stand.setItemSlot(EquipmentSlot.FEET, feet);
            level.addFreshEntity(stand);
        }
        sign(level, new BlockPos(x0 + 2, Y, z0 + 2), Component.translatable("testmap.orbitalis.zone.suits"), null, null);
    }

    /**
     * Les machines en marche, tournées vers le sud : le réacteur à fusion, nourri d'hélium-3 et d'hydrogène par deux
     * réservoirs créatifs, alimente à l'ouest le réacteur de Sabatier (du charbon, et l'hydrogène d'un autre réservoir)
     * et à l'est la chambre de Casimir (des perles d'ender). Plus loin, l'extracteur d'hélium-3 cuit de l'ilménite sur
     * une source d'énergie créative.
     */
    private static void machines(ServerLevel level, int x0, int z0) {
        loadAround(level, x0, z0, 1);
        BlockState concrete = ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
        BlockState tiles = ModBlocks.FACILITY_TILES.get().defaultBlockState();
        for (int x = x0 - 3; x <= x0 + 6; x++) {
            for (int z = z0 - 2; z <= z0 + 3; z++) {
                boolean edge = x == x0 - 3 || x == x0 + 6 || z == z0 - 2 || z == z0 + 3;
                level.setBlock(new BlockPos(x, Y - 1, z), edge ? concrete : tiles, Block.UPDATE_ALL);
            }
        }
        BlockPos reactor = new BlockPos(x0, Y, z0);
        machine(level, reactor, ModBlocks.FUSION_REACTOR.get(), ItemStack.EMPTY);
        level.setBlock(reactor.north(), creativeTank("helium_3"), Block.UPDATE_ALL);
        level.setBlock(reactor.above(), creativeTank("hydrogen"), Block.UPDATE_ALL);
        machine(level, reactor.west(), ModBlocks.SABATIER_REACTOR.get(), new ItemStack(net.minecraft.world.item.Items.COAL, 64));
        level.setBlock(reactor.west().above(), creativeTank("hydrogen"), Block.UPDATE_ALL);
        machine(level, reactor.east(), ModBlocks.CASIMIR_CHAMBER.get(), new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16));
        BlockPos extractor = reactor.east(3);
        machine(level, extractor, ModBlocks.HELIUM3_EXTRACTOR.get(), new ItemStack(ModBlocks.MOON_ILMENITE_ORE.get(), 64));
        level.setBlock(extractor.above(), ModBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState(), Block.UPDATE_ALL);
        // Le panneau, sur le côté : il ne cache aucune machine.
        sign(level, new BlockPos(x0 - 2, Y, z0 + 2), Component.translatable("testmap.orbitalis.zone.machines"), null, null);
    }

    /**
     * Les fusées : chaque modèle du catalogue, à son réglage par défaut et avec sa première charge utile, deux fois. Au
     * premier rang, tous ses blocs de construction posés, comme sur le pas de tir juste avant l'assemblage ; au second,
     * la fusée assemblée, ses tronçons et ses boosters, telle qu'elle se dresse sur la table de lancement. Chacune sur une
     * dalle, son nom sur un panneau.
     */
    private static void rockets(ServerLevel level, int x0, int z0) {
        BlockState slab = ModBlocks.FACILITY_CONCRETE.get().defaultBlockState();
        Direction trench = Direction.SOUTH;
        var templates = com.orbitalis.phase1.rocket.RocketCatalog.all();
        for (int k = 0; k < templates.size(); k++) {
            var template = templates.get(k);
            var design = com.orbitalis.phase1.rocket.RocketDesign.fromSlots(
                    com.orbitalis.phase1.rocket.RocketCatalog.build(template, 0, template.presets())::get);
            int x = x0 + k * ROCKET_SPACING;
            for (int row = 0; row < 2; row++) {
                int z = z0 + row * ROCKET_ROWS;
                loadAround(level, x, z, 1);
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        level.setBlock(new BlockPos(x + dx, Y - 1, z + dz), slab, Block.UPDATE_ALL);
                    }
                }
                BlockPos base = new BlockPos(x, Y, z);
                if (row == 0) {
                    // Les blocs de construction : chaque pièce à sa place, rien d'assemblé.
                    for (var piece : com.orbitalis.phase1.rocket.RocketPlan.pieces(design, trench)) {
                        level.setBlock(base.offset(piece.offset()).below(), piece.block().defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                } else {
                    // La fusée assemblée : sa colonne de tronçons, et ses boosters de part et d'autre.
                    var shape = com.orbitalis.phase1.spaceport.RocketShape.of(design);
                    BlockState rocket = ModBlocks.ROCKET.get().defaultBlockState();
                    for (int i = 0; i < shape.parts().size(); i++) {
                        level.setBlock(base.above(i), rocket.setValue(com.orbitalis.phase1.spaceport.RocketBlock.PART, shape.parts().get(i))
                                .setValue(com.orbitalis.phase1.spaceport.RocketBlock.SIZE, shape.size()), Block.UPDATE_CLIENTS);
                    }
                    var boosters = shape.boosterOffsets(trench);
                    for (int b = 0; b < boosters.size(); b++) {
                        int height = shape.boosterHeight();
                        level.setBlock(base.offset(boosters.get(b)).below(), rocket
                                .setValue(com.orbitalis.phase1.spaceport.RocketBlock.PART, com.orbitalis.phase1.spaceport.RocketShape.boosterPart(b % height, height))
                                .setValue(com.orbitalis.phase1.spaceport.RocketBlock.SIZE, shape.boosterSize()), Block.UPDATE_CLIENTS);
                    }
                }
                sign(level, new BlockPos(x - 2, Y, z - 4), Component.translatable(template.nameKey()),
                        Component.translatable(row == 0 ? "testmap.orbitalis.rocket.parts" : "testmap.orbitalis.rocket.assembled"), null);
            }
        }
    }

    /**
     * Le pas de tir d'essai : son tablier 3×3 posé sur le sol, mis en service, une fusée-sonde à poudre sur sa table, et
     * le pupitre d'essai dix blocs au sud, l'écran tourné vers celui qui le regarde depuis le sud, le pas de tir derrière.
     */
    private static void testPad(ServerLevel level, int x0, int z0) {
        loadAround(level, x0, z0, 1);
        BlockPos tablePos = new BlockPos(x0, Y + 1, z0);
        level.setBlock(tablePos, ModBlocks.LAUNCH_TABLE.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(tablePos) instanceof com.orbitalis.phase1.spaceport.LaunchTableBlockEntity table)) {
            return;
        }
        var plan = com.orbitalis.phase1.spaceport.PadPlan.test(Direction.NORTH);
        table.setPlan(plan);
        for (var piece : plan.pieces()) {
            level.setBlock(tablePos.offset(piece.offset()), piece.display(), Block.UPDATE_ALL);
        }
        table.commission(true);
        net.minecraft.core.NonNullList<ItemStack> parts = net.minecraft.core.NonNullList.withSize(com.orbitalis.phase1.rocket.RocketDesign.SLOT_COUNT,
                ItemStack.EMPTY);
        parts.set(com.orbitalis.phase1.rocket.RocketDesign.SLOT_PAYLOAD, new ItemStack(com.orbitalis.core.registry.ModItems.SOUNDING_PAYLOAD.get()));
        parts.set(com.orbitalis.phase1.rocket.RocketDesign.SLOT_NOSE, new ItemStack(com.orbitalis.core.registry.ModItems.NOSE_CONE.get()));
        parts.set(com.orbitalis.phase1.rocket.RocketDesign.engineSlot(0), new ItemStack(com.orbitalis.core.registry.ModItems.SOLID_MOTOR.get()));
        table.setRocket(com.orbitalis.phase1.rocket.RocketItem.create(parts));
        BlockPos console = new BlockPos(x0, Y, z0 + 10);
        level.setBlock(console, ModBlocks.TEST_CONSOLE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
                Block.UPDATE_ALL);
        sign(level, console.east(2), Component.translatable("testmap.orbitalis.zone.test_pad"), null, null);
    }

    /** Pose une machine tournée vers le sud, et met dans sa première case ce qu'on lui donne à transformer. */
    private static void machine(ServerLevel level, BlockPos pos, Block block, ItemStack input) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!input.isEmpty() && level.getBlockEntity(pos) instanceof com.orbitalis.phase1.machines.MachineBlockEntity machine) {
            machine.getInventory().setStack(0, input);
        }
    }

    /** Le réservoir créatif d'un fluide, désigné par son nom (« hydrogen », « helium_3 »…). */
    private static BlockState creativeTank(String fluid) {
        return ModBlocks.CREATIVE_TANKS.stream().filter(tank -> tank.getId().getPath().equals("creative_tank_" + fluid))
                .findFirst().orElseThrow().get().defaultBlockState();
    }

    /**
     * Les centres spatiaux : chaque sorte deux fois, normale et en grand, puis abandonnée et en petit, sur une
     * longue rangée au sud. Renvoie les abscisses de leurs esplanades.
     */
    private static int[] facilities(MinecraftServer server, ServerLevel level, List<String[]> destinations) {
        var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Integer> placed = new ArrayList<>();
        int x = 160;
        for (FacilityKind kind : FacilityKind.values()) {
            for (boolean abandoned : new boolean[] {false, true}) {
                var holder = structures.get(ResourceKey.create(Registries.STRUCTURE, Orbitalis.id(kind.getSerializedName())));
                if (holder.isEmpty()) {
                    continue;
                }
                int z = 160;
                int spread = kind == FacilityKind.CONTROL_CENTER ? 6 : 4;
                loadAround(level, x, z, spread);
                SpaceFacilityStructure.forcedLarge = !abandoned;
                SpaceFacilityStructure.forcedAbandoned = abandoned;
                try {
                    PlaceCommand.placeStructure(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), holder.get(), new BlockPos(x, Y, z));
                    int cx = (x & ~15) + 8;
                    int cz = (z & ~15) + 8;
                    placed.add(cx);
                    sign(level, new BlockPos(cx - 2, Y, cz - 2), Component.translatable("testmap.orbitalis.site." + kind.getSerializedName()),
                            Component.translatable(abandoned ? "testmap.orbitalis.abandoned" : "testmap.orbitalis.normal"), null);
                    destinations.add(new String[] {"testmap.orbitalis.site." + kind.getSerializedName(), "tp @s " + (cx - 2) + " " + (Y + 1) + " "
                            + (cz + 1) + " 180 10"});
                } catch (Exception e) {
                    Orbitalis.LOGGER.warn("Carte de test : centre {} non posé ({})", kind, e.getMessage());
                } finally {
                    SpaceFacilityStructure.forcedLarge = null;
                    SpaceFacilityStructure.forcedAbandoned = null;
                }
                x += kind == FacilityKind.CONTROL_CENTER ? 192 : 128;
            }
        }
        return placed.stream().mapToInt(Integer::intValue).toArray();
    }

    /** La station complète, en orbite : le module central et un module de chaque sorte. */
    private static void station(MinecraftServer server) {
        ServerLevel orbit = server.getLevel(SpaceRules.ORBIT);
        if (orbit == null) {
            return;
        }
        loadAround(orbit, STATION.getX(), STATION.getZ() - 6, 3);
        BlockPos c = STATION;
        StationManager.build(orbit, c);
        StationLayout.clear(server, c);
        StationLayout.modules(server, c);
        StationModules.attach(orbit, c.offset(-2, 0, 1), Direction.WEST, ModuleType.LABORATORY);
        StationModules.attach(orbit, c.offset(1, 0, -2), Direction.NORTH, ModuleType.HABITATION);
        StationModules.attach(orbit, c.offset(2, 0, 1), Direction.EAST, ModuleType.GREENHOUSE);
        StationModules.attach(orbit, c.offset(-5, 0, -1), Direction.NORTH, ModuleType.CUPOLA);
        StationModules.attach(orbit, c.offset(2, 0, -8), Direction.NORTH, ModuleType.POWER);
        StationModules.attach(orbit, c.offset(8, 0, 1), Direction.EAST, ModuleType.AIRLOCK);
        StationModules.attach(orbit, c.offset(3, 0, -4), Direction.EAST, ModuleType.CARGO);
        // Le chantier naval, au bout du module logistique (son fond n'a pas de terminal, le passage tombe au ras du sol) :
        // son berceau s'ouvre vers l'est, loin des autres modules.
        Component problem = com.orbitalis.core.Features.interstellar()
                ? StationModules.attach(orbit, c.offset(9, 0, -4), Direction.EAST, ModuleType.SHIPYARD) : null;
        if (problem != null) {
            Orbitalis.LOGGER.warn("Carte de test : chantier naval non posé ({})", problem.getString());
        }
    }
}
