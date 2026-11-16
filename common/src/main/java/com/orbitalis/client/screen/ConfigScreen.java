package com.orbitalis.client.screen;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.orbitalis.core.config.ClientConfig;
import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.config.StartupConfig;
import com.orbitalis.platform.config.ModConfigSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Les réglages d'Orbitalis, ouverts depuis la liste des mods (NeoForge) ou Mod Menu (Fabric).
 * Comme l'écran de réglages de NeoForge sur la version 26 : un bouton par fichier, puis la liste de ses réglages.
 */
public final class ConfigScreen extends OptionsSubScreen {
    private static final String PREFIX = "orbitalis.configuration.";

    public ConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(PREFIX + "title"));
    }

    @Override
    protected void addOptions() {
        Button client = section("orbitalis.client.toml", ClientConfig.SPEC);
        Button server = section("orbitalis.server.toml", ServerConfig.SPEC);
        Button startup = section("orbitalis.startup.toml", StartupConfig.SPEC);
        // Les réglages du serveur sont propres à chaque monde : ils se changent dans un monde ouvert en solo.
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.hasSingleplayerServer()) {
            server.active = false;
            server.setTooltip(Tooltip.create(Component.translatable(PREFIX + "server_locked")));
        }
        list.addSmall(client, server);
        list.addSmall(startup, null);
    }

    /** L'écran d'un fichier (« client », « server » ou « startup ») ; sert aussi à l'outil de capture. */
    public static Screen section(Screen parent, String file) {
        ModConfigSpec spec = switch (file) {
            case "server" -> ServerConfig.SPEC;
            case "startup" -> StartupConfig.SPEC;
            default -> ClientConfig.SPEC;
        };
        String key = PREFIX + "section.orbitalis." + file + ".toml";
        return new SpecScreen(parent, Minecraft.getInstance().options, Component.translatable(key + ".title"), spec);
    }

    private Button section(String file, ModConfigSpec spec) {
        String key = PREFIX + "section." + file;
        return Button.builder(Component.translatable(key),
                button -> minecraft.setScreen(new SpecScreen(this, options, Component.translatable(key + ".title"), spec))).build();
    }

    /** La liste des réglages d'un fichier ; il est réécrit en quittant l'écran. */
    private static final class SpecScreen extends OptionsSubScreen {
        private final ModConfigSpec spec;

        SpecScreen(Screen parent, Options options, Component title, ModConfigSpec spec) {
            super(parent, options, title);
            this.spec = spec;
        }

        @Override
        protected void addOptions() {
            for (ModConfigSpec.Value<?> value : spec.values()) {
                OptionInstance<?> option = option(value);
                if (option != null) {
                    list.addBig(option);
                }
            }
        }

        @Override
        public void removed() {
            super.removed();
            spec.save();
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (spec == ServerConfig.SPEC && server != null) {
                server.execute(() -> ServerConfig.broadcast(server));
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static OptionInstance<?> option(ModConfigSpec.Value<?> value) {
            String name = value.translation();
            OptionInstance.TooltipSupplier tooltip = I18n.exists(name + ".tooltip")
                    ? OptionInstance.cachedConstantTooltip(Component.translatable(name + ".tooltip"))
                    : OptionInstance.noTooltip();
            if (value instanceof ModConfigSpec.BooleanValue bool) {
                return OptionInstance.createBoolean(name, tooltip, bool.get(), bool::set);
            }
            if (value instanceof ModConfigSpec.DoubleValue number) {
                // Par dixièmes : 0,1 à 10 pour les multiplicateurs, 0 à 2 pour les particules.
                int min = (int) Math.round(number.min() * 10);
                int max = (int) Math.round(number.max() * 10);
                return new OptionInstance<>(name, tooltip,
                        (caption, tenths) -> Options.genericValueLabel(caption, Component.literal(String.format(Locale.ROOT, "%.1f", tenths / 10.0))),
                        new OptionInstance.IntRange(min, max), Codec.INT, (int) Math.round(number.get() * 10),
                        tenths -> number.set(tenths / 10.0));
            }
            if (value instanceof ModConfigSpec.EnumValue choice) {
                return enumOption(name, tooltip, choice);
            }
            return null;
        }

        private static <E extends Enum<E>> OptionInstance<E> enumOption(String name, OptionInstance.TooltipSupplier<E> tooltip,
                ModConfigSpec.EnumValue<E> choice) {
            Class<E> type = choice.getDefault().getDeclaringClass();
            List<E> constants = List.of(type.getEnumConstants());
            Codec<E> codec = Codec.STRING.xmap(text -> Enum.valueOf(type, text), Enum::name);
            // Le bouton à choix écrit lui-même le nom devant la valeur.
            return new OptionInstance<>(name, tooltip, (caption, constant) -> Component.literal(constant.name()),
                    new OptionInstance.Enum<>(constants, codec), codec, choice.get(), choice::set);
        }
    }
}
