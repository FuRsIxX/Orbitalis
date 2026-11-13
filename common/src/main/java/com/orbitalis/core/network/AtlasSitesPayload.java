package com.orbitalis.core.network;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : les installations que l'atlas d'un pupitre montre, avec leur état (voir
 * {@code AtlasSites}). Envoyé en réponse à {@link AtlasSitesRequestPayload}, tant que l'atlas est ouvert.
 *
 * @param source le pupitre depuis lequel l'atlas est consulté
 * @param sites  les installations connues de ce pupitre
 * @param sky     le ciel de l'agence du joueur : ce que ses satellites lui donnent
 * @param markers les repères que son agence a posés dans ce monde
 */
public record AtlasSitesPayload(BlockPos source, List<Site> sites, Sky sky, List<MarkerInfo> markers) implements CustomPacketPayload {
    public static final Type<AtlasSitesPayload> TYPE = new Type<>(Orbitalis.id("atlas_sites"));
    /** Sortes d'installations. */
    public static final int PAD = 0;
    public static final int CONSOLE = 1;
    public static final int STATION = 2;
    /** Phases d'un pas de tir ; {@link #UNKNOWN} quand il n'est pas chargé et qu'on ne l'a pas vu depuis. */
    public static final int UNKNOWN = -1;
    public static final int IDLE = 0;
    public static final int COUNTDOWN = 1;
    public static final int FLIGHT = 2;

    /**
     * Une installation.
     *
     * @param kind      sa sorte ({@link #PAD}, {@link #CONSOLE}, {@link #STATION})
     * @param pos       sa position : la table d'un pas de tir, le bloc d'un pupitre ou d'une station
     * @param live      vrai si l'état vient de l'installation elle-même, à l'instant ; faux s'il date de la dernière
     *                  fois qu'elle a été chargée
     * @param phase     phase d'un pas de tir (voir plus haut)
     * @param rocket    vrai si une fusée est posée sur le pas de tir
     * @param result    issue du dernier lancement (rang de {@code LaunchTableBlockEntity.Result})
     * @param reach     destination de la fusée (rang de {@code RocketStats.Reach}), -1 sans fusée
     * @param fuel      remplissage des réservoirs, en %
     * @param size      côté du tablier, en blocs
     * @param tower     hauteur de la tour, en blocs
     * @param heading   cap du carneau (valeur 2D d'une direction : 0 sud, 1 ouest, 2 nord, 3 est)
     * @param ticks     ticks écoulés dans la phase en cours
     * @param blast     rayon de l'onde de choc si la fusée explosait, en blocs ; 0 sans fusée
     * @param driftX    écart de la fusée en vol à la verticale de sa table, vers l'est, en blocs
     * @param driftZ    vers le sud, en blocs
     * @param downrange distance au sol du vol calculé, en km
     * @param altitude  altitude du vol calculé, en km
     * @param extra     pour un pupitre, 1 s'il est relié à un pas de tir ; pour une station, ses satellites en service
     */
    public record Site(int kind, BlockPos pos, boolean live, int phase, boolean rocket, int result, int reach, int fuel, int size, int tower,
            int heading, int ticks, float blast, float driftX, float driftZ, float downrange, float altitude, int extra) {
        /** Une installation dont on ne sait que la sorte et la place. */
        public static Site bare(int kind, BlockPos pos) {
            return new Site(kind, pos, false, UNKNOWN, false, 0, -1, 0, 0, 0, 0, 0, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0);
        }

        /** La même, marquée comme un souvenir : elle n'est plus chargée. */
        public Site remembered() {
            return new Site(kind, pos, false, phase, rocket, result, reach, fuel, size, tower, heading, ticks, blast, driftX, driftZ, downrange,
                    altitude, extra);
        }
    }

    /**
     * Le ciel d'une agence : ce que ses satellites en service lui donnent.
     *
     * @param forecast      le temps qu'il fait, selon son satellite météo ({@code SatelliteStatusPayload.FORECAST_*}),
     *                      {@code FORECAST_NONE} sans satellite météo
     * @param forecastTicks durée pendant laquelle ce temps doit encore durer, en ticks
     * @param relay         vrai si un satellite de télécommunications relaie ses données
     * @param navigation    satellites de navigation en service
     * @param observers     satellites d'observation en service
     * @param satellites    les satellites en service : numéro puis type (rang de {@code SatelliteType}), à la suite
     * @param known         rayon de ce que l'agence connaît autour du pupitre, en blocs : il grandit avec ses
     *                      satellites d'observation
     */
    public record Sky(int forecast, int forecastTicks, boolean relay, int navigation, int observers, int[] satellites, int known) {
        /** Avant la première réponse : rien du ciel, et le rayon que connaît un centre de contrôle seul. */
        public static final Sky NONE = new Sky(-1, 0, false, 0, 0, new int[0], 1536);
    }

