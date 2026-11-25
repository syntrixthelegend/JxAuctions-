# JxAuctions

> A free, lightweight, open-source Minecraft Auction House plugin for **Spigot, Paper, Purpur, and Folia (1.18–1.21+)** — engineered as a clean-room alternative with **~95% functional and configuration parity** to [AxAuctions](https://docs.artillex-studios.com/axauctions.html).

[https://img.shields.io/badge/Java-17%2B-orange]
[https://img.shields.io/badge/Minecraft-1.18--1.21%2B-brightgreen]
[https://img.shields.io/badge/Folia-Supported-blue]
[https://img.shields.io/badge/JAR%20Size-~120%20KB-informational]

---

## Why JxAuctions?

**JxAuctions** (`v2.0.0`) delivers the complete feature set, YAML structure, GUI system, and multi-server architecture expected from a modern auction house plugin while remaining **100% open-source** and **lightweight** (~120 KB compiled JAR via Spigot/Paper's runtime `libraries:` loader).

### ~95% Similarity to AxAuctions
JxAuctions is written completely from scratch (`dev.jxauctions`, 0% shared code) to mirror **~95%** of [AxAuctions](https://docs.artillex-studios.com/axauctions.html)'s user experience and configuration layout:

| Category | Parity | Details |
| :--- | :---: | :--- |
| **Config & GUI Schema** | ~98% | Uses the same root YAMLs (`config.yml`, `categories.yml`, `currencies.yml`, `discord.yml`, `lang.yml`) and all 11 GUI templates (`main-gui.yml`, `category-gui.yml`, `my-items-gui.yml`, `expired-items-gui.yml`, `history-gui.yml`, `deleted-gui.yml`, `logs-gui.yml`, `confirm-purchase-gui.yml`, `currency-selector-gui.yml`, `admin-removal-gui.yml`, `shulker-preview-gui.yml`) plus `confirm-listing-gui.yml`. |
| **Commands & Permissions** | ~95% | Identical `/ah` and `/ahadmin` subcommands, tab completion, and permission hierarchy (`jxauctions.*`, plus `axauctions.limit.<N>` compatibility). |
| **Databases & Sync** | ~95% | Supports H2 and MySQL 8 (`utf8mb4`), **adds SQLite**, and includes SQL cursor polling, Proxy messaging, Redis Pub/Sub, and RabbitMQ fanout sync. |
| **Currencies & Hooks** | ~95% | Built-in reflection hooks for 16 currency providers, PlaceholderAPI custom currencies with per-currency tax, and `%jxauctions_*%` placeholders. |
| **Extra in JxAuctions** | — | Includes `SQLite` database support, an optional `confirm-listing-gui.yml` confirmation menu, and `Floodgate` Bedrock detection. |

---

## Key Features

- **Multi-Database Storage:** Embedded **H2** (default), **SQLite**, and **MySQL 8** (`utf8mb4`) powered by HikariCP connection pooling, a dedicated single-thread async write queue, and graceful shutdown flushing.
- **Zero-Setup & High-Speed Multi-Server Sync:**
  - `disabled` — Single-server mode.
  - `sql` — Database cursor polling (zero extra infrastructure).
  - `proxy` — BungeeCord / Velocity plugin messaging channels.
  - `redis` — Redis Pub/Sub with automatic reconnection.
  - `rabbitmq` — RabbitMQ AMQP fanout exchange with automatic recovery.
- **16+ Currency Providers & Custom Currencies:** Built-in support for Vault, Experience, PlayerPoints, CoinsEngine, ExcellentEconomy, EcoBits, RedisEconomy, UltraEconomy, TokenManager, RoyaleEconomy, SuperMobCoins, TheOnly-MobCoins, BeastTokens, RivalCredits, KingdomsX, and RivalHarvesterHoes — plus **PlaceholderAPI custom currencies** and per-currency tax rates.
- **12 Fully Customizable GUIs:** Configurable titles, row counts, slot ranges, conditional buttons (`%categories_enabled%`, `%expired_items_enabled%`), `auto-update-ticks` live timer refreshes, and action tags (`[MENU]`, `[SORTING]`, `[CATEGORY]`, `[CURRENCY]`, `[SEARCH]`, `[PAGE]`, `[REFRESH]`, `[SOUND]`, `[CLOSE]`).
- **Shulker, Barrel & Bundle Preview:** Right-click any container in the main auction house, history, or deleted items menu to inspect its contents before purchasing.
- **Smart Cached Search & Sorting:** Search across item name, lore, material, enchantments, seller name, and container contents (up to 2 levels deep) via Anvil GUI or Chat input (with automatic Bedrock fallback). Sort by `newest`, `oldest`, `cheapest`, or `priciest`.
- **Anti-Dupe & Transaction Safety:**
  - Atomic database compare-and-set (`DELETE ... WHERE uuid = ? AND status = 'ACTIVE'`) to prevent cross-server double-buys.
  - Single-use menu click locks (`MenuHolder.consumed`) and configurable GUI click cooldowns (`gui-refresh-cooldown-milliseconds`).
  - Withdraw-before-claim with automatic refund/pending-payout rollback if a listing was already bought or cancelled.
  - Offline pending item & pending payout delivery on join (`delay-money-give-seconds`).
  - Item blacklisting (material, display name wildcards, CustomModelData) and serialized byte-size limits (`content-limit-bytes`).
- **Admin Moderation & Audit Logs:** History viewer, deleted items recovery, staff action logs (`/ahadmin logs`), force-open menus, dynamic sell limits, and built-in data converters.
- **Discord Webhooks:** Customizable JSON embeds for `auction-start`, `auction-buy`, and `auction-cancel`.
- **Folia & Bedrock Ready:** Native Folia `AsyncScheduler`, `GlobalRegionScheduler`, and `EntityScheduler` support, plus Floodgate detection.

---

## Requirements & Installation

- **Server Software:** Spigot, Paper, Purpur, or Folia (`1.18` – `1.21+`)
- **Java:** Java `17` or newer
- **Optional Dependencies:** `Vault` (recommended), `PlaceholderAPI`, `Floodgate`, and any supported economy plugin

### Setup
1. Download `JxAuctions-2.0.0.jar` (or build from source).
2. Drop the JAR into your server's `plugins/` folder.
3. Start or restart the server — Paper/Spigot will automatically download runtime drivers (`HikariCP`, `H2`, `SQLite`, `MySQL`, `Jedis`, `RabbitMQ`) defined in `plugin.yml`.
4. Configure `plugins/JxAuctions/config.yml`, `currencies.yml`, `lang.yml`, and `guis/*.yml` as needed, then run `/ahadmin reload`.

---

## Commands

### Player Commands (`/ah`)
Aliases: `/jxah`, `/auctionhouse`, `/jxauction`, `/auction`, `/jxauctionhouse` (customizable in `config.yml`)

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/ah` | Open the default auction house menu | `jxauctions.open` |
| `/ah open [menu]` | Open a specific menu (`main`, `category`, `my-items`, `expired-items`, `history`, `deleted`) | `jxauctions.open` |
| `/ah sell <price> [amount] [currency]` | List the held item (supports `1k`, `1.5m`, `2b`, `1t` abbreviations) | `jxauctions.sell` |
| `/ah search [text]` | Search active listings or open the search prompt | `jxauctions.search` |
| `/ah view [player]` | View your own items or another player's active listings | `jxauctions.view` |
| `/ah history` | Open your transaction history | `jxauctions.history` |
| `/ah deleted` | View your auto-deleted expired items | `jxauctions.deleted` |

### Admin Commands (`/ahadmin`)
Requires `jxauctions.admin` (OP by default).

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/ahadmin reload` | Reload all configuration, GUI, and currency files | `jxauctions.reload` |
| `/ahadmin forceopen <player> [menu]` | Force-open a menu for another player | `jxauctions.forceopen` |
| `/ahadmin history [player]` | View global or player transaction history | `jxauctions.history.admin` |
| `/ahadmin deleted [player]` | View global or player deleted items | `jxauctions.deleted.admin` |
| `/ahadmin logs [player]` | Open the staff & player audit log viewer | `jxauctions.logs.admin` |
| `/ahadmin limit <set\|give\|take\|reset> <player> [amount]` | Modify a player's database sell limit offset | `jxauctions.limit` |
| `/ahadmin convert <plugin>` | Import listings from `AuctionHouse`, `zAuctionHouseV3`, `CrazyAuctions`, or `PlayerAuctions` | `jxauctions.convert` |

---

## Permissions

| Permission | Description | Default |
| :--- | :--- | :---: |
| `jxauctions.use` | Access to `/ah` base command | `true` |
| `jxauctions.open` | Open auction house menus | `true` |
| `jxauctions.sell` | Sell items on the auction house | `true` |
| `jxauctions.search` | Search listings | `true` |
| `jxauctions.view` | View a player's active listings | `true` |
| `jxauctions.history` | Open personal history viewer | `true` |
| `jxauctions.deleted` | Open personal deleted items viewer | `true` |
| `jxauctions.limit.<amount>` | Set permission-based sell limit (also reads `axauctions.limit.<amount>`) | `—` |
| `jxauctions.limit.bypass` | Unlimited auction listings (`∞`) | `op` |
| `jxauctions.admin` | Base permission for `/ahadmin` | `op` |
| `jxauctions.admin.removal` | Shift-click listings in `/ah` to open the Admin Removal GUI | `op` |
| `jxauctions.history.admin.take` | Recover items directly from `/ahadmin history` | `op` |
| `jxauctions.deleted.admin.take` | Recover items directly from `/ahadmin deleted` | `op` |
| `jxauctions.update-notify` | Receive update notifications on join | `op` |

---

## Supported Currencies & Plugins

Configure currencies, display formats, and per-currency tax percentages in `currencies.yml`:

- **Built-in:** `Vault`, `Experience`
- **Multi-Currency Plugins:** `CoinsEngine`, `ExcellentEconomy`, `EcoBits`, `RedisEconomy`, `UltraEconomy`
- **Token & Coin Plugins:** `PlayerPoints`, `TokenManager`, `RoyaleEconomy`, `BeastTokens`, `SuperMobCoins`, `TheOnly-MobCoins`, `RivalCredits`, `KingdomsX`, `RivalHarvesterHoes`
- **Custom PlaceholderAPI Currencies:** Define unlimited currencies in `currencies.yml` under `placeholder-currencies` using any numeric placeholder + console `give-command` / `take-command`.

---

## Placeholders (`PlaceholderAPI`)

All placeholders use the `jxauctions` identifier (e.g., `%jxauctions_sell_limit%`). Append `_raw` to any numeric placeholder for unformatted numbers.

| Placeholder | Description |
| :--- | :--- |
| `%jxauctions_active_count%` / `%jxauctions_sell_count%` | Player's active listings count |
| `%jxauctions_expired_count%` | Player's expired listings count |
| `%jxauctions_total_active_count%` | Global active listings count |
| `%jxauctions_sell_limit%` | Player's max listing limit (or `∞` if bypassed) |
| `%jxauctions_selected_sorting%` / `_raw` | Currently selected sort mode |
| `%jxauctions_selected_category%` / `_raw` | Currently selected category |
| `%jxauctions_selected_currency%` / `_raw` | Currently selected currency filter |
| `%jxauctions_items_sold%` / `%jxauctions_money_made%` | Player's lifetime sales count & earnings |
| `%jxauctions_items_purchased%` / `%jxauctions_money_spent%` | Player's lifetime purchases count & spend |
| `%jxauctions_items_sold_<currency>%` / `%jxauctions_money_made_<currency>%` | Per-currency player sales & earnings |
| `%jxauctions_items_purchased_<currency>%` / `%jxauctions_money_spent_<currency>%` | Per-currency player purchases & spend |
| `%jxauctions_total_items_sold%` / `%jxauctions_total_money_made%` | Global server-wide sales & earnings |
| `%jxauctions_total_items_purchased%` / `%jxauctions_total_money_spent%` | Global server-wide purchases & spend |

---

## Developer API

`JxAuctions` exposes `dev.jxauctions.api.JxAuctionsAPI` and four Bukkit events:

- `AuctionListEvent` *(Cancellable — supports modifying listing price)*
- `AuctionBuyEvent` *(Cancellable)*
- `AuctionCancelEvent` *(Includes `isAdminAction()`)*
- `AuctionExpireEvent`

```java
// Fetch active Diamond-category listings sorted by cheapest
List<AuctionItem> listings = JxAuctionsAPI.getActiveAuctions("minerals", null, null, SortType.CHEAPEST);

// Check a player's effective sell limit
int limit = JxAuctionsAPI.getSellLimit(player);
