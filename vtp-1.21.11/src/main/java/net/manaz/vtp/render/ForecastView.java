package net.manaz.vtp.render;

import java.util.List;
import java.util.UUID;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;

public final class ForecastView {
   private ForecastView() {
   }

   public static String levelText(UUID uuid, VillagerData data, int level, long seed) {
      String prefix = "Lv." + level + ": ";
      if (VillagerTracker.hasSavedLevel(uuid, level)) {
         List<VillagerTracker.OfferData> saved = VillagerTracker.getSavedLevelOffers(uuid, level);
         return prefix
            + (
               saved.isEmpty()
                  ? "no enchanted book"
                  : String.join(
                     ", ",
                     saved.stream()
                        .map(o -> name(o.enchantmentKey()) + " " + VillagerHudRenderer.fmtLvl(o.enchantmentLevel()) + " (" + o.additionalCost() + " base em)")
                        .toList()
                  )
            )
            + " [observed]";
      } else if (level > data.level() || level == data.level() && VillagerTracker.isAwaitingOffers(uuid)) {
         List<List<TradeSimulator.SimulatedOffer>> rounds = PredictionEngine.getForecastRounds(seed, data, level, 1);
         if (rounds.isEmpty()) {
            return prefix + "forecast unavailable";
         } else {
            List<String> books = OfferText.highlights(rounds.getFirst())
               .stream()
               .map(h -> h.text() + (h.price() >= 0 ? " (" + h.price() + " base em)" : ""))
               .toList();
            return prefix + (books.isEmpty() ? "nothing notable" : String.join(", ", books)) + " [forecast]";
         }
      } else {
         return prefix + "not observed";
      }
   }

   private static String name(String key) {
      String value = key.substring(key.lastIndexOf(58) + 1).replace('_', ' ');
      return Character.toUpperCase(value.charAt(0)) + value.substring(1);
   }
}
