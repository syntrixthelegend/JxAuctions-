package dev.jxauctions.sync;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.rabbitmq.client.*;
import dev.jxauctions.JxAuctions;
import dev.jxauctions.database.DatabaseManager;
import dev.jxauctions.util.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MultiServerSync implements PluginMessageListener {

    private final JxAuctions plugin;
    private final String serverId = UUID.randomUUID().toString().substring(0, 8);
    private String mode = "disabled";
    private String channelName = "jxauctions:primary";
    private volatile long lastSyncId = 0L;
    private volatile boolean shuttingDown = false;

    private JedisPool jedisPool;
    private JedisPubSub jedisPubSub;

    private com.rabbitmq.client.Connection rabbitConnection;
    private Channel rabbitChannel;

    public MultiServerSync(JxAuctions plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        var cfg = plugin.getConfigManager().getConfig();
        this.mode = cfg.getString("multi-server-support.mode", "disabled").toLowerCase(Locale.ROOT);
        this.channelName = "jxauctions:" + cfg.getString("multi-server-support.channel-name", "primary");

        switch (mode) {
            case "proxy" -> {
                Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
                Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
            }
            case "sql" -> {
                this.lastSyncId = plugin.getDatabaseManager().getMaxSyncId();
                long interval = cfg.getLong("multi-server-support.sql-poll-interval-ticks", 40L);
                SchedulerUtil.runAsyncTimer(plugin, () -> {
                    List<DatabaseManager.SyncMessage> updates = plugin.getDatabaseManager().pollSyncMessages(serverId, lastSyncId);
                    if (!updates.isEmpty()) {
                        lastSyncId = updates.get(updates.size() - 1).id();
                        plugin.getAuctionManager().reloadCacheFromDatabase();
                    }
                    plugin.getDatabaseManager().pruneSyncMessages(System.currentTimeMillis() - 300_000L);
                }, interval, interval);
            }
            case "redis" -> initRedis(cfg);
            case "rabbitmq" -> initRabbitMq(cfg);
        }
    }

    private void initRedis(org.bukkit.configuration.file.FileConfiguration cfg) {
        String host = cfg.getString("multi-server-support.redis.address", "127.0.0.1");
        int port = cfg.getInt("multi-server-support.redis.port", 6379);
        String user = cfg.getString("multi-server-support.redis.username", "");
        String pass = cfg.getString("multi-server-support.redis.password", "");

        if (!user.isBlank() && !pass.isBlank()) {
            this.jedisPool = new JedisPool(new JedisPoolConfig(), host, port, 3000, user, pass);
        } else if (!pass.isBlank()) {
            this.jedisPool = new JedisPool(new JedisPoolConfig(), host, port, 3000, pass);
        } else {
            this.jedisPool = new JedisPool(new JedisPoolConfig(), host, port);
        }

        this.jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (!message.startsWith(serverId + ":")) {
                    SchedulerUtil.runAsync(plugin, () -> plugin.getAuctionManager().reloadCacheFromDatabase());
                }
            }
        };

        SchedulerUtil.runAsync(plugin, () -> {
            while (!shuttingDown && !Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    plugin.getAuctionManager().reloadCacheFromDatabase();
                    jedis.subscribe(jedisPubSub, channelName);
                } catch (Exception e) {
                    if (shuttingDown) break;
                    plugin.getLogger().warning("Redis subscriber disconnected, reconnecting in 5s: " + e.getMessage());
                    try { Thread.sleep(5000L); } catch (InterruptedException ie) { break; }
                }
            }
        });
    }

    private void initRabbitMq(org.bukkit.configuration.file.FileConfiguration cfg) {
        SchedulerUtil.runAsync(plugin, () -> {
            while (!shuttingDown && !Thread.currentThread().isInterrupted()) {
                try {
                    ConnectionFactory factory = new ConnectionFactory();
                    factory.setHost(cfg.getString("multi-server-support.rabbitmq.address", "127.0.0.1"));
                    factory.setPort(cfg.getInt("multi-server-support.rabbitmq.port", 5672));
                    factory.setVirtualHost(cfg.getString("multi-server-support.rabbitmq.vhost", "/"));
                    factory.setUsername(cfg.getString("multi-server-support.rabbitmq.username", "guest"));
                    factory.setPassword(cfg.getString("multi-server-support.rabbitmq.password", "guest"));
                    factory.setAutomaticRecoveryEnabled(true);
                    factory.setNetworkRecoveryInterval(5000);

                    this.rabbitConnection = factory.newConnection();
                    this.rabbitChannel = rabbitConnection.createChannel();
                    rabbitChannel.exchangeDeclare(channelName, BuiltinExchangeType.FANOUT);
                    String queueName = rabbitChannel.queueDeclare().getQueue();
                    rabbitChannel.queueBind(queueName, channelName, "");

                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                        if (!message.startsWith(serverId + ":")) {
                            SchedulerUtil.runAsync(plugin, () -> plugin.getAuctionManager().reloadCacheFromDatabase());
                        }
                    };
                    plugin.getAuctionManager().reloadCacheFromDatabase();
                    rabbitChannel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
                    break;
                } catch (Exception e) {
                    if (shuttingDown) break;
                    plugin.getLogger().warning("RabbitMQ connection error, retrying in 5s: " + e.getMessage());
                    try { Thread.sleep(5000L); } catch (InterruptedException ie) { break; }
                }
            }
        });
    }

    public void broadcastSync(String eventType, String auctionUuid) {
        if (mode.equals("disabled")) return;
        String payload = serverId + ":" + eventType + ":" + auctionUuid;

        switch (mode) {
            case "sql" -> plugin.getDatabaseManager().pushSyncMessage(serverId, payload);
            case "proxy" -> {
                Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                if (carrier != null) {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("Forward");
                    out.writeUTF("ALL");
                    out.writeUTF(channelName);
                    out.writeUTF(payload);
                    carrier.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
                }
            }
            case "redis" -> {
                if (jedisPool != null) {
                    SchedulerUtil.runAsync(plugin, () -> {
                        try (Jedis jedis = jedisPool.getResource()) {
                            jedis.publish(channelName, payload);
                        } catch (Exception ignored) {}
                    });
                }
            }
            case "rabbitmq" -> {
                if (rabbitChannel != null && rabbitChannel.isOpen()) {
                    SchedulerUtil.runAsync(plugin, () -> {
                        try {
                            rabbitChannel.basicPublish(channelName, "", null, payload.getBytes(StandardCharsets.UTF_8));
                        } catch (Exception ignored) {}
                    });
                }
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        if (subchannel.equals(channelName)) {
            try {
                short len = in.readShort();
                if (len <= 0) return;
                byte[] msgBytes = new byte[len];
                in.readFully(msgBytes);
                ByteArrayDataInput msgIn = ByteStreams.newDataInput(msgBytes);
                String payload = msgIn.readUTF();
                if (!payload.startsWith(serverId + ":")) {
                    SchedulerUtil.runAsync(plugin, () -> plugin.getAuctionManager().reloadCacheFromDatabase());
                }
            } catch (Exception ignored) {}
        }
    }

    public String getServerId() { return serverId; }

    public void shutdown() {
        shuttingDown = true;
        if (mode.equals("proxy")) {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, "BungeeCord");
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, "BungeeCord", this);
        }
        if (jedisPubSub != null && jedisPubSub.isSubscribed()) {
            jedisPubSub.unsubscribe();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
        try {
            if (rabbitChannel != null && rabbitChannel.isOpen()) rabbitChannel.close();
            if (rabbitConnection != null && rabbitConnection.isOpen()) rabbitConnection.close();
        } catch (Exception ignored) {}
    }
}
