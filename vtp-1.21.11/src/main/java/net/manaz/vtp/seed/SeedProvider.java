package net.manaz.vtp.seed;

import java.util.OptionalLong;
import net.manaz.vtp.VillagerTradingPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

public class SeedProvider {
   private static OptionalLong manualSeed = OptionalLong.empty();
   private static boolean seedLogged = false;

   public static void setManualSeed(long seed) {
      manualSeed = OptionalLong.of(seed);
      seedLogged = false;
      VillagerTradingPredictor.LOGGER.info("Manual seed set to: {}", seed);
   }

   public static void clearManualSeed() {
      manualSeed = OptionalLong.empty();
      seedLogged = false;
   }

   public static OptionalLong getSeed() {
      if (manualSeed.isPresent()) {
         return manualSeed;
      } else {
         Minecraft client = Minecraft.getInstance();
         IntegratedServer server = client.getSingleplayerServer();
         if (server != null) {
            long seed = server.overworld().getSeed();
            if (!seedLogged) {
               VillagerTradingPredictor.LOGGER.info("Auto-detected world seed: {}", seed);
               seedLogged = true;
            }

            return OptionalLong.of(seed);
         } else {
            return OptionalLong.empty();
         }
      }
   }

   public static boolean hasSeed() {
      return getSeed().isPresent();
   }

   public static void onWorldJoin() {
      seedLogged = false;
   }

   public static void onWorldLeave() {
      manualSeed = OptionalLong.empty();
      seedLogged = false;
   }
}
