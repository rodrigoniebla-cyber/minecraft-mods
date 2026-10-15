package net.manaz.vtp.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.trade.ObservedOffer;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/** Short descriptions of the offers worth looking at in a roll: books, enchanted gear, stew, arrows, maps and targeted trades. */
public final class OfferText {
   private OfferText() {
   }

   public record Highlight(String text, int price, boolean match, TradeSimulator.SimulatedOffer offer) {
   }

   public static List<Highlight> highlights(List<TradeSimulator.SimulatedOffer> offers) {
      List<EnchantmentTarget> targets = VillagerHudRenderer.getTargets();
      List<Highlight> result = new ArrayList<>();

      for (TradeSimulator.SimulatedOffer offer : offers) {
         boolean match = targets.stream().anyMatch(t -> t.matches(offer));
         describe(offer, match).ifPresent(text -> result.add(new Highlight(text, price(offer), match, offer)));
      }

      return result;
   }

   private static int price(TradeSimulator.SimulatedOffer offer) {
      return offer.enchantment().map(TradeSimulator.EnchantmentResult::emeraldPrice).orElseGet(() -> offer.rolled() == null ? -1 : offer.rolled().priceA());
   }

   private static Optional<String> describe(TradeSimulator.SimulatedOffer offer, boolean match) {
      if (offer.enchantment().isPresent()) {
         TradeSimulator.EnchantmentResult e = offer.enchantment().get();
         return Optional.of(e.enchantment().value().description().getString() + " " + VillagerHudRenderer.fmtLvl(e.level()));
      } else {
         TradeSimulator.Rolled rolled = offer.rolled();
         if (rolled == null) {
            return Optional.empty();
         } else if (!rolled.gear().isEmpty()) {
            return Optional.of(item(rolled.result()) + ": " + String.join(", ", rolled.gear().stream().map(OfferText::enchantment).toList()));
         } else if (!rolled.effect().isEmpty()) {
            return Optional.of("Stew: " + VtpUi.name(rolled.effect()));
         } else if (!rolled.potion().isEmpty()) {
            return Optional.of("Arrows: " + VtpUi.name(rolled.potion()));
         } else if (rolled.result().equals("minecraft:filled_map")) {
            return Optional.of(offer.tradeDefinition() == null ? "Explorer map" : "Map: " + mapName(offer.tradeDefinition().id().getPath()));
         } else if (match) {
            return Optional.of(trade(rolled));
         } else {
            return Optional.empty();
         }
      }
   }

   /** "Buys 8 Tripwire Hook" / "Sells Cake". */
   public static String trade(TradeSimulator.Rolled rolled) {
      return rolled.result().equals("minecraft:emerald")
         ? "Buys " + rolled.priceA() + " " + item(rolled.costA())
         : "Sells " + (rolled.count() > 1 ? rolled.count() + " " : "") + item(rolled.result());
   }

   private static String mapName(String tradePath) {
      String name = tradePath.substring(tradePath.lastIndexOf('/') + 1).replace("emerald_and_compass_", "").replace("_map", "");
      return VtpUi.name(name);
   }

   public static String item(String id) {
      return VtpUi.name(id);
   }

   public static String enchantment(ObservedOffer.Book book) {
      Minecraft mc = Minecraft.getInstance();
      String name = mc.level == null
         ? VtpUi.name(book.key())
         : mc.level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(book.key())))
            .map(h -> ((Enchantment)h.value()).description().getString())
            .orElse(VtpUi.name(book.key()));
      return name + " " + VillagerHudRenderer.fmtLvl(book.level());
   }
}
