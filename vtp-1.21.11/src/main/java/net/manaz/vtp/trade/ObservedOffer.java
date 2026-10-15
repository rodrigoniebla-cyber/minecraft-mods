package net.manaz.vtp.trade;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The parts of a merchant offer that the 26.2 trade roll decides: items, base prices, book and gear
 * enchantments, stew effect, dye colour and potion. Two offers with equal fingerprints came from the
 * same roll.
 */
public record ObservedOffer(
   String result,
   int count,
   String costA,
   int basePrice,
   String costB,
   int countB,
   List<ObservedOffer.Book> books,
   List<ObservedOffer.Book> gear,
   String effect,
   int dye,
   String potion
) {
   public ObservedOffer {
      books = books == null ? List.of() : books;
      gear = gear == null ? List.of() : gear;
      effect = effect == null ? "" : effect;
      potion = potion == null ? "" : potion;
   }

   public ObservedOffer(String result, int count, String costA, int basePrice, String costB, int countB, List<ObservedOffer.Book> books) {
      this(result, count, costA, basePrice, costB, countB, books, List.of(), "", -1, "");
   }

   public static ObservedOffer from(MerchantOffer offer) {
      ItemStack result = offer.getResult();
      SuspiciousStewEffects stew = result.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
      DyedItemColor dye = result.get(DataComponents.DYED_COLOR);
      PotionContents potion = result.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
      return new ObservedOffer(
         id(result),
         result.getCount(),
         id(offer.getBaseCostA()),
         offer.getBaseCostA().getCount(),
         id(offer.getCostB()),
         offer.getCostB().getCount(),
         enchantments(result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)),
         enchantments(result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)),
         stew.effects().isEmpty() ? "" : stew.effects().getFirst().effect().unwrapKey().map(k -> k.identifier().toString()).orElse(""),
         dye == null ? -1 : dye.rgb() & 0xFFFFFF,
         potion.potion().flatMap(p -> p.unwrapKey()).map(k -> k.identifier().toString()).orElse("")
      );
   }

   static List<ObservedOffer.Book> enchantments(ItemEnchantments enchantments) {
      return enchantments.keySet()
         .stream()
         .map(h -> new ObservedOffer.Book(h.unwrapKey().orElseThrow().identifier().toString(), enchantments.getLevel(h)))
         .sorted(Comparator.comparing(ObservedOffer.Book::key))
         .toList();
   }

   private static String id(ItemStack stack) {
      return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
   }

   public static List<ObservedOffer> copyOf(List<MerchantOffer> offers) {
      return offers.stream().map(ObservedOffer::from).toList();
   }

   public record Book(String key, int level) {
   }
}
