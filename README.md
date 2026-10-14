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

Limitations:
- In singleplayer, a 1.21.11 integrated server still uses the old hard-coded trades, which
  don't come from a predictable sequence. The mod reports "singleplayer runs 1.21.11 trades"
  there instead of showing wrong predictions.
- The same trade types as the original are reported as unsupported: enchanted gear,
  explorer maps, dyed leather, stew effects, and conditional trades.

Source and tests are in `vtp-1.21.11/`:
- `./gradlew build` builds the jar and runs `ServerTruthTest`. That test replays 1,080 villager
  trade rolls recorded from a real vanilla 26.2 server (two seeds, all professions and levels).
  The simulator must match every offer in every supported round.
- `./gradlew runClientGameTest` / `prodClientGameTest` (with `-Dvtp.e2e.address`,
  `-Dvtp.e2e.seed`, RCON on `127.0.0.1:25575` with password `test`) run an end-to-end test.
  A 1.21.11 client joins a 26.2 server through ViaProxy, opens villagers, and checks that the
  forecast round really sells the target book.
