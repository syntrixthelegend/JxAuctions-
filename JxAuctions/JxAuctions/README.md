# JxAuctions

A free, lightweight, open-source Minecraft Auction House plugin alternative to AxAuctions, built for Spigot, Paper, Purpur, and Folia (1.18–1.21+).

## Features
- **Databases**: Embedded H2 (default), SQLite, and MySQL 8 (`utf8mb4`) with HikariCP connection pooling, ordered async write queue, and clean shutdown flushing.
- **Multi-Server Sync**: SQL cursor-based messaging, BungeeCord/Velocity proxy messaging, Redis Pub/Sub (with auto-reconnect), and RabbitMQ AMQP (with auto-reconnect).
- **Multi-Currency & Custom Currencies**: Built-in support for Vault, Experience, PlayerPoints, CoinsEngine, ExcellentEconomy, EcoBits, RedisEconomy, UltraEconomy, TokenManager, RoyaleEconomy, SuperMobCoins, TheOnly-MobCoins, BeastTokens, RivalCredits, KingdomsX, RivalHarvesterHoes, plus PlaceholderAPI custom currencies with per-currency tax.
- **Interactive GUIs**: Main Auction House, Category Selector, My Items, Expired Items, History Viewer, Deleted Items Viewer, Admin Logs, Purchase Confirmation, Listing Confirmation, Currency Selector, Admin Removal, and Shulker/Barrel/Bundle Content Preview.
- **Smart Cached Search & Sorting**: Instant cached search across item name, lore, material, enchantments, seller, and container contents (shulkers/bundles); sort by newest, oldest, cheapest, or priciest.
- **Anti-Dupe & Security**: Atomic DB compare-and-set claims, withdraw-before-claim with automatic refund rollback, inventory-full checks, GUI click cooldowns, item blacklisting, and serialized byte-size limits.
- **Folia & Bedrock Ready**: Native Folia `AsyncScheduler`, `GlobalRegionScheduler`, and `EntityScheduler` support, plus Floodgate Bedrock detection.
- **Discord Webhooks & PlaceholderAPI**: Full embed webhooks (`auction-start`, `auction-buy`, `auction-cancel`) and `%jxauctions_*%` PlaceholderAPI expansion.

## Building from Source
Requires **Java 17+** and **Maven 3.8+**:
```bash
mvn clean package
```
This builds a lightweight JAR (`target/JxAuctions-2.0.0.jar`, ~120 KB) that uses Paper/Spigot's runtime `libraries:` loader in `plugin.yml` to load database and messaging drivers cleanly.
