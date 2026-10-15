# minecraft-mods

## Villager Trading Predictor (VTP)

| File | Minecraft client | Notes |
| --- | --- | --- |
| `VTP-26.3-fabric-1.3.0.jar` | 26.3 | Original build. |
| `VTP-1.21.11-fabric-1.3.0.jar` | **1.21.11** | Port for a 1.21.11 client that plays on a **26.2 server**. |

### 1.21.11 port

Install `VTP-1.21.11-fabric-1.3.0.jar` with Fabric Loader 0.17+ and Fabric API for 1.21.11 (Java 21).

Minecraft 26.x made villager trades data-driven and rolls them from a per-world random
sequence. That is what VTP predicts. The 1.21.11 client has none of that code, so the port
bundles the vanilla **26.2** trade definitions (`vtp_data/26.2/`) and reproduces 26.2's trade
roll exactly. Enchantment pools use the enchantment registry and tags the server sends.
All features are kept: `/vtp` commands, keybinds, HUD, merchant-screen overlay, settings GUI,
calibration, sequence and level forecasts, and per-server saved seeds and state.

To join a 26.2 server from a 1.21.11 client you need protocol translation: ViaVersion and
ViaBackwards on the server, or ViaProxy in between.

Every vanilla villager trade is predicted: all 13 professions × 5 levels. That includes
enchanted books, enchanted armor, tools and weapons, suspicious stew effects, tipped arrows,
dyed leather, explorer maps and biome-specific trades.

Targets (`/vtp` → Targets tab, or commands):

| Target | Command | Example |
| --- | --- | --- |
| Enchanted book | `/vtp target <enchantment> [level] [maxprice]` | `/vtp target mending 1` |
| Enchanted gear | `/vtp target gear <enchantment> [level] [item]` | `/vtp target gear protection 4 diamond_chestplate` |
| Stew effect | `/vtp target stew <effect>` | `/vtp target stew night_vision` |
| Tipped arrows | `/vtp target arrow <potion>` | `/vtp target arrow slowness` |
| Any trade with an item | `/vtp target trade <item>` | `/vtp target trade tripwire_hook` |

- Level-1 trades: the HUD shows how many rerolls until the target.
- Higher-level trades (Expert stew, Master tripwire, crossbows and arrows, enchanted diamond
  gear): the HUD shows which level-up gets the target, e.g. "Master: after 2 other level-ups".
  That level's trades come from a shared sequence too, so 2 other villagers of that profession
  must reach Master first. The level preview rows show what the next villager to reach each
  level will get.

Limitations:
- In singleplayer, a 1.21.11 integrated server still uses the old hard-coded trades, which
  don't come from a predictable sequence. The mod reports "singleplayer runs 1.21.11 trades"
  there instead of showing wrong predictions.
- Each profession level has its own sequence. The mod finds its place in a level's sequence
  when you open a villager's trades right after they were rolled: a fresh level-1 villager,
  or a villager you watch level up. Rolls nobody observed (other players, other villagers)
  are found again on the next such observation.
- Explorer maps assume the server finds the structure, which it practically always does. If it
  can't, the server skips that trade and that level's sequence needs re-syncing.

Source and tests are in `vtp-1.21.11/`:
- `./gradlew build` builds the jar and runs `ServerTruthTest`. That test replays 1,156 villager
  trade rolls recorded from a real vanilla 26.2 server: two seeds, all 65 profession levels,
  every biome type for biome-specific trades. The simulator must reproduce every offer
  exactly: items, prices, book and gear enchantments, stew, dye and potion.
- `./gradlew runClientGameTest` / `prodClientGameTest` (with `-Dvtp.e2e.address`,
  `-Dvtp.e2e.seed`, RCON on `127.0.0.1:25575` with password `test`) run an end-to-end test.
  A 1.21.11 client joins a 26.2 server (normal world) through ViaProxy and opens villagers of
  many professions and biome types. It checks that the forecast round really sells the target
  book, and that level-up forecasts for tripwire hooks, Night Vision stew and a Protection III
  chestplate come true.
