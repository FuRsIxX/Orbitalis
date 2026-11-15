package com.orbitalis.client.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * Démonstration animée d'un chapitre du manuel : une petite construction en 3D qui se monte étape par étape,
 * avec une légende par étape.
 * <p>
 * Les blocs sont placés sur une grille aux mêmes axes que le monde : x vers l'est, y vers le haut, z vers le sud.
 * La caméra regarde depuis le sud-est : on voit les faces sud et est des blocs.
 * Les légendes viennent des traductions : {@code guide.orbitalis.<chapitre>.scene.<numéro d'étape>}.
 */
public final class GuideScene {
    private static final int FOREVER = Integer.MAX_VALUE;

    /**
     * Bloc de la construction.
     *
     * @param facing  orientation de la face avant (pour les machines)
     * @param from    étape à laquelle le bloc apparaît
     * @param until   étape à laquelle il disparaît
     * @param litFrom étape à partir de laquelle la machine est en marche (face avant allumée)
     */
    public record Piece(Supplier<? extends Block> block, Direction facing, int x, int y, int z, int from, int until, int litFrom) {
        public boolean visibleAt(int step) {
            return step >= from && step < until;
        }

        public boolean litAt(int step) {
            return step >= litFrom;
        }
    }

    /** Objet qui flotte au-dessus d'une case pendant une étape (ce qu'on met dans une machine, ce qui en sort). */
    public record Marker(ItemLike item, float x, float y, float z, int step) {
    }

    /**
     * Flux animé (énergie ou fluide) : un ruban lumineux le long d'un trajet passant par le centre des cases.
     *
     * @param color  couleur du ruban (0xAARRGGBB)
     * @param points sommets du trajet, chacun sous la forme {x, y, z}
     */
    public record Flow(int color, int from, int until, float[][] points) {
        public boolean visibleAt(int step) {
            return step >= from && step < until;
        }
    }

    private final String chapterId;
    private final int stepCount;
    private final List<Piece> pieces;
    private final List<Marker> markers;
    private final List<Flow> flows;

    private GuideScene(String chapterId, int stepCount, List<Piece> pieces, List<Marker> markers, List<Flow> flows) {
        this.chapterId = chapterId;
        this.stepCount = stepCount;
        this.pieces = List.copyOf(pieces);
        this.markers = List.copyOf(markers);
        this.flows = List.copyOf(flows);
    }

    public String chapterId() {
        return chapterId;
    }

    public int stepCount() {
        return stepCount;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public List<Marker> markers() {
        return markers;
    }

    public List<Flow> flows() {
        return flows;
    }

    /** Clé de traduction de la légende d'une étape (la première étape porte le numéro 1). */
    public String captionKey(int step) {
        return "guide.orbitalis." + chapterId + ".scene." + (step + 1);
    }

    public static Builder builder(String chapterId) {
        return new Builder(chapterId);
    }

    /**
     * Construit une démonstration étape par étape : chaque appel à {@link #step()} ouvre une nouvelle étape,
     * et les éléments ajoutés ensuite apparaissent à cette étape.
     */
    public static final class Builder {
        private final String chapterId;
        private final List<Piece> pieces = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();
        private final List<Flow> flows = new ArrayList<>();
        private int step = -1;

        private Builder(String chapterId) {
            this.chapterId = chapterId;
        }

        public Builder step() {
            step++;
            return this;
        }

        /** Pose un bloc, face avant tournée vers le sud (vers la caméra) ; il reste en place jusqu'à la fin. */
        public Builder block(Supplier<? extends Block> block, int x, int y, int z) {
            return block(block, x, y, z, Direction.SOUTH);
        }

        /** Pose un bloc avec une orientation précise. */
        public Builder block(Supplier<? extends Block> block, int x, int y, int z, Direction facing) {
            pieces.add(new Piece(block, facing, x, y, z, step, FOREVER, FOREVER));
            return this;
        }

        /** Met en marche la machine posée à cet endroit, à partir de cette étape. */
        public Builder lit(int x, int y, int z) {
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                if (piece.x() == x && piece.y() == y && piece.z() == z && piece.until() == FOREVER) {
                    pieces.set(i, new Piece(piece.block(), piece.facing(), x, y, z, piece.from(), piece.until(), step));
                }
            }
            return this;
        }

        /** Retire le bloc posé à cet endroit. */
        public Builder remove(int x, int y, int z) {
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                if (piece.x() == x && piece.y() == y && piece.z() == z && piece.until() == FOREVER) {
                    pieces.set(i, new Piece(piece.block(), piece.facing(), x, y, z, piece.from(), step, piece.litFrom()));
                }
            }
            return this;
        }

        /** Fait flotter un objet au-dessus d'une case, pendant cette étape seulement. */
        public Builder marker(ItemLike item, float x, float y, float z) {
            markers.add(new Marker(item, x, y, z, step));
            return this;
        }

        /** Ajoute un flux qui circule jusqu'à la fin de la démonstration. */
        public Builder flow(int color, float[]... points) {
            flows.add(new Flow(color, step, FOREVER, points));
            return this;
        }

        /** Arrête tous les flux en cours. */
        public Builder stopFlows() {
            for (int i = 0; i < flows.size(); i++) {
                Flow flow = flows.get(i);
                if (flow.until() == FOREVER) {
                    flows.set(i, new Flow(flow.color(), flow.from(), step, flow.points()));
                }
            }
            return this;
        }

        public GuideScene build() {
            return new GuideScene(chapterId, step + 1, pieces, markers, flows);
        }
    }

    /** Raccourci pour écrire un sommet de trajet. */
    public static float[] at(float x, float y, float z) {
        return new float[] {x, y, z};
    }
}
