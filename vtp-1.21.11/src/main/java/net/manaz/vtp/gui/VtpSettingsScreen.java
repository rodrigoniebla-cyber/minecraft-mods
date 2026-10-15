package net.manaz.vtp.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.prediction.CalibrationListStore;
import net.manaz.vtp.prediction.CalibrationService;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.manaz.vtp.render.OfferText;
import net.manaz.vtp.trade.TargetCatalog;
import net.manaz.vtp.trade.TradePrices;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.manaz.vtp.trade.TradeSet;
import net.manaz.vtp.trade.VillagerTrade;
import net.minecraft.world.level.ItemLike;

public class VtpSettingsScreen extends Screen {
   private static final String[] TABS = new String[]{"Settings", "Targets", "Sequence", "Trades", "Calibrate"};
   private static final String[] LEVELS = new String[]{"", "Novice", "Apprentice", "Journeyman", "Expert", "Master"};
   private static final String[] PROFESSIONS = new String[]{
      "armorer",
      "butcher",
      "cartographer",
      "cleric",
      "farmer",
      "fisherman",
      "fletcher",
      "leatherworker",
      "librarian",
      "mason",
      "shepherd",
      "toolsmith",
      "weaponsmith"
   };
   private static int lastTab;
   private static final String[] TARGET_KINDS = new String[]{"Books", "Gear", "Stew", "Arrow", "Trade"};
   private static int lastTargetKind;
   private int targetKind = lastTargetKind;
   private String chosenId;
   private int gearItemIndex;
   private int tab;
   private int wx;
   private int wy;
   private int ww;
   private int wh;
   private int left;
   private int right;
   private int column;
   private int top;
   private int bottom;
   private final List<VtpSettingsScreen.Label> labels;
   private final Map<String, VtpList> lists;
   private final Map<String, Integer> scrolls;
   private List<Holder<Enchantment>> enchantments;
   private Holder<Enchantment> chosen;
   private String query;
   private String price;
   private String maxSearch;
   private String seedText;
   private String message;
   private int chosenLevel;
   private int targetIndex;
   private int observationIndex;
   private int sequenceLevel;
   private int tradeLevel;
   private String professionId;
   private Button addButton;
   private Button matchButton;
   private VtpSlider priceSlider;
   private EditBox priceBox;
   private String targetPrice;
   private boolean cheapestPrice;
   private boolean updatingPrice;
   private OptionalInt minimumPrice;
   private final List<VtpButton> targetLevelButtons;
   private List<Integer> matches;
   private boolean showingMatches;
   private long viewRevision;

   public VtpSettingsScreen() {
      super(Component.literal("Villager Trading Predictor"));
      this.tab = lastTab;
      this.labels = new ArrayList<>();
      this.lists = new HashMap<>();
      this.scrolls = new HashMap<>();
      this.enchantments = List.of();
      this.query = "";
      this.price = "";
      this.maxSearch = String.valueOf(PredictionEngine.getMaxRerolls());
      this.seedText = SeedProvider.getSeed().isPresent() ? String.valueOf(SeedProvider.getSeed().orElseThrow()) : "";
      this.message = "";
      this.chosenLevel = 1;
      this.targetIndex = -1;
      this.observationIndex = -1;
      this.sequenceLevel = 1;
      this.tradeLevel = 0;
      this.professionId = "librarian";
      this.targetPrice = "64";
      this.minimumPrice = OptionalInt.empty();
      this.targetLevelButtons = new ArrayList<>();
      this.matches = List.of();
      this.viewRevision = -1L;
   }

   protected void init() {
      this.lists.forEach((key, list) -> this.scrolls.put(key, list.offset()));
      this.lists.clear();
      this.labels.clear();
      this.targetLevelButtons.clear();
      this.addButton = null;
      this.priceSlider = null;
      this.priceBox = null;
      this.ww = Math.min(520, this.width - 12);
      this.wh = Math.min(330, this.height - 12);
      this.wx = (this.width - this.ww) / 2;
      this.wy = (this.height - this.wh) / 2;
      this.left = this.wx + 10;
      this.column = (this.ww - 28) / 2;
      this.right = this.left + this.column + 8;
      this.top = this.wy + 52;
      this.bottom = this.wy + this.wh - 34;
      int tabWidth = (this.ww - 20 - 8) / TABS.length;

      for (int i = 0; i < TABS.length; i++) {
         int targetTab = i;
         VtpButton button = this.button(TABS[i], this.left + i * (tabWidth + 2), this.wy + 26, tabWidth, () -> {
            this.tab = targetTab;
            lastTab = this.tab;
            this.message = "";
            this.rebuildWidgets();
         });
         button.active = i != this.tab;
         VtpButton var10000 = button.selected(i == this.tab);
         Item var10001;
         if (tabWidth >= 90) {
            switch (i) {
               case 0:
                  var10001 = Items.COMPARATOR;
                  break;
               case 1:
                  var10001 = Items.ENCHANTED_BOOK;
                  break;
               case 2:
                  var10001 = Items.LECTERN;
                  break;
               case 3:
                  var10001 = Items.EMERALD;
                  break;
               default:
                  var10001 = Items.COMPASS;
            }
         } else {
            var10001 = null;
         }

         var10000.icon(var10001);
      }

      switch (this.tab) {
         case 0:
            this.settings();
            break;
         case 1:
            this.targets();
            break;
         case 2:
            this.sequence();
            break;
         case 3:
            this.trades();
            break;
         case 4:
            this.calibration();
      }

      this.button("Done", this.wx + this.ww - 90, this.wy + this.wh - 27, 80, this::onClose);
      this.viewRevision = PredictionEngine.revision();
   }

