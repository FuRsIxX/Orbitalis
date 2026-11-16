package com.orbitalis.core.network;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : les réglages du serveur (réalisme, multiplicateurs…), à la connexion puis à chaque changement, pour que
 * les écrans du joueur montrent les mêmes coûts et les mêmes règles que le serveur.
 *
 * @param values chaque réglage, sous sa clé, tel qu'écrit dans le fichier
 */
public record ServerConfigPayload(Map<String, String> values) implements CustomPacketPayload {
    public static final Type<ServerConfigPayload> TYPE = new Type<>(Orbitalis.id("server_config"));

    public static final StreamCodec<ByteBuf, ServerConfigPayload> STREAM_CODEC = ByteBufCodecs
            .map(HashMap<String, String>::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8, 64)
            .map(ServerConfigPayload::new, payload -> new HashMap<>(payload.values()));

    @Override
    public Type<ServerConfigPayload> type() {
        return TYPE;
    }
}
