package com.orbitalis.platform.network;

import java.util.HashMap;
import java.util.Map;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Les messages du mod entre le serveur et les joueurs, posés sur le réseau d'Architectury : la même forme que l'inscription de
 * NeoForge ({@link Registrar#playToClient}, {@link Registrar#playToServer}), pour Fabric comme pour NeoForge.
 */
public final class Network {
    /** Ce que reçoit le gestionnaire d'un message : le joueur, et de quoi revenir sur le fil principal du jeu. */
    public interface Context {
        Player player();

        void enqueueWork(Runnable work);
    }

    @FunctionalInterface
    public interface Handler<T> {
        void handle(T payload, Context context);
    }

    /** Les codecs des messages vers le client : le client y attache ses gestionnaires au démarrage. */
    private static final Map<CustomPacketPayload.Type<?>, StreamCodec<? super RegistryFriendlyByteBuf, ?>> TO_CLIENT = new HashMap<>();

    private Network() {
    }

    public static final class Registrar {
        public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            TO_CLIENT.put(type, codec);
            if (Platform.getEnvironment() == Env.SERVER) {
                // Un serveur dédié n'a pas de gestionnaire : il ne fait qu'envoyer.
                NetworkManager.registerS2CPayloadType(type, codec);
            }
        }

        public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                Handler<T> handler) {
            // Comme sous NeoForge : le gestionnaire tourne sur le fil principal du serveur.
            NetworkManager.registerReceiver(NetworkManager.Side.C2S, type, codec, (payload, context) ->
                    context.queue(() -> handler.handle(payload, wrap(context))));
        }

        /** Un message qui va dans les deux sens : le client l'attache aussi à son gestionnaire au démarrage. */
        public <T extends CustomPacketPayload> void playBidirectional(CustomPacketPayload.Type<T> type,
                StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Handler<T> serverHandler) {
            playToClient(type, codec);
            playToServer(type, codec, serverHandler);
        }
    }

    public static final Registrar REGISTRAR = new Registrar();

    /** Le client attache son gestionnaire à un message vers le client (au démarrage du client). */
    @SuppressWarnings("unchecked")
    public static <T extends CustomPacketPayload> void onClient(CustomPacketPayload.Type<T> type, Handler<T> handler) {
        StreamCodec<? super RegistryFriendlyByteBuf, T> codec = (StreamCodec<? super RegistryFriendlyByteBuf, T>) TO_CLIENT.get(type);
        if (codec == null) {
            throw new IllegalStateException("Message vers le client non inscrit : " + type.id());
        }
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, type, codec, (payload, context) ->
                context.queue(() -> handler.handle(payload, wrap(context))));
    }

    private static Context wrap(NetworkManager.PacketContext context) {
        return new Context() {
            @Override
            public Player player() {
                return context.getPlayer();
            }

            @Override
            public void enqueueWork(Runnable work) {
                context.queue(work);
            }
        };
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        NetworkManager.sendToServer(payload);
    }
}
