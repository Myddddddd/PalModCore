package com.mx.palmod.client;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Client-only opener for the Paldex. Reuses the Alex's Mobs / Citadel dictionary
 * book engine ({@code GUIAnimalDictionary} → Citadel {@code GuiBasicBook}) via
 * reflection — no custom Screen, no compile dependency on AM. Opens directly at
 * Palmod's index page (the table-of-contents).
 *
 * <p>NOTE: the page name is passed WITHOUT the {@code .json} extension —
 * {@code GUIAnimalDictionary(ItemStack, String)} builds its resource as
 * {@code alexsmobs:book/animal_dictionary/<page>.json} (it appends {@code .json}
 * itself). Passing the extension yields {@code …palmod_paldex.json.json}, which
 * is not found and renders a blank book.</p>
 */
public final class PaldexClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PALDEX_ROOT_PAGE = "palmod_paldex";

    private PaldexClient() {}

    public static void open(ItemStack stack) {
        try {
            Class<?> amClass = Class.forName("com.github.alexthe666.alexsmobs.AlexsMobs");
            Object proxy = amClass.getField("PROXY").get(null);
            proxy.getClass()
                    .getMethod("openBookGUI", ItemStack.class, String.class)
                    .invoke(proxy, stack, PALDEX_ROOT_PAGE);
        } catch (Throwable t) {
            LOGGER.warn("Paldex: could not open the Alex's Mobs dictionary book "
                    + "(is Alex's Mobs installed?)", t);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.palmod.paldex_needs_am")
                                .withStyle(ChatFormatting.RED), true);
            }
        }
    }
}