   private VtpButton button(String text, int x, int y, int w, Runnable action) {
      return (VtpButton)this.addRenderableWidget(new VtpButton(text, x, y, w, action));
   }

   private EditBox input(String name, String value, String hint, int x, int y, int w, Consumer<String> update) {
      EditBox box = new VtpEditBox(this.font, x, y, w, 20, Component.literal(name));
      box.setTextColor(-1);
      box.setTextColorUneditable(-6250336);
      box.setTextShadow(true);
      box.setMaxLength(80);
      box.setValue(value);
      box.setHint(Component.literal(hint));
      box.setResponder(update);
      box.setTooltip(Tooltip.create(Component.literal(name)));
      return (EditBox)this.addRenderableWidget(box);
   }

   private VtpList list(String id, int x, int y, int w, int h, String name) {
      VtpList list = (VtpList)this.addRenderableWidget(new VtpList(x, y, w, Math.max(24, h), name));
      this.lists.put(id, list);
      return list;
   }

   private void rows(String id, List<VtpList.Entry> entries) {
      VtpList list = this.lists.get(id);
      list.entries(entries);
      if (this.scrolls.containsKey(id)) {
         list.offset(this.scrolls.remove(id));
      }
   }

   private void label(String text, int x, int y, int w) {
      this.labels.add(new VtpSettingsScreen.Label(text, x, y, w, -1));
   }

   protected boolean singleplayer() {
      return Minecraft.getInstance().getSingleplayerServer() != null;
   }

   private void settings() {
      this.label("Accent color", this.left, this.top, this.column);
      new VtpColorPicker(
         this.font,
         this.left,
         this.top + 14,
         this.column,
         this.bottom - this.top - 14,
         x$0 -> this.addRenderableWidget(x$0),
         x$0 -> this.addRenderableOnly(x$0)
      );
      this.label("General", this.right, this.top, this.column);
      this.button("HUD settings", this.right, this.top + 14, this.column, () -> this.minecraft.setScreen(new VtpHudSettingsScreen(this)))
         .icon(Items.SPYGLASS);
      int half = (this.column - 4) / 2;
      this.label("Search limit", this.right, this.top + 38, half);
      this.input("Maximum rerolls to search (1–10000)", this.maxSearch, "500", this.right, this.top + 50, half, value -> this.maxSearch = value);
      this.button("Levels: " + (VillagerHudRenderer.isRomanNumerals() ? "I–V" : "123"), this.right + half + 4, this.top + 50, this.column - half - 4, () -> {
         VillagerHudRenderer.setRomanNumerals(!VillagerHudRenderer.isRomanNumerals());
         this.saveSettings();
         this.rebuildWidgets();
      }).setTooltip(Tooltip.create(Component.literal("Switch between numeric and Roman enchantment levels.")));
      this.label(this.singleplayer() ? "World seed (auto)" : "World seed", this.right, this.top + 76, this.column);
      EditBox seed = this.input(
         "World seed; required on multiplayer",
         this.seedText,
         "Enter seed",
         this.right,
         this.top + 88,
         this.singleplayer() ? this.column : this.column - 48,
         value -> this.seedText = value
      );
      seed.active = !this.singleplayer();
      if (!this.singleplayer()) {
         this.button("Set", this.right + this.column - 44, this.top + 88, 44, () -> {
            try {
               VtpPersistence.changeSeed(Long.parseLong(this.seedText.trim()));
               this.message = "Seed saved.";
               this.rebuildWidgets();
            } catch (NumberFormatException var2x) {
               this.message = "Enter a valid world seed.";
            }
         });
      }

      this.button("Reset calibration", this.right, this.top + 112, this.column, () -> {
         VillagerTracker.resetCalibration();
         VtpPersistence.saveCurrentWorldState();
         this.message = "Calibration reset.";
      }).active = !this.singleplayer();
   }

   private void loadEnchantments() {
      this.enchantments = new ArrayList<>(this.targetKind == 1 ? TargetCatalog.gearEnchantments() : TradeDataLoader.getTradeableEnchantments());
      this.enchantments.sort(Comparator.comparing(h -> ((Enchantment)h.value()).description().getString()));
   }

