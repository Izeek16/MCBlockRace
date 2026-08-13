# Block Race (Fabric 1.20.1)

head-to-head race mod. a random item is picked and shown at the top of the screen, both players are frozen (can look around, can't move) for a 15 second countdown, then a stopwatch starts and you race to get that item into your inventory first — find it or craft it, doesn't matter. the moment someone has it, everyone freezes, the winner + final time show on screen, a point is added, and you go again.

**server-side only.** only the world host (or the server) needs this mod. friends can join on a completely vanilla 1.20.1 client and everything still works — the freeze, the countdown, the timer bar, the win screen are all vanilla packets.

---

## Building the jar (GitHub Actions — same flow as Destructinator 3000)

1. Create a new GitHub repo and upload everything in this folder (keep the folder structure, including `.github/workflows/build.yml`).
2. Go to the **Actions** tab — the `build` workflow runs automatically on push (~2–3 min).
3. Open the finished run → **Artifacts** → download `blockrace` → unzip it.
4. Use `blockrace-1.0.0.jar` (ignore the `-sources` jar if present).

## Installing

**Singleplayer / LAN (easiest for you two):**
1. Install **Fabric Loader for 1.20.1** on the host's client.
2. Drop **Fabric API (1.20.1)** and **blockrace-1.0.0.jar** into the host's `mods` folder.
3. Host opens a world → Esc → **Open to LAN** (cheats don't need to be on — `/race` works for everyone).
4. Friend joins over LAN on plain vanilla 1.20.1 (or Fabric, doesn't matter).

**Dedicated server:** drop the jar + Fabric API into the server's `mods` folder. Clients need nothing.

## Commands (anyone can run them)

| Command | What it does |
|---|---|
| `/race start` | Start a round right where everyone stands (clears inventories, picks item, 15s frozen countdown, go) |
| `/race next` | Teleport everyone together to a random spot up to ~12,000 blocks away — fresh terrain, fresh inventory — and start a round. This is the "new world" button. |
| `/race skip` | Reroll the target item and restart the round (for when it picks something dumb) |
| `/race scores` | Show the tally |
| `/race stop` | End the session and show final scores |
| `/race pool easy` | Curated fast-round pool (~100 common items — logs, tools, food, basic ores) |
| `/race pool normal` | Default. Everything obtainable in survival minus extreme grinds (no netherite, end-game, silk-touch-only blocks, ocean monument loot, music discs, etc.) |
| `/race pool everything` | Everything obtainable in survival. Chaos. Netherite block is on the table. |

## Notes

- **"New world" per round:** truly regenerating the world can't be done without a restart, so `/race next` does the practical equivalent — teleports you both to a random far-off ungenerated area with nothing in your inventory. If you want a literally new seed, just create a new world; the mod works instantly, no setup.
- **Win condition** is the item entering your *inventory* — a crafted item sitting in the crafting output slot doesn't count until you take it, exactly like the video.
- **Scores** are per-session (reset when the server/world closes).
- **Tuning the item pools:** everything is plain item-id strings in `src/main/java/com/toasterz/blockrace/ItemPools.java` — add/remove ids from the blacklists or the easy whitelist and re-push to rebuild. The filter won't be 100% perfect for every obscure item; that's what `/race skip` is for.
- Timer runs on server ticks, so precision is 0.05s.
