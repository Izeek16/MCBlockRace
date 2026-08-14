package com.toasterz.blockrace;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class BlockRaceMod implements ModInitializer {

    @Override
    public void onInitialize() {
        RaceCommands.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> RaceManager.INSTANCE.tick(server));
    }
}
