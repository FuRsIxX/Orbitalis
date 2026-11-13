package com.orbitalis.platform;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * Une sorte de données gardées avec le monde, décrite comme en Minecraft 26 : un nom, un constructeur et un codec. Elle se lit
 * et s'écrit dans le fichier {@code data/<espace>_<nom>.dat} du monde.
 *
 * @param <T> les données
 */
public final class SavedDataType<T extends SavedData> {
    /** Le codec de chaque sorte de données, par classe : {@link CodecSavedData} s'en sert pour s'écrire. */
    private static final Map<Class<?>, Codec<?>> CODECS = new HashMap<>();

    private final String name;
    private final Supplier<T> constructor;
    private final Codec<T> codec;

    public SavedDataType(ResourceLocation id, Supplier<T> constructor, Codec<T> codec) {
        this.name = id.getNamespace() + "_" + id.getPath().replace('/', '_');
        this.constructor = constructor;
        this.codec = codec;
    }

    public SavedDataType(ResourceLocation id, Supplier<T> constructor, Codec<T> codec, Object ignoredDataFix) {
        this(id, constructor, codec);
    }

    /** Les données de cette sorte dans ce monde : lues du fichier, ou neuves. */
    public static <T extends SavedData> T computeIfAbsent(DimensionDataStorage storage, SavedDataType<T> type) {
        T data = storage.computeIfAbsent(new SavedData.Factory<>(type.constructor, type::load, null), type.name);
        type.remember(data.getClass());
        return data;
    }

    private T load(CompoundTag tag, HolderLookup.Provider registries) {
        Tag value = tag.contains("data") && tag.size() == 1 ? tag.get("data") : tag;
        T data = codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), value).result().orElseGet(constructor);
        CODECS.putIfAbsent(data.getClass(), codec);
        return data;
    }

    void remember(Class<?> type) {
        CODECS.putIfAbsent(type, codec);
    }

    @SuppressWarnings("unchecked")
    static <T> Codec<T> codecOf(Class<?> type) {
        return (Codec<T>) CODECS.get(type);
    }

    /** Les données gardées avec le monde, écrites par le codec de leur sorte. */
    public abstract static class CodecSavedData extends SavedData {
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            Codec<Object> codec = codecOf(getClass());
            if (codec == null) {
                return tag;
            }
            codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).result().ifPresent(encoded -> {
                if (encoded instanceof CompoundTag compound) {
                    tag.merge(compound);
                } else {
                    tag.put("data", encoded);
                }
            });
            return tag;
        }
    }
}
