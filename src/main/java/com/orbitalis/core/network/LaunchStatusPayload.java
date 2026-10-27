package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : état du complexe de lancement relié à un pupitre, de sa fusée et du lancement en cours.
 * Envoyé quand le joueur utilise le pupitre, puis à chaque demande d'actualisation de l'écran.
 *
 * @param console         position du pupitre
 * @param open            vrai si le client doit ouvrir l'écran du pupitre (clic sur le bloc), faux pour une actualisation
 * @param linked          vrai si une table de lancement se trouve dans la portée du pupitre
 * @param distance        distance entre le pupitre et la table, en blocs
 * @param commissioned    vrai si le complexe a été mis en service (clic droit sur la table)
 * @param radius          taille du pas de tir : 1 pour 3×3, 2 pour 5×5, 3 pour 7×7 ; 0 s'il est incomplet
 * @param missingPads     dalles manquantes pour compléter un pas de tir 3×3
 * @param skyClear        vrai si rien ne se trouve au-dessus de la table
 * @param towerHeight     hauteur de la tour de service, en blocs
 * @param arms            nombre de bras ombilicaux
 * @param towerAuthorized vrai si la technologie de la tour de service était maîtrisée à la mise en service
 * @param requiredTower   hauteur de tour exigée par la fusée posée, en blocs
 * @param hasRocket       vrai si une fusée est posée sur la table
 * @param rocketMass      masse de la fusée au décollage, en kg
 * @param reach           ce que la fusée peut atteindre (rang dans {@code RocketStats.Reach})
 * @param needsFuel       vrai si la fusée demande des ergols liquides
 * @param fuelPercent     remplissage des réservoirs, de 0 à 100
 * @param weatherGo       vrai si la météo permet le lancement
 * @param phase           déroulement du lancement (rang dans {@code LaunchTableBlockEntity.Phase})
 * @param seconds         secondes avant le décollage (compte à rebours) ou depuis le décollage (vol)
 * @param altitudeKm      altitude de la fusée en vol, en km
 * @param speed           vitesse de la fusée en vol, en m/s
 * @param result          issue du dernier lancement (rang dans {@code LaunchTableBlockEntity.Result})
 * @param points          points de recherche gagnés au dernier vol réussi
 * @param liquid          vrai si la fusée embarque des ergols liquides (plein et mise sous pression à faire)
 * @param valvesOpen      vrai si les vannes de remplissage sont ouvertes
 * @param pressure        secondes pendant lesquelles les réservoirs restent sous pression ; 0 s'ils ne le sont pas
 * @param retracted       vrai si le bras ombilical et la passerelle sont retirés
 * @param hasDeluge       vrai si le pas de tir a des rampes de déluge
 * @param deluge          secondes de déluge restantes ; 0 s'il est fermé
 * @param water           réserve d'eau, en pourcentage de ce qu'un déluge consomme (100 : de quoi en faire un)
 * @param thrust          poussée des moteurs, de 0 à 100
 * @param hold            secondes pendant lesquelles les attaches retiennent encore la fusée à pleine poussée
 * @param flightBits      étapes en vol (voir {@code LaunchTableBlockEntity.flightBits})
 * @param flightWindow    secondes avant que ne se referme la fenêtre ouverte la plus pressée
 * @param assist          niveau d'aide de l'ordinateur de bord : 0 manuel, 1 assisté, 2 automatique
 * @param capacityRadius  taille prise en compte pour la masse des fusées : celle du tablier s'il est équipé
 *                        (déflecteur, carneau, mâts), sinon celle d'un 3×3
 * @param busyStep        opération en cours au sol (rang dans {@code LaunchStep}), ou -1
 * @param busyPercent     avancement de cette opération, de 0 à 100
 * @param poll            tour de table : -1 aucun, 0 à 99 en cours, 100 tous prêts, 101 un poste a dit non
 * @param forecast        prévision météo ({@code SatelliteStatusPayload.FORECAST_*}) : -1 sans satellite météo
 * @param forecastTicks   durée pendant laquelle ce temps doit encore durer, en ticks
 * @param chill           secondes pendant lesquelles les moteurs restent en froid ; -1 si la fusée n'en a pas besoin
 * @param internal        alimentation du lanceur : -1 s'il n'a pas à en changer, 0 sur le sol, 1 sur ses batteries
 * @param overpressure    surpression des réservoirs, de 0 à 100 : vannes restées ouvertes après le plein
 * @param testPad         vrai pour le pas de tir d'essai : ni bras à retirer, ni déluge
 */
