package net.manaz.vtp.gui;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class VtpColorPicker {
   private static final int[] PRESETS = new int[]{5635925, 5636095, 5609983, 11171839, 16742314, 16755285, 16777045, 16777215};
   private static final String[] NAMES = new String[]{"Green", "Cyan", "Blue", "Purple", "Pink", "Orange", "Yellow", "White"};
   private static final String[] CHANNELS = new String[]{"Red", "Green", "Blue"};
   private int draft = VtpAccent.color();
   private float hue;
   private float saturation;
   private float brightness;
   private boolean updating;
   private final EditBox[] rgbBoxes = new EditBox[3];
   private final EditBox hexBox;

   VtpColorPicker(Font font, int x, int y, int w, int h, Consumer<AbstractWidget> add, Consumer<Renderable> decorate) {
      this.readHsv();
      int side = Math.min(w - Math.clamp((long)(w / 3), 64, 80) - 8, h - 58);
      int controlsX = x + side + 8;
      int controlsWidth = w - side - 8;
      int colorHeight = Math.max(86, side + 16);
      boolean stackedLabels = side >= 122;
      int previewHeight = colorHeight - (stackedLabels ? 102 : 66);
      decorate.accept((g, mx, my, delta) -> {
         g.fill(controlsX, y, controlsX + controlsWidth, y + previewHeight, this.draft);
         g.renderOutline(controlsX, y, controlsWidth, previewHeight, -13092808);
      });
      add.accept(new VtpColorPicker.ColorPlane(x, y, side, side, false));
      add.accept(new VtpColorPicker.ColorPlane(x, y + side + 4, side, 12, true));

      for (int i = 0; i < this.rgbBoxes.length; i++) {
         int shift = (2 - i) * 8;
         int row = y + previewHeight + (stackedLabels ? 4 + i * 34 : 2 + i * 22);
         int fieldX = stackedLabels ? controlsX : controlsX + 32;
         int fieldY = stackedLabels ? row + 10 : row;
         label(decorate, font, CHANNELS[i], controlsX, stackedLabels ? row : row + 6);
         EditBox box = new VtpEditBox(font, fieldX, fieldY, stackedLabels ? controlsWidth : controlsWidth - 32, 20, Component.literal(CHANNELS[i]));
         this.rgbBoxes[i] = box;
         box.setMaxLength(3);
         box.setValue(String.valueOf(this.draft >> shift & 0xFF));
         box.setTooltip(Tooltip.create(Component.literal(CHANNELS[i] + ": 0–255. Changes apply immediately.")));
         box.setResponder(value -> {
            if (!this.updating) {
               int amount = value.matches("[0-9]{1,3}") ? Integer.parseInt(value) : -1;
               boolean valid = amount >= 0 && amount <= 255;
               box.setTextColor(valid ? -1 : -34953);
               if (valid) {
                  this.draft = this.draft & ~(255 << shift) | amount << shift;
                  this.readHsv();
                  this.sync(box);
               }
            }
         });
         add.accept(box);
      }

      int row = y + colorHeight + 4;

      for (int i = 0; i < PRESETS.length; i++) {
         int start = i * (w + 2) / PRESETS.length;
         int end = (i + 1) * (w + 2) / PRESETS.length - 2;
         add.accept(new VtpColorPicker.Swatch(NAMES[i], 0xFF000000 | PRESETS[i], x + start, row, end - start));
      }

      label(decorate, font, "Hex", x, row + 24);
      this.hexBox = new VtpEditBox(font, x + 24, row + 18, w - 70, 20, Component.literal("Hex color"));
      this.hexBox.setMaxLength(7);
      this.hexBox.setValue(VtpAccent.hex(this.draft));
      this.hexBox.setTooltip(Tooltip.create(Component.literal("Hex: #RRGGBB. Changes apply immediately.")));
      this.hexBox.setResponder(value -> {
         if (!this.updating) {
            OptionalInt parsed = VtpAccent.parse(value);
            this.hexBox.setTextColor(parsed.isPresent() ? -1 : -34953);
            if (parsed.isPresent()) {
               this.draft = parsed.getAsInt();
               this.readHsv();
               this.sync(this.hexBox);
            }
         }
      });
      add.accept(this.hexBox);
      add.accept(new VtpButton("Reset", x + w - 42, row + 18, 42, () -> this.setDraft(-11141291)));
   }

   private static void label(Consumer<Renderable> decorate, Font font, String text, int x, int y) {
      decorate.accept((g, mx, my, delta) -> g.drawString(font, text, x, y, -6250336));
   }

   private void readHsv() {
      float[] hsv = VtpAccent.hsv(this.draft);
      if (hsv[1] > 0.0F) {
         this.hue = hsv[0];
      }

      this.saturation = hsv[1];
      this.brightness = hsv[2];
   }

   private void setDraft(int color) {
      this.draft = color;
      this.readHsv();
      this.sync(null);
   }

   private void sync(EditBox source) {
      this.updating = true;

      for (int i = 0; i < this.rgbBoxes.length; i++) {
         if (this.rgbBoxes[i] != source) {
            this.rgbBoxes[i].setValue(String.valueOf(this.draft >> (2 - i) * 8 & 0xFF));
            this.rgbBoxes[i].setTextColor(-1);
         }
      }

      if (this.hexBox != source) {
         this.hexBox.setValue(VtpAccent.hex(this.draft));
         this.hexBox.setTextColor(-1);
      }

      this.updating = false;
      VtpAccent.set(this.draft);
   }

   private void changed() {
      this.draft = VtpAccent.hsv(this.hue, this.saturation, this.brightness);
      this.sync(null);
   }

   private final class ColorPlane extends AbstractWidget {
      private final boolean hueOnly;

      ColorPlane(int x, int y, int w, int h, boolean hueOnly) {
         super(x, y, w, h, Component.literal(hueOnly ? "Hue" : "Saturation and brightness"));
         this.hueOnly = hueOnly;
         this.setTooltip(
            Tooltip.create(
               Component.literal(
                  hueOnly
                     ? "Drag to choose a hue. Arrow keys make fine adjustments."
                     : "Left to right: saturation. Top to bottom: brightness. Drag or use arrow keys."
               )
            )
         );
      }

      protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
         int x = this.getX();
         int y = this.getY();
         if (this.hueOnly) {
            for (int i = 0; i < this.width; i++) {
               g.fill(x + i, y, x + i + 1, y + this.height, VtpAccent.hsv((float)i / (this.width - 1), 1.0F, 1.0F));
            }

            int cx = x + Math.round(VtpColorPicker.this.hue * (this.width - 1));
            g.renderOutline(cx - 2, y, 5, this.height, -16777216);
            g.renderOutline(cx - 1, y + 1, 3, this.height - 2, -1);
         } else {
            for (int i = 0; i < this.width; i++) {
               g.fillGradient(x + i, y, x + i + 1, y + this.height, VtpAccent.hsv(VtpColorPicker.this.hue, (float)i / (this.width - 1), 1.0F), -16777216);
            }

            int cx = x + Math.round(VtpColorPicker.this.saturation * (this.width - 1));
            int cy = y + Math.round((1.0F - VtpColorPicker.this.brightness) * (this.height - 1));
            g.renderOutline(cx - 3, cy - 3, 7, 7, -16777216);
            g.renderOutline(cx - 2, cy - 2, 5, 5, -1);
         }

         if (this.isFocused()) {
            g.renderOutline(x, y, this.width, this.height, -1);
         }
      }

      private void pick(double mx, double my) {
         float horizontal = (float)Math.clamp((mx - this.getX()) / (this.width - 1), 0.0, 1.0);
         if (this.hueOnly) {
            VtpColorPicker.this.hue = horizontal;
         } else {
            VtpColorPicker.this.saturation = horizontal;
            VtpColorPicker.this.brightness = 1.0F - (float)Math.clamp((my - this.getY()) / (this.height - 1), 0.0, 1.0);
         }

         VtpColorPicker.this.changed();
      }

      public void onClick(MouseButtonEvent event, boolean doubleClick) {
         this.pick(event.x(), event.y());
      }

      protected void onDrag(MouseButtonEvent event, double dx, double dy) {
         this.pick(event.x(), event.y());
      }

      public boolean keyPressed(KeyEvent event) {
         int key = event.key();
         if (key != 80 && key != 79 && key != 82 && key != 81) {
            return false;
         } else {
            if (this.hueOnly) {
               VtpColorPicker.this.hue = Math.clamp(VtpColorPicker.this.hue + (key != 81 && key != 79 ? -1 : 1) / 360.0F, 0.0F, 1.0F);
            } else if (key != 80 && key != 79) {
               VtpColorPicker.this.brightness = Math.clamp(VtpColorPicker.this.brightness + (key == 82 ? 0.01F : -0.01F), 0.0F, 1.0F);
            } else {
               VtpColorPicker.this.saturation = Math.clamp(VtpColorPicker.this.saturation + (key == 79 ? 0.01F : -0.01F), 0.0F, 1.0F);
            }

            VtpColorPicker.this.changed();
            return true;
         }
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
         output.add(NarratedElementType.TITLE, this.getMessage());
         output.add(
            NarratedElementType.USAGE,
            (
                  this.hueOnly
                     ? "Hue " + Math.round(VtpColorPicker.this.hue * 360.0F) + " degrees."
                     : "Saturation "
                        + Math.round(VtpColorPicker.this.saturation * 100.0F)
                        + " percent. Brightness "
                        + Math.round(VtpColorPicker.this.brightness * 100.0F)
                        + " percent."
               )
               + " Use arrow keys to adjust."
         );
      }
   }

   private final class Swatch extends Button {
      private final int color;

      Swatch(String name, int color, int x, int y, int w) {
         super(x, y, w, 14, Component.literal(name + " " + VtpAccent.hex(color)), ignored -> VtpColorPicker.this.setDraft(color), DEFAULT_NARRATION);
         this.color = color;
         this.setTooltip(Tooltip.create(this.getMessage()));
      }

      protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
         VtpUi.slot(g, this.getX(), this.getY(), this.width, this.height);
         g.fill(this.getX() + 3, this.getY() + 3, this.getX() + this.width - 3, this.getY() + this.height - 3, this.color);
         if (VtpColorPicker.this.draft == this.color || this.isHoveredOrFocused()) {
            g.renderOutline(this.getX(), this.getY(), this.width, this.height, -1);
         }
      }
   }
}