    /**
     * Un repère de l'agence.
     *
     * @param name   son nom, vide tant qu'on ne l'a pas nommé
     * @param author le joueur qui l'a posé
     */
    public record MarkerInfo(int id, int x, int y, int z, String name, String author) {
    }

    /** Nombre d'installations envoyées, au plus. */
    private static final int MAX_SITES = 256;

    public static final StreamCodec<FriendlyByteBuf, AtlasSitesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.source);
                int count = Math.min(payload.sites.size(), MAX_SITES);
                buffer.writeVarInt(count);
                for (int k = 0; k < count; k++) {
                    Site site = payload.sites.get(k);
                    buffer.writeByte(site.kind);
                    buffer.writeBlockPos(site.pos);
                    buffer.writeBoolean(site.live);
                    buffer.writeByte(site.phase);
                    buffer.writeBoolean(site.rocket);
                    buffer.writeByte(site.result);
                    buffer.writeByte(site.reach);
                    buffer.writeByte(site.fuel);
                    buffer.writeVarInt(site.size);
                    buffer.writeVarInt(site.tower);
                    buffer.writeByte(site.heading);
                    buffer.writeVarInt(site.ticks);
                    buffer.writeFloat(site.blast);
                    buffer.writeFloat(site.driftX);
                    buffer.writeFloat(site.driftZ);
                    buffer.writeFloat(site.downrange);
                    buffer.writeFloat(site.altitude);
                    buffer.writeVarInt(site.extra);
                }
                Sky sky = payload.sky;
                buffer.writeVarInt(sky.forecast + 1);
                buffer.writeVarInt(Math.max(0, sky.forecastTicks));
                buffer.writeBoolean(sky.relay);
                buffer.writeVarInt(sky.navigation);
                buffer.writeVarInt(sky.observers);
                int pairs = Math.min(sky.satellites.length / 2, MAX_SITES);
                buffer.writeVarInt(pairs);
                for (int k = 0; k < pairs * 2; k++) {
                    buffer.writeVarInt(sky.satellites[k]);
                }
                buffer.writeVarInt(sky.known);
                int markers = Math.min(payload.markers.size(), MAX_SITES);
                buffer.writeVarInt(markers);
                for (int k = 0; k < markers; k++) {
                    MarkerInfo marker = payload.markers.get(k);
                    buffer.writeVarInt(marker.id);
                    buffer.writeInt(marker.x);
                    buffer.writeInt(marker.y);
                    buffer.writeInt(marker.z);
                    buffer.writeUtf(marker.name, 64);
                    buffer.writeUtf(marker.author, 64);
                }
            },
            buffer -> {
                BlockPos source = buffer.readBlockPos();
                int count = Math.min(buffer.readVarInt(), MAX_SITES);
                List<Site> sites = new ArrayList<>(count);
                for (int k = 0; k < count; k++) {
                    sites.add(new Site(buffer.readByte(), buffer.readBlockPos(), buffer.readBoolean(), buffer.readByte(), buffer.readBoolean(),
                            buffer.readByte(), buffer.readByte(), buffer.readByte(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByte(),
                            buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                            buffer.readVarInt()));
                }
                int forecast = buffer.readVarInt() - 1;
                int forecastTicks = buffer.readVarInt();
                boolean relay = buffer.readBoolean();
                int navigation = buffer.readVarInt();
                int observers = buffer.readVarInt();
                int[] satellites = new int[Math.min(buffer.readVarInt(), MAX_SITES) * 2];
                for (int k = 0; k < satellites.length; k++) {
                    satellites[k] = buffer.readVarInt();
                }
                int known = buffer.readVarInt();
                int markerCount = Math.min(buffer.readVarInt(), MAX_SITES);
                List<MarkerInfo> markers = new ArrayList<>(markerCount);
                for (int k = 0; k < markerCount; k++) {
                    markers.add(new MarkerInfo(buffer.readVarInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readUtf(64),
                            buffer.readUtf(64)));
                }
                return new AtlasSitesPayload(source, sites, new Sky(forecast, forecastTicks, relay, navigation, observers, satellites, known), markers);
            });

    @Override
    public Type<AtlasSitesPayload> type() {
        return TYPE;
    }
}