   private void picker() {
      this.loadEnchantments();
      int kindWidth = (this.column - 8) / TARGET_KINDS.length;

      for (int i = 0; i < TARGET_KINDS.length; i++) {
         int kind = i;
         VtpButton choice = this.button(
            TARGET_KINDS[i], this.left + i * (kindWidth + 2), this.top, i == TARGET_KINDS.length - 1 ? this.column - i * (kindWidth + 2) : kindWidth, () -> {
               this.targetKind = kind;
               lastTargetKind = kind;
               this.chosen = null;
               this.chosenId = null;
               this.query = "";
               this.scrolls.remove("enchants");
               this.rebuildWidgets();
            }
         );
         choice.active = i != this.targetKind;
         choice.selected(i == this.targetKind);
      }

      this.input("Search", this.query, "Search", this.left, this.top + 22, this.column, value -> {
         this.query = value;
         this.scrolls.remove("enchants");
         this.lists.get("enchants").offset(0);
         this.enchantRows();
      });
      this.list("enchants", this.left, this.top + 48, this.column, this.bottom - this.top - 48, TARGET_KINDS[this.targetKind]);
      this.enchantRows();
   }

   private boolean enchantmentKind() {
      return this.targetKind == 0 || this.targetKind == 1;
   }

   private List<String> idOptions() {
      return switch (this.targetKind) {
         case 2 -> TargetCatalog.stewEffects();
         case 3 -> TargetCatalog.arrowPotions();
         default -> TargetCatalog.tradeItems();
      };
   }

   private ItemStack idIcon(String id) {
      return switch (this.targetKind) {
         case 2 -> VtpIcons.of(Items.SUSPICIOUS_STEW);
         case 3 -> VtpIcons.of(Items.TIPPED_ARROW);
         default -> VtpIcons.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
      };
   }

   private void enchantRows() {
      String filter = this.query.toLowerCase(Locale.ROOT).trim();
      List<VtpList.Entry> entries = new ArrayList<>();
      if (this.enchantmentKind()) {
         for (Holder<Enchantment> enchant : this.enchantments) {
            String name = ((Enchantment)enchant.value()).description().getString();
            if (name.toLowerCase(Locale.ROOT).contains(filter)) {
               entries.add(new VtpList.Entry(name, "", this.targetKind == 1 ? VtpIcons.of(Items.DIAMOND_CHESTPLATE) : VtpIcons.book(), List.of(), name, Objects.equals(enchant, this.chosen), () -> {
                  this.chosen = enchant;
                  this.chosenLevel = Math.min(this.chosenLevel, ((Enchantment)enchant.value()).getMaxLevel());
                  this.updateTargetControls();
                  this.enchantRows();
               }));
            }
         }
      } else {
         for (String id : this.idOptions()) {
            String name = VtpUi.name(id);
            if (name.toLowerCase(Locale.ROOT).contains(filter)) {
               entries.add(new VtpList.Entry(name, "", this.idIcon(id), List.of(), name, id.equals(this.chosenId), () -> {
                  this.chosenId = id;
                  this.updateTargetControls();
                  this.enchantRows();
               }));
            }
         }
      }

      this.rows("enchants", entries);
   }

   private void targetControls() {
      int size = (this.column - 8) / 5;

      for (int level = 1; level <= 5; level++) {
         int selectedLevel = level;
         VtpButton choice = this.button(
            VillagerHudRenderer.fmtLvl(level), this.right + (level - 1) * (size + 2), this.top, level == 5 ? this.column - 4 * (size + 2) : size, () -> {
               this.chosenLevel = selectedLevel;
               this.updateTargetControls();
            }
         );
         this.targetLevelButtons.add(choice);
      }

      if (this.targetKind == 1) {
         List<String> items = TargetCatalog.gearItems();
         this.gearItemIndex = Math.clamp(this.gearItemIndex, 0, items.size());
         VtpButton itemButton = this.button(this.gearItemLabel(), this.right, this.top + 24, this.column, () -> {
            this.gearItemIndex = (this.gearItemIndex + 1) % (TargetCatalog.gearItems().size() + 1);
            this.rebuildWidgets();
         });
         itemButton.setTooltip(Tooltip.create(Component.literal("Which item must carry the enchantment. Click to cycle.")));
      }

      if (this.targetKind == 0) {
      this.priceSlider = (VtpSlider)this.addRenderableWidget(
         new VtpSlider(
            this.right,
            this.top + 24,
            this.column - 40,
            0,
            60,
            this.cheapestPrice ? 0 : this.pricePosition(),
            amount -> this.cheapestPrice ? "Cheapest" : "Max: " + (this.targetPrice.isBlank() ? "64" : this.targetPrice),
            amount -> {
               this.cheapestPrice = amount == 0;
               this.targetPrice = this.cheapestPrice
                  ? (this.minimumPrice.isPresent() ? String.valueOf(this.minimumPrice.getAsInt()) : "")
                  : String.valueOf(amount + 4);
               this.refreshPrice();
            }
         )
      );
      this.priceBox = this.input("Maximum base emerald price (1–64)", this.targetPrice, "64", this.right + this.column - 36, this.top + 24, 36, value -> {
         if (!this.updatingPrice) {
            this.targetPrice = value;
            this.cheapestPrice = false;
            this.priceSlider.sync(this.pricePosition());
            this.updateAddTarget();
         }
      });
      this.priceBox.setMaxLength(2);
      }

      this.addButton = this.button("Add target", this.right, this.top + 48, this.column, () -> {
         EnchantmentTarget target = this.pendingTarget();
         if (target != null) {
            VillagerHudRenderer.addTarget(target);
            VtpPersistence.saveCurrentWorldState();
            this.targetRows();
            this.message = "Target added.";
         }
      });
      this.updateTargetControls();
   }

