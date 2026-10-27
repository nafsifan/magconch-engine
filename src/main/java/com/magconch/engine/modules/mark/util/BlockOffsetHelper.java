package com.magconch.engine.modules.mark.util;

import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 方块世界渲染动态偏移解析器
 * <p>
 * 适配 Minecraft 现代内核，通过 MethodHandle 纳秒级动态提取方块的实时偏移量，
 * 解决植物花草类展示实体与原版带偏移模型错位的问题。
 */
public final class BlockOffsetHelper {

    private static final MethodHandle GET_BLOCK_STATE;
    private static final MethodHandle GET_POSITION;
    private static final MethodHandle GET_OFFSET;
    private static final Field VEC3_X;
    private static final Field VEC3_Y;
    private static final Field VEC3_Z;
    private static final boolean NMS_AVAILABLE;

    static {
        boolean available = false;
        MethodHandle getBlockState = null;
        MethodHandle getPosition = null;
        MethodHandle getOffset = null;
        Field vecX = null;
        Field vecY = null;
        Field vecZ = null;

        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> craftBlockClass = Class.forName("org.bukkit.craftbukkit.block.CraftBlock");
            Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");

            Method mGetBlockState = craftBlockClass.getMethod("getBlockState");
            Method mGetPosition = craftBlockClass.getMethod("getPosition");
            Method mGetOffset = blockStateClass.getMethod("getOffset", blockPosClass);

            getBlockState = lookup.unreflect(mGetBlockState);
            getPosition = lookup.unreflect(mGetPosition);
            getOffset = lookup.unreflect(mGetOffset);

            vecX = vec3Class.getField("x");
            vecY = vec3Class.getField("y");
            vecZ = vec3Class.getField("z");
            available = true;
        } catch (Throwable ignored) {
        }

        GET_BLOCK_STATE = getBlockState;
        GET_POSITION = getPosition;
        GET_OFFSET = getOffset;
        VEC3_X = vecX;
        VEC3_Y = vecY;
        VEC3_Z = vecZ;
        NMS_AVAILABLE = available;
    }

    private BlockOffsetHelper() {}

    /**
     * 计算方块当前在世界中的动态渲染偏移
     *
     * @param block 目标方块
     * @return 偏移向量，若方块无偏移或获取失败则返回零向量
     */
    public static Vector getOffset(Block block) {
        if (block == null) {
            return new Vector(0, 0, 0);
        }

        if (NMS_AVAILABLE) {
            try {
                Object blockState = GET_BLOCK_STATE.invoke(block);
                Object position = GET_POSITION.invoke(block);
                Object vec3 = GET_OFFSET.invoke(blockState, position);
                if (vec3 != null) {
                    double x = VEC3_X.getDouble(vec3);
                    double y = VEC3_Y.getDouble(vec3);
                    double z = VEC3_Z.getDouble(vec3);
                    if (x != 0.0 || y != 0.0 || z != 0.0) {
                        return new Vector(x, y, z);
                    }
                }
                return new Vector(0, 0, 0);
            } catch (Throwable ignored) {
            }
        }

        return new Vector(0, 0, 0);
    }
}
