package com.hollowdread;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<StalkerEntity> STALKER = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(HollowDread.MOD_ID, "stalker"),
            EntityType.Builder.create(StalkerEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 2.6f)
                    .makeFireImmune()
                    .maxTrackingRange(12)
                    .build("stalker"));

    private ModEntities() {}

    public static void init() {
        FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createStalkerAttributes());
    }
}
