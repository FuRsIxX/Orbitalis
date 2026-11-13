package com.orbitalis.platform.nbt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Écriture de la sauvegarde d'un bloc ou d'une entité, sur le modèle de la {@code ValueOutput} de Minecraft 26 : le code du
 * mod garde ses appels ({@code putInt}, {@code child}, {@code store}…), et ils s'écrivent ici dans un {@link CompoundTag}.
 */
public final class ValueOutput {
    private final CompoundTag tag;
    private final HolderLookup.@Nullable Provider registries;

    private ValueOutput(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    public static ValueOutput of(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        return new ValueOutput(tag, registries);
    }

    /** Le tag où tout s'écrit. */
    public CompoundTag tag() {
        return tag;
    }

    /** Les registres du monde, pour les codecs qui en ont besoin. */
    public HolderLookup.@Nullable Provider lookup() {
        return registries;
    }

    DynamicOps<Tag> ops() {
        return registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE);
    }

    public void putInt(String key, int value) {
        tag.putInt(key, value);
    }

    public void putLong(String key, long value) {
        tag.putLong(key, value);
    }

    public void putFloat(String key, float value) {
        tag.putFloat(key, value);
    }

    public void putDouble(String key, double value) {
        tag.putDouble(key, value);
    }

    public void putBoolean(String key, boolean value) {
        tag.putBoolean(key, value);
    }

    public void putString(String key, String value) {
        tag.putString(key, value);
    }

    public void putShort(String key, short value) {
        tag.putShort(key, value);
    }

    public void putByte(String key, byte value) {
        tag.putByte(key, value);
    }

    public void putIntArray(String key, int[] value) {
        tag.putIntArray(key, value);
    }

    /** Une sous-partie de la sauvegarde, sous cette clé. */
    public ValueOutput child(String key) {
        CompoundTag child = new CompoundTag();
        tag.put(key, child);
        return new ValueOutput(child, registries);
    }

    /** Écrit une valeur avec son codec ; une valeur que le codec refuse n'est pas écrite. */
    public <T> void store(String key, Codec<T> codec, T value) {
        codec.encodeStart(ops(), value).result().ifPresent(encoded -> tag.put(key, encoded));
    }

    public <T> void storeNullable(String key, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            store(key, codec, value);
        }
    }

    public void discard(String key) {
        tag.remove(key);
    }

    public boolean isEmpty() {
        return tag.isEmpty();
    }
}
