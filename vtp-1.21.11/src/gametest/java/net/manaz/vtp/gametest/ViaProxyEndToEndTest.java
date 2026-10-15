package net.manaz.vtp.gametest;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.manaz.vtp.gui.GuiAccess;
import net.manaz.vtp.gui.VtpSettingsScreen;
import net.manaz.vtp.prediction.EnchantmentTarget;
import net.manaz.vtp.prediction.OfferMatching;
import net.manaz.vtp.prediction.PredictionEngine;
import net.manaz.vtp.render.HudView;
import net.manaz.vtp.render.VillagerHudRenderer;
import net.manaz.vtp.render.VillagerTracker;
import net.manaz.vtp.seed.SeedProvider;
import net.manaz.vtp.trade.ObservedOffer;
import net.manaz.vtp.trade.TradeSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Joins a vanilla 26.2 server through ViaProxy with this 1.21.11 client and checks the mod against
 * real server behaviour: observed trades must be recognised at the right sequence round, and the
 * forecast round for a target enchantment must really produce that enchantment on the server.
 * Skipped unless {@code -Dvtp.e2e.address} and {@code -Dvtp.e2e.seed} are set.
 */
public class ViaProxyEndToEndTest implements FabricClientGameTest {
   private static final Logger LOGGER = LoggerFactory.getLogger("vtp-e2e");
   private static final String SPOT = "0.5 200 0.5";

   @Override
   public void runTest(ClientGameTestContext context) {
      String address = System.getProperty("vtp.e2e.address");
      String seedProperty = System.getProperty("vtp.e2e.seed");
      if (address == null || seedProperty == null) {
         LOGGER.info("[VTP e2e] skipped: -Dvtp.e2e.address / -Dvtp.e2e.seed not set");
         return;
      }

      long seed = Long.parseLong(seedProperty);
      String[] rconTarget = System.getProperty("vtp.e2e.rcon", "127.0.0.1:25575").split(":");

      try (Rcon rcon = new Rcon(rconTarget[0], Integer.parseInt(rconTarget[1]), "test")) {
         run(context, rcon, address, seed);
      } catch (java.io.IOException e) {
         throw new AssertionError("RCON failed", e);
      }
   }

