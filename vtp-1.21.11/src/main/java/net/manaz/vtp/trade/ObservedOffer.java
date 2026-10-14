package net.manaz.vtp.trade;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

public record ObservedOffer(String result, int count, String costA, int basePrice, String costB, int countB, List<ObservedOffer.Book> books) {
   public static ObservedOffer from(MerchantOffer offer) {
      ItemStack result = offer.getResult();
      ItemEnchantments ench = (ItemEnchantments)result.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
      List<ObservedOffer.Book> books = ench.keySet()
         .stream()
         .map(h -> new ObservedOffer.Book(((ResourceKey)h.unwrapKey().orElseThrow()).identifier().toString(), ench.getLevel(h)))
         .sorted(Comparator.comparing(ObservedOffer.Book::key))
         .toList();
      return new ObservedOffer(
         id(result), result.getCount(), id(offer.getBaseCostA()), offer.getBaseCostA().getCount(), id(offer.getCostB()), offer.getCostB().getCount(), books
      );
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
