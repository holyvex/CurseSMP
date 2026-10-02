# CurseSMP (Paper 26.2)

Curses, mythic weapons, rerolls and events for a Cursed SMP server.

## Build
Requires **JDK 25** and Gradle 9+ (or open the folder in IntelliJ).

    gradle build

The jar is in `build/libs/`. Drop it in your server's `plugins/` folder (Paper 26.2).

## Commands
| Command | Who | What |
|---|---|---|
| `/cursesmp give <curse\|energy\|reroll\|shard\|handle\|shadowslayer\|soulcrusher\|shield> [player]` | op | Give items |
| `/cursesmp start` | op | Every player gets a rolled curse (each curse rolls 25%, first hit wins) |
| `/cursesmp randomcurse <player>` | op | Roll a curse for one player |
| `/cursesmp energy <player> <0-3>` | op | Set cursed energy (testing) |
| `/mythiceventstart <ShadowSlayerSwordEvent\|SoulCrusherEvent\|CursedShieldEvent>` | op | Start an event |
| `/cursetrust <player>` / `/cursetrustremove <player>` | everyone | Trust links |
| `/ability <1-4>` | everyone | Use a curse ability (Dragon: 4 = dash) |
| `/curseinfo` | everyone | Your curse, energy, cooldowns |

Mythic weapons: **F** = ability 1, **Shift+F** = ability 2 (while held in main hand).

## Crafting (edit in `Items.java`)
- **Cursed Energy** (shapeless): Diamond Block, Echo Shard, Netherite Scrap, Amethyst Shard
- **Reroll System**: `DED / ENE / DED` (D = Diamond, E = Echo Shard, N = Nether Star)
- **Shadow Slayer**: ` S / SWS / H ` (S = Shadow Shard, W = Netherite Sword, H = Shadow Handle)

## Textures
Set `custom-models: true` in `config.yml` once a resource pack is installed. Items then use item models
`cursesmp:shadow_slayer`, `cursesmp:soul_crusher`, `cursesmp:curse_<id>`, `cursesmp:shadow_shard`,
`cursesmp:shadow_handle`, `cursesmp:cursed_energy`, `cursesmp:reroll_system`
(files at `assets/cursesmp/items/<name>.json`). The Cursed Shield intentionally has none.
