package com.hollowdread;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockSoundGroup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Sistema de medo (0..1000) por jogador.
 * Sobe no escuro e perto do Espreitador; desce na luz e segurando o Amuleto.
 * Quanto maior o medo, mais eventos paranormais acontecem.
 */
public final class FearManager {
    /** Mude para false se não quiser que tochas sejam apagadas. */
    private static final boolean TORCH_SNUFF = true;

    private static final Map<UUID, Integer> FEAR = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPAWN = new HashMap<>();
    private static final List<Delayed> TASKS = new ArrayList<>();

    private static final String[] WHISPERS = {
            "Atrás de você.",
            "Eu estou vendo você.",
            "Não olhe para trás.",
            "Você não devia estar aqui.",
            "Apague a luz.",
            "Ele sabe o seu nome.",
            "Por que você parou?",
            "Está mais perto agora."
    };

    private static final List<SoundEvent> SCARE_SOUNDS = List.of(
            SoundEvents.AMBIENT_CAVE.value(),
            SoundEvents.ENTITY_GHAST_AMBIENT,
            SoundEvents.ENTITY_CREEPER_PRIMED,
            SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
            SoundEvents.ENTITY_PHANTOM_AMBIENT,
            SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
            SoundEvents.ENTITY_ENDERMAN_AMBIENT
    );

    private record Event(int minFear, int weight, BiConsumer<ServerPlayerEntity, ServerWorld> action) {}

    private static final class Delayed {
        int ticks;
        final Runnable run;
        Delayed(int ticks, Runnable run) { this.ticks = ticks; this.run = run; }
    }

    private static final List<Event> EVENTS = List.of(
            new Event(40, 10, FearManager::scareSound),
            new Event(100, 6, FearManager::footsteps),
            new Event(150, 4, FearManager::snuffTorch),
            new Event(200, 5, FearManager::whisperChat),
            new Event(350, 3, FearManager::darknessPulse),
            new Event(500, 3, FearManager::trySpawn)
    );

