package com.orbitalis.platform.nbt;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Lecture de la sauvegarde d'un bloc ou d'une entité, sur le modèle de la {@code ValueInput} de Minecraft 26 : chaque
 * lecture dit quoi rendre si la clé manque, ce qui évite les valeurs à zéro par mégarde.
 */
public final class ValueInput {
    private final CompoundTag tag;
    private final HolderLookup.@Nullable Provider registries;

    private ValueInput(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    public static ValueInput of(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        return new ValueInput(tag, registries);
    }

    public CompoundTag tag() {
        return tag;
    }

    public HolderLookup.@Nullable Provider lookup() {
        return registries;
    }

    private DynamicOps<Tag> ops() {
        return registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE);
    }

    public boolean contains(String key) {
        return tag.contains(key);
    }

    public int getIntOr(String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public long getLongOr(String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    public float getFloatOr(String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }

    public double getDoubleOr(String key, double fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }

    public boolean getBooleanOr(String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }

    public short getShortOr(String key, short fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getShort(key) : fallback;
    }

    public byte getByteOr(String key, byte fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getByte(key) : fallback;
    }

    public String getStringOr(String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    public Optional<String> getString(String key) {
        return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    public Optional<Integer> getInt(String key) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? Optional.of(tag.getInt(key)) : Optional.empty();
    }

    /** Le tableau rangé sous cette clé, ou un tableau vide. */
    public int[] getIntArray(String key) {
        return tag.getIntArray(key);
    }

    /** La sous-partie sous cette clé, s'il y en a une. */
    public Optional<ValueInput> child(String key) {
        return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(new ValueInput(tag.getCompound(key), registries)) : Optional.empty();
    }

    /** La sous-partie sous cette clé, ou une partie vide. */
    public ValueInput childOrEmpty(String key) {
        return new ValueInput(tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : new CompoundTag(), registries);
    }

    /** Lit une valeur avec son codec ; rien si la clé manque ou si la valeur ne se lit pas. */
    public <T> Optional<T> read(String key, Codec<T> codec) {
        Tag value = tag.get(key);
        return value == null ? Optional.empty() : codec.parse(ops(), value).result();
    }

    public boolean isEmpty() {
        return tag.isEmpty();
    }
}
