package net.manaz.vtp.prediction;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

public record EnchantmentTarget(ResourceKey<Enchantment> enchantmentKey, int minLevel, int maxPrice) {
   public EnchantmentTarget(ResourceKey<Enchantment> enchantmentKey, int minLevel) {
      this(enchantmentKey, minLevel, -1);
   }

   public boolean matches(Holder<Enchantment> enchantment, int level) {
      return level < this.minLevel ? false : enchantment.is(this.enchantmentKey);
   }

   public boolean matches(Holder<Enchantment> enchantment, int level, int emeraldPrice) {
      return !this.matches(enchantment, level) ? false : this.maxPrice < 0 || emeraldPrice <= this.maxPrice;
   }

   public boolean matchesKey(String enchantmentKeyStr, int level, int emeraldPrice) {
      if (level < this.minLevel) {
         return false;
      } else {
         return !this.enchantmentKey.identifier().toString().equals(enchantmentKeyStr) ? false : this.maxPrice < 0 || emeraldPrice <= this.maxPrice;
      }
   }
}
