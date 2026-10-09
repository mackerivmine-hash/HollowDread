package com.hollowdread;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * O Espreitador.
 * - Fica PARADO enquanto algum jogador o encara (como um Weeping Angel).
 * - Quando ninguém olha, corre até o jogador mais próximo.
 * - Se você o encara por tempo demais, ele some. Se segurar um Amuleto de Proteção, some mais rápido.
 */
public class StalkerEntity extends HostileEntity {
    private static final int STARE_LIMIT = 160;      // 8 s encarando = ele some
    private static final int MAX_LIFE = 20 * 60 * 5; // 5 min

    private int stareTicks;
    private int attackCooldown;
    private int lifeTicks;
    private boolean frozen;

    public StalkerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 20;
    }

    public static DefaultAttributeContainer.Builder createStalkerAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
    }

    @Override
    protected boolean isImmobile() {
        return frozen || super.isImmobile();
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        ServerWorld world = (ServerWorld) getWorld();

        if (++lifeTicks > MAX_LIFE) {
            vanish(world, false);
            return;
        }

        PlayerEntity target = world.getClosestPlayer(getX(), getY(), getZ(), 64.0,
                EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
        if (target == null) {
            vanish(world, false);
            return;
        }

        boolean watched = false;
        boolean warded = false;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double d2 = squaredDistanceTo(p);
            if (d2 > 64 * 64) continue;
            if (isWatchedBy(p)) watched = true;
            if (d2 < 10 * 10 && FearManager.holdingCharm(p)) warded = true;
        }

        frozen = watched || warded;

        if (frozen) {
            getNavigation().stop();
            stareTicks += warded ? 3 : 1;
            if (stareTicks % 50 == 0) {
                world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
                        SoundCategory.HOSTILE, 0.8f, 0.7f);
            }
            if (stareTicks >= STARE_LIMIT) vanish(world, true);
            return;
        }

        stareTicks = Math.max(0, stareTicks - 1);
        setTarget(target);
        getLookControl().lookAt(target, 30f, 30f);
        if (age % 4 == 0) getNavigation().startMovingTo(target, 1.4);

        if (attackCooldown > 0) attackCooldown--;
        if (attackCooldown <= 0 && squaredDistanceTo(target) < 4.5 && canSee(target)) {
            if (tryAttack(target)) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 0));
                attackCooldown = 30;
            }
        }
    }

    private boolean isWatchedBy(PlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d to = new Vec3d(getX() - p.getX(), getEyeY() - p.getEyeY(), getZ() - p.getZ());
        double len = to.length();
        if (len < 0.001) return true;
        return look.dotProduct(to.multiply(1.0 / len)) > 0.5 && p.canSee(this);
    }

    private void vanish(ServerWorld world, boolean loud) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.3, getZ(), 40, 0.3, 0.8, 0.3, 0.02);
        if (loud) {
            world.playSound(null, getBlockPos(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,
                    SoundCategory.HOSTILE, 1.0f, 0.5f);
        }
        discard();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WARDEN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WARDEN_DEATH;
    }
}
