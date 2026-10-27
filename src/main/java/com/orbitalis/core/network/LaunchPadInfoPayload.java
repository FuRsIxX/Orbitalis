package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Serveur → client : informations d'un pas de tir et de sa fusée. Le client ouvre l'écran d'informations
 * à sa réception (clic droit sur la table, sur une dalle du pas de tir formé, ou sur la fusée).
 * <p>
 * La fusée elle-même est envoyée : le client recalcule ses statistiques avec les mêmes règles que le serveur.
 *
 * @param commissioned    vrai si le complexe a été mis en service
 * @param radius          taille du tablier : 1 pour 3×3 ... 5 pour 11×11 ; 0 s'il est incomplet
 * @param skyClear        vrai si rien ne se trouve au-dessus de la table
 * @param hasDeflector    vrai si un déflecteur de flammes se trouve sous la table
 * @param hasTrench       vrai si un carneau relie la table à un bord du tablier
 * @param mastHeight      hauteur du plus petit des quatre mâts paratonnerres, en blocs
 * @param towerHeight     hauteur de la tour de service, en blocs
 * @param requiredTower   hauteur de tour exigée par la fusée posée, en blocs
 * @param arms            nombre de bras ombilicaux
 * @param towerAuthorized vrai si la technologie de la tour de service était maîtrisée à la mise en service
 * @param rocket          fusée posée sur la table, ou une pile vide
 * @param rocketHeight    hauteur de la fusée, en blocs
 * @param rp1             RP-1 chargé, en mB
 * @param oxygen          oxygène liquide chargé, en mB
 * @param hydrogen        hydrogène liquide chargé, en mB
 * @param methane         méthane liquide chargé, en mB
 * @param helium3         hélium-3 chargé, en mB
 * @param phase           déroulement du lancement (rang dans {@code LaunchTableBlockEntity.Phase})
 * @param compact         vrai pour le pas de tir d'essai, qui n'a ni tour, ni mâts, ni bras
 */
public record LaunchPadInfoPayload(boolean commissioned, int radius, boolean skyClear, boolean hasDeflector, boolean hasTrench,
        int mastHeight, int towerHeight, int requiredTower, int arms, boolean towerAuthorized, ItemStack rocket,
        int rocketHeight, int rp1, int oxygen, int hydrogen, int methane, int helium3, int phase, boolean compact) implements CustomPacketPayload {
    public static final Type<LaunchPadInfoPayload> TYPE = new Type<>(Orbitalis.id("launch_pad_info"));

    /** Sans méthane chargé. */
    public LaunchPadInfoPayload(boolean commissioned, int radius, boolean skyClear, boolean hasDeflector, boolean hasTrench,
            int mastHeight, int towerHeight, int requiredTower, int arms, boolean towerAuthorized, ItemStack rocket,
            int rocketHeight, int rp1, int oxygen, int hydrogen, int phase) {
        this(commissioned, radius, skyClear, hasDeflector, hasTrench, mastHeight, towerHeight, requiredTower, arms, towerAuthorized,
                rocket, rocketHeight, rp1, oxygen, hydrogen, 0, 0, phase, false);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LaunchPadInfoPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.commissioned);
                buffer.writeInt(payload.radius);
                buffer.writeBoolean(payload.skyClear);
                buffer.writeBoolean(payload.hasDeflector);
                buffer.writeBoolean(payload.hasTrench);
                buffer.writeInt(payload.mastHeight);
                buffer.writeInt(payload.towerHeight);
                buffer.writeInt(payload.requiredTower);
                buffer.writeInt(payload.arms);
                buffer.writeBoolean(payload.towerAuthorized);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.rocket);
                buffer.writeInt(payload.rocketHeight);
                buffer.writeInt(payload.rp1);
                buffer.writeInt(payload.oxygen);
                buffer.writeInt(payload.hydrogen);
                buffer.writeInt(payload.methane);
                buffer.writeInt(payload.helium3);
                buffer.writeInt(payload.phase);
                buffer.writeBoolean(payload.compact);
            },
            buffer -> new LaunchPadInfoPayload(buffer.readBoolean(), buffer.readInt(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readBoolean()));

    @Override
    public Type<LaunchPadInfoPayload> type() {
        return TYPE;
    }
}
