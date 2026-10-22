package io.github.marconik.minecraftchunithm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Local X is across the structure; local Z follows its forward direction. */
public record Placement(BlockPos origin,Direction forward) {
    public Direction across(){return forward.getCounterClockWise();}
    public BlockPos block(int x,int y,int z){return origin.relative(across(),x).relative(forward,z).above(y);}
    public Vec3 point(double x,double y,double z){return new Vec3(origin.getX()+.5+across().getStepX()*(x-.5)+forward.getStepX()*(z-.5),
            origin.getY()+y,origin.getZ()+.5+across().getStepZ()*(x-.5)+forward.getStepZ()*(z-.5));}
    public float yaw(){return switch(forward){case SOUTH->0;case WEST->90;case NORTH->180;case EAST->270;default->0;};}
    public static Direction looking(CommandSourceStack source){return Direction.fromYRot(source.getRotation().y);}
    public static BlockPos standing(CommandSourceStack source){return BlockPos.containing(source.getPosition()).below();}
    public static Placement track(CommandSourceStack source){var direction=looking(source);return new Placement(standing(source).relative(direction.getCounterClockWise(),-4),direction);}
    public static Placement head(CommandSourceStack source){var front=looking(source).getOpposite();return new Placement(standing(source).relative(looking(source)).relative(front.getCounterClockWise(),-5),front);}
}
