package net.manaz.vtp.gui;

import java.util.EnumMap;
import java.util.Objects;
import java.util.function.IntFunction;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.render.HudSize;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.HudVisibility;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public final class VtpHudSettingsScreen extends Screen {
   private final Screen parent;
   private boolean enabled = VillagerHudRenderer.isEnabled();
   private boolean levels = VillagerHudRenderer.isShowLevels();
   private boolean resizing;
   private int percent = HudSize.percent();
   private HudVisibility.Mode mode = HudVisibility.mode();
   private String seconds = String.valueOf(HudVisibility.seconds());
   private String range = String.valueOf(HudVisibility.range());
   private final EnumMap<HudVisibility.Mode, VtpButton> choices = new EnumMap<>(HudVisibility.Mode.class);
   private EditBox timeBox;
   private EditBox rangeBox;
   private VtpButton toggle;
   private VtpButton preview;
   private VtpButton apply;
   private VtpSlider size;
   private int x;
   private int y;
   private int w;

   VtpHudSettingsScreen(Screen parent) {
      super(Component.literal("HUD settings"));
      this.parent = parent;
   }

   public int previewPercent() {
      return this.percent;
   }

   public boolean isResizing() {
      return this.resizing;
   }

   protected void init() {
      this.w = Math.min(340, this.width - 12);
      this.x = (this.width - this.w) / 2;
      this.y = (this.height - 228) / 2;
      this.choices.clear();
      this.resizing = false;
      int column = (this.w - 24) / 2;
      this.toggle = (VtpButton)this.addRenderableWidget(new VtpButton("", this.x + 10, this.y + 28, column, () -> {
         this.enabled = !this.enabled;
         this.update();
      }));
      this.preview = (VtpButton)this.addRenderableWidget(new VtpButton("", this.x + 14 + column, this.y + 28, column, () -> {
         this.levels = !this.levels;
         this.update();
      }));

      for (HudVisibility.Mode choice : HudVisibility.Mode.values()) {
         int index = choice.ordinal();
         VtpButton option = (VtpButton)this.addRenderableWidget(
            new VtpButton(choice.label, this.x + 10 + index % 2 * (column + 4), this.y + 70 + index / 2 * 24, column, () -> {
               this.mode = choice;
               this.update();
            })
         );

         option.setTooltip(Tooltip.create(Component.literal(switch (choice) {
            case NEVER -> "Keep the selected villager's HUD visible.";
            case TIME -> "Hide after the timeout since you last looked at or traded with this villager.";
            case RANGE -> "Hide when the selected villager is farther away than the range below.";
            case LOOKING -> "Hide as soon as you stop pointing at the selected villager.";
         })));
         this.choices.put(choice, option);
      }

      this.timeBox = (EditBox)this.addRenderableWidget(new VtpEditBox(this.font, this.x + 10, this.y + 134, column, 20, Component.literal("Timeout seconds")));
      this.timeBox.setMaxLength(4);
      this.timeBox.setValue(this.seconds);
      this.timeBox.setResponder(value -> {
         this.seconds = value;
         this.update();
      });
      this.rangeBox = (EditBox)this.addRenderableWidget(
         new VtpEditBox(this.font, this.x + 14 + column, this.y + 134, column, 20, Component.literal("Range blocks"))
      );
      this.rangeBox.setMaxLength(3);
      this.rangeBox.setValue(this.range);
      this.rangeBox.setResponder(value -> {
         this.range = value;
         this.update();
      });
      this.size = (VtpSlider)this.addRenderableWidget(
         new VtpSlider(this.x + 10, this.y + 162, this.w - 20, 50, 200, this.percent, amount -> "HUD size: " + amount + "%", amount -> this.percent = amount) {
            {
               Objects.requireNonNull(VtpHudSettingsScreen.this);
            }

            public void onClick(MouseButtonEvent event, boolean twice) {
               VtpHudSettingsScreen.this.resizing = true;
               super.onClick(event, twice);
            }

            public void onRelease(MouseButtonEvent event) {
               VtpHudSettingsScreen.this.resizing = false;
               super.onRelease(event);
            }
         }
      );
      this.addRenderableWidget(new VtpButton("100%", this.x + 10, this.y + 200, 54, () -> {
         this.percent = 100;
         this.size.sync(this.percent);
      }));
      this.addRenderableWidget(new VtpButton("Cancel", this.x + this.w - 164, this.y + 200, 74, this::onClose));
      this.apply = (VtpButton)this.addRenderableWidget(
         new VtpButton(
            "Apply",
            this.x + this.w - 84,
            this.y + 200,
            74,
            () -> {
               if (this.valid()) {
                  VillagerHudRenderer.setEnabled(this.enabled);
                  VillagerHudRenderer.setShowLevels(this.levels);
                  HudSize.set(this.percent);
                  HudVisibility.configure(
                     this.mode,
                     number(this.seconds, 1, 3600) ? Integer.parseInt(this.seconds) : HudVisibility.seconds(),
                     number(this.range, 1, 256) ? Integer.parseInt(this.range) : HudVisibility.range()
                  );
                  VtpPersistence.saveGlobalSettings();
                  this.onClose();
               }
            }
         )
      );
      this.update();
   }

   private static boolean number(String value, int min, int max) {
      try {
         int n = Integer.parseInt(value);
         return n >= min && n <= max;
      } catch (NumberFormatException var4) {
         return false;
      }
   }

   private boolean valid() {
      return (this.mode != HudVisibility.Mode.TIME || number(this.seconds, 1, 3600)) && (this.mode != HudVisibility.Mode.RANGE || number(this.range, 1, 256));
   }

   private void update() {
      this.choices.forEach((choice, button) -> button.selected(this.mode == choice));
      if (this.toggle != null) {
         this.toggle.setMessage(Component.literal("HUD: " + (this.enabled ? "ON" : "OFF")));
      }

      if (this.preview != null) {
         this.preview.setMessage(Component.literal("Level preview: " + (this.levels ? "ON" : "OFF")));
      }

      if (this.timeBox != null) {
         this.timeBox.active = this.mode == HudVisibility.Mode.TIME;
      }

      if (this.rangeBox != null) {
         this.rangeBox.active = this.mode == HudVisibility.Mode.RANGE;
      }

      if (this.apply != null) {
         this.apply.active = this.valid();
      }
   }

   public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
   }

   public void render(GuiGraphics g, int mx, int my, float delta) {
      VillagerHudRenderer.renderPreview(g, this.percent, this.levels);
      if (this.resizing) {
         VtpUi.frame(g, this.x, this.size.getY() - 4, this.w, 28);
         this.size.render(g, mx, my, delta);
      } else {
         VtpUi.frame(g, this.x, this.y, this.w, 228);
         g.renderItem(VtpIcons.of(Items.SPYGLASS), this.x + 8, this.y + 5);
         g.drawString(this.font, this.getTitle(), this.x + 30, this.y + 10, -1);
         int column = (this.w - 24) / 2;
         g.drawString(this.font, "When to hide the HUD", this.x + 10, this.y + 56, -1);
         g.drawString(this.font, "Timeout (1–3600 s)", this.x + 10, this.y + 122, this.timeBox.active ? -1 : -6250336);
         g.drawString(this.font, "Range (1–256 blocks)", this.x + 14 + column, this.y + 122, this.rangeBox.active ? -1 : -6250336);
         VtpUi.text(
            g,
            this.font,
            HudView.current() == null ? "Sample HUD preview. Drag to resize." : "Drag to preview. Large sizes fit the screen.",
            this.x + 10,
            this.y + 188,
            this.w - 20,
            -6250336,
            mx,
            my
         );
         super.render(g, mx, my, delta);
      }
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
