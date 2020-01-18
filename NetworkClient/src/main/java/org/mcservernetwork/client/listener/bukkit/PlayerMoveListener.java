package org.mcservernetwork.client.listener.bukkit;

import com.connorlinfoot.actionbarapi.ActionBarAPI;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.mcservernetwork.client.ClientStatusHandler;
import org.mcservernetwork.client.util.ColorUtils;
import org.mcservernetwork.client.util.PlayerUtils;
import org.mcservernetwork.client.util.SectorLocationUtils;
import org.mcservernetwork.client.util.StringUtils;
import org.mcservernetwork.commons.NetworkAPI;
import org.mcservernetwork.commons.net.Channel;
import org.mcservernetwork.commons.net.Sector;
import org.mcservernetwork.commons.net.packet.PacketStatus;
import org.mcservernetwork.commons.net.packet.PacketTransfer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerMoveListener implements Listener {

    public static final Map<UUID, BossBar> BOSSBARS = new WeakHashMap<>();
    public static final Set<UUID> TRANSFERRING = new HashSet<>();

    private final Map<UUID, Set<Location>> changedBlocks = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService service = Executors.newScheduledThreadPool(4);

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = event.getTo();
        if (location == null)
            return;

        Location fromLocation = event.getFrom();
        if (location.getX() == fromLocation.getX() && location.getZ() == fromLocation.getZ())
            return;

        double distance = SectorLocationUtils.distance(location);
        Sector nearest = SectorLocationUtils.getNearest(location);
        if (distance > SectorLocationUtils.MAX_DISTANCE || nearest == null) {
            BossBar bossBar = BOSSBARS.get(player.getUniqueId());
            if (bossBar != null) {
                bossBar.removeAll();
                BOSSBARS.remove(player.getUniqueId());
            }
            return;
        }

        BossBar bossBar = BOSSBARS.get(player.getUniqueId());
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("",
                    BarColor.PINK, BarStyle.SOLID);
            bossBar.setVisible(true);
            bossBar.setProgress(0);
            bossBar.addPlayer(player);
            BOSSBARS.put(player.getUniqueId(), bossBar);
        }
        String name =StringUtils.capitalizeFirstLetter(nearest.getSectorName());
        bossBar.setTitle(ColorUtils.fixColors("&5&l" + name
                + " &5&l(&d&l" + Math.round(distance) + "m&5&l)"));
        bossBar.setProgress(SectorLocationUtils.progress(distance));

        Sector in = SectorLocationUtils.getSectorIn(location);
        if (in == null)
            return;

        if (TRANSFERRING.contains(player.getUniqueId()))
            return;

        bossBar.removeAll();
        BOSSBARS.remove(player.getUniqueId());

        PacketTransfer packet = new PacketTransfer();
        packet.targetSectorName = in.getSectorName();
        packet.uniqueId = player.getUniqueId().toString();
        packet.info = PlayerUtils.wrap(player);

        TRANSFERRING.add(player.getUniqueId());
        service.schedule(() -> {
            TRANSFERRING.remove(player.getUniqueId());
        }, 1, TimeUnit.SECONDS);

        NetworkAPI.Net.publish(Channel.TRANSFER_REQUEST, packet);
    }
}
