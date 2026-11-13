package com.orbitalis.phase1.spaceport;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Pièces de structure du pas de tir. Ce bloc n'est jamais posé dans le monde et n'existe pas en objet :
 * il sert seulement à porter les modèles 3D que la table de lancement dessine et anime
 * (bras, passerelles, cabine d'ascenseur, mâts, carneau, déluge). Ces modèles sont écrits par {@code tools/generate_launch_pad.py}.
 */
public class StructurePartBlock extends Block {
    /** Pièce représentée. */
    public enum Part implements StringRepresentable {
        // Cabine de l'ascenseur de la tour ; passerelle d'embarquement et son seuil.
        ELEVATOR_CABIN,
        ELEVATOR_CABIN_TOP,
        // Passerelle : des travées de 1, 2, 4, 8 et 16 pixels de long, mises bout à bout sans jamais être étirées,
        // un potelet de garde-corps, et le seuil.
        GANGWAY_SPAN_1,
        GANGWAY_SPAN_2,
        GANGWAY_SPAN_4,
        GANGWAY_SPAN_8,
        GANGWAY_SPAN_16,
        GANGWAY_POST,
        GANGWAY_END,
        // Bras : des travées de poutre de 1 à 16 pixels, le cadre qui les rythme, des longueurs de flexibles, et la
        // plaque de raccordement.
        ARM_SPAN_1,
        ARM_SPAN_2,
        ARM_SPAN_4,
        ARM_SPAN_8,
        ARM_SPAN_16,
        ARM_RIB,
        ARM_HOSE_1,
        ARM_HOSE_2,
        ARM_HOSE_4,
        ARM_PLATE,
        // Mâts : embase, tronçons blancs et rouges, tronçon à projecteurs (éteints ou allumés), tête, balise.
        MAST_BASE,
        MAST_MID,
        MAST_MID_RED,
        MAST_LAMP,
        MAST_LAMP_ON,
        MAST_TOP,
        BEACON,
        // Plateforme : le carneau en relief.
        FLAME_TRENCH,
        // Vol habité : capsule, parachutes, train d'alunissage.
        CAPSULE,
        // La capsule en vol libre, en deux : la coque (éclairée par le jour) et la cabine (par ses plafonniers) ; le manche.
        CAPSULE_SHELL,
        CAPSULE_CABIN,
        CAPSULE_STICK,
        CAPSULE_MK2_SHELL,
        CAPSULE_MK2_CABIN,
        CAPSULE_MK3_SHELL,
        CAPSULE_MK3_CABIN,
        PARACHUTE,
        LANDER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public StructurePartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.ARM_PLATE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }
}