public record LaunchStatusPayload(BlockPos console, boolean open, boolean linked, int distance, boolean commissioned,
        int radius, int missingPads, boolean skyClear, int towerHeight, int arms, boolean towerAuthorized, int requiredTower,
        boolean hasRocket, float rocketMass, int reach, boolean needsFuel, int fuelPercent, boolean weatherGo,
        int phase, int seconds, float altitudeKm, float speed, int result, int points, boolean liquid, boolean valvesOpen,
        int pressure, boolean retracted, boolean hasDeluge, int deluge, int water, int thrust, int hold, int flightBits, int flightWindow, int assist, int capacityRadius,
        int busyStep, int busyPercent, int poll, int forecast, int forecastTicks, int chill, int internal, int overpressure,
        boolean testPad)
        implements CustomPacketPayload {
    public static final Type<LaunchStatusPayload> TYPE = new Type<>(Orbitalis.id("launch_status"));

    public static final StreamCodec<ByteBuf, LaunchStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.console);
                buffer.writeBoolean(payload.open);
                buffer.writeBoolean(payload.linked);
                buffer.writeInt(payload.distance);
                buffer.writeBoolean(payload.commissioned);
                buffer.writeInt(payload.radius);
                buffer.writeInt(payload.missingPads);
                buffer.writeBoolean(payload.skyClear);
                buffer.writeInt(payload.towerHeight);
                buffer.writeInt(payload.arms);
                buffer.writeBoolean(payload.towerAuthorized);
                buffer.writeInt(payload.requiredTower);
                buffer.writeBoolean(payload.hasRocket);
                buffer.writeFloat(payload.rocketMass);
                buffer.writeInt(payload.reach);
                buffer.writeBoolean(payload.needsFuel);
                buffer.writeInt(payload.fuelPercent);
                buffer.writeBoolean(payload.weatherGo);
                buffer.writeInt(payload.phase);
                buffer.writeInt(payload.seconds);
                buffer.writeFloat(payload.altitudeKm);
                buffer.writeFloat(payload.speed);
                buffer.writeInt(payload.result);
                buffer.writeInt(payload.points);
                buffer.writeBoolean(payload.liquid);
                buffer.writeBoolean(payload.valvesOpen);
                buffer.writeInt(payload.pressure);
                buffer.writeBoolean(payload.retracted);
                buffer.writeBoolean(payload.hasDeluge);
                buffer.writeInt(payload.deluge);
                buffer.writeInt(payload.water);
                buffer.writeInt(payload.thrust);
                buffer.writeInt(payload.hold);
                buffer.writeInt(payload.flightBits);
                buffer.writeInt(payload.flightWindow);
                buffer.writeInt(payload.assist);
                buffer.writeInt(payload.capacityRadius);
                buffer.writeInt(payload.busyStep);
                buffer.writeInt(payload.busyPercent);
                buffer.writeInt(payload.poll);
                buffer.writeInt(payload.forecast);
                buffer.writeInt(payload.forecastTicks);
                buffer.writeInt(payload.chill);
                buffer.writeInt(payload.internal);
                buffer.writeInt(payload.overpressure);
                buffer.writeBoolean(payload.testPad);
            },
            buffer -> new LaunchStatusPayload(BlockPos.STREAM_CODEC.decode(buffer), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readInt(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readInt(),
                    buffer.readInt(), buffer.readBoolean(), buffer.readInt(), buffer.readBoolean(), buffer.readFloat(), buffer.readInt(),
                    buffer.readBoolean(), buffer.readInt(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readBoolean()));

    /** Aucun complexe relié. */
    public static LaunchStatusPayload notLinked(BlockPos console, boolean open) {
        return new LaunchStatusPayload(console, open, false, 0, false, 0, 0, false, 0, 0, false, 0,
                false, 0, 0, false, 0, true, 0, 0, 0, 0, 0, 0, false, false, 0, false, false, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, -1, -1, 0, -1, -1, 0, false);
    }

    @Override
    public Type<LaunchStatusPayload> type() {
        return TYPE;
    }
}
