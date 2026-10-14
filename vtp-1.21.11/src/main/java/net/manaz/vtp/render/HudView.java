package net.manaz.vtp.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.prediction.PredictionResult;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.enchantment.Enchantment;

public record HudView(
   UUID uuid, String target, String result, int price, boolean found, String warning, List<HudView.Row> trades, List<HudView.Row> levels, boolean locked
) {
   private static HudView current;

   public static HudView current() {
      return current;
   }

   public static int futureIndex(boolean pending, int rerolls) {
      return rerolls - (pending ? 0 : 1);
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      UUID uuid = VillagerTracker.getLastVillagerUuid();
      Optional<VillagerData> dataOpt = VillagerTracker.getLastVillagerData();
      if (mc.level != null && uuid != null && !dataOpt.isEmpty()) {
         VillagerData data = dataOpt.get();
         if (!data.profession().is(VillagerProfession.NONE) && !data.profession().is(VillagerProfession.NITWIT)) {
            boolean locked = data.level() > 1 || VillagerTracker.isTradeLocked(uuid);
            if (!SeedProvider.hasSeed()) {
               Optional<List<TradeSimulator.SimulatedOffer>> observed = VillagerTracker.getCurrentOffers(uuid);
               current = new HudView(
                  uuid,
                  "No seed set",
                  "Set seed in /vtp",
                  -1,
                  false,
                  "",
                  observed.<List<HudView.Row>>map(offers -> bookRows(locked ? "" : "Current", (List<TradeSimulator.SimulatedOffer>)offers, false))
                     .orElse(List.of()),
                  List.of(),
                  locked
               );
            } else {
               long seed = SeedProvider.getSeed().orElseThrow();
               List<EnchantmentTarget> targets = VillagerHudRenderer.getTargets();
               PredictionResult best = null;
               EnchantmentTarget selected = null;

               for (EnchantmentTarget target : targets) {
                  PredictionResult result = PredictionEngine.predictForVillager(seed, uuid, data, target);
                  if (best == null || result.found() && (!best.found() || result.rerollsNeeded().orElseThrow() < best.rerollsNeeded().orElseThrow())) {
                     best = result;
                     selected = target;
                  }
               }

               String targetx = selected == null
                  ? "No target"
                  : VtpUi.name(selected.enchantmentKey().identifier().toString()) + " " + VillagerHudRenderer.fmtLvl(selected.minLevel());
               String result = "Add a target in /vtp";
               int price = -1;
               boolean found = best != null && best.found();
               if (found) {
                  int count = best.rerollsNeeded().orElseThrow();
                  result = count == 0 ? "Available" : count + (count == 1 ? " reroll" : " rerolls");

                  for (TradeSimulator.SimulatedOffer offer : best.targetRollOffers()) {
                     if (offer.enchantment().isPresent()) {
                        TradeSimulator.EnchantmentResult e = offer.enchantment().get();
                        if (selected.matches(e.enchantment(), e.level(), e.emeraldPrice())) {
                           price = e.emeraldPrice();
                           break;
                        }
                     }
                  }
               } else if (best != null) {
                  result = locked ? "Trades locked" : (best.maxRerollsSearched() > 0 ? "Over " + best.maxRerollsSearched() + " rerolls" : "Waiting for trades");
               }

               String status = VillagerTracker.status(uuid, data);
               String warning = status.startsWith("Uncertain")
                  ? "Check alignment in /vtp"
                  : (status.startsWith("Forecast unavailable") ? "Forecast unavailable" : "");
               List<List<TradeSimulator.SimulatedOffer>> future = locked
                  ? List.of()
                  : PredictionEngine.getForecastRounds(seed, data.profession(), data.level(), 5);
               Optional<List<TradeSimulator.SimulatedOffer>> actual = VillagerTracker.getCurrentOffers(uuid);
               boolean pending = VillagerTracker.isAwaitingOffers(uuid);
               List<HudView.Row> trades = new ArrayList<>();
               if (locked) {
                  trades.addAll(
                     actual.<List<? extends HudView.Row>>map(offers -> bookRows("", (List<TradeSimulator.SimulatedOffer>)offers, false))
                        .orElse(List.of(new HudView.Row("", "Open trades to view", -1, false, false)))
                  );
               } else {
                  trades.addAll(bookRows("Current", actual.orElseGet(() -> pending && !future.isEmpty() ? future.getFirst() : null), actual.isEmpty()));

                  for (int reroll = 1; reroll <= 3; reroll++) {
                     int index = futureIndex(pending, reroll);
                     trades.addAll(bookRows("+" + reroll, index < future.size() ? future.get(index) : null, true));
                  }
               }

               List<HudView.Row> levels = new ArrayList<>();

               for (int level = locked ? data.level() + 1 : 2; level <= 5; level++) {
                  String label = switch (level) {
                     case 2 -> "II";
                     case 3 -> "III";
                     case 4 -> "IV";
                     default -> "V";
                  };
                  if (VillagerTracker.hasSavedLevel(uuid, level)) {
                     List<VillagerTracker.OfferData> saved = VillagerTracker.getSavedLevelOffers(uuid, level);
                     if (saved.isEmpty()) {
                        levels.add(new HudView.Row(label, "—", -1, false, false));
                     }

                     for (VillagerTracker.OfferData e : saved) {
                        boolean match = targets.stream().anyMatch(t -> t.matchesKey(e.enchantmentKey(), e.enchantmentLevel(), e.additionalCost()));
                        levels.add(
                           new HudView.Row(
                              label, VtpUi.name(e.enchantmentKey()) + " " + VillagerHudRenderer.fmtLvl(e.enchantmentLevel()), e.additionalCost(), match, false
                           )
                        );
                     }
                  } else if (level > data.level() || level == data.level() && pending) {
                     List<List<TradeSimulator.SimulatedOffer>> rounds = PredictionEngine.getForecastRounds(seed, data.profession(), level, 1);
                     levels.addAll(bookRows(label, rounds.isEmpty() ? null : rounds.getFirst(), true));
                  } else {
                     levels.add(new HudView.Row(label, "Not observed", -1, false, false));
                  }
               }

               current = new HudView(uuid, targetx, result, price, found, warning, List.copyOf(trades), List.copyOf(levels), locked);
            }
         } else {
            current = null;
         }
      } else {
         current = null;
      }
   }

   public static List<HudView.Row> bookRows(String label, List<TradeSimulator.SimulatedOffer> offers, boolean forecast) {
      if (offers == null) {
         return List.of(new HudView.Row(label, "…", -1, false, forecast));
      } else {
         List<TradeSimulator.EnchantmentResult> books = offers.stream().flatMap(o -> o.enchantment().stream()).toList();
         return books.isEmpty()
            ? List.of(new HudView.Row(label, "—", -1, false, forecast))
            : books.stream()
               .map(
                  e -> new HudView.Row(
                     label,
                     ((Enchantment)e.enchantment().value()).description().getString() + " " + VillagerHudRenderer.fmtLvl(e.level()),
                     e.emeraldPrice(),
                     VillagerHudRenderer.getTargets().stream().anyMatch(t -> t.matches(e.enchantment(), e.level(), e.emeraldPrice())),
                     forecast
                  )
               )
               .toList();
      }
   }

   public record Row(String label, String text, int price, boolean match, boolean forecast) {
   }
}
