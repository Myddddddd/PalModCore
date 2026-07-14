package com.mx.palmod.client;

import com.mx.palmod.Palmod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.resource.PathPackResources;

import java.nio.file.Path;

/**
 * Registers a forced built-in resource pack that overrides a handful of Alex's
 * Mobs Animal Dictionary pages to add "Pal Info" link buttons.
 *
 * Why a pack: mod-jar assets merge into one hidden mod_resources pack where
 * SAME-PATH conflicts are won by the first mod in load order (alexsmobs sorts
 * before palmod), so plain overrides in our jar are silently ignored. A pack
 * registered at Pack.Position.TOP with required=true resolves above
 * mod_resources and reliably wins. New (non-conflicting) pages under
 * assets/alexsmobs/ still ship as normal jar assets.
 */
@Mod.EventBusSubscriber(modid = Palmod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PalmodBookPack {

    private PalmodBookPack() {}

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        if (!ModList.get().isLoaded("alexsmobs")) return;

        Path path = ModList.get().getModFileById(Palmod.MODID).getFile()
                .findResource("packs/palmod_am_book");
        Pack pack = Pack.readMetaAndCreate(
                "palmod:am_book_overrides",
                Component.literal("Palmod Dictionary Pages"),
                true, // required => always enabled, hidden from the pack UI
                id -> new PathPackResources(id, true, path),
                PackType.CLIENT_RESOURCES,
                Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }
}
