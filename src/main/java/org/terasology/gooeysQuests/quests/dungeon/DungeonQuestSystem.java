/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.gooeysQuests.quests.dungeon;

import org.joml.RoundingMode;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import org.terasology.engine.entitySystem.entity.EntityBuilder;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.entity.lifecycleEvents.BeforeDeactivateComponent;
import org.terasology.engine.entitySystem.prefab.Prefab;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.math.Side;
import org.terasology.engine.registry.In;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockManager;
import org.terasology.gestalt.assets.management.AssetManager;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;
import org.terasology.gooeysQuests.api.CreateStartQuestsEvent;
import org.terasology.gooeysQuests.api.PersonalQuestsComponent;
import org.terasology.gooeysQuests.api.PrepareQuestEvent;
import org.terasology.gooeysQuests.api.QuestReadyEvent;
import org.terasology.gooeysQuests.api.QuestStartRequest;
import org.terasology.module.inventory.systems.InventoryManager;
import org.terasology.structureTemplates.events.CheckSpawnConditionEvent;
import org.terasology.structureTemplates.events.SpawnStructureEvent;
import org.terasology.structureTemplates.interfaces.BlockPredicateProvider;
import org.terasology.structureTemplates.interfaces.StructureTemplateProvider;
import org.terasology.structureTemplates.util.BlockRegionTransform;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Makes gooey offer a dungeon quest
 */
@RegisterSystem(RegisterMode.AUTHORITY)
public class DungeonQuestSystem extends BaseComponentSystem {

    private static final int MAX_HORIZONTAL_DISTANCE = 20;
    private static final int VERTICAL_SCAN_DISTANCE = 5;
    @In
    private AssetManager assetManager;

    @In
    private EntityManager entityManager;

    @In
    private WorldProvider worldProvider;

    @In
    private BlockManager blockManager;

    @In
    private InventoryManager inventoryManager;

    @In
    private BlockPredicateProvider blockPredicateProvider;

    @In
    private StructureTemplateProvider structureTemplateProvider;

    private Map<EntityRef, FoundSpawnPossiblity> questToFoundSpawnPossibilityMap = new HashMap<>();

    private Random random = new Random();

    private Predicate<Block> isAirCondition;
    private Predicate<Block> isGroundCondition;


    @Override
    public void postBegin() {
        isAirCondition = blockPredicateProvider.getBlockPredicate("StructureTemplates:IsAirLike");
        isGroundCondition = blockPredicateProvider.getBlockPredicate("StructureTemplates:IsGroundLike");
    }

    @ReceiveEvent
    public void onCreateStartQuestsEvent(CreateStartQuestsEvent event, EntityRef character,
                                         PersonalQuestsComponent questsComponent) {
        Prefab questPrefab = assetManager.getAsset("GooeysQuests:DungeonQuest", Prefab.class).get();
        EntityBuilder questEntityBuilder = entityManager.newBuilder(questPrefab);
        questEntityBuilder.setOwner(character);
        EntityRef entity = questEntityBuilder.build();
        questsComponent.questsInPreperation.add(entity);
        character.saveComponent(questsComponent);
    }


    @ReceiveEvent(components = DungeonQuestComponent.class)
    public void onPrepareQuest(PrepareQuestEvent event, EntityRef quest) {

        EntityRef owner = quest.getOwner();
        LocationComponent questOwnerLocation = owner.getComponent(LocationComponent.class);
        Vector3i questOwnerBlockPos = new Vector3i(questOwnerLocation.getWorldPosition(new Vector3f()), RoundingMode.FLOOR);
        Vector3i randomPosition = new Vector3i(questOwnerBlockPos);
        randomPosition.add(randomHorizontalOffset(), 0, randomHorizontalOffset());

        Vector3i surfaceGroundBlockPosition = findSurfaceGroundBlockPosition(randomPosition);
        if (surfaceGroundBlockPosition == null) {
            return;
        }

        EntityRef etranceSpawner = structureTemplateProvider.
            getRandomTemplateOfType("GooeysQuests:dungeonEntrance");

        BlockRegionTransform foundSpawnTransformation = findGoodSpawnTransformation(surfaceGroundBlockPosition,
            etranceSpawner);
        if (foundSpawnTransformation == null) {
            return;
        }


        questToFoundSpawnPossibilityMap.put(quest, new FoundSpawnPossiblity(etranceSpawner, foundSpawnTransformation));
        quest.send(new QuestReadyEvent());
    }

    private static class FoundSpawnPossiblity {
        private EntityRef entranceSpawner;
        private BlockRegionTransform transformation;

        public FoundSpawnPossiblity(EntityRef entranceSpawner, BlockRegionTransform transformation) {
            this.entranceSpawner = entranceSpawner;
            this.transformation = transformation;
        }

        public EntityRef getEntranceSpawner() {
            return entranceSpawner;
        }

        public BlockRegionTransform getTransformation() {
            return transformation;
        }
    }

    private BlockRegionTransform findGoodSpawnTransformation(Vector3i spawnPosition, EntityRef entranceSpawner) {
        for (Side side : Side.horizontalSides()) {
            BlockRegionTransform transformList = createTransformation(spawnPosition, side);

            CheckSpawnConditionEvent checkConditionEvent = new CheckSpawnConditionEvent(transformList);
            entranceSpawner.send(checkConditionEvent);
            if (!checkConditionEvent.isPreventSpawn()) {
                return transformList;
            }
        }

        return null;
    }

    private BlockRegionTransform createTransformation(Vector3i spawnPosition, Side side) {
        return BlockRegionTransform.createRotationThenMovement(Side.FRONT, side, spawnPosition);
    }

    @ReceiveEvent(components = DungeonQuestComponent.class)
    public void onQuestStart(QuestStartRequest event, EntityRef quest) {
        FoundSpawnPossiblity spawnPossiblity = questToFoundSpawnPossibilityMap.get(quest);
        BlockRegionTransform spawnTransformation = spawnPossiblity.getTransformation();
        if (spawnTransformation == null) {
            return; // TODO report failure to client and gooey system
        }

        EntityRef entranceSpawner = spawnPossiblity.getEntranceSpawner();
        entranceSpawner.send(new SpawnStructureEvent(spawnTransformation));
    }

    /**
     * Free the memory once the quest is no longer loaded
     */
    @ReceiveEvent
    public void onDeactivateQuestEntity(BeforeDeactivateComponent event, EntityRef questEntity,
                                        PersonalQuestsComponent questsComponent) {
        questToFoundSpawnPossibilityMap.remove(questEntity);
    }

    private Vector3i findSurfaceGroundBlockPosition(Vector3ic position) {
        int yScanStop = position.y() - VERTICAL_SCAN_DISTANCE;
        int yScanStart = position.y() + VERTICAL_SCAN_DISTANCE;
        // TODO simplify algorithm
        boolean airFound = false;
        for (int y = yScanStart; y > yScanStop; y--) {
            int x = position.x();
            int z = position.z();
            Block block = worldProvider.getBlock(x, y, z);
            if (isAirCondition.test(block)) {
                airFound = true;
            } else if (isGroundCondition.test(block)) {
                if (!airFound) {
                    return null; // found ground first -> not surface
                }
                return new Vector3i(x, y, z);
            } else {
                return null; //neither ground nor air (e.g. water)
            }
        }
        return null; // no ground found
    }

    private int randomHorizontalOffset() {
        return randomSign() * (random.nextInt(MAX_HORIZONTAL_DISTANCE));
    }

    private int randomSign() {
        if (random.nextBoolean()) {
            return 1;
        } else {
            return -1;
        }
    }
}
