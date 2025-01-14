// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.gooeysQuests.api;

import org.terasology.engine.world.block.BlockRegion;
import org.terasology.gestalt.entitysystem.component.Component;
import org.terasology.structureTemplates.events.SpawnStructureEvent;
import org.terasology.structureTemplates.events.StructureBlocksSpawnedEvent;

/**
 * If a entity with this component gets a {@link SpawnStructureEvent} (that triggers a
 * {@link StructureBlocksSpawnedEvent}) then particles will be spawned in the given region.
 */
public class SpawnMagicBuildParticlesComponent implements Component<SpawnMagicBuildParticlesComponent> {
    public BlockRegion region = new BlockRegion(BlockRegion.INVALID);

    @Override
    public void copyFrom(SpawnMagicBuildParticlesComponent other) {
        this.region.set(other.region);
    }
}