    private FearManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FearManager::onTick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> FEAR.remove(newP.getUuid()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FEAR.remove(handler.getPlayer().getUuid());
            NEXT_SPAWN.remove(handler.getPlayer().getUuid());
        });
    }

    // ---------------------------------------------------------------- API
    public static int getFear(PlayerEntity p) { return FEAR.getOrDefault(p.getUuid(), 0); }

    public static void setFear(PlayerEntity p, int value) {
        FEAR.put(p.getUuid(), MathHelper.clamp(value, 0, 1000));
    }

    private static int addFear(PlayerEntity p, int delta) {
        int v = MathHelper.clamp(getFear(p) + delta, 0, 1000);
        FEAR.put(p.getUuid(), v);
        return v;
    }

    public static boolean holdingCharm(PlayerEntity p) {
        return p.getMainHandStack().isOf(ModItems.WARDING_CHARM) || p.getOffHandStack().isOf(ModItems.WARDING_CHARM);
    }

    /** Dispara um evento aleatório elegível para o nível de medo informado. */
    public static void trigger(ServerPlayerEntity p, int fear) {
        ServerWorld w = (ServerWorld) p.getWorld();
        List<Event> ok = EVENTS.stream().filter(e -> e.minFear() <= fear).toList();
        if (ok.isEmpty()) return;
        int total = ok.stream().mapToInt(Event::weight).sum();
        int r = w.random.nextInt(total);
        for (Event e : ok) {
            r -= e.weight();
            if (r < 0) {
                e.action().accept(p, w);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- tick
    private static void onTick(MinecraftServer server) {
        if (!TASKS.isEmpty()) {
            Iterator<Delayed> it = TASKS.iterator();
            List<Runnable> due = new ArrayList<>();
            while (it.hasNext()) {
                Delayed d = it.next();
                if (--d.ticks <= 0) { it.remove(); due.add(d.run); }
            }
            due.forEach(Runnable::run);
        }
        if (server.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) tickPlayer(p);
    }

    private static void tickPlayer(ServerPlayerEntity p) {
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        ServerWorld w = (ServerWorld) p.getWorld();

        if (w.getRegistryKey() != World.OVERWORLD) {
            addFear(p, -5);
            return;
        }

        int light = w.getLightLevel(p.getBlockPos());
        int delta = light <= 3 ? 5 : light <= 7 ? 2 : -4;
        if (holdingCharm(p)) delta -= 10;
        StalkerEntity near = nearestStalker(w, p, 24);
        if (near != null) delta += 8;
        int fear = addFear(p, delta);

        if (near != null || fear > 700) {
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.AMBIENT, 1.2f, near != null ? 1.2f : 0.9f);
        }

        if (w.random.nextInt(100) < fear / 40) trigger(p, fear);

        if (fear >= 100) {
            int filled = Math.min(10, fear / 100);
            Formatting color = fear < 300 ? Formatting.GRAY : fear < 600 ? Formatting.RED : Formatting.DARK_RED;
            String bar = "☠ " + "█".repeat(filled) + "░".repeat(10 - filled);
            p.sendMessage(Text.literal(bar).formatted(color), true);
        }
    }

    // ---------------------------------------------------------------- eventos
    private static void schedule(int ticks, Runnable r) { TASKS.add(new Delayed(ticks, r)); }

    private static Vec3d flatLook(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() < 1e-4) flat = new Vec3d(0, 0, 1);
        return flat.normalize();
    }

    private static void scareSound(ServerPlayerEntity p, ServerWorld w) {
        Vec3d pos = p.getPos().subtract(flatLook(p).multiply(6 + w.random.nextDouble() * 6));
        SoundEvent s = SCARE_SOUNDS.get(w.random.nextInt(SCARE_SOUNDS.size()));
        w.playSound(null, pos.x, p.getY() + 1, pos.z, s, SoundCategory.AMBIENT, 1.0f, 0.6f + w.random.nextFloat() * 0.4f);
    }

    private static void footsteps(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = flatLook(p);
        SoundEvent step = w.getBlockState(p.getBlockPos().down()).getSoundGroup().getStepSound();
        for (int i = 0; i < 7; i++) {
            double dist = 9 - i * 1.2;
            Vec3d pos = p.getPos().subtract(dir.multiply(dist));
            schedule(i * 9 + 1, () ->
                    w.playSound(null, pos.x, p.getY(), pos.z, step, SoundCategory.HOSTILE, 0.9f, 0.85f));
        }
    }

    private static void snuffTorch(ServerPlayerEntity p, ServerWorld w) {
        if (!TORCH_SNUFF) return;
        BlockPos c = p.getBlockPos();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(c.add(-8, -4, -8), c.add(8, 4, 8))) {
            BlockState s = w.getBlockState(pos);
            if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH)
                    || s.isOf(Blocks.SOUL_TORCH) || s.isOf(Blocks.SOUL_WALL_TORCH)) {
                found.add(pos.toImmutable());
            }
        }
        if (found.isEmpty()) return;
        BlockPos t = found.get(w.random.nextInt(found.size()));
        w.setBlockState(t, Blocks.AIR.getDefaultState());
        w.playSound(null, t, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 1.0f);
        w.spawnParticles(ParticleTypes.SMOKE, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5, 8, 0.1, 0.1, 0.1, 0.02);
    }

    private static void whisperChat(ServerPlayerEntity p, ServerWorld w) {
        String line = WHISPERS[w.random.nextInt(WHISPERS.length)];
        p.sendMessage(Text.literal("<???> " + line).formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
    }

    private static void darknessPulse(ServerPlayerEntity p, ServerWorld w) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, false));
    }

    private static void trySpawn(ServerPlayerEntity p, ServerWorld w) {
        long now = w.getTime();
        if (NEXT_SPAWN.getOrDefault(p.getUuid(), 0L) > now) return;
        if (w.getLightLevel(p.getBlockPos()) > 7) return;
        if (spawnStalker(p, w)) NEXT_SPAWN.put(p.getUuid(), now + 2400);
    }

    // ---------------------------------------------------------------- Espreitador
    private static StalkerEntity nearestStalker(ServerWorld w, PlayerEntity p, double r) {
        List<StalkerEntity> list = w.getEntitiesByClass(StalkerEntity.class, p.getBoundingBox().expand(r), e -> true);
        return list.isEmpty() ? null : list.get(0);
    }

    public static boolean spawnStalker(ServerPlayerEntity p, ServerWorld w) {
        if (nearestStalker(w, p, 80) != null) return false;
        for (int i = 0; i < 24; i++) {
            double ang = w.random.nextDouble() * Math.PI * 2;
            double dist = 18 + w.random.nextDouble() * 10;
            int x = MathHelper.floor(p.getX() + Math.cos(ang) * dist);
            int z = MathHelper.floor(p.getZ() + Math.sin(ang) * dist);
            if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos pos = new BlockPos(x, p.getBlockY() + dy, z);
                if (!w.getBlockState(pos.down()).isSideSolidFullSquare(w, pos.down(), Direction.UP)) continue;
                StalkerEntity s = ModEntities.STALKER.create(w);
                if (s == null) return false;
                s.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        w.random.nextFloat() * 360f, 0f);
                if (!w.isSpaceEmpty(s)) continue;
                w.spawnEntity(s);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMBIENT_CAVE.value(),
                        SoundCategory.AMBIENT, 1.0f, 0.5f);
                return true;
            }
        }
        return false;
    }
}
