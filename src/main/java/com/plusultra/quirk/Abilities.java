package com.plusultra.quirk;

import com.plusultra.config.PUConfig;
import com.plusultra.item.HeroCostume;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The actual effects of every Quirk ability. Everything here runs on the logical server. */
public final class Abilities {
    private Abilities() {}

    // ------------------------------------------------------------------ helpers

    public static ServerLevel level(ServerPlayer p) {
        return p.serverLevel();
    }

    public static boolean canHit(ServerPlayer p, Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive()) return false;
        if (le instanceof Player && !PUConfig.PVP.get()) return false;
        if (le instanceof TamableAnimal pet && pet.isOwnedBy(p)) return false;
        return true;
    }

    /** Living entities in front of the player within range; cosMin is the cosine of the half-angle. */
    public static List<LivingEntity> cone(ServerPlayer p, double range, double cosMin) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(range), x -> canHit(p, x))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range) continue;
            if (d < 1.2 || to.normalize().dot(look) >= cosMin) out.add(e);
        }
        return out;
    }

    public static List<LivingEntity> around(ServerPlayer p, Vec3 center, double r) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r),
                x -> canHit(p, x) && x.position().distanceTo(center) <= r);
    }

    public static void hurt(ServerPlayer p, LivingEntity target, double amount) {
        double mult = PlayerData.get(p).damageMultiplier() * PUConfig.DAMAGE_MULT.get()
                * (HeroCostume.pieces(p) == 4 ? 1.1 : 1.0);
        target.hurt(p.damageSources().playerAttack(p), (float) (amount * mult));
    }

    public static void shove(LivingEntity target, Vec3 dir, double horizontal, double vertical) {
        target.push(dir.x * horizontal, vertical, dir.z * horizontal);
        target.hurtMarked = true;
    }

    public static Vec3 flat(Vec3 v) {
        return new Vec3(v.x, 0, v.z).normalize();
    }

    public static void dash(ServerPlayer p, double speed, double lift) {
        Vec3 l = p.getLookAngle();
        p.setDeltaMovement(l.x * speed, l.y * speed * 0.6 + lift, l.z * speed);
        p.hurtMarked = true;
        p.fallDistance = 0;
    }

    public static void particles(ServerPlayer p, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level(p).sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void sound(ServerPlayer p, SoundEvent s, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, 1.0f, pitch);
    }

    public static void effect(LivingEntity e, Holder<MobEffect> eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp));
    }

    /** Visual and optional real explosion at a point. Damage is applied manually. */
    public static void blastAt(ServerPlayer p, Vec3 at, double radius, double damage) {
        ServerLevel l = level(p);
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        Fx.burst(l, ParticleTypes.FLAME, at, 40, radius * 0.3, 0.06);
        Fx.sphere(l, Fx.dust(0xFF8C1A, 1.8f), at, radius * 0.8, 36);
        Fx.ring(l, Fx.dust(0xFFE08A, 1.4f), at.add(0, 0.2, 0), radius, 28);
        Fx.ring(l, ParticleTypes.SMOKE, at.add(0, 0.2, 0), radius * 0.6, 16);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.6f);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.6f);
        for (LivingEntity t : around(p, at, radius)) {
            hurt(p, t, damage);
            Vec3 away = t.position().subtract(at);
            shove(t, away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize(), 1.0, 0.5);
        }
        if (PUConfig.BLOCK_DAMAGE.get()) {
            l.explode(p, at.x, at.y, at.z, (float) (radius * 0.6), Level.ExplosionInteraction.MOB);
        }
    }

    /** Cone of particles that widens with distance. */
    public static void coneFx(ServerPlayer p, ParticleOptions o, double range, int perStep) {
        Vec3 look = p.getLookAngle();
        for (int i = 1; i <= range; i++) {
            particles(p, o, p.getEyePosition().add(look.scale(i)), perStep, 0.12 + i * 0.07, 0.02);
        }
    }

    // ------------------------------------------------------------------ One For All

    public static void smash(ServerPlayer p, double power) {
        Vec3 look = p.getLookAngle();
        for (LivingEntity t : cone(p, 4.5, 0.6)) {
            hurt(p, t, 9 * power);
            shove(t, flat(look), 1.8, 0.45);
        }
        ServerLevel l = level(p);
        Vec3 pt = p.getEyePosition().add(look.scale(2.5));
        particles(p, ParticleTypes.EXPLOSION, pt, 1, 0, 0);
        Fx.burst(l, Fx.dust(0x7CFC00, 1.6f), pt, 40, 0.7, 0.05);
        Fx.ring(l, Fx.dust(0xFFFFFF, 1.2f), pt, 1.6, 18);
        Fx.ring(l, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 1.5, 14);
        sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f);
        sound(p, SoundEvents.ANVIL_LAND, 1.4f);
    }

    public static void delawareLeap(ServerPlayer p, double power) {
        ServerLevel l = level(p);
        Fx.ring(l, Fx.dust(0x7CFC00, 1.3f), p.position().add(0, 0.1, 0), 1.2, 16);
        Fx.ring(l, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 2.0, 20);
        p.setDeltaMovement(p.getLookAngle().x * 1.0, 1.4 + 0.05 * power, p.getLookAngle().z * 1.0);
        p.hurtMarked = true;
        p.fallDistance = 0;
        effect(p, MobEffects.SLOW_FALLING, 80, 0);
        Fx.spiral(l, Fx.dust(0x7CFC00, 1.0f), p.position(), 2.0, 0.6, 2, 28);
        sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.2f);
    }

    public static void fullCowl(ServerPlayer p, double power) {
        int t = 300;
        effect(p, MobEffects.DAMAGE_BOOST, t, 2);
        effect(p, MobEffects.MOVEMENT_SPEED, t, 1);
        effect(p, MobEffects.DAMAGE_RESISTANCE, t, 1);
        effect(p, MobEffects.JUMP, t, 1);
        ServerLevel l = level(p);
        Fx.lightning(l, p.position());
        Fx.spiral(l, ParticleTypes.ELECTRIC_SPARK, p.position(), 2.2, 0.7, 3, 40);
        Fx.sphere(l, Fx.dust(0x7CFC00, 1.1f), p.position().add(0, 1, 0), 1.2, 30);
        Fx.startAura(p, t);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.8f);
    }

    // ------------------------------------------------------------------ Half-Cold Half-Hot

    public static void flameBurst(ServerPlayer p, double power) {
        for (LivingEntity t : cone(p, 7, 0.75)) {
            hurt(p, t, 6 * power);
            t.setRemainingFireTicks(100);
        }
        coneFx(p, ParticleTypes.FLAME, 7, 5);
        coneFx(p, Fx.dust(0xFF5A1F, 1.5f), 7, 4);
        sound(p, SoundEvents.FIRECHARGE_USE, 1.0f);
    }

    public static void iceBurst(ServerPlayer p, double power) {
        for (LivingEntity t : cone(p, 7, 0.75)) {
            hurt(p, t, 5 * power);
            t.setTicksFrozen(200);
            effect(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 2);
        }
        coneFx(p, ParticleTypes.SNOWFLAKE, 7, 6);
        coneFx(p, Fx.dust(0x9FE8FF, 1.5f), 7, 4);
        sound(p, SoundEvents.GLASS_BREAK, 0.6f);
    }

    public static void flashfreezeHeatwave(ServerPlayer p, double power) {
        Vec3 c = p.position();
        for (LivingEntity t : around(p, c, 9)) {
            hurt(p, t, 10 * power);
            t.setRemainingFireTicks(120);
            t.setTicksFrozen(300);
            effect(t, MobEffects.MOVEMENT_SLOWDOWN, 120, 3);
            Vec3 away = t.position().subtract(c);
            shove(t, away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize(), 1.2, 0.4);
        }
        ServerLevel l = level(p);
        for (int r = 2; r <= 8; r += 2) {
            ParticleOptions o = (r / 2) % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SNOWFLAKE;
            Fx.ring(l, o, c.add(0, 0.3, 0), r, 10 + r * 4);
            Fx.ring(l, r % 4 == 0 ? Fx.dust(0xFF5A1F, 1.3f) : Fx.dust(0x9FE8FF, 1.3f), c.add(0, 1.2, 0), r, 10 + r * 3);
        }
        Fx.spiral(l, ParticleTypes.FLAME, c, 4, 1.0, 3, 40);
        Fx.spiral(l, ParticleTypes.SNOWFLAKE, c.add(0, 0, 0), 4, 1.6, -3, 40);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f);
    }

    // ------------------------------------------------------------------ Explosion

    public static void blast(ServerPlayer p, double power) {
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(3.0));
        blastAt(p, at, 2.5, 7 * power);
    }

    public static void blastPropulsion(ServerPlayer p, double power) {
        ServerLevel l = level(p);
        Vec3 back = p.position().add(0, 0.8, 0).subtract(p.getLookAngle().scale(0.5));
        Fx.beam(l, ParticleTypes.SMOKE, back, back.subtract(p.getLookAngle().scale(3)), 0.3);
        Fx.beam(l, Fx.dust(0xFF8C1A, 1.4f), back, back.subtract(p.getLookAngle().scale(2.5)), 0.25);
        dash(p, 1.8, 0.2);
        particles(p, ParticleTypes.EXPLOSION, p.position().add(0, 0.5, 0), 2, 0.2, 0);
        Fx.ring(l, ParticleTypes.FLAME, p.position().add(0, 0.5, 0), 1.0, 14);
        sound(p, SoundEvents.FIRECHARGE_USE, 1.2f);
    }

    public static void howitzerImpact(ServerPlayer p, double power) {
        Vec3 at = p.pick(20, 1.0f, false).getLocation();
        ServerLevel l = level(p);
        Fx.beam(l, Fx.dust(0xFF8C1A, 1.2f), p.getEyePosition().add(0, -0.3, 0), at, 0.6);
        Fx.spiral(l, ParticleTypes.FLAME, p.position(), 2, 0.8, 2, 24);
        blastAt(p, at, 5.5, 14 * power);
        Fx.lightning(l, at);
    }

    // ------------------------------------------------------------------ Hardening

    public static void harden(ServerPlayer p, double power) {
        effect(p, MobEffects.DAMAGE_RESISTANCE, 400, 1);
        effect(p, MobEffects.ABSORPTION, 400, 1);
        effect(p, MobEffects.MOVEMENT_SLOWDOWN, 400, 0);
        Fx.sphere(level(p), Fx.dust(0xC0392B, 1.3f), p.position().add(0, 1, 0), 1.0, 30);
        particles(p, ParticleTypes.CRIT, p.position().add(0, 1, 0), 30, 0.5, 0.1);
        Fx.startAura(p, 400);
        sound(p, SoundEvents.ANVIL_LAND, 0.8f);
    }

    public static void redRiotPunch(ServerPlayer p, double power) {
        ServerLevel l = level(p);
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Fx.beam(l, Fx.dust(0xC0392B, 1.3f), from, from.add(p.getLookAngle().scale(3.0)), 0.3);
        List<LivingEntity> hit = cone(p, 3.5, 0.8);
        if (!hit.isEmpty()) {
            LivingEntity t = hit.get(0);
            hurt(p, t, 12 * power);
            shove(t, flat(p.getLookAngle()), 1.2, 0.3);
            Vec3 c = t.getBoundingBox().getCenter();
            particles(p, ParticleTypes.CRIT, c, 25, 0.4, 0.2);
            Fx.burst(l, Fx.dust(0xC0392B, 1.5f), c, 20, 0.4, 0.1);
            Fx.ring(l, ParticleTypes.CRIT, c, 0.9, 12);
        }
        sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f);
    }

    public static void unbreakable(ServerPlayer p, double power) {
        effect(p, MobEffects.DAMAGE_RESISTANCE, 200, 3);
        effect(p, MobEffects.DAMAGE_BOOST, 200, 1);
        effect(p, MobEffects.ABSORPTION, 200, 3);
        ServerLevel l = level(p);
        Vec3 c = p.position().add(0, 1, 0);
        Fx.sphere(l, Fx.dust(0xE74C3C, 1.6f), c, 1.4, 50);
        Fx.ring(l, ParticleTypes.CRIT, p.position().add(0, 0.2, 0), 1.8, 20);
        Fx.lightning(l, p.position());
        Fx.startAura(p, 200);
        sound(p, SoundEvents.ANVIL_LAND, 0.5f);
    }

    // ------------------------------------------------------------------ Zero Gravity

    public static void floatTargets(ServerPlayer p, double power) {
        Vec3 c = p.getEyePosition().add(p.getLookAngle().scale(4));
        ServerLevel l = level(p);
        for (LivingEntity t : around(p, c, 5)) {
            effect(t, MobEffects.LEVITATION, 80, 0);
            Fx.spiral(l, Fx.dust(0xFFB6C1, 1.2f), t.position(), 2, 0.6, 2, 16);
        }
        Fx.sphere(l, Fx.dust(0xFFB6C1, 1.4f), c, 5, 60);
        particles(p, ParticleTypes.END_ROD, c, 30, 1.5, 0.02);
        sound(p, SoundEvents.ENDER_EYE_LAUNCH, 1.2f);
    }

    public static void weightless(ServerPlayer p, double power) {
        effect(p, MobEffects.SLOW_FALLING, 200, 0);
        effect(p, MobEffects.JUMP, 200, 3);
        effect(p, MobEffects.MOVEMENT_SPEED, 200, 0);
        ServerLevel l = level(p);
        Fx.spiral(l, Fx.dust(0xFFB6C1, 1.2f), p.position(), 2.4, 0.7, 3, 36);
        Fx.ring(l, ParticleTypes.END_ROD, p.position().add(0, 0.1, 0), 1.5, 16);
        Fx.startAura(p, 200);
        sound(p, SoundEvents.ENDER_EYE_LAUNCH, 1.6f);
    }

    public static void meteorShower(ServerPlayer p, double power) {
        Vec3 c = p.position();
        ServerLevel l = level(p);
        for (LivingEntity t : around(p, c, 9)) {
            hurt(p, t, 4 * power);
            effect(t, MobEffects.LEVITATION, 100, 1);
            effect(t, MobEffects.GLOWING, 200, 0);
        }
        for (int i = 0; i < 40; i++) {
            double a = p.getRandom().nextDouble() * Math.PI * 2;
            double r = p.getRandom().nextDouble() * 8;
            Vec3 top = c.add(Math.cos(a) * r, 8 + p.getRandom().nextDouble() * 3, Math.sin(a) * r);
            Fx.streak(l, i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME, top, 0.6);
            Fx.streak(l, Fx.dust(0xFFB6C1, 1.4f), top.add(0, -1.5, 0), 0.5);
        }
        Fx.ring(l, Fx.dust(0xFFB6C1, 1.5f), c.add(0, 0.2, 0), 8, 40);
        Fx.sphere(l, ParticleTypes.END_ROD, c.add(0, 1, 0), 9, 40);
        sound(p, SoundEvents.ENDER_DRAGON_GROWL, 1.8f);
    }

    // ------------------------------------------------------------------ Engine

    public static void reciproBurst(ServerPlayer p, double power) {
        effect(p, MobEffects.MOVEMENT_SPEED, 120, 3);
        ServerLevel l = level(p);
        Fx.spiral(l, Fx.dust(0x3498DB, 1.2f), p.position(), 2.0, 0.6, 3, 30);
        Fx.ring(l, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 1.2, 16);
        Fx.startAura(p, 120);
        sound(p, SoundEvents.FIRECHARGE_USE, 1.6f);
    }

    public static void engineKick(ServerPlayer p, double power) {
        ServerLevel l = level(p);
        Vec3 from = p.position().add(0, 0.9, 0);
        for (LivingEntity t : cone(p, 5, 0.7)) {
            hurt(p, t, 8 * power);
            shove(t, flat(p.getLookAngle()), 1.4, 0.35);
            Fx.burst(l, Fx.dust(0x3498DB, 1.4f), t.getBoundingBox().getCenter(), 18, 0.4, 0.1);
        }
        Fx.beam(l, Fx.dust(0x3498DB, 1.3f), from, from.add(p.getLookAngle().scale(4)), 0.3);
        dash(p, 1.6, 0.1);
        Fx.ring(l, ParticleTypes.CLOUD, p.position().add(0, 0.1, 0), 1.4, 16);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f);
    }

    public static void reciproExtend(ServerPlayer p, double power) {
        effect(p, MobEffects.MOVEMENT_SPEED, 240, 4);
        effect(p, MobEffects.JUMP, 240, 1);
        effect(p, MobEffects.DIG_SPEED, 240, 1);
        ServerLevel l = level(p);
        Fx.lightning(l, p.position());
        Fx.spiral(l, ParticleTypes.ELECTRIC_SPARK, p.position(), 2.4, 0.8, 4, 48);
        Fx.sphere(l, Fx.dust(0x3498DB, 1.4f), p.position().add(0, 1, 0), 1.3, 40);
        Fx.startAura(p, 240);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0f);
    }

    // ------------------------------------------------------------------ shared by gadgets and Quirks

    /** Pulls the player toward the block they are looking at. Returns false if nothing is in range. */
    public static boolean grapple(ServerPlayer p, double range, double speed) {
        HitResult hit = p.pick(range, 1.0f, false);
        if (hit.getType() == HitResult.Type.MISS) return false;
        Vec3 to = hit.getLocation();
        Vec3 from = p.position().add(0, 1.0, 0);
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.5) return false;
        Vec3 v = d.normalize().scale(Math.min(speed, 0.6 + len * 0.08));
        p.setDeltaMovement(v.x, v.y + 0.25, v.z);
        p.hurtMarked = true;
        p.fallDistance = 0;
        Fx.beam(level(p), Fx.dust(0xDDDDDD, 0.9f), p.getEyePosition().add(0, -0.4, 0), to, 0.5);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 0.9f);
        return true;
    }
}