   private String gearItemLabel() {
      List<String> items = TargetCatalog.gearItems();
      return "Item: " + (this.gearItemIndex == 0 || this.gearItemIndex > items.size() ? "Any" : VtpUi.name(items.get(this.gearItemIndex - 1)));
   }

   private EnchantmentTarget pendingTarget() {
      switch (this.targetKind) {
         case 0:
            if (this.availableTargetLevel(this.chosenLevel)) {
               Integer p = this.targetPriceValue();
               if (p != null) {
                  return new EnchantmentTarget((ResourceKey<Enchantment>)this.chosen.unwrapKey().orElseThrow(), this.chosenLevel, p);
               }
            }

            return null;
         case 1:
            if (this.availableTargetLevel(this.chosenLevel)) {
               List<String> items = TargetCatalog.gearItems();
               String item = this.gearItemIndex == 0 || this.gearItemIndex > items.size() ? "" : items.get(this.gearItemIndex - 1);
               return EnchantmentTarget.gear((ResourceKey<Enchantment>)this.chosen.unwrapKey().orElseThrow(), this.chosenLevel, -1, item);
            }

            return null;
         case 2:
            return this.chosenId == null ? null : EnchantmentTarget.stew(this.chosenId);
         case 3:
            return this.chosenId == null ? null : EnchantmentTarget.arrow(this.chosenId, -1);
         default:
            return this.chosenId == null ? null : EnchantmentTarget.trade(this.chosenId);
      }
   }

   private boolean availableTargetLevel(int level) {
      return this.enchantmentKind()
         && this.chosen != null
         && level >= 1
         && level <= 5
         && level >= ((Enchantment)this.chosen.value()).getMinLevel()
         && level <= ((Enchantment)this.chosen.value()).getMaxLevel();
   }

   private void updateTargetControls() {
      if (!this.availableTargetLevel(this.chosenLevel)) {
         for (int i = 1; i <= 5; i++) {
            if (this.availableTargetLevel(i)) {
               this.chosenLevel = i;
               break;
            }
         }
      }

      for (int ix = 0; ix < this.targetLevelButtons.size(); ix++) {
         int level = ix + 1;
         VtpButton choice = this.targetLevelButtons.get(ix);
         choice.active = this.availableTargetLevel(level);
         choice.selected(choice.active && this.chosenLevel == level);
         choice.setTooltip(
            Tooltip.create(
               Component.literal(
                  !this.enchantmentKind()
                     ? "No level for this kind of target."
                     : this.chosen == null
                     ? "Select an enchantment first."
                     : "Level " + VillagerHudRenderer.fmtLvl(level) + (choice.active ? "" : " is unavailable for this enchantment.")
               )
            )
         );
      }

      this.minimumPrice = this.targetKind == 0 ? TradePrices.cheapest(this.chosen, this.chosenLevel) : OptionalInt.empty();
      if (this.cheapestPrice) {
         this.targetPrice = this.minimumPrice.isPresent() ? String.valueOf(this.minimumPrice.getAsInt()) : "";
      }

      this.refreshPrice();
   }

   private int pricePosition() {
      try {
         return Math.clamp((long)(Integer.parseInt(this.targetPrice) - 4), 1, 60);
      } catch (NumberFormatException var2) {
         return 60;
      }
   }

   private Integer targetPriceValue() {
      if (this.cheapestPrice) {
         return this.minimumPrice.isPresent() ? this.minimumPrice.getAsInt() : null;
      } else if (this.targetPrice.isBlank()) {
         return 64;
      } else {
         try {
            int value = Integer.parseInt(this.targetPrice);
            if (value >= 1 && value <= 64) {
               return value;
            }
         } catch (NumberFormatException var2) {
         }

         return null;
      }
   }

   private void refreshPrice() {
      if (this.priceBox != null) {
         this.updatingPrice = true;
         this.priceBox.setValue(this.targetPrice);
         this.updatingPrice = false;
      }

      if (this.priceSlider != null) {
         this.priceSlider.sync(this.cheapestPrice ? 0 : this.pricePosition());
         this.priceSlider
            .setTooltip(
               Tooltip.create(
                  Component.literal(
                     this.minimumPrice.isPresent()
                        ? "Cheapest: " + this.minimumPrice.getAsInt() + " emeralds. Prices are before discounts."
                        : "Select a supported enchantment to find its cheapest base price."
                  )
               )
            );
      }

      this.updateAddTarget();
   }

