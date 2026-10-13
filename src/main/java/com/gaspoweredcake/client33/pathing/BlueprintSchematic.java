package com.gaspoweredcake.client33.pathing;

import baritone.api.schematic.AbstractSchematic;
import com.gaspoweredcake.client33.utils.schematic.SchematicBlueprint;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Loaded only by the Baritone backend; other path managers do not need its optional API. */
public final class BlueprintSchematic extends AbstractSchematic {
    private final SchematicBlueprint blueprint;
    private final boolean keepAir;

    public BlueprintSchematic(SchematicBlueprint blueprint, boolean keepAir) {
        super(blueprint.width, blueprint.height, blueprint.length);
        this.blueprint = blueprint; this.keepAir = keepAir;
    }
    @Override public boolean inSchematic(int x, int y, int z, BlockState current) {
        return x >= 0 && x < widthX() && y >= 0 && y < heightY() && z >= 0 && z < lengthZ()
            && blueprint.includes(x, y, z, keepAir);
    }
    @Override public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> available) {
        return blueprint.stateAt(x, y, z);
    }
}
