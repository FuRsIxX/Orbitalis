package com.orbitalis.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.orbitalis.Orbitalis;

/**
 * Un fichier de réglages du mod, sur le modèle du {@code ModConfigSpec} de NeoForge : on décrit les réglages une fois
 * (commentaire, valeur par défaut, bornes), puis on lit et on écrit un petit fichier {@code clé = valeur} dans le dossier des
 * configurations. Le même fichier sert sous Fabric et sous NeoForge.
 */
public final class ModConfigSpec {
    private final List<Value<?>> values;

    private ModConfigSpec(List<Value<?>> values) {
        this.values = values;
    }

    /** Lit le fichier ; s'il manque ou s'il lui manque des réglages, il est réécrit avec les valeurs par défaut. */
    public void load(Path file) {
        Map<String, String> read = new HashMap<>();
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.strip();
                    int equals = trimmed.indexOf('=');
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || equals < 0) {
                        continue;
                    }
                    read.put(trimmed.substring(0, equals).strip(), trimmed.substring(equals + 1).strip().replace("\"", ""));
                }
            } catch (IOException e) {
                Orbitalis.LOGGER.warn("Réglages illisibles ({}) : valeurs par défaut", file, e);
            }
        }
        boolean complete = true;
        for (Value<?> value : values) {
            String text = read.get(value.key);
            if (text == null || !value.parse(text)) {
                value.reset();
                complete = false;
            }
        }
        if (!complete) {
            save(file);
        }
    }

    /** Écrit le fichier, chaque réglage sous son commentaire. */
    public void save(Path file) {
        StringBuilder out = new StringBuilder();
        for (Value<?> value : values) {
            for (String line : value.comment) {
                out.append("# ").append(line).append('\n');
            }
            out.append(value.key).append(" = ").append(value.format()).append("\n\n");
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Orbitalis.LOGGER.warn("Réglages non écrits ({})", file, e);
        }
    }

    /** Un réglage : sa clé, son commentaire, sa valeur par défaut et sa valeur courante. */
    public abstract static class Value<T> implements Supplier<T> {
        final String key;
        final List<String> comment;
        final T fallback;
        T current;

        Value(String key, List<String> comment, T fallback) {
            this.key = key;
            this.comment = comment;
            this.fallback = fallback;
            this.current = fallback;
        }

        @Override
        public T get() {
            return current;
        }

        public void set(T value) {
            current = value;
        }

        public T getDefault() {
            return fallback;
        }

        void reset() {
            current = fallback;
        }

        abstract boolean parse(String text);

        String format() {
            return String.valueOf(current);
        }
    }

    public static final class BooleanValue extends Value<Boolean> implements BooleanSupplier {
        BooleanValue(String key, List<String> comment, boolean fallback) {
            super(key, comment, fallback);
        }

        @Override
        public boolean getAsBoolean() {
            return current;
        }

        @Override
        boolean parse(String text) {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                return false;
            }
            current = Boolean.parseBoolean(text);
            return true;
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;

        DoubleValue(String key, List<String> comment, double fallback, double min, double max) {
            super(key, comment, fallback);
            this.min = min;
            this.max = max;
        }

        public double getAsDouble() {
            return current;
        }

        @Override
        boolean parse(String text) {
            try {
                double value = Double.parseDouble(text);
                if (value < min || value > max) {
                    return false;
                }
                current = value;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends Value<E> {
        EnumValue(String key, List<String> comment, E fallback) {
            super(key, comment, fallback);
        }

        @Override
        boolean parse(String text) {
            for (E constant : fallback.getDeclaringClass().getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(text)) {
                    current = constant;
                    return true;
                }
            }
            return false;
        }

        @Override
        String format() {
            return current.name();
        }
    }

    /** La description des réglages, réglage après réglage. */
    public static final class Builder {
        private final List<Value<?>> values = new ArrayList<>();
        private List<String> comment = List.of();

        public Builder comment(String... lines) {
            comment = List.of(lines);
            return this;
        }

        public Builder translation(String key) {
            return this;
        }

        public Builder worldRestart() {
            return this;
        }

        public Builder gameRestart() {
            return this;
        }

        public BooleanValue define(String key, boolean fallback) {
            return add(new BooleanValue(key, take(), fallback));
        }

        public DoubleValue defineInRange(String key, double fallback, double min, double max) {
            List<String> lines = new ArrayList<>(take());
            lines.add(String.format(Locale.ROOT, "De %s à %s (%s par défaut).", min, max, fallback));
            return add(new DoubleValue(key, lines, fallback, min, max));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String key, E fallback) {
            List<String> lines = new ArrayList<>(take());
            List<String> names = new ArrayList<>();
            for (E constant : fallback.getDeclaringClass().getEnumConstants()) {
                names.add(constant.name());
            }
            lines.add("Valeurs : " + String.join(", ", names) + ".");
            return add(new EnumValue<>(key, lines, fallback));
        }

        private List<String> take() {
            List<String> taken = comment;
            comment = List.of();
            return taken;
        }

        private <V extends Value<?>> V add(V value) {
            values.add(value);
            return value;
        }

        public ModConfigSpec build() {
            return new ModConfigSpec(List.copyOf(values));
        }
    }
}
