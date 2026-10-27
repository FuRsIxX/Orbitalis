package com.orbitalis.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.orbitalis.Orbitalis;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Les réglages de vue du joueur, gardés d'une partie à l'autre : l'orientation de la carte de navigation et de la
 * maquette de chaque pas de tir, l'orientation, les calques et la carte du ciel de l'atlas, l'angle de la planète de
 * la station de poursuite, le zoom de la maquette. Ils sont propres à ce joueur, sur cet ordinateur, et enregistrés
 * dans {@code config/orbitalis-views.properties} dès qu'ils changent.
 */
public final class ViewPrefs {
    private static final Properties VALUES = new Properties();
    private static boolean loaded;

    private ViewPrefs() {
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("orbitalis-views.properties");
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                VALUES.load(in);
            } catch (IOException problem) {
                Orbitalis.LOGGER.warn("Réglages de vue illisibles ({})", file, problem);
            }
        }
    }

    private static synchronized void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                VALUES.store(out, "Orbitalis : réglages de vue (orientation des cartes, calques de l'atlas...)");
            }
        } catch (IOException problem) {
            Orbitalis.LOGGER.warn("Réglages de vue non enregistrés ({})", file, problem);
        }
    }

    public static synchronized int getInt(String key, int fallback) {
        load();
        try {
            return Integer.parseInt(VALUES.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException problem) {
            return fallback;
        }
    }

    public static synchronized float getFloat(String key, float fallback) {
        load();
        try {
            return Float.parseFloat(VALUES.getProperty(key, Float.toString(fallback)).trim());
        } catch (NumberFormatException problem) {
            return fallback;
        }
    }

    public static synchronized boolean getBoolean(String key, boolean fallback) {
        load();
        String value = VALUES.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    /** Change un réglage, et l'enregistre s'il a changé. */
    public static synchronized void set(String key, Object value) {
        load();
        String text = String.valueOf(value);
        if (!text.equals(VALUES.getProperty(key))) {
            VALUES.setProperty(key, text);
            save();
        }
    }

    /**
     * La clé d'un réglage propre à un pas de tir : le monde (la partie solo, ou le serveur) et la place de sa table.
     * Deux mondes peuvent avoir une table au même endroit : chacun garde la sienne.
     */
    public static String padKey(String what, BlockPos table) {
        Minecraft minecraft = Minecraft.getInstance();
        String world = minecraft.getSingleplayerServer() != null ? "solo:" + minecraft.getSingleplayerServer().getWorldData().getLevelName()
                : minecraft.getCurrentServer() != null ? "server:" + minecraft.getCurrentServer().ip : "unknown";
        String dimension = minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
        return what + "." + (world + "." + dimension).replaceAll("[^A-Za-z0-9_.:-]", "_") + "." + table.getX() + "_" + table.getY() + "_" + table.getZ();
    }
}
