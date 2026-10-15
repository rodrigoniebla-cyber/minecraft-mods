package net.manaz.vtp.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.manaz.vtp.VtpPersistence;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.prediction.CalibrationService;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.seed.ServerSeedStore;
import net.manaz.vtp.trade.TargetCatalog;
import net.manaz.vtp.trade.TradeDataLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.manaz.vtp.trade.TradeSet;

public class VtpCommand {
   private static final SuggestionProvider<FabricClientCommandSource> ENCHANTMENT_SUGGESTIONS = (ctx, builder) -> {
      String remaining = builder.getRemaining().toLowerCase();

      for (Holder<Enchantment> holder : TradeDataLoader.getTradeableEnchantments()) {
         holder.unwrapKey().ifPresent(key -> {
            String name = key.identifier().getPath();
            if (name.startsWith(remaining) || remaining.isEmpty()) {
               builder.suggest(name);
            }
         });
      }

      return builder.buildFuture();
   };
   private static final SuggestionProvider<FabricClientCommandSource> MULTI_ENCHANT_SUGGESTIONS = (ctx, builder) -> {
      String remaining = builder.getRemaining();
      int lastSep = Math.max(remaining.lastIndexOf(59), remaining.lastIndexOf(44));
      String currentPart = (lastSep >= 0 ? remaining.substring(lastSep + 1) : remaining).stripLeading();
      String currentLower = currentPart.toLowerCase();
      String keepPrefix = lastSep >= 0 ? remaining.substring(0, lastSep + 1) + " " : "";

      for (Holder<Enchantment> holder : TradeDataLoader.getTradeableEnchantments()) {
         holder.unwrapKey().ifPresent(key -> {
            String name = key.identifier().getPath();
            if (name.startsWith(currentLower) || currentLower.isEmpty()) {
               builder.suggest(keepPrefix + name);
            }
         });
      }

      return builder.buildFuture();
   };
   private static final SuggestionProvider<FabricClientCommandSource> TARGET_INDEX_SUGGESTIONS = (ctx, builder) -> {
      List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();

      for (int i = 0; i < tgts.size(); i++) {
         builder.suggest(i + 1);
      }

      return builder.buildFuture();
   };

