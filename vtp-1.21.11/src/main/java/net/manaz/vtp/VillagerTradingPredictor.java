package net.manaz.vtp;

import java.util.OptionalLong;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.manaz.vtp.command.VtpCommand;
import net.manaz.vtp.command.VtpKeybinds;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.SingleplayerSequences;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.HudVisibility;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.seed.ServerSeedStore;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTradingPredictor implements ClientModInitializer {
   public static final String MOD_ID = "villager-trading-predictor";
   public static final Logger LOGGER = LoggerFactory.getLogger("villager-trading-predictor");

   public void onInitializeClient() {
      LOGGER.info("Villager Trading Predictor initializing...");
      VtpPersistence.loadGlobalSettings();
      VtpCommand.register();
      VtpKeybinds.register();
      VillagerHudRenderer.register();
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> {
         VtpPersistence.beginSession();
         TradeDataLoader.clearEnchantmentCache();
         PredictionEngine.clearCache();
         SeedProvider.onWorldJoin();
         if (client.getSingleplayerServer() == null) {
            ServerData serverData = client.getCurrentServer();
            if (serverData != null) {
               ServerSeedStore.getSeedForServer(serverData.ip).ifPresent(savedSeed -> {
                  SeedProvider.setManualSeed(savedSeed);
                  LOGGER.info("[VTP] Auto-restored seed {} for server {}", savedSeed, serverData.ip);
               });
            }
         }

         OptionalLong seed = SeedProvider.getSeed();
         if (seed.isPresent()) {
            VtpPersistence.load(seed.getAsLong());
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         TradeDataLoader.resetRegistryLog();
         VtpPersistence.endSession();
         SeedProvider.onWorldLeave();
         VillagerTracker.clear();
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.screen instanceof MerchantScreen) {
            UUID uuid = VillagerTracker.getMenuVillagerUuid();
            VillagerTracker.getData(uuid).ifPresent(data -> VillagerTracker.updateAndSelect(uuid, data));
         } else if (client.crosshairPickEntity instanceof Villager villager) {
            VillagerTracker.updateAndSelect(villager.getUUID(), villager.getVillagerData());
         }

         SingleplayerSequences.tick(client);
         PredictionEngine.prepareView();
         HudView.tick();
         HudVisibility.tick(client);
         VtpPersistence.tick();
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         VtpPersistence.endSession();
         VtpPersistence.flush();
      });
      LOGGER.info("Villager Trading Predictor initialized.");
   }
}
