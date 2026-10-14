package net.manaz.vtp.trade;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.enchantment.Enchantment;

public final class TradePrices {
   private TradePrices() {
   }

   public static OptionalInt cheapest(Holder<Enchantment> enchantment, int level) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && enchantment != null && level >= enchantment.value().getMinLevel() && level <= enchantment.value().getMaxLevel()) {
         Optional<Holder.Reference<VillagerProfession>> librarian = mc.level
            .registryAccess()
            .lookupOrThrow(Registries.VILLAGER_PROFESSION)
            .get(ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.withDefaultNamespace("librarian")));
         if (librarian.isEmpty()) {
            return OptionalInt.empty();
         } else {
            int cheapest = Integer.MAX_VALUE;

            for (int tier = 1; tier <= 5; tier++) {
               ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(librarian.get(), tier);
               if (key != null) {
                  Optional<TradeSet> set = TradeDataLoader.getTradeSet(key);
                  if (!set.isEmpty() && TradeSimulator.compatibilityProblem(set.get()).isEmpty()) {
                     for (VillagerTrade trade : TradeDataLoader.getTradesFromSet(set.get())) {
                        if (trade.wants().itemId().equals("minecraft:emerald") && trade.enchantRandomly().isPresent()) {
                           boolean available = TradeSimulator.enchantmentOptions(trade)
                              .stream()
                              .anyMatch(option -> option.unwrapKey().equals(enchantment.unwrapKey()));
                           if (available) {
                              int cost = trade.enchantRandomly().get().includeAdditionalCost() ? 2 + 3 * level : 0;
                              if (TradeSimulator.doublesPrice(trade, enchantment)) {
                                 cost *= 2;
                              }

                              int price = trade.wants().toItemCount(cost);
                              if (price >= 1) {
                                 cheapest = Math.min(cheapest, price);
                              }
                           }
                        }
                     }
                  }
               }
            }

            return cheapest == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(cheapest);
         }
      } else {
         return OptionalInt.empty();
      }
   }
}