   private void updateAddTarget() {
      if (this.addButton != null) {
         EnchantmentTarget target = this.pendingTarget();
         this.addButton.active = target != null;
         this.addButton.setTooltip(Tooltip.create(Component.literal(target == null ? "Select something to target first." : target.label())));
      }
   }

   private Integer parsePrice() {
      if (this.price.isBlank()) {
         return -1;
      } else {
         try {
            int value = Integer.parseInt(this.price.trim());
            if (value >= 1 && value <= 64) {
               return value;
            }
         } catch (NumberFormatException var2) {
         }

         this.message = "Price must be 1–64, or blank.";
         return null;
      }
   }

   private void targets() {
      this.picker();
      this.targetControls();
      this.label("Saved targets", this.right, this.top + 76, this.column);
      this.list("targets", this.right, this.top + 88, this.column, this.bottom - this.top - 112, "Saved targets");
      this.targetRows();
      this.button("Remove selected", this.right, this.bottom - 20, this.column, () -> {
         if (this.targetIndex >= 0) {
            VillagerHudRenderer.removeTarget(this.targetIndex);
            this.targetIndex = -1;
            VtpPersistence.saveCurrentWorldState();
            this.targetRows();
         }
      });
   }

   private void targetRows() {
      List<VtpList.Entry> entries = new ArrayList<>();
      List<EnchantmentTarget> targets = VillagerHudRenderer.getTargets();

      for (int i = 0; i < targets.size(); i++) {
         int index = i;
         EnchantmentTarget target = targets.get(i);
         String text = target.label();
         String hint = text + (target.maxPrice() >= 0 ? "\nMaximum base price: " + target.maxPrice() + " emeralds" : "\nAny base price");
         entries.add(new VtpList.Entry(text, "", targetIcon(target), List.of(), hint, i == this.targetIndex, () -> {
            this.targetIndex = index;
            this.targetRows();
         }));
      }

      this.rows("targets", entries);
   }