   public static void register() {
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> dispatcher.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(
                                                   "vtp"
                                                )
                                                .executes(ctx -> {
                                                   Minecraft mc = Minecraft.getInstance();
                                                   mc.execute(() -> mc.setScreen(new VtpSettingsScreen()));
                                                   return 1;
                                                }))
                                             .then(
                                                ((LiteralArgumentBuilder)ClientCommandManager.literal("seed")
                                                      .then(ClientCommandManager.argument("value", LongArgumentType.longArg()).executes(ctx -> {
                                                         long seed = LongArgumentType.getLong(ctx, "value");
                                                         VtpPersistence.changeSeed(seed);
                                                         ServerData serverData = Minecraft.getInstance().getCurrentServer();
                                                         if (serverData != null) {
                                                            ServerSeedStore.saveSeedForServer(serverData.ip, seed);
                                                         }

                                                         ((FabricClientCommandSource)ctx.getSource())
                                                            .sendFeedback(Component.literal("[VTP] Seed set to: " + seed));
                                                         return 1;
                                                      })))
                                                   .executes(
                                                      ctx -> {
                                                         OptionalLong seedOpt = SeedProvider.getSeed();
                                                         if (seedOpt.isPresent()) {
                                                            ((FabricClientCommandSource)ctx.getSource())
                                                               .sendFeedback(Component.literal("[VTP] Current seed: " + seedOpt.getAsLong()));
                                                         } else {
                                                            ((FabricClientCommandSource)ctx.getSource())
                                                               .sendFeedback(Component.literal("[VTP] No seed available. Use /vtp seed <value> to set one."));
                                                         }

                                                         return 1;
                                                      }
                                                   )
                                             ))
                                          .then(
                                             ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)withTargetKinds(ClientCommandManager.literal("target"))
                                                         .then(ClientCommandManager.literal("clear").executes(ctx -> {
                                                            VillagerHudRenderer.clearTargets();
                                                            VtpPersistence.saveCurrentWorldState();
                                                            ((FabricClientCommandSource)ctx.getSource())
                                                               .sendFeedback(Component.literal("[VTP] All targets cleared."));
                                                            return 1;
                                                         })))
                                                      .then(
                                                         ClientCommandManager.literal("remove")
                                                            .then(
                                                               ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                                                                  .suggests(TARGET_INDEX_SUGGESTIONS)
                                                                  .executes(
                                                                     ctx -> {
                                                                        int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                                        List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                                                                        if (idx >= 1 && idx <= tgts.size()) {
                                                                           EnchantmentTarget removed = tgts.get(idx - 1);
                                                                           VillagerHudRenderer.removeTarget(idx - 1);
                                                                           VtpPersistence.saveCurrentWorldState();
                                                                           ((FabricClientCommandSource)ctx.getSource())
                                                                              .sendFeedback(
                                                                                 Component.literal(
                                                                                    "[VTP] Removed target: " + removed.label()
                                                                                 )
                                                                              );
                                                                           return 1;
                                                                        } else {
                                                                           ((FabricClientCommandSource)ctx.getSource())
                                                                              .sendFeedback(
                                                                                 Component.literal(
                                                                                    "[VTP] No target at index " + idx + " (active: " + tgts.size() + ")."
                                                                                 )
                                                                              );
                                                                           return 0;
                                                                        }
                                                                     }
                                                                  )
                                                            )
                                                      ))
                                                   .then(
                                                      ((RequiredArgumentBuilder)ClientCommandManager.argument("enchantment", StringArgumentType.word())
                                                            .suggests(ENCHANTMENT_SUGGESTIONS)
                                                            .then(
                                                               ((RequiredArgumentBuilder)ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 10))
                                                                     .then(
                                                                        ClientCommandManager.argument("maxprice", IntegerArgumentType.integer(1)).executes(ctx -> {
                                                                           String enchName = StringArgumentType.getString(ctx, "enchantment");
                                                                           int level = IntegerArgumentType.getInteger(ctx, "level");
                                                                           int maxPrice = IntegerArgumentType.getInteger(ctx, "maxprice");
                                                                           return addTarget(
                                                                              (FabricClientCommandSource)ctx.getSource(), enchName, level, maxPrice
                                                                           );
                                                                        })
                                                                     ))
                                                                  .executes(ctx -> {
                                                                     String enchName = StringArgumentType.getString(ctx, "enchantment");
                                                                     int level = IntegerArgumentType.getInteger(ctx, "level");
                                                                     return addTarget((FabricClientCommandSource)ctx.getSource(), enchName, level, -1);
                                                                  })
                                                            ))
                                                         .executes(ctx -> {
                                                            String enchName = StringArgumentType.getString(ctx, "enchantment");
                                                            return addTarget((FabricClientCommandSource)ctx.getSource(), enchName, 1, -1);
                                                         })
                                                   ))
                                                .executes(
                                                   ctx -> {
                                                      List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                                                      if (tgts.isEmpty()) {
                                                         ((FabricClientCommandSource)ctx.getSource())
                                                            .sendFeedback(Component.literal("[VTP] No targets set. Use /vtp target <enchantment> [level], or /vtp target gear|stew|arrow|trade ..."));
                                                      } else {
                                                         StringBuilder sb = new StringBuilder("[VTP] Active targets:");

                                                         for (int i = 0; i < tgts.size(); i++) {
                                                            EnchantmentTarget t = tgts.get(i);
                                                            sb.append("\n  ")
                                                               .append(i + 1)
                                                               .append(". ")
                                                               .append(t.label());
                                                            if (t.maxPrice() >= 0) {
                                                               sb.append(" (max ").append(t.maxPrice()).append(" em)");
                                                            }
                                                         }

                                                         ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal(sb.toString()));
                                                      }

                                                      return 1;
                                                   }
                                                )
                                          ))
                                       .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                                          boolean newState = !VillagerHudRenderer.isEnabled();
                                          VillagerHudRenderer.setEnabled(newState);
                                          VtpPersistence.saveGlobalSettings();
                                          ((FabricClientCommandSource)ctx.getSource())
                                             .sendFeedback(Component.literal("[VTP] Overlay " + (newState ? "enabled" : "disabled")));
                                          return 1;
                                       })))
                                    .then(
                                       ((LiteralArgumentBuilder)ClientCommandManager.literal("maxrerolls")
                                             .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 10000)).executes(ctx -> {
                                                int count = IntegerArgumentType.getInteger(ctx, "count");
                                                PredictionEngine.setMaxRerolls(count);
                                                VtpPersistence.saveGlobalSettings();
                                                ((FabricClientCommandSource)ctx.getSource())
                                                   .sendFeedback(Component.literal("[VTP] Max rerolls set to: " + count));
                                                return 1;
                                             })))
                                          .executes(ctx -> {
                                             ((FabricClientCommandSource)ctx.getSource())
                                                .sendFeedback(Component.literal("[VTP] Max rerolls: " + PredictionEngine.getMaxRerolls()));
                                             return 1;
                                          })
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("configure")
                                             .then(
                                                ((LiteralArgumentBuilder)ClientCommandManager.literal("clear")
                                                      .then(ClientCommandManager.argument("villagerlevel", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
                                                         int lvl = IntegerArgumentType.getInteger(ctx, "villagerlevel");
                                                         return configureClear((FabricClientCommandSource)ctx.getSource(), lvl);
                                                      })))
                                                   .executes(ctx -> configureClear((FabricClientCommandSource)ctx.getSource(), -1))
                                             ))
                                          .then(
                                             ((RequiredArgumentBuilder)ClientCommandManager.argument("villagerlevel", IntegerArgumentType.integer(1, 5))
                                                   .then(ClientCommandManager.literal("clear").executes(ctx -> {
                                                      int lvl = IntegerArgumentType.getInteger(ctx, "villagerlevel");
                                                      return configureClear((FabricClientCommandSource)ctx.getSource(), lvl);
                                                   })))
                                                .then(
                                                   ClientCommandManager.argument("enchantment", StringArgumentType.word())
                                                      .suggests(ENCHANTMENT_SUGGESTIONS)
                                                      .then(
                                                         ClientCommandManager.argument("enchantlevel", IntegerArgumentType.integer(1, 10))
                                                            .then(ClientCommandManager.argument("price", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                                                               int vLvl = IntegerArgumentType.getInteger(ctx, "villagerlevel");
                                                               String ench = StringArgumentType.getString(ctx, "enchantment");
                                                               int eLvl = IntegerArgumentType.getInteger(ctx, "enchantlevel");
                                                               int price = IntegerArgumentType.getInteger(ctx, "price");
                                                               return configureLevel((FabricClientCommandSource)ctx.getSource(), vLvl, ench, eLvl, price);
                                                            }))
                                                      )
                                                )
                                          ))
                                       .executes(ctx -> {
                                          if (!(Minecraft.getInstance().crosshairPickEntity instanceof Villager villager)) {
                                             ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal("[VTP] Look at a villager first."));
                                             return 0;
                                          } else {
                                             UUID uuid = villager.getUUID();
                                             StringBuilder sb = new StringBuilder("[VTP] Saved offers for this villager:");

                                             for (int lvl = 1; lvl <= 5; lvl++) {
                                                List<VillagerTracker.OfferData> saved = VillagerTracker.getSavedLevelOffers(uuid, lvl);
                                                sb.append("\n  Lv.").append(lvl).append(": ");
                                                if (saved.isEmpty()) {
                                                   sb.append("(not recorded)");
                                                } else {
                                                   for (VillagerTracker.OfferData od : saved) {
                                                      String name = od.enchantmentKey().substring(od.enchantmentKey().lastIndexOf(58) + 1).replace("_", " ");
                                                      sb.append(name)
                                                         .append(' ')
                                                         .append(od.enchantmentLevel())
                                                         .append("  (")
                                                         .append(od.additionalCost())
                                                         .append(" em)");
                                                   }
                                                }
                                             }

                                             ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal(sb.toString()));
                                             return 1;
                                          }
                                       })
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("calibrate")
                                             .then(
                                                ClientCommandManager.literal("reset")
                                                   .executes(
                                                      ctx -> {
                                                         VillagerTracker.resetCalibration();
                                                         ((FabricClientCommandSource)ctx.getSource())
                                                            .sendFeedback(
                                                               Component.literal("[VTP] Calibration cleared. Multiplayer position is now unverified.")
                                                            );
                                                         return 1;
                                                      }
                                                   )
                                             ))
                                          .then(
                                             ClientCommandManager.literal("confirm")
                                                .then(ClientCommandManager.argument("round", IntegerArgumentType.integer(0, 1000000)).executes(ctx -> {
                                                   int round = IntegerArgumentType.getInteger(ctx, "round");
                                                   return confirmCalibrate((FabricClientCommandSource)ctx.getSource(), round);
                                                }))
                                          ))
                                       .then(
                                          ClientCommandManager.argument("enchantments", StringArgumentType.greedyString())
                                             .suggests(MULTI_ENCHANT_SUGGESTIONS)
                                             .executes(ctx -> {
                                                String spec = StringArgumentType.getString(ctx, "enchantments");
                                                return runCalibrate((FabricClientCommandSource)ctx.getSource(), spec);
                                             })
                                       ))
                                    .executes(
                                       ctx -> {
                                          Map<ResourceKey<TradeSet>, Integer> offsets = PredictionEngine.getAllOffsets();
                                          String msg = offsets.isEmpty()
                                             ? "[VTP] No calibrations set. Usage: /vtp calibrate <ench> [lvl]; <ench> [lvl]; ... — look at your villager first."
                                             : "[VTP] Calibrated offsets: " + offsets.size() + " trade set(s). Use 'reset' to clear all.";
                                          ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal(msg));
                                          return 1;
                                       }
                                    )
                              ))
                           .then(
                              ClientCommandManager.literal("confirm")
                                 .then(
                                    ClientCommandManager.argument("round", IntegerArgumentType.integer(0, 1000000))
                                       .executes(
                                          ctx -> confirmCalibrate((FabricClientCommandSource)ctx.getSource(), IntegerArgumentType.getInteger(ctx, "round"))
                                       )
                                 )
                           ))
                        .then(
                           ClientCommandManager.literal("help")
                              .executes(
                                 ctx -> {
                                    ((FabricClientCommandSource)ctx.getSource())
                                       .sendFeedback(
                                          Component.literal(
                                             "[VTP] Commands:\n  /vtp                                  Open settings GUI\n  /vtp help                             Show this help\n  /vtp status                           Show current state\n  /vtp seed [value]                     Get or set world seed\n  /vtp toggle                           Toggle HUD overlay\n  /vtp maxrerolls <count>               Set max search limit\n  /vtp target [<ench> [lvl] [price]]    List or add targets\n  /vtp target remove <index>            Remove target by index\n  /vtp target clear                     Clear all targets\n  /vtp configure [<lvl> <ench> <l> <p>] View or set saved offers\n  /vtp configure [<lvl>] clear          Clear saved offers\n  /vtp calibrate <ench> [lvl]; ...      Auto-find RNG offset\n  /vtp calibrate confirm <round>        Apply specific match\n  /vtp calibrate reset                  Clear all offsets"
                                          )
                                       );
                                    return 1;
                                 }
                              )
                        ))
                     .then(ClientCommandManager.literal("debug").then(ClientCommandManager.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                        VillagerTracker.setDebug(enabled);
                        ((FabricClientCommandSource)ctx.getSource())
                           .sendFeedback(Component.literal("[VTP] Observation logging " + (enabled ? "enabled" : "disabled")));
                        return 1;
                     }))))
                  .then(ClientCommandManager.literal("status").executes(ctx -> {
                     OptionalLong seedOpt = SeedProvider.getSeed();
                     List<EnchantmentTarget> tgts = VillagerHudRenderer.getTargets();
                     Map<ResourceKey<TradeSet>, Integer> offsets = PredictionEngine.getAllOffsets();
                     StringBuilder sb = new StringBuilder("[VTP] Status:");
                     sb.append("\n  Seed: ").append(seedOpt.isPresent() ? seedOpt.getAsLong() : "(none)");
                     sb.append("\n  Overlay: ").append(VillagerHudRenderer.isEnabled() ? "on" : "off");
                     sb.append("\n  Max rerolls: ").append(PredictionEngine.getMaxRerolls());
                     sb.append("\n  Calibrated sequences: ").append(offsets.size());
                     VillagerTracker.getLastVillagerData().ifPresent(data -> {
                        UUID uuid = VillagerTracker.getLastVillagerUuid();
                        sb.append("\n  Villager: ").append(uuid);
                        sb.append("\n  Tracking: ").append(VillagerTracker.status(uuid, data));
                        sb.append("\n  Estimated next round (multiplayer): ").append(VillagerTracker.getNextRoundForLevel(data.profession(), data.level()));
                     });
                     if (tgts.isEmpty()) {
                        sb.append("\n  Targets: (none)");
                     } else {
                        sb.append("\n  Targets:");

                        for (int i = 0; i < tgts.size(); i++) {
                           EnchantmentTarget t = tgts.get(i);
                           sb.append("\n    ").append(i + 1).append(". ").append(t.label());
                           if (t.maxPrice() >= 0) {
                              sb.append(" (max ").append(t.maxPrice()).append(" em)");
                           }
                        }
                     }

                     ((FabricClientCommandSource)ctx.getSource()).sendFeedback(Component.literal(sb.toString()));
                     return 1;
                  }))
            )
         );
   }

   private static List<PredictionEngine.CalibrationSpec> parseEnchantSpecs(String input) {
      List<PredictionEngine.CalibrationSpec> result = new ArrayList<>();

      for (String part : input.split("[;,]")) {
         part = part.trim().toLowerCase(Locale.ROOT);
         if (!part.isEmpty()) {
            if (!part.equals("none") && !part.equals("no_trade")) {
               String[] tokens = part.split("\\s+");
               int nameEnd = tokens.length;
               int level = 1;
               int price = -1;
               if (nameEnd > 2 && tokens[nameEnd - 1].matches("[0-9]+") && tokens[nameEnd - 2].matches("[0-9]+")) {
                  price = Integer.parseInt(tokens[--nameEnd]);
               }

               if (nameEnd > 1 && tokens[nameEnd - 1].matches("[0-9]+")) {
                  level = Integer.parseInt(tokens[--nameEnd]);
               }

               String name = String.join("_", Arrays.copyOf(tokens, nameEnd));
               Identifier id = Identifier.parse(name.contains(":") ? name : "minecraft:" + name);
               ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
               Enchantment enchantment = (Enchantment)Minecraft.getInstance()
                  .level
                  .registryAccess()
                  .lookupOrThrow(Registries.ENCHANTMENT)
                  .getOptional(key)
                  .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment " + name));
               if (level < enchantment.getMinLevel() || level > enchantment.getMaxLevel()) {
                  throw new IllegalArgumentException("Invalid level for " + name);
               }

               if (price != -1 && (price < 1 || price > 64)) {
                  throw new IllegalArgumentException("Base price must be 1–64");
               }

               result.add(PredictionEngine.CalibrationSpec.of(key, level, price));
            } else {
               result.add(PredictionEngine.CalibrationSpec.missing());
            }
         }
      }

      if (result.size() > 100) {
         throw new IllegalArgumentException("At most 100 observations");
      } else {
         return result;
      }
   }

   private static int runCalibrate(FabricClientCommandSource source, String spec) {
      if (Minecraft.getInstance().crosshairPickEntity instanceof Villager villager) {
         OptionalLong seedOpt = SeedProvider.getSeed();
         if (seedOpt.isEmpty()) {
            source.sendFeedback(Component.literal("[VTP] No seed set. Use /vtp seed <value> first."));
            return 0;
         } else {
            List<PredictionEngine.CalibrationSpec> specs;
            try {
               specs = parseEnchantSpecs(spec);
            } catch (IllegalArgumentException var11) {
               source.sendFeedback(Component.literal("[VTP] Invalid observation: " + var11.getMessage()));
               return 0;
            }

            if (specs.isEmpty()) {
               source.sendFeedback(Component.literal("[VTP] No valid enchantments parsed. Example: fire_aspect 2; knockback 2; mending"));
               return 0;
            } else {
               VillagerData data = villager.getVillagerData();
               String problem = CalibrationService.problem(data);
               if (!problem.isEmpty()) {
                  source.sendFeedback(Component.literal("[VTP] " + problem));
                  return 0;
               } else {
                  List<Integer> matches = CalibrationService.search(villager.getUUID(), data, specs);
                  if (matches.isEmpty()) {
                     String hint = specs.size() == 1
                        ? "[VTP] No round found with that enchantment within "
                        : "[VTP] No matching sequence of " + specs.size() + " consecutive rerolls found within ";
                     source.sendFeedback(Component.literal(hint + PredictionEngine.getMaxRerolls() + " rounds. Check your seed, order, and enchantment names."));
                     return 0;
                  } else if (matches.size() == 1) {
                     applyAndConfirm(source, villager, data, matches.get(0));
                     return 1;
                  } else {
                     StringBuilder sb = new StringBuilder("[VTP] Current round matches at: ");

                     for (int i = 0; i < matches.size(); i++) {
                        if (i > 0) {
                           sb.append(", ");
                        }

                        sb.append(matches.get(i));
                     }

                     sb.append(". Add more rerolls to the sequence or use /vtp calibrate confirm <round>.");
                     source.sendFeedback(Component.literal(sb.toString()));
                     return 1;
                  }
               }
            }
         }
      } else {
         source.sendFeedback(Component.literal("[VTP] Look at a villager first, then run /vtp calibrate."));
         return 0;
      }
   }

   private static int confirmCalibrate(FabricClientCommandSource source, int round) {
      if (Minecraft.getInstance().crosshairPickEntity instanceof Villager villager) {
         applyAndConfirm(source, villager, villager.getVillagerData(), round);
         return 1;
      } else {
         source.sendFeedback(Component.literal("[VTP] Look at the same villager, then confirm."));
         return 0;
      }
   }

   private static void applyAndConfirm(FabricClientCommandSource source, Villager villager, VillagerData data, int round) {
      if (!CalibrationService.confirm(villager.getUUID(), data, round)) {
         source.sendFeedback(Component.literal("[VTP] This is not a current match for this villager. Run calibration again."));
      } else {
         source.sendFeedback(Component.literal("[VTP] Calibrated current round " + round + "."));
      }
   }

   private static int configureLevel(FabricClientCommandSource source, int villagerLevel, String enchName, int enchLevel, int price) {
      if (Minecraft.getInstance().crosshairPickEntity instanceof Villager villager) {
         Identifier id = enchName.contains(":") ? Identifier.parse(enchName) : Identifier.fromNamespaceAndPath("minecraft", enchName);
         String enchKey = id.toString();
         VillagerTracker.OfferData offer = new VillagerTracker.OfferData(enchKey, enchLevel, price);
         boolean ok = VillagerTracker.setLevelOffer(villager.getUUID(), villagerLevel, offer);
         if (!ok) {
            source.sendFeedback(Component.literal("[VTP] Villager not tracked yet — look at it for a moment first."));
            return 0;
         } else {
            OptionalLong seedOpt = SeedProvider.getSeed();
            if (seedOpt.isPresent()) {
               VtpPersistence.save(seedOpt.getAsLong());
            }

            source.sendFeedback(
               Component.literal("[VTP] Lv." + villagerLevel + " set to: " + enchName.replace("_", " ") + " " + enchLevel + "  (" + price + " em)")
            );
            return 1;
         }
      } else {
         source.sendFeedback(Component.literal("[VTP] Look at a villager first."));
         return 0;
      }
   }

   private static int configureClear(FabricClientCommandSource source, int villagerLevel) {
      if (Minecraft.getInstance().crosshairPickEntity instanceof Villager villager) {
         boolean ok = VillagerTracker.clearLevelOffer(villager.getUUID(), villagerLevel);
         if (!ok) {
            source.sendFeedback(Component.literal("[VTP] Villager not tracked yet — look at it for a moment first."));
            return 0;
         } else {
            OptionalLong seedOpt = SeedProvider.getSeed();
            if (seedOpt.isPresent()) {
               VtpPersistence.save(seedOpt.getAsLong());
            }

            String levelStr = villagerLevel == -1 ? "all levels" : "Lv." + villagerLevel;
            source.sendFeedback(Component.literal("[VTP] Cleared saved offers for " + levelStr + ". Simulation will be used."));
            return 1;
         }
      } else {
         source.sendFeedback(Component.literal("[VTP] Look at a villager first."));
         return 0;
      }
   }

   private static SuggestionProvider<FabricClientCommandSource> suggest(java.util.function.Supplier<List<String>> ids) {
      return (ctx, builder) -> {
         String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

         for (String id : ids.get()) {
            String name = id.substring(id.indexOf(':') + 1);
            if (name.startsWith(remaining)) {
               builder.suggest(name);
            }
         }

         return builder.buildFuture();
      };
   }

   private static List<String> gearEnchantmentIds() {
      return TargetCatalog.gearEnchantments().stream().flatMap(h -> h.unwrapKey().stream()).map(k -> k.identifier().toString()).toList();
   }

   private static List<String> gearItemIds() {
      List<String> items = new ArrayList<>(TargetCatalog.gearItems());
      items.addFirst("minecraft:any");
      return items;
   }

   private static String qualified(String name) {
      return name.contains(":") ? name : "minecraft:" + name;
   }

   /** Adds {@code gear}, {@code stew}, {@code arrow} and {@code trade} target kinds under /vtp target. */
   private static LiteralArgumentBuilder<FabricClientCommandSource> withTargetKinds(LiteralArgumentBuilder<FabricClientCommandSource> target) {
      return target.then(
            ClientCommandManager.literal("gear")
               .then(
                  ClientCommandManager.argument("enchantment", StringArgumentType.word())
                     .suggests(suggest(VtpCommand::gearEnchantmentIds))
                     .executes(ctx -> addGear(ctx.getSource(), StringArgumentType.getString(ctx, "enchantment"), 1, "any"))
                     .then(
                        ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 10))
                           .executes(ctx -> addGear(ctx.getSource(), StringArgumentType.getString(ctx, "enchantment"), IntegerArgumentType.getInteger(ctx, "level"), "any"))
                           .then(
                              ClientCommandManager.argument("item", StringArgumentType.word())
                                 .suggests(suggest(VtpCommand::gearItemIds))
                                 .executes(
                                    ctx -> addGear(
                                       ctx.getSource(),
                                       StringArgumentType.getString(ctx, "enchantment"),
                                       IntegerArgumentType.getInteger(ctx, "level"),
                                       StringArgumentType.getString(ctx, "item")
                                    )
                                 )
                           )
                     )
               )
         )
         .then(
            ClientCommandManager.literal("stew")
               .then(
                  ClientCommandManager.argument("effect", StringArgumentType.word())
                     .suggests(suggest(TargetCatalog::stewEffects))
                     .executes(ctx -> addSimple(ctx.getSource(), EnchantmentTarget.Kind.STEW, StringArgumentType.getString(ctx, "effect"), TargetCatalog.stewEffects()))
               )
         )
         .then(
            ClientCommandManager.literal("arrow")
               .then(
                  ClientCommandManager.argument("potion", StringArgumentType.word())
                     .suggests(suggest(TargetCatalog::arrowPotions))
                     .executes(ctx -> addSimple(ctx.getSource(), EnchantmentTarget.Kind.ARROW, StringArgumentType.getString(ctx, "potion"), TargetCatalog.arrowPotions()))
               )
         )
         .then(
            ClientCommandManager.literal("trade")
               .then(
                  ClientCommandManager.argument("item", StringArgumentType.word())
                     .suggests(suggest(TargetCatalog::tradeItems))
                     .executes(ctx -> addSimple(ctx.getSource(), EnchantmentTarget.Kind.TRADE, StringArgumentType.getString(ctx, "item"), TargetCatalog.tradeItems()))
               )
         );
   }

   private static int addGear(FabricClientCommandSource source, String enchantment, int level, String item) {
      String enchantmentId = qualified(enchantment);
      if (!gearEnchantmentIds().contains(enchantmentId)) {
         source.sendFeedback(Component.literal("[VTP] Villagers never put " + enchantment + " on traded gear."));
         return 0;
      } else {
         String itemId = item.equals("any") ? "" : qualified(item);
         if (!itemId.isEmpty() && !TargetCatalog.gearItems().contains(itemId)) {
            source.sendFeedback(Component.literal("[VTP] No villager sells enchanted " + item + "."));
            return 0;
         } else {
            EnchantmentTarget target = EnchantmentTarget.gear(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(enchantmentId)), level, -1, itemId);
            VillagerHudRenderer.addTarget(target);
            VtpPersistence.saveCurrentWorldState();
            source.sendFeedback(Component.literal("[VTP] Target added: " + target.label()));
            return 1;
         }
      }
   }

   private static int addSimple(FabricClientCommandSource source, EnchantmentTarget.Kind kind, String name, List<String> allowed) {
      String id = qualified(name);
      if (!allowed.contains(id)) {
         source.sendFeedback(Component.literal("[VTP] No villager trade offers " + name + "."));
         return 0;
      } else {
         EnchantmentTarget target = switch (kind) {
            case STEW -> EnchantmentTarget.stew(id);
            case ARROW -> EnchantmentTarget.arrow(id, -1);
            default -> EnchantmentTarget.trade(id);
         };
         VillagerHudRenderer.addTarget(target);
         VtpPersistence.saveCurrentWorldState();
         source.sendFeedback(Component.literal("[VTP] Target added: " + target.label()));
         return 1;
      }
   }

   private static int addTarget(FabricClientCommandSource source, String enchName, int level, int maxPrice) {
      Identifier id = enchName.contains(":") ? Identifier.parse(enchName) : Identifier.fromNamespaceAndPath("minecraft", enchName);
      ResourceKey<Enchantment> enchKey = ResourceKey.create(Registries.ENCHANTMENT, id);
      EnchantmentTarget target = new EnchantmentTarget(enchKey, level, maxPrice);
      VillagerHudRenderer.addTarget(target);
      VtpPersistence.saveCurrentWorldState();
      String msg = "[VTP] Target added: " + enchName.replace("_", " ") + " " + level;
      if (maxPrice >= 0) {
         msg = msg + " (max " + maxPrice + " emeralds)";
      }

      source.sendFeedback(Component.literal(msg));
      return 1;
   }
}
