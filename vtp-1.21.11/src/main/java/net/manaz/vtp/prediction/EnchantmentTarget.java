package net.manaz.vtp.prediction;

import net.manaz.vtp.gui.VtpUi;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.trade.ObservedOffer;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Something to roll a villager for.
 *
 * <ul>
 *   <li>{@code BOOK}: an enchanted book with {@code enchantmentKey} at least {@code minLevel}.</li>
 *   <li>{@code GEAR}: armor, a tool or weapon carrying {@code enchantmentKey} at least {@code minLevel};
 *       {@code id} limits it to one item (empty for any).</li>
 *   <li>{@code STEW}: suspicious stew with the mob effect {@code id}.</li>
 *   <li>{@code ARROW}: tipped arrows with the potion {@code id}.</li>
 *   <li>{@code TRADE}: any trade that buys or sells the item {@code id}.</li>
 * </ul>
 * {@code maxPrice} caps the first cost (emeralds) where it applies; -1 means any price.
 */
public record EnchantmentTarget(EnchantmentTarget.Kind kind, ResourceKey<Enchantment> enchantmentKey, int minLevel, int maxPrice, String id) {
   public EnchantmentTarget {
      id = id == null ? "" : id;
   }

   public EnchantmentTarget(ResourceKey<Enchantment> enchantmentKey, int minLevel, int maxPrice) {
      this(EnchantmentTarget.Kind.BOOK, enchantmentKey, minLevel, maxPrice, "");
   }

   public EnchantmentTarget(ResourceKey<Enchantment> enchantmentKey, int minLevel) {
      this(enchantmentKey, minLevel, -1);
   }

   public static EnchantmentTarget gear(ResourceKey<Enchantment> enchantmentKey, int minLevel, int maxPrice, String item) {
      return new EnchantmentTarget(EnchantmentTarget.Kind.GEAR, enchantmentKey, minLevel, maxPrice, item);
   }

   public static EnchantmentTarget stew(String effect) {
      return new EnchantmentTarget(EnchantmentTarget.Kind.STEW, null, 1, -1, effect);
   }

   public static EnchantmentTarget arrow(String potion, int maxPrice) {
      return new EnchantmentTarget(EnchantmentTarget.Kind.ARROW, null, 1, maxPrice, potion);
   }

   public static EnchantmentTarget trade(String item) {
      return new EnchantmentTarget(EnchantmentTarget.Kind.TRADE, null, 1, -1, item);
   }

   public boolean isBook() {
      return this.kind == EnchantmentTarget.Kind.BOOK;
   }

   /** Book targets only. */
   public boolean matches(Holder<Enchantment> enchantment, int level) {
      return this.isBook() && level >= this.minLevel && enchantment.is(this.enchantmentKey);
   }

   /** Book targets only. */
   public boolean matches(Holder<Enchantment> enchantment, int level, int emeraldPrice) {
      return !this.matches(enchantment, level) ? false : this.maxPrice < 0 || emeraldPrice <= this.maxPrice;
   }

   /** Book targets only. */
   public boolean matchesKey(String enchantmentKeyStr, int level, int emeraldPrice) {
      if (!this.isBook() || level < this.minLevel) {
         return false;
      } else {
         return !this.enchantmentKey.identifier().toString().equals(enchantmentKeyStr) ? false : this.maxPrice < 0 || emeraldPrice <= this.maxPrice;
      }
   }

   public boolean matches(TradeSimulator.SimulatedOffer offer) {
      TradeSimulator.Rolled rolled = offer.rolled();
      return switch (this.kind) {
         case BOOK -> offer.enchantment().filter(e -> this.matches(e.enchantment(), e.level(), e.emeraldPrice())).isPresent();
         case GEAR -> rolled != null
            && (this.id.isEmpty() || this.id.equals(rolled.result()))
            && this.priceOk(rolled.priceA())
            && rolled.gear().stream().anyMatch(this::matchesEnchantment);
         case STEW -> rolled != null && this.id.equals(rolled.effect());
         case ARROW -> rolled != null && this.id.equals(rolled.potion()) && this.priceOk(rolled.priceA());
         case TRADE -> rolled != null && (this.id.equals(rolled.result()) || this.id.equals(rolled.costA()) || this.id.equals(rolled.costB()));
      };
   }

   private boolean matchesEnchantment(ObservedOffer.Book enchantment) {
      return enchantment.level() >= this.minLevel && enchantment.key().equals(this.enchantmentKey.identifier().toString());
   }

   private boolean priceOk(int price) {
      return this.maxPrice < 0 || price <= this.maxPrice;
   }

   /** The emerald price of the offer that satisfies this target, or -1. */
   public int priceOf(TradeSimulator.SimulatedOffer offer) {
      if (!this.matches(offer)) {
         return -1;
      } else {
         return this.isBook() ? offer.enchantment().map(TradeSimulator.EnchantmentResult::emeraldPrice).orElse(-1) : offer.rolled().priceA();
      }
   }

   public String label() {
      return switch (this.kind) {
         case BOOK -> enchantment();
         case GEAR -> (this.id.isEmpty() ? "Gear" : VtpUi.name(this.id)) + ": " + enchantment();
         case STEW -> "Stew: " + VtpUi.name(this.id);
         case ARROW -> "Arrows: " + VtpUi.name(this.id);
         case TRADE -> "Trade: " + VtpUi.name(this.id);
      };
   }

   private String enchantment() {
      return VtpUi.name(this.enchantmentKey.identifier().toString()) + " " + VillagerHudRenderer.fmtLvl(this.minLevel);
   }

   public static enum Kind {
      BOOK,
      GEAR,
      STEW,
      ARROW,
      TRADE;
   }
}