   private static void run(ClientGameTestContext context, Rcon rcon, String address, long seed) throws java.io.IOException {
      context.runOnClient(
         mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), new ServerData("e2e", address, ServerData.Type.OTHER), false, null)
      );
      context.waitFor(mc -> mc.player != null && mc.level != null && mc.screen == null, 20 * 60);
      context.waitTicks(40);
      String name = context.computeOnClient(mc -> mc.player.getGameProfile().name());
      LOGGER.info("[VTP e2e] joined {} as {}", address, name);
      rcon.command("kill @e[type=!minecraft:player]");
      rcon.command("forceload add -16 -16 16 16");
      rcon.command("fill -4 199 -6 4 199 9 minecraft:glass");
      rcon.command("gamemode creative " + name);
      rcon.command("tp " + name + " 0.5 200 -2.5 0 0");
      context.waitTicks(20);

      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp seed " + seed));
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target mending 1"));
      context.waitTicks(10);
      check(SeedProvider.getSeed().orElse(Long.MIN_VALUE) == seed, "client command /vtp seed did not apply");
      check(!VillagerHudRenderer.getTargets().isEmpty(), "client command /vtp target did not apply");

      // Villager A: first observation on a fresh world must be recognised as round 0.
      Villager a = summonAndOpen(context, rcon, "A");
      List<ObservedOffer> offersA = currentMenuOffers(context);
      Holder<VillagerProfession> profession = a.getVillagerData().profession();
      assertRound(context, a, offersA, seed, profession, 0);
      context.takeScreenshot("vtp-merchant-A");
      closeMenu(context);

      // Villager B: the next server roll must be round 1, recognised as the expected continuation.
      rcon.command("kill @e[type=minecraft:villager]");
      Villager b = summonAndOpen(context, rcon, "B");
      List<ObservedOffer> offersB = currentMenuOffers(context);
      assertRound(context, b, offersB, seed, profession, 1);
      String status = context.computeOnClient(mc -> VillagerTracker.status(b.getUUID(), b.getVillagerData()));
      LOGGER.info("[VTP e2e] status after B: {}", status);
      check(status.startsWith("Observed match"), "unexpected sync status: " + status);
      context.takeScreenshot("vtp-merchant-B");
      closeMenu(context);
      context.waitTicks(40);
      HudView view = context.computeOnClient(mc -> HudView.current());
      LOGGER.info("[VTP e2e] HUD: {}", view);
      context.takeScreenshot("vtp-hud-B");

      // Forecast: the mod's first round with Mending, then make the server roll up to it.
      int next = context.computeOnClient(mc -> VillagerTracker.getNextRoundForLevel(profession, 1));
      check(next == 2, "next round should be 2 but was " + next);
      EnchantmentTarget target = VillagerHudRenderer.getTargets().getFirst();
      int targetRound = context.computeOnClient(mc -> PredictionEngine.predictFrom(seed, b.getVillagerData(), 1, target, next).rerollsNeeded().orElse(-1));
      check(targetRound >= next, "no mending forecast within the reroll limit");
      LOGGER.info("[VTP e2e] mod forecasts mending at round {} ({} rerolls from now)", targetRound, targetRound - next);
      if (view != null) {
         check(view.found(), "HUD did not find the mending target: " + view);
      }

      for (int round = next; round < targetRound; round++) {
         rcon.command("kill @e[type=!minecraft:player]");
         rcon.command("summon minecraft:villager " + SPOT + " {NoAI:1b,Tags:[\"r\"],VillagerData:{profession:\"minecraft:librarian\",level:1,type:\"minecraft:plains\"}}");
         rcon.command("summon minecraft:zombie 2.5 200 0.5 {NoAI:1b,Tags:[\"z\"]}");
         String damage = rcon.command("damage @e[tag=r,limit=1] 1000 minecraft:mob_attack by @e[tag=z,limit=1]");
         check(damage.startsWith("Applied"), "server reroll failed: " + damage);
      }

      rcon.command("kill @e[type=!minecraft:player]");
      Villager z = summonAndOpen(context, rcon, "Target");
      List<ObservedOffer> offersZ = currentMenuOffers(context);
      LOGGER.info("[VTP e2e] server offers at forecast round {}: {}", targetRound, offersZ);
      check(offersZ.stream().anyMatch(o -> o.books().stream().anyMatch(book -> book.key().equals("minecraft:mending"))), "forecast round did not sell mending: " + offersZ);
      assertRound(context, z, offersZ, seed, profession, targetRound);
      context.takeScreenshot("vtp-merchant-mending");
      closeMenu(context);

      // Other professions through ViaProxy: the first roll of each trade set on this fresh world is round 0.
      // These use the client's registries as translated from the 26.2 server (enchantment definitions, tags).
      String[][] firsts = {
         {"armorer", "4", "plains"}, {"weaponsmith", "1", "plains"}, {"toolsmith", "3", "plains"}, {"fisherman", "3", "plains"},
         {"leatherworker", "1", "plains"}, {"cartographer", "2", "desert"}, {"fisherman", "5", "taiga"}, {"shepherd", "2", "plains"}
      };

      for (String[] first : firsts) {
         rcon.command("kill @e[type=!minecraft:player]");
         Villager villager = summonAndOpen(context, rcon, "First " + first[0] + first[1], first[0], Integer.parseInt(first[1]), first[2]);
         assertSimulated(context, villager, currentMenuOffers(context), seed, 0);
         context.takeScreenshot("vtp-merchant-" + first[0] + "-" + first[1]);
         closeMenu(context);
      }

      checkLevelUpForecast(context, rcon, seed, "fletcher", "trade tripwire_hook", offers -> offers.stream().anyMatch(o -> o.costA().equals("minecraft:tripwire_hook")));
      checkLevelUpForecast(context, rcon, seed, "farmer", "stew night_vision", offers -> offers.stream().anyMatch(o -> o.effect().equals("minecraft:night_vision")));
      checkLevelUpForecast(
         context, rcon, seed, "armorer", "gear protection 3 diamond_chestplate",
         offers -> offers.stream().anyMatch(o -> o.result().equals("minecraft:diamond_chestplate") && o.gear().stream().anyMatch(g -> g.key().equals("minecraft:protection") && g.level() >= 3))
      );
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target clear"));
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target mending 1"));
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target arrow slowness"));
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target trade tripwire_hook"));
      context.waitTicks(5);

      // Every settings tab and sub-screen must open and render against live server data.
      String[] tabs = {"settings", "targets", "sequence", "trades", "calibrate"};

      for (int tab = 0; tab < tabs.length; tab++) {
         int index = tab;
         context.runOnClient(mc -> mc.setScreen(GuiAccess.settingsOnTab(index)));
         context.waitTicks(10);
         context.takeScreenshot("vtp-settings-" + tabs[tab]);
      }

      String[] kinds = {"books", "gear", "stew", "arrow", "trade"};

      for (int kind = 1; kind < kinds.length; kind++) {
         int index = kind;
         context.runOnClient(mc -> mc.setScreen(GuiAccess.targetsOfKind(index)));
         context.waitTicks(10);
         context.takeScreenshot("vtp-targets-" + kinds[kind]);
      }

      context.runOnClient(mc -> mc.setScreen(GuiAccess.targetsOfKind(0)));
      context.runOnClient(mc -> mc.setScreen(GuiAccess.hudSettings(new VtpSettingsScreen())));
      context.waitTicks(10);
      context.takeScreenshot("vtp-hud-settings");
      context.runOnClient(mc -> mc.setScreen(GuiAccess.enchantmentPicker(new VtpSettingsScreen())));
      context.waitTicks(10);
      context.takeScreenshot("vtp-enchantment-picker");
      context.runOnClient(mc -> mc.setScreen(GuiAccess.settingsOnTab(0)));
      context.runOnClient(mc -> mc.setScreen(null));
      context.runOnClient(mc -> mc.disconnect(new TitleScreen(), false));
      context.waitForScreen(TitleScreen.class);
      LOGGER.info("[VTP e2e] PASSED");
   }

   private static Villager summonAndOpen(ClientGameTestContext context, Rcon rcon, String name) throws java.io.IOException {
      return summonAndOpen(context, rcon, name, "librarian", 1, "plains");
   }

   private static Villager summon(ClientGameTestContext context, Rcon rcon, String name, String profession, int level, String type) throws java.io.IOException {
      rcon.command("kill @e[type=minecraft:villager]");
      String summoned = rcon.command(
         "summon minecraft:villager " + SPOT + " {NoAI:1b,CustomName:\"" + name + "\",VillagerData:{profession:\"minecraft:" + profession + "\",level:" + level + ",type:\"minecraft:" + type + "\"}}"
      );
      check(summoned.startsWith("Summoned"), "summon failed: " + summoned);
      context.waitFor(mc -> find(mc, name).filter(v -> v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath().equals(profession)).orElse(false)).isPresent(), 200);
      context.waitFor(mc -> mc.crosshairPickEntity instanceof Villager v && v.getCustomName() != null && v.getCustomName().getString().equals(name), 100);
      context.waitTicks(10);
      return context.computeOnClient(mc -> find(mc, name).orElseThrow());
   }

   private static Villager summonAndOpen(ClientGameTestContext context, Rcon rcon, String name, String profession, int level, String type) throws java.io.IOException {
      String summoned = rcon.command(
         "summon minecraft:villager " + SPOT + " {NoAI:1b,CustomName:\"" + name + "\",VillagerData:{profession:\"minecraft:" + profession + "\",level:" + level + ",type:\"minecraft:" + type + "\"}}"
      );
      check(summoned.startsWith("Summoned"), "summon failed: " + summoned);
      try {
         context.waitFor(mc -> find(mc, name).filter(v -> v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath().equals(profession)).orElse(false)).isPresent(), 200);
      } catch (AssertionError e) {
         context.runOnClient(mc -> {
            for (Entity entity : mc.level.entitiesForRendering()) {
               LOGGER.error("[VTP e2e] client entity {} name={} pos={} data={}", entity.getType(), entity.getCustomName(), entity.position(), entity instanceof Villager v ? v.getVillagerData() : null);
            }
         });
         throw e;
      }
      context.waitTicks(10);
      Villager villager = context.computeOnClient(mc -> find(mc, name).orElseThrow());
      // Right-click exactly like a player does, so the real 1.21.11 interact packets go through ViaProxy.
      context.waitFor(mc -> mc.crosshairPickEntity == villager, 100);
      context.getInput().pressKey(options -> options.keyUse);
      context.waitForScreen(MerchantScreen.class);
      context.waitFor(mc -> mc.player.containerMenu instanceof MerchantMenu menu && !menu.getOffers().isEmpty(), 100);
      context.waitTicks(10);
      return villager;
   }

   private static Optional<Villager> find(Minecraft mc, String name) {
      for (Entity entity : mc.level.entitiesForRendering()) {
         if (entity instanceof Villager villager && villager.getCustomName() != null && villager.getCustomName().getString().equals(name)) {
            return Optional.of(villager);
         }
      }

      return Optional.empty();
   }

   private static List<ObservedOffer> currentMenuOffers(ClientGameTestContext context) {
      return context.computeOnClient(mc -> ObservedOffer.copyOf(((MerchantMenu)mc.player.containerMenu).getOffers()));
   }

   private static void assertRound(
      ClientGameTestContext context, Villager villager, List<ObservedOffer> observed, long seed, Holder<VillagerProfession> profession, int round
   ) {
      assertSimulated(context, villager, observed, seed, round);
      OptionalInt tracked = context.computeOnClient(mc -> VillagerTracker.getCurrentRound(villager.getUUID()));
      check(tracked.isPresent() && tracked.getAsInt() == round, "tracker placed villager at " + tracked + ", expected " + round);
   }

   /** The server's offers must equal the mod's simulation of that round for this villager's profession, level and biome type. */
   private static void assertSimulated(ClientGameTestContext context, Villager villager, List<ObservedOffer> observed, long seed, int round) {
      List<ObservedOffer> simulated = context.computeOnClient(
         mc -> TradeSimulator.fingerprint(PredictionEngine.simulateRound(seed, villager.getVillagerData(), villager.getVillagerData().level(), round))
      );
      LOGGER.info("[VTP e2e] {} round {} observed={} simulated={}", villager.getVillagerData(), round, observed, simulated);
      check(!simulated.isEmpty() && OfferMatching.same(observed, simulated), "round " + round + " mismatch: observed " + observed + " simulated " + simulated);
   }

   /**
    * Level-up forecast: with only {@code target} active, look at a level-1 villager, read the HUD's
    * "LEVEL: after N other level-ups", make the server roll N rounds of that level, then check the next
    * villager reaching that level really gets the target.
    */
   private static void checkLevelUpForecast(
      ClientGameTestContext context, Rcon rcon, long seed, String profession, String targetCommand, java.util.function.Predicate<List<ObservedOffer>> hasTarget
   ) throws java.io.IOException {
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target clear"));
      context.runOnClient(mc -> mc.player.connection.sendCommand("vtp target " + targetCommand));
      context.waitTicks(5);
      EnchantmentTarget target = VillagerHudRenderer.getTargets().getFirst();
      Villager novice = summon(context, rcon, "Novice " + profession, profession, 1, "plains");
      context.waitFor(mc -> HudView.current() != null && HudView.current().uuid().equals(novice.getUUID()) && HudView.current().result().contains("level-up"), 200);
      HudView view = context.computeOnClient(mc -> HudView.current());
      Optional<PredictionEngine.LevelPlan> plan = context.computeOnClient(mc -> PredictionEngine.predictLevelUp(seed, novice.getVillagerData(), target));
      LOGGER.info("[VTP e2e] {} target {}: HUD says '{}' plan={}", profession, target.label(), view.result(), plan.map(p -> p.level() + "/" + p.skips()).orElse("none"));
      check(plan.isPresent(), "no level-up plan for " + target.label());
      context.takeScreenshot("vtp-hud-levelup-" + profession);

      for (int i = 0; i < plan.get().skips(); i++) {
         rcon.command("kill @e[type=!minecraft:player]");
         rcon.command("summon minecraft:villager 0.5 200 6.5 {NoAI:1b,Tags:[\"r\"],VillagerData:{profession:\"minecraft:" + profession + "\",level:" + plan.get().level() + ",type:\"minecraft:plains\"}}");
         rcon.command("summon minecraft:zombie 2.5 200 6.5 {NoAI:1b,Tags:[\"z\"]}");
         String damage = rcon.command("damage @e[tag=r,limit=1] 1000 minecraft:mob_attack by @e[tag=z,limit=1]");
         check(damage.startsWith("Applied"), "server roll failed: " + damage);
      }

      rcon.command("kill @e[type=!minecraft:player]");
      Villager levelled = summonAndOpen(context, rcon, "Levelled " + profession, profession, plan.get().level(), "plains");
      List<ObservedOffer> offers = currentMenuOffers(context);
      LOGGER.info("[VTP e2e] {} level {} after {} skips: {}", profession, plan.get().level(), plan.get().skips(), offers);
      check(hasTarget.test(offers), target.label() + " missing at forecast level-up: " + offers);
      assertSimulated(context, levelled, offers, seed, plan.get().skips());
      context.takeScreenshot("vtp-merchant-levelup-" + profession);
      closeMenu(context);
   }

   private static void closeMenu(ClientGameTestContext context) {
      context.runOnClient(mc -> mc.player.closeContainer());
      context.waitForScreen(null);
   }

   private static void check(boolean condition, String message) {
      if (!condition) {
         throw new AssertionError("[VTP e2e] " + message);
      }
   }
}
