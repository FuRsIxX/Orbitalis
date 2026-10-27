package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.NativeImage;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.atlas.AtlasTiles;
import com.orbitalis.core.network.AtlasRequestPayload;
import com.orbitalis.core.network.AtlasTilePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * L'atlas, côté client : les tuiles de terrain reçues du serveur, peintes en cubes fidèles au monde, sous un
 * éclairage soigné (voir {@link AtlasPainter}), et gardées prêtes à être dessinées.
 * <p>
 * Chaque tuile a sa propre image, pour une orientation de la vue. L'écran de l'atlas dit lesquelles il veut
 * ({@link #want}) : celles qui manquent sont demandées au serveur, quelques-unes par tick ; une fois reçues, elles
 * sont peintes hors du fil du jeu, puis installées ({@link #pump}). Une tuile montrée est redemandée de temps en
 * temps, pour suivre ce qui change dans le monde ; son image reste en place tant que la nouvelle n'est pas prête.
 * Les images les moins récemment montrées sont libérées quand il y en a trop ; celles que l'écran vient de montrer
 * ne le sont jamais.
 */
public final class AtlasMap {
    /** Où en est une tuile. */
    public enum State {
        /** Demandée au serveur. */
        WAITING,
        /** Hors de ce que le centre de contrôle connaît : rien à montrer. */
        UNKNOWN,
        /** Reçue, en cours de peinture. */
        PAINTING,
        /** Peinte : son image est prête. */
        READY
    }

    /** Une tuile de l'atlas, pour une orientation de la vue. */
    public static final class Tile {
        public final int lod;
        public final int tx;
        public final int tz;
        public final int turn;
        public State state = State.WAITING;
        /** Les cases reçues du serveur : l'écran y lit l'altitude et la nature du terrain sous le curseur. */
        public int[] cells;
        /** L'image de la tuile, une fois prête, et la hauteur de son point le plus bas (voir {@code AtlasPainter.Painted}). */
        public Identifier id;
        public int base;
        /** Instant où l'image est devenue prête, en millisecondes : elle apparaît en fondu. */
        public long readyAt;
        private CompletableFuture<AtlasPainter.Painted> painting;
        private long asked;
        private long used;

        private Tile(int lod, int tx, int tz, int turn) {
            this.lod = lod;
            this.tx = tx;
            this.tz = tz;
            this.turn = turn;
        }
    }

    /** Nombre d'images gardées : au-delà, les moins récemment montrées sont libérées. */
    private static final int KEPT = 180;
    /** Temps laissé au serveur pour répondre avant de redemander une tuile, en ticks. */
    private static final int ASK_AGAIN = 80;
    /** Âge au-delà duquel une tuile montrée est redemandée, pour suivre ce qui change dans le monde, en ticks. */
    private static final int REFRESH = 600;
    /** Images envoyées à la carte graphique par image affichée, au plus : au-delà, l'affichage saccaderait. */
    private static final int INSTALLS_PER_FRAME = 6;

    private static final Map<Long, Tile> TILES = new HashMap<>();
    /** Le monde dont viennent les tuiles : en changer les efface toutes. */
    private static Level world;

    private AtlasMap() {
    }

    private static long key(int lod, int tx, int tz, int turn) {
        return (long) lod << 60 | (long) turn << 58 | (tx & 0x1FFFFFFFL) << 29 | tz & 0x1FFFFFFFL;
    }

    /** Efface tout si le joueur a changé de monde : les tuiles d'un autre monde n'ont plus rien à faire là. */
    private static void follow(Level level) {
        if (level != world) {
            clear();
            world = level;
        }
    }

    /** Libère toutes les images. */
    public static void clear() {
        for (Tile tile : TILES.values()) {
            release(tile);
        }
        TILES.clear();
    }

    private static void release(Tile tile) {
        if (tile.id != null) {
            Minecraft.getInstance().getTextureManager().release(tile.id);
            tile.id = null;
        }
    }

    /** La tuile telle qu'on l'a, sans la demander : {@code null} si elle n'a jamais été voulue. */
    public static Tile peek(int lod, int tx, int tz, int turn) {
        return TILES.get(key(lod, tx, tz, turn));
    }

    /**
     * L'écran veut cette tuile. Si on ne l'a pas, elle est demandée au serveur, dans la limite de ce que
     * {@code budget} permet encore pour ce tick ; si le serveur n'a pas répondu depuis longtemps, elle est redemandée.
     *
     * @param source le pupitre depuis lequel l'atlas est consulté
     * @param budget nombre de demandes encore permises ce tick, dans sa première case ; il est décompté ici
     */
    public static Tile want(Level level, BlockPos source, int lod, int tx, int tz, int turn, long now, int[] budget) {
        follow(level);
        Tile tile = TILES.get(key(lod, tx, tz, turn));
        if (tile == null) {
            // Une autre orientation de la même tuile a déjà ses cases : inutile de les redemander.
            Tile sibling = null;
            for (int other = 0; other < 4 && sibling == null; other++) {
                Tile candidate = TILES.get(key(lod, tx, tz, other));
                sibling = candidate != null && candidate.cells != null ? candidate : null;
            }
            if (sibling == null && budget[0] <= 0) {
                return null;
            }
            tile = new Tile(lod, tx, tz, turn);
            tile.asked = Long.MIN_VALUE / 2;
            if (sibling != null) {
                tile.asked = sibling.asked;
                paint(tile, sibling.cells);
            }
            TILES.put(key(lod, tx, tz, turn), tile);
        }
        tile.used = now;
        boolean stale = tile.state == State.READY && now - tile.asked > REFRESH;
        if ((tile.state == State.WAITING && now - tile.asked > ASK_AGAIN || stale) && budget[0] > 0) {
            budget[0]--;
            tile.asked = now;
            ClientPacketDistributor.sendToServer(new AtlasRequestPayload(source, lod, tx, tz));
        }
        return tile;
    }

    /** Note qu'une tuile vient d'être montrée, pour qu'elle ne soit pas libérée. */
    public static void touch(Tile tile, long now) {
        tile.used = now;
    }

    /** Le serveur envoie une tuile : toutes les orientations qu'on en veut sont peintes. */
    public static void accept(AtlasTilePayload payload) {
        if (payload.lod() < 0 || payload.lod() >= AtlasTiles.CELL.length) {
            return;
        }
        int[] cells = payload.cells();
        for (int turn = 0; turn < 4; turn++) {
            Tile tile = TILES.get(key(payload.lod(), payload.tx(), payload.tz(), turn));
            if (tile == null) {
                continue;
            }
            if (cells.length != AtlasTiles.SIDE * AtlasTiles.SIDE) {
                if (tile.state != State.READY) {
                    tile.state = State.UNKNOWN;
                    tile.cells = null;
                }
                continue;
            }
            // Rien n'a changé depuis la dernière fois : l'image en place est la bonne.
            if (tile.cells != null && Arrays.equals(tile.cells, cells)) {
                continue;
            }
            paint(tile, cells);
        }
    }

    /** Lance la peinture d'une tuile, hors du fil du jeu. L'image en place, s'il y en a une, reste montrée en attendant. */
    private static void paint(Tile tile, int[] cells) {
        tile.cells = cells;
        if (tile.state != State.READY) {
            tile.state = State.PAINTING;
        }
        int lod = tile.lod;
        int tx = tile.tx;
        int tz = tile.tz;
        int turn = tile.turn;
        tile.painting = CompletableFuture.supplyAsync(() -> AtlasPainter.paint(lod, tx, tz, cells, turn));
    }

    /**
     * Installe les tuiles dont la peinture est finie, et libère les images les moins récemment montrées s'il y en
     * a trop. À appeler à chaque image, depuis le fil du jeu.
     *
     * @return le nombre de tuiles encore attendues ou en cours de peinture
     */
    public static int pump(long now) {
        int pending = 0;
        int ready = 0;
        int installed = 0;
        for (Tile tile : TILES.values()) {
            if (tile.painting != null && tile.painting.isDone() && installed < INSTALLS_PER_FRAME) {
                AtlasPainter.Painted painted = tile.painting.isCompletedExceptionally() ? null : tile.painting.getNow(null);
                tile.painting = null;
                if (painted == null) {
                    if (tile.state != State.READY) {
                        tile.state = State.UNKNOWN;
                    }
                } else {
                    boolean fresh = tile.state != State.READY;
                    tile.base = painted.base();
                    install(tile, painted.pixels());
                    if (fresh) {
                        tile.readyAt = Util.getMillis();
                    }
                    installed++;
                }
            }
            if (tile.state == State.WAITING && now - tile.used < 40 || tile.state == State.PAINTING) {
                pending++;
            }
            if (tile.state == State.READY) {
                ready++;
            }
        }
        if (ready > KEPT || TILES.size() > KEPT * 3) {
            List<Tile> old = new ArrayList<>(TILES.values());
            old.sort((a, b) -> Long.compare(a.used, b.used));
            for (Tile tile : old) {
                if (ready <= KEPT * 3 / 4 && TILES.size() <= KEPT * 2) {
                    break;
                }
                // Une tuile montrée il y a peu ne part pas, même si la mémoire est pleine : rien ne disparaît sous les yeux.
                if (now - tile.used < 200) {
                    break;
                }
                if (tile.state == State.READY) {
                    ready--;
                }
                release(tile);
                TILES.remove(key(tile.lod, tile.tx, tile.tz, tile.turn));
            }
        }
        return pending;
    }

    /** Recopie une peinture dans une image neuve, lissée quand elle est agrandie ou réduite. */
    private static void install(Tile tile, int[] pixels) {
        release(tile);
        int wide = AtlasPainter.WIDTH;
        int tall = AtlasPainter.HEIGHT;
        DynamicTexture texture = new DynamicTexture(() -> "Orbitalis atlas tile", wide, tall, true) {
            {
                sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
            }
        };
        NativeImage image = texture.getPixels();
        if (image == null) {
            tile.state = State.UNKNOWN;
            return;
        }
        for (int y = 0; y < tall; y++) {
            for (int x = 0; x < wide; x++) {
                image.setPixel(x, y, pixels[y * wide + x]);
            }
        }
        texture.upload();
        tile.id = Orbitalis.id("atlas/" + tile.turn + "/" + tile.lod + "/" + tile.tx + "_" + tile.tz);
        Minecraft.getInstance().getTextureManager().register(tile.id, texture);
        tile.state = State.READY;
    }
}
