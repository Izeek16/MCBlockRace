package com.toasterz.blockrace;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RaceManager {

    public static final RaceManager INSTANCE = new RaceManager();

    private enum State { IDLE, COUNTDOWN, RACING, FINISHED }

    private static final int COUNTDOWN_SECONDS = 15;
    private static final int FINISH_FREEZE_SECONDS = 8;
    private static final int TELEPORT_RANGE = 12000;

    private State state = State.IDLE;
    private ItemPools.Pool pool = ItemPools.Pool.NORMAL;
    private Item target = null;

    private int countdownTicks = 0;
    private int raceTicks = 0;
    private int finishTicks = 0;

    private final Set<UUID> racers = new HashSet<>();
    private final Map<UUID, Vec3d> frozenPositions = new HashMap<>();
    private final Map<UUID, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    private ServerBossBar bossBar = null;
    private final Random random = new Random();

    private RaceManager() {}

    // ------------------------------------------------------------------
    // Commands entry points
    // ------------------------------------------------------------------

    /** /race start - start (or restart) a round right where everyone is standing. */
    public void startHere(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return;
        }
        beginRound(server, players);
    }

    /** /race next - teleport everyone together to a random far-away fresh location, then start a round. */
    public void nextLocation(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            return;
        }

        ServerWorld world = server.getOverworld();
        int x = 0;
        int y = 80;
        int z = 0;
        boolean found = false;

        for (int attempt = 0; attempt < 12 && !found; attempt++) {
            x = random.nextInt(TELEPORT_RANGE * 2) - TELEPORT_RANGE;
            z = random.nextInt(TELEPORT_RANGE * 2) - TELEPORT_RANGE;
            world.getChunk(x >> 4, z >> 4); // force sync chunk load so the heightmap is real
            y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            BlockPos ground = new BlockPos(x, y - 1, z);
            // avoid dropping people into an ocean
            if (world.getBlockState(ground).getFluidState().isEmpty() && y > world.getBottomY() + 4) {
                found = true;
            }
        }

        broadcast(server, Text.literal("Teleporting to a fresh location...").formatted(Formatting.GRAY));

        int offset = 0;
        for (ServerPlayerEntity p : players) {
            int px = x + offset * 2;
            world.getChunk(px >> 4, z >> 4);
            int py = world.getTopY(Heightmap.Type.MOTION_BLOCKING, px, z);
            p.teleport(world, px + 0.5, py, z + 0.5, p.getYaw(), p.getPitch());
            p.fallDistance = 0.0f;
            offset++;
        }

        beginRound(server, players);
    }

    /** /race skip - reroll the target item and restart the round where everyone stands. */
    public void skip(MinecraftServer server) {
        if (state == State.IDLE) {
            broadcast(server, Text.literal("No race running. Use /race start first.").formatted(Formatting.RED));
            return;
        }
        broadcast(server, Text.literal("Target skipped, rerolling...").formatted(Formatting.YELLOW));
        startHere(server);
    }

    /** /race stop - end the session. */
    public void stop(MinecraftServer server) {
        state = State.IDLE;
        target = null;
        frozenPositions.clear();
        racers.clear();
        if (bossBar != null) {
            bossBar.clearPlayers();
        }
        broadcast(server, Text.literal("Race session ended.").formatted(Formatting.GOLD));
        broadcast(server, scoresLine());
    }

    /** /race scores */
    public void showScores(MinecraftServer server) {
        broadcast(server, scoresLine());
    }

    /** /race pool <easy|normal|everything> */
    public void setPool(MinecraftServer server, ItemPools.Pool newPool) {
        this.pool = newPool;
        broadcast(server, Text.literal("Item pool set to ").formatted(Formatting.GRAY)
                .append(Text.literal(newPool.name().toLowerCase(Locale.ROOT)).formatted(Formatting.AQUA))
                .append(Text.literal(" (" + ItemPools.size(newPool) + " possible items)").formatted(Formatting.DARK_GRAY)));
    }

    // ------------------------------------------------------------------
    // Round lifecycle
    // ------------------------------------------------------------------

    private void beginRound(MinecraftServer server, List<ServerPlayerEntity> players) {
        racers.clear();
        frozenPositions.clear();

        for (ServerPlayerEntity p : players) {
            racers.add(p.getUuid());
            names.put(p.getUuid(), p.getName().getString());
            scores.putIfAbsent(p.getUuid(), 0);
            prepPlayer(p);
            frozenPositions.put(p.getUuid(), p.getPos());
        }

        target = ItemPools.pick(pool, random);
        state = State.COUNTDOWN;
        countdownTicks = COUNTDOWN_SECONDS * 20;
        raceTicks = 0;

        if (bossBar == null) {
            bossBar = new ServerBossBar(Text.literal("Block Race"), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        }
        bossBar.clearPlayers();
        for (ServerPlayerEntity p : players) {
            bossBar.addPlayer(p);
        }
        bossBar.setColor(BossBar.Color.YELLOW);
        bossBar.setPercent(1.0f);
        bossBar.setName(countdownBarText(COUNTDOWN_SECONDS));

        for (ServerPlayerEntity p : players) {
            sendTitle(p,
                    Text.literal("Find: ").formatted(Formatting.WHITE).append(targetName()),
                    Text.literal("Race starts in " + COUNTDOWN_SECONDS + " seconds - get it in your inventory first!")
                            .formatted(Formatting.GRAY));
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
    }

    private void prepPlayer(ServerPlayerEntity p) {
        p.changeGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        p.clearStatusEffects();
        p.setHealth(p.getMaxHealth());
        p.getHungerManager().setFoodLevel(20);
        p.setExperienceLevel(0);
        p.setExperiencePoints(0);
        p.setFireTicks(0);
        p.fallDistance = 0.0f;
    }

    // ------------------------------------------------------------------
    // Tick loop
    // ------------------------------------------------------------------

    public void tick(MinecraftServer server) {
        switch (state) {
            case IDLE -> { }
            case COUNTDOWN -> tickCountdown(server);
            case RACING -> tickRacing(server);
            case FINISHED -> tickFinished(server);
        }
    }

    private void tickCountdown(MinecraftServer server) {
        freezeAll(server);

        if (countdownTicks % 20 == 0) {
            int seconds = countdownTicks / 20;
            if (seconds > 0) {
                for (ServerPlayerEntity p : onlineRacers(server)) {
                    sendTitle(p,
                            Text.literal(String.valueOf(seconds)).formatted(Formatting.GOLD, Formatting.BOLD),
                            Text.literal("Find: ").formatted(Formatting.WHITE).append(targetName()));
                    if (seconds <= 5) {
                        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                SoundCategory.PLAYERS, 1.0f, 1.5f);
                    }
                }
                if (bossBar != null) {
                    bossBar.setName(countdownBarText(seconds));
                }
            }
        }

        if (bossBar != null) {
            bossBar.setPercent(Math.max(0.0f, countdownTicks / (float) (COUNTDOWN_SECONDS * 20)));
        }

        countdownTicks--;

        if (countdownTicks <= 0) {
            state = State.RACING;
            raceTicks = 0;
            frozenPositions.clear();
            if (bossBar != null) {
                bossBar.setPercent(1.0f);
                bossBar.setColor(BossBar.Color.GREEN);
            }
            for (ServerPlayerEntity p : onlineRacers(server)) {
                sendTitle(p,
                        Text.literal("GO!").formatted(Formatting.GREEN, Formatting.BOLD),
                        Text.literal("Find: ").formatted(Formatting.WHITE).append(targetName()));
                p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.PLAYERS, 1.0f, 1.2f);
            }
        }
    }

    private void tickRacing(MinecraftServer server) {
        raceTicks++;

        if (bossBar != null && raceTicks % 2 == 0) {
            bossBar.setName(Text.literal("Find: ").formatted(Formatting.WHITE)
                    .append(targetName())
                    .append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(formatTime(raceTicks)).formatted(Formatting.AQUA)));
        }

        for (ServerPlayerEntity p : onlineRacers(server)) {
            if (target != null && p.getInventory().count(target) > 0) {
                win(server, p);
                return;
            }
        }
    }

    private void win(MinecraftServer server, ServerPlayerEntity winner) {
        state = State.FINISHED;
        finishTicks = FINISH_FREEZE_SECONDS * 20;

        frozenPositions.clear();
        for (ServerPlayerEntity p : onlineRacers(server)) {
            frozenPositions.put(p.getUuid(), p.getPos());
        }

        scores.merge(winner.getUuid(), 1, Integer::sum);
        String time = formatTime(raceTicks);

        MutableText headline = Text.literal(winner.getName().getString()).formatted(Formatting.GREEN, Formatting.BOLD)
                .append(Text.literal(" found ").formatted(Formatting.WHITE))
                .append(targetName())
                .append(Text.literal(" first!").formatted(Formatting.WHITE));

        for (ServerPlayerEntity p : onlineRacers(server)) {
            sendTitle(p, headline, Text.literal("Time: ").formatted(Formatting.GRAY)
                    .append(Text.literal(time).formatted(Formatting.AQUA, Formatting.BOLD)));
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
        }

        if (bossBar != null) {
            bossBar.setColor(BossBar.Color.GREEN);
            bossBar.setPercent(1.0f);
            bossBar.setName(Text.literal(winner.getName().getString() + " wins in " + time)
                    .formatted(Formatting.GREEN));
        }

        broadcast(server, headline.copy().append(Text.literal("  (" + time + ")").formatted(Formatting.AQUA)));
        broadcast(server, scoresLine());
        broadcast(server, Text.literal("Run ").formatted(Formatting.GRAY)
                .append(Text.literal("/race next").formatted(Formatting.YELLOW))
                .append(Text.literal(" for a fresh location, or ").formatted(Formatting.GRAY))
                .append(Text.literal("/race start").formatted(Formatting.YELLOW))
                .append(Text.literal(" to rematch here.").formatted(Formatting.GRAY)));
    }

    private void tickFinished(MinecraftServer server) {
        freezeAll(server);
        finishTicks--;
        if (finishTicks <= 0) {
            state = State.IDLE;
            frozenPositions.clear();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void freezeAll(MinecraftServer server) {
        for (ServerPlayerEntity p : onlineRacers(server)) {
            Vec3d pos = frozenPositions.computeIfAbsent(p.getUuid(), id -> p.getPos());
            // teleport back but keep their current look direction: frozen in place, free to look around
            p.networkHandler.requestTeleport(pos.x, pos.y, pos.z, p.getYaw(), p.getPitch());
            p.setVelocity(Vec3d.ZERO);
            p.fallDistance = 0.0f;
        }
    }

    private List<ServerPlayerEntity> onlineRacers(MinecraftServer server) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (UUID id : racers) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private MutableText targetName() {
        if (target == null) {
            return Text.literal("???").formatted(Formatting.YELLOW);
        }
        return target.getName().copy().formatted(Formatting.YELLOW, Formatting.BOLD);
    }

    private Text countdownBarText(int seconds) {
        return Text.literal("Find: ").formatted(Formatting.WHITE)
                .append(targetName())
                .append(Text.literal("  |  starting in " + seconds + "s").formatted(Formatting.GOLD));
    }

    private MutableText scoresLine() {
        MutableText line = Text.literal("Scores: ").formatted(Formatting.GOLD);
        boolean first = true;
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
            if (!first) {
                line.append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY));
            }
            String name = names.getOrDefault(e.getKey(), "?");
            line.append(Text.literal(name + " ").formatted(Formatting.WHITE))
                    .append(Text.literal(String.valueOf(e.getValue())).formatted(Formatting.AQUA, Formatting.BOLD));
            first = false;
        }
        if (first) {
            line.append(Text.literal("no rounds played yet").formatted(Formatting.GRAY));
        }
        return line;
    }

    private void sendTitle(ServerPlayerEntity p, Text title, Text subtitle) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 30, 10));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        p.networkHandler.sendPacket(new TitleS2CPacket(title));
    }

    private void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(text, false);
    }

    private static String formatTime(int ticks) {
        double seconds = ticks / 20.0;
        int minutes = (int) (seconds / 60);
        double rem = seconds - minutes * 60;
        return String.format(Locale.ROOT, "%d:%05.2f", minutes, rem);
    }
}