   private static ItemStack targetIcon(EnchantmentTarget target) {
      return switch (target.kind()) {
         case BOOK -> VtpIcons.book();
         case GEAR -> VtpIcons.of(target.id().isEmpty() ? Items.DIAMOND_CHESTPLATE : BuiltInRegistries.ITEM.getValue(Identifier.parse(target.id())));
         case STEW -> VtpIcons.of(Items.SUSPICIOUS_STEW);
         case ARROW -> VtpIcons.of(Items.TIPPED_ARROW);
         case TRADE -> VtpIcons.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(target.id())));
      };
   }

   private void sequence() {
      int full = this.ww - 20;
      int w = (full - 8) / 5;

      for (int i = 1; i <= 5; i++) {
         int level = i;
         String var10001;
         if (w < 80) {
            switch (i) {
               case 1:
                  var10001 = "I";
                  break;
               case 2:
                  var10001 = "II";
                  break;
               case 3:
                  var10001 = "III";
                  break;
               case 4:
                  var10001 = "IV";
                  break;
               default:
                  var10001 = "V";
            }
         } else {
            var10001 = LEVELS[i];
         }

         VtpButton tier = this.button(var10001, this.left + (i - 1) * (w + 2), this.top, w, () -> {
            this.sequenceLevel = level;
            this.scrolls.remove("sequence");
            this.rebuildWidgets();
         });
         tier.active = this.sequenceLevel != i;
         tier.selected(this.sequenceLevel == i);
         tier.setTooltip(Tooltip.create(Component.literal(LEVELS[i])));
      }

      this.list("sequence", this.left, this.top + 40, full, this.bottom - this.top - 40, "Trade sequence");
      this.sequenceRows();
   }

   private void sequenceRows() {
      VillagerData data = VillagerTracker.getLastVillagerData().orElse(null);
      if (data != null && SeedProvider.hasSeed()) {
         long seed = SeedProvider.getSeed().orElseThrow();
         List<List<TradeSimulator.SimulatedOffer>> future = PredictionEngine.getForecastRounds(seed, data, this.sequenceLevel, 50);
         List<VtpList.Entry> entries = new ArrayList<>();
         UUID uuid = VillagerTracker.getLastVillagerUuid();
         boolean sameLevel = data.level() == this.sequenceLevel;
         boolean pending = sameLevel && VillagerTracker.isAwaitingOffers(uuid);
         if (sameLevel) {
            Optional<List<TradeSimulator.SimulatedOffer>> actual = VillagerTracker.getCurrentOffers(uuid);
            List<TradeSimulator.SimulatedOffer> current = actual.orElseGet(() -> pending && !future.isEmpty() ? future.getFirst() : null);
            this.addRound(entries, "Current", current, actual.isEmpty() ? "Forecast for the pending trade set." : "Observed offers.");
         }

         if (!sameLevel && this.sequenceLevel <= data.level()) {
            if (VillagerTracker.hasSavedLevel(uuid, this.sequenceLevel)) {
               List<VillagerTracker.OfferData> saved = VillagerTracker.getSavedLevelOffers(uuid, this.sequenceLevel);
               if (saved.isEmpty()) {
                  entries.add(VtpList.Entry.text("Observed   —", null));
               }

               for (VillagerTracker.OfferData offer : saved) {
                  String name = VtpUi.name(offer.enchantmentKey()) + " " + VillagerHudRenderer.fmtLvl(offer.enchantmentLevel());
                  entries.add(
                     new VtpList.Entry(
                        "Observed   " + name,
                        offer.additionalCost() + " em",
                        VtpIcons.book(),
                        List.of(),
                        "Observed at " + LEVELS[this.sequenceLevel] + ": " + name,
                        false,
                        null
                     )
                  );
               }
            } else {
               entries.add(VtpList.Entry.text("Earlier offers not observed.", null));
            }
         }

         for (int r = pending ? 1 : 0; r < future.size(); r++) {
            String label = sameLevel ? "+" + (pending ? r : r + 1) : "Next " + (r + 1);
            this.addRound(entries, label, future.get(r), "Forecast; base emerald price. Other villagers can change the shared sequence.");
         }

         if (entries.isEmpty()) {
            entries.add(VtpList.Entry.text("Forecast unavailable.", null));
         }

         this.rows("sequence", entries);
      } else {
         this.rows("sequence", List.of(VtpList.Entry.text(data == null ? "Look at a villager first." : "Set the world seed in Settings.", null)));
      }
   }

   private void addRound(List<VtpList.Entry> entries, String prefix, List<TradeSimulator.SimulatedOffer> offers, String hint) {
      List<OfferText.Highlight> highlights = offers == null ? List.of() : OfferText.highlights(offers);
      if (highlights.isEmpty()) {
         String text = offers == null ? "…" : "—";
         entries.add(new VtpList.Entry(prefix + "   " + text, "", VtpIcons.of(Items.BOOK), List.of(), prefix + ": " + text + "\n" + hint, false, null));
      }

      for (OfferText.Highlight row : highlights) {
         TradeSimulator.Rolled rolled = row.offer().rolled();
         ItemStack icon = rolled == null ? VtpIcons.book() : VtpIcons.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(rolled.result())));
         entries.add(
            new VtpList.Entry(
               prefix + "   " + row.text(), row.price() >= 0 ? row.price() + " em" : "", icon, List.of(), prefix + ": " + row.text() + "\n" + hint, row.match(), null
            )
         );
      }
   }

   private void trades() {
      int profW = Math.min(150, this.column);
      int tradeX = this.left + profW + 8;
      int tradeW = this.ww - 20 - profW - 8;
      this.list("professions", this.left, this.top, profW, this.bottom - this.top, "Professions");
      List<VtpList.Entry> profs = new ArrayList<>();

      for (String id : PROFESSIONS) {
         profs.add(new VtpList.Entry(VtpUi.name(id), "", VtpIcons.profession(id), List.of(), VtpUi.name(id), this.professionId.equals(id), () -> {
            this.professionId = id;
            this.scrolls.remove("trades");
            this.rebuildWidgets();
         }));
      }

      this.rows("professions", profs);
      this.button(this.tradeLevel == 0 ? "All levels" : LEVELS[this.tradeLevel], tradeX, this.top, tradeW, () -> {
         this.tradeLevel = (this.tradeLevel + 1) % 6;
         this.scrolls.remove("trades");
         this.rebuildWidgets();
      });
      this.list("trades", tradeX, this.top + 26, tradeW, this.bottom - this.top - 26, "Available trades");
      List<VtpList.Entry> entries = new ArrayList<>();
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         this.rows("trades", entries);
      } else {
         Optional<Holder.Reference<VillagerProfession>> profession = mc.level
            .registryAccess()
            .lookupOrThrow(Registries.VILLAGER_PROFESSION)
            .get(ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath("minecraft", this.professionId)));
         if (profession.isEmpty()) {
            this.rows("trades", entries);
         } else {
            for (int level = 1; level <= 5; level++) {
               if (this.tradeLevel == 0 || this.tradeLevel == level) {
                  ResourceKey<TradeSet> key = TradeDataLoader.getTradeSetKey(profession.get(), level);
                  if (key != null) {
                     Optional<TradeSet> set = TradeDataLoader.getTradeSet(key);
                     if (!set.isEmpty()) {
                        entries.add(VtpList.Entry.text(LEVELS[level], null));

                        for (VillagerTrade trade : TradeDataLoader.getTradesFromSet(set.get())) {
                           ItemStack a = trade.wants().displayStack();
                           ItemStack b = trade.additionalWants().map(VillagerTrade.TradeCost::displayStack).orElse(ItemStack.EMPTY);
                           ItemStack result = trade.gives().displayStack();
                           entries.add(
                              new VtpList.Entry(
                                 result.getHoverName().getString(), "", ItemStack.EMPTY, List.of(a, b, result), result.getHoverName().getString(), false, null
                              )
                           );
                        }
                     }
                  }
               }
            }

            this.rows("trades", entries);
         }
      }
   }

   private void calibration() {
      if (this.singleplayer()) {
         this.label("Singleplayer stays aligned automatically.", this.left, this.top + 8, this.ww - 20);
         this.label("Calibration is only needed on multiplayer.", this.left, this.top + 25, this.ww - 20);
         this.label("You can inspect the trade sequence in the Sequence tab.", this.left, this.top + 48, this.ww - 20);
      } else {
         this.label("Observed rounds", this.left, this.top, this.column);
         this.list("observations", this.left, this.top + 16, this.column, this.bottom - this.top - 64, "Observed rounds or calibration matches");
         int half = (this.column - 4) / 2;
         this.button("Remove", this.left, this.bottom - 44, half, () -> {
            if (this.observationIndex >= 0 && !this.showingMatches) {
               CalibrationListStore.remove(this.observationIndex);
               CalibrationService.clear();
               this.observationIndex = -1;
               this.matches = List.of();
               this.observationRows();
            }
         });
         this.button("Clear", this.left + half + 4, this.bottom - 44, this.column - half - 4, () -> {
            CalibrationListStore.clear();
            this.matches = List.of();
            this.showingMatches = false;
            this.observationRows();
         });
         this.matchButton = this.button(this.showingMatches ? "Back to observations" : "Find matches", this.left, this.bottom - 20, this.column, () -> {
            if (this.showingMatches) {
               this.showingMatches = false;
               this.observationRows();
            } else {
               this.findMatches();
            }
         });
         this.label("Enchantment", this.right, this.top, this.column);
         Button choose = this.button(
            this.chosen == null ? "Choose enchantment" : VtpUi.fit(this.font, ((Enchantment)this.chosen.value()).description().getString(), this.column - 10),
            this.right,
            this.top + 14,
            this.column,
            () -> this.minecraft.setScreen(new EnchantmentPickerScreen(this, value -> {
               this.chosen = value;
               this.chosenLevel = Math.min(this.chosenLevel, ((Enchantment)value.value()).getMaxLevel());
            }))
         );
         if (this.chosen != null) {
            choose.setTooltip(Tooltip.create(((Enchantment)this.chosen.value()).description()));
         }

         this.button("Level: " + VillagerHudRenderer.fmtLvl(this.chosenLevel), this.right, this.top + 38, this.column, () -> {
            this.chosenLevel = this.chosenLevel % ((Enchantment)this.chosen.value()).getMaxLevel() + 1;
            this.rebuildWidgets();
         }).active = this.chosen != null && ((Enchantment)this.chosen.value()).getMaxLevel() > 1;
         this.input("Optional base emerald price (1–64)", this.price, "Base price: any", this.right, this.top + 62, this.column, value -> this.price = value);
         this.button("Add observation", this.right, this.top + 86, this.column, this::addObservation).active = this.chosen != null;
         this.button("Add no book", this.right, this.top + 110, this.column, () -> {
            if (CalibrationListStore.size() >= 100) {
               this.message = "Maximum 100 observations.";
            } else {
               CalibrationListStore.add(CalibrationListStore.Observation.missing());
               this.matches = List.of();
               this.showingMatches = false;
               this.observationRows();
               this.message = "Observation added.";
            }
         });
         this.observationRows();
      }
   }

   private void addObservation() {
      if (CalibrationListStore.size() >= 100) {
         this.message = "Maximum 100 observations.";
      } else {
         Integer p = this.parsePrice();
         if (p != null) {
            if (this.chosen == null) {
               this.message = "Select an enchantment first.";
            } else {
               CalibrationListStore.add(
                  CalibrationListStore.Observation.ofTrade((ResourceKey<Enchantment>)this.chosen.unwrapKey().orElseThrow(), this.chosenLevel, p)
               );
               this.matches = List.of();
               this.showingMatches = false;
               this.observationRows();
               this.message = "Observation added.";
            }
         }
      }
   }

   private void observationRows() {
      if (this.lists.containsKey("observations")) {
         if (this.matchButton != null) {
            this.matchButton.setMessage(Component.literal(this.showingMatches ? "Back to observations" : "Find matches"));
         }

         List<VtpList.Entry> entries = new ArrayList<>();
         if (this.showingMatches) {
            for (int round : this.matches) {
               entries.add(VtpList.Entry.text("Confirm round " + round, () -> {
                  VillagerData data = VillagerTracker.getLastVillagerData().orElse(null);
                  boolean ok = data != null && CalibrationService.confirm(VillagerTracker.getLastVillagerUuid(), data, round);
                  this.message = ok ? "Calibration applied." : "Observation changed; search again.";
                  this.showingMatches = false;
                  this.matches = List.of();
                  this.rebuildWidgets();
               }));
            }
         } else {
            List<CalibrationListStore.Observation> observations = CalibrationListStore.getAll();

            for (int i = 0; i < observations.size(); i++) {
               int index = i;
               CalibrationListStore.Observation o = observations.get(i);
               String value = o.noTrade()
                  ? "No book"
                  : VtpUi.name(o.enchantment().identifier().toString()) + " " + VillagerHudRenderer.fmtLvl(o.enchantmentLevel());
               entries.add(
                  new VtpList.Entry(
                     i + 1 + ". " + value,
                     o.price() >= 0 ? o.price() + " em" : "",
                     o.noTrade() ? VtpIcons.of(Items.BOOK) : VtpIcons.book(),
                     List.of(),
                     value,
                     this.observationIndex == i,
                     () -> {
                        this.observationIndex = index;
                        this.observationRows();
                     }
                  )
               );
            }
         }

         this.rows("observations", entries);
      }
   }

   private void findMatches() {
      VillagerData data = VillagerTracker.getLastVillagerData().orElse(null);
      if (data == null) {
         this.message = "Look at a villager first.";
      } else {
         String problem = CalibrationService.problem(data);
         if (!problem.isEmpty()) {
            this.message = problem;
         } else if (CalibrationListStore.toSpecs().isEmpty()) {
            this.message = "Add observations from this villager.";
         } else {
            UUID uuid = VillagerTracker.getLastVillagerUuid();
            this.matches = CalibrationService.search(uuid, data, CalibrationListStore.toSpecs());
            if (this.matches.isEmpty()) {
               this.message = "No match. Check observations and seed.";
            } else if (this.matches.size() == 1) {
               this.message = CalibrationService.confirm(uuid, data, this.matches.getFirst()) ? "Calibration applied." : "Observation changed; search again.";
               this.matches = List.of();
            } else {
               this.showingMatches = true;
               this.message = this.matches.size() + " matches. Select a round to confirm.";
               this.rebuildWidgets();
            }
         }
      }
   }

   public void tick() {
      if (this.tab == 2 && this.viewRevision != PredictionEngine.revision()) {
         this.sequenceRows();
         this.viewRevision = PredictionEngine.revision();
      }
   }

   public void render(GuiGraphics g, int mx, int my, float delta) {
      VtpUi.frame(g, this.wx, this.wy, this.ww, this.wh);
      String title = this.getTitle().getString();
      g.renderItem(VtpIcons.of(Items.LECTERN), this.wx + 8, this.wy + 5);
      g.drawString(this.font, title, this.wx + 30, this.wy + 10, -1, true);

      for (VtpSettingsScreen.Label label : this.labels) {
         VtpUi.text(g, this.font, label.text(), label.x(), label.y(), label.width(), label.color(), mx, my);
      }

      if (this.tab == 2) {
         VtpUi.text(g, this.font, "Round / enchanted book", this.left + 5, this.top + 27, this.ww - 100, -6250336, mx, my);
         g.drawString(this.font, "Emeralds", this.wx + this.ww - 58, this.top + 27, -6250336, true);
      }

      g.fill(this.left, this.wy + this.wh - 32, this.wx + this.ww - 10, this.wy + this.wh - 31, -13092808);
      String footer = !this.message.isEmpty()
         ? this.message
         : (
            this.tab == 2
               ? "Prices shown before discounts."
               : (this.tab == 4 && !this.singleplayer() ? "Add consecutive rounds from one villager." : (this.tab == 0 ? this.trackingStatus() : ""))
         );
      VtpUi.text(g, this.font, footer, this.left, this.wy + this.wh - 20, this.ww - 112, -6250336, mx, my);
      super.render(g, mx, my, delta);
   }

   private String trackingStatus() {
      return VillagerTracker.getLastVillagerData().map(d -> VillagerTracker.status(VillagerTracker.getLastVillagerUuid(), d)).orElse("Look at a villager.");
   }

   public void removed() {
      this.saveSettings();
      super.removed();
   }

   private void saveSettings() {
      VtpPersistence.saveGlobalSettings();
   }

   public void onClose() {
      try {
         int max = Integer.parseInt(this.maxSearch.trim());
         if (max < 1 || max > 10000) {
            throw new NumberFormatException();
         }

         if (max != PredictionEngine.getMaxRerolls()) {
            PredictionEngine.setMaxRerolls(max);
         }
      } catch (NumberFormatException var2) {
         this.message = "Search limit must be 1–10000.";
         this.tab = 0;
         this.rebuildWidgets();
         return;
      }

      this.saveSettings();
      VtpPersistence.saveCurrentWorldState();
      super.onClose();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private record Label(String text, int x, int y, int width, int color) {
   }
}
