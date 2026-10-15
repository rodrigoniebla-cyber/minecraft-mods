package net.manaz.vtp.trade;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.enchantment.Enchantment;

/** What villagers can offer, read from the bundled 26.2 trade definitions. */
public final class TargetCatalog {
   private TargetCatalog() {
   }

   public record Source(Holder<VillagerProfession> profession, int level, TradeSet set) {
   }

   public static List<Source> sources() {
      List<Source> sources = new ArrayList<>();

      for (Holder.Reference<VillagerProfession> profession : BuiltInRegistries.VILLAGER_PROFESSION.listElements().toList()) {
         for (int level = 1; level <= 5; level++) {
            ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession, level);
            Optional<TradeSet> set = TradeDataLoader.getTradeSet(key);
            if (set.isPresent()) {
               sources.add(new Source(profession, level, set.get()));
            }
         }
      }

      return sources;
   }

   public static List<VillagerTrade> trades() {
      List<VillagerTrade> trades = new ArrayList<>();
      sources().forEach(source -> trades.addAll(source.set().trades()));
      return trades;
   }

   /** Items sold with random enchantments (armor, tools, weapons, bows, fishing rods). */
   public static List<String> gearItems() {
      SequencedSet<String> items = new LinkedHashSet<>();
      trades().stream().filter(TradeSimulator::enchantsGear).forEach(t -> items.add(t.gives().itemId()));
      return List.copyOf(items);
   }

   /** Enchantments that can appear on traded gear. */
   public static List<Holder<Enchantment>> gearEnchantments() {
      SequencedSet<Holder<Enchantment>> result = new LinkedHashSet<>();
      trades().stream().filter(TradeSimulator::enchantsGear).forEach(t -> result.addAll(TradeSimulator.gearEnchantmentOptions(t)));
      return List.copyOf(result);
   }

   public static List<String> stewEffects() {
      SequencedSet<String> effects = new LinkedHashSet<>();
      trades().forEach(t -> effects.addAll(TradeSimulator.stewEffects(t)));
      return List.copyOf(effects);
   }

   public static List<String> arrowPotions() {
      SequencedSet<String> potions = new LinkedHashSet<>();
      trades().forEach(t -> potions.addAll(TradeSimulator.potions(t)));
      return List.copyOf(potions);
   }

   public static List<String> tradeItems() {
      SequencedSet<String> items = new LinkedHashSet<>();
      trades().forEach(t -> items.addAll(TradeSimulator.tradeItems(t)));
      items.remove("minecraft:emerald");
      return items.stream().sorted().toList();
   }

   /** Whether a trade set can ever produce something matching the target. */
   public static boolean canProduce(TradeSet set, EnchantmentTarget target) {
      for (VillagerTrade trade : set.trades()) {
         boolean possible = switch (target.kind()) {
            case BOOK -> TradeSimulator.enchantmentOptions(trade).stream().anyMatch(e -> e.is(target.enchantmentKey()));
            case GEAR -> TradeSimulator.enchantsGear(trade)
               && (target.id().isEmpty() || target.id().equals(trade.gives().itemId()))
               && TradeSimulator.gearEnchantmentOptions(trade).stream().anyMatch(e -> e.is(target.enchantmentKey()) && e.value().getMaxLevel() >= target.minLevel());
            case STEW -> TradeSimulator.stewEffects(trade).contains(target.id());
            case ARROW -> TradeSimulator.potions(trade).contains(target.id());
            case TRADE -> TradeSimulator.tradeItems(trade).contains(target.id());
         };
         if (possible) {
            return true;
         }
      }

      return false;
   }
}
