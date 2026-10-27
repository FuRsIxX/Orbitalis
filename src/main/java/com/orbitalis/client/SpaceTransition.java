package com.orbitalis.client;

import com.orbitalis.Orbitalis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Passage progressif de l'air à l'espace, au-dessus de la Terre.
 * <p>
 * À mesure qu'on s'élève, le ciel fonce : du bleu du jour au bleu profond de la haute atmosphère, puis au noir, et
 * les étoiles paraissent en plein jour. L'horizon, lui, garde plus longtemps sa lueur bleue : c'est la mince couche
 * d'air qu'on voit sur la tranche, au-dessus de la Terre restée bleue en contrebas. Tout en haut, le ciel est celui de
 * l'orbite : noir, étoilé, avec la Terre sous soi.
 * <p>
 * Seule la hauteur compte : la montée d'une fusée comme la descente d'une capsule passent par les mêmes couleurs.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class SpaceTransition {
    /** Hauteur où le ciel commence à foncer, et hauteur où il est tout à fait celui de l'espace, en blocs. */
    private static final double FROM = 220.0;
    private static final double TO = 800.0;
    /** Le bleu profond de la haute atmosphère, par lequel passe le ciel avant le noir. */
    private static final int HIGH_SKY = 0xFF0A1C4E;

    private SpaceTransition() {
    }

    /** Part du chemin vers l'espace, de 0 (au sol) à 1 (dans le noir), à cette hauteur. */
    public static float share(double height) {
        float t = (float) Mth.clamp((height - FROM) / (TO - FROM), 0.0, 1.0);
        return t * t * (3.0F - 2.0F * t);
    }

    /** La même part, pour la caméra, là où elle se trouve ; 0 hors de la Terre. */
    private static float share(net.minecraft.client.Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) {
            return 0.0F;
        }
        return share(camera.position().y);
    }

    @SubscribeEvent
    static void onSky(ExtractLevelRenderStateEvent event) {
        float share = share(event.getCamera());
        if (share <= 0.0F) {
            return;
        }
        SkyRenderState sky = event.getRenderState().skyRenderState;
        // Le zénith fonce le premier : bleu profond à mi-chemin, noir aux trois quarts.
        float deep = Mth.clamp(share / 0.5F, 0.0F, 1.0F);
        float black = Mth.clamp((share - 0.4F) / 0.5F, 0.0F, 1.0F);
        sky.skyColor = ARGB.srgbLerp(black, ARGB.srgbLerp(deep, sky.skyColor, HIGH_SKY), 0xFF000000);
        sky.starBrightness = Math.max(sky.starBrightness, share * share * 0.6F);
        // Plus de pluie, plus de lever de soleil rougeoyant au-dessus de l'air.
        sky.rainBrightness = Mth.lerp(share, sky.rainBrightness, 1.0F);
        sky.sunriseAndSunsetColor = ARGB.srgbLerp(share, sky.sunriseAndSunsetColor, sky.sunriseAndSunsetColor & 0x00FFFFFF);
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.ComputeFogColor event) {
        float share = share(event.getCamera());
        if (share <= 0.0F) {
            return;
        }
        // L'horizon bleuit (le rouge et le vert s'en vont, le bleu reste), puis s'assombrit un peu tout en haut,
        // sans s'éteindre : c'est la Terre, qu'on voit toujours sous soi.
        float tint = Mth.clamp(share / 0.6F, 0.0F, 1.0F);
        float out = 1.0F - 0.45F * Mth.clamp((share - 0.6F) / 0.4F, 0.0F, 1.0F);
        event.setRed(event.getRed() * Mth.lerp(tint, 1.0F, 0.22F) * out);
        event.setGreen(event.getGreen() * Mth.lerp(tint, 1.0F, 0.42F) * out);
        event.setBlue(event.getBlue() * Mth.lerp(tint, 1.0F, 0.85F) * out);
    }
}
