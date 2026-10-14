package net.manaz.vtp.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

final class EnchantmentPickerScreen extends Screen {
   private final Screen parent;
   private final Consumer<Holder<Enchantment>> onChoose;
   private String query = "";
   private VtpList list;
   private int x;
   private int y;
   private int w;
   private int h;

   EnchantmentPickerScreen(Screen parent, Consumer<Holder<Enchantment>> onChoose) {
      super(Component.literal("Choose enchantment"));
      this.parent = parent;
      this.onChoose = onChoose;
   }

   protected void init() {
      this.w = Math.min(300, this.width - 12);
      this.h = Math.min(280, this.height - 12);
      this.x = (this.width - this.w) / 2;
      this.y = (this.height - this.h) / 2;
      EditBox search = (EditBox)this.addRenderableWidget(
         new VtpEditBox(this.font, this.x + 10, this.y + 28, this.w - 20, 20, Component.literal("Search enchantments"))
      );
      search.setTextColor(-1);
      search.setTextShadow(true);
      search.setValue(this.query);
      search.setHint(Component.literal("Search enchantments"));
      this.list = (VtpList)this.addRenderableWidget(new VtpList(this.x + 10, this.y + 54, this.w - 20, this.h - 90, "Enchantments"));
      search.setResponder(value -> {
         this.query = value;
         this.list.offset(0);
         this.refresh();
      });
      this.refresh();
      this.setInitialFocus(search);
      this.addRenderableWidget(new VtpButton("Back", this.x + (this.w - 100) / 2, this.y + this.h - 28, 100, this::onClose));
   }

   private void refresh() {
      String filter = this.query.trim().toLowerCase(Locale.ROOT);
      this.list
         .entries(
            TradeDataLoader.getTradeableEnchantments()
               .stream()
               .sorted(Comparator.comparing(e -> ((Enchantment)e.value()).description().getString()))
               .filter(e -> ((Enchantment)e.value()).description().getString().toLowerCase(Locale.ROOT).contains(filter))
               .map(
                  e -> new VtpList.Entry(
                     ((Enchantment)e.value()).description().getString(),
                     "",
                     VtpIcons.book(),
                     List.of(),
                     ((Enchantment)e.value()).description().getString(),
                     false,
                     () -> {
                        this.onChoose.accept((Holder<Enchantment>)e);
                        this.onClose();
                     }
                  )
               )
               .toList()
         );
   }

   public void render(GuiGraphics g, int mx, int my, float delta) {
      VtpUi.frame(g, this.x, this.y, this.w, this.h);
      String title = this.getTitle().getString();
      g.renderItem(VtpIcons.book(), this.x + 8, this.y + 5);
      g.drawString(this.font, title, this.x + 30, this.y + 10, -1, true);
      super.render(g, mx, my, delta);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
