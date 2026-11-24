@file:Suppress("DEPRECATION")

package com.tacz.legacy.common.block

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.block.entity.ChunkLoaderTileEntity
import com.tacz.legacy.common.block.entity.GunSmithTableTileEntity
import com.tacz.legacy.common.block.entity.StatueTileEntity
import com.tacz.legacy.common.block.entity.TargetTileEntity
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.gui.LegacyGuiIds
import com.tacz.legacy.common.item.LegacyBlockItem
import com.tacz.legacy.common.registry.LegacyCreativeTabs
import com.tacz.legacy.common.registry.LegacySoundEvents
import net.minecraft.block.BlockContainer
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.InventoryHelper
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.NonNullList
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.registries.IForgeRegistry
import java.util.Random

internal object LegacyBlocks {
    internal val GUN_SMITH_TABLE: LegacyGunSmithTableBlock = LegacyGunSmithTableBlock("gun_smith_table")
    internal val WORKBENCH_A: LegacyGunSmithTableBlock = LegacyGunSmithTableBlock("workbench_a")
    internal val WORKBENCH_B: LegacyGunSmithTableBlock = LegacyGunSmithTableBlock("workbench_b")
    internal val WORKBENCH_C: LegacyGunSmithTableBlock = LegacyGunSmithTableBlock("workbench_c")
    internal val TARGET: LegacyTargetBlock = LegacyTargetBlock("target")
    internal val STATUE: LegacyStatueBlock = LegacyStatueBlock("statue")
    internal val CHUNK_LOADER: LegacyChunkLoaderBlock = LegacyChunkLoaderBlock("chunk_loader")

    internal val allBlocks: List<BlockContainer> = listOf(
        GUN_SMITH_TABLE,
        WORKBENCH_A,
        WORKBENCH_B,
        WORKBENCH_C,
        TARGET,
        STATUE,
        CHUNK_LOADER,
    )

    internal fun registerAll(registry: IForgeRegistry<net.minecraft.block.Block>): Unit {
        allBlocks.forEach(registry::register)
    }
}

internal abstract class LegacyBaseBlock(path: String, material: Material, soundType: SoundType) : BlockContainer(material) {
    init {
        registryName = ResourceLocation(TACZLegacy.MOD_ID, path)
        setTranslationKey("${TACZLegacy.MOD_ID}.$path")
        setCreativeTab(LegacyCreativeTabs.DECORATION)
        setSoundType(soundType)
        setHardness(2.0f)
        setResistance(3.0f)
        setTickRandomly(false)
    }

    override fun getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.MODEL

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false
}

internal class LegacyGunSmithTableBlock(path: String) : LegacyBaseBlock(path, Material.WOOD, SoundType.WOOD) {
    override fun getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.ENTITYBLOCK_ANIMATED

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity = GunSmithTableTileEntity().apply {
        blockId = requireNotNull(this@LegacyGunSmithTableBlock.registryName)
    }

    /**
     * 掉落物要带上 TileEntity 里的 BlockId，否则枪包工作台（配件台/弹药台等）被破坏后
     * 会变回没有 BlockId 的默认枪匠台。
     */
    override fun getDrops(
        drops: NonNullList<ItemStack>,
        world: IBlockAccess,
        pos: BlockPos,
        state: IBlockState,
        fortune: Int,
    ) {
        val item = Item.getItemFromBlock(this)
        if (item == Items.AIR) return
        val stack = ItemStack(item)
        val tile = world.getTileEntity(pos) as? GunSmithTableTileEntity
        if (item is LegacyBlockItem && tile != null) {
            item.setBlockId(stack, tile.blockId)
        }
        drops += stack
    }

    override fun getPickBlock(
        state: IBlockState,
        target: RayTraceResult,
        world: World,
        pos: BlockPos,
        player: EntityPlayer,
    ): ItemStack {
        val item = Item.getItemFromBlock(this)
        val stack = ItemStack(item)
        val tile = world.getTileEntity(pos) as? GunSmithTableTileEntity
        if (item is LegacyBlockItem && tile != null) {
            item.setBlockId(stack, tile.blockId)
        }
        return stack
    }

    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
    ): Boolean {
        if (worldIn.isRemote) {
            return true
        }
        if (worldIn.getTileEntity(pos) !is GunSmithTableTileEntity) {
            return true
        }
        playerIn.openGui(TACZLegacy, LegacyGuiIds.GUN_SMITH_TABLE, worldIn, pos.x, pos.y, pos.z)
        worldIn.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f)
        return true
    }
}

internal class LegacyTargetBlock(path: String) : LegacyBaseBlock(path, Material.WOOD, SoundType.WOOD) {
    init {
        setTickRandomly(true)
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity = TargetTileEntity()

    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
    ): Boolean {
        if (worldIn.isRemote) {
            return true
        }
        val tile = worldIn.getTileEntity(pos) as? TargetTileEntity ?: return true
        tile.trigger()
        worldIn.notifyBlockUpdate(pos, state, state, 3)
        worldIn.notifyNeighborsOfStateChange(pos, this, false)
        worldIn.playSound(null, pos, LegacySoundEvents.TARGET_BLOCK_HIT, SoundCategory.BLOCKS, 1.0f, 1.0f)
        worldIn.scheduleUpdate(pos, this, 40)
        return true
    }

    override fun canProvidePower(state: IBlockState): Boolean = true

    override fun getWeakPower(state: IBlockState, blockAccess: net.minecraft.world.IBlockAccess, pos: BlockPos, side: EnumFacing): Int {
        val tile = blockAccess.getTileEntity(pos) as? TargetTileEntity ?: return 0
        return if (tile.triggered) 15 else 0
    }

    override fun getStrongPower(state: IBlockState, blockAccess: net.minecraft.world.IBlockAccess, pos: BlockPos, side: EnumFacing): Int =
        getWeakPower(state, blockAccess, pos, side)

    override fun updateTick(worldIn: World, pos: BlockPos, state: IBlockState, rand: Random): Unit {
        val tile = worldIn.getTileEntity(pos) as? TargetTileEntity ?: return
        if (tile.triggered) {
            tile.reset()
            worldIn.notifyBlockUpdate(pos, state, state, 3)
            worldIn.notifyNeighborsOfStateChange(pos, this, false)
        }
    }
}

internal class LegacyStatueBlock(path: String) : LegacyBaseBlock(path, Material.ROCK, SoundType.STONE) {
    init {
        setHardness(3.0f)
        setResistance(6.0f)
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity = StatueTileEntity()

    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
    ): Boolean {
        if (worldIn.isRemote) {
            return true
        }
        val tile = worldIn.getTileEntity(pos) as? StatueTileEntity ?: return true
        val held = playerIn.getHeldItem(hand)
        if (!held.isEmpty && tile.storedItem.isEmpty) {
            val copy = held.copy()
            copy.count = 1
            tile.store(copy)
            held.shrink(1)
            worldIn.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_ADD_ITEM, SoundCategory.BLOCKS, 1.0f, 1.0f)
            return true
        }
        if (held.isEmpty && !tile.storedItem.isEmpty) {
            InventoryHelper.spawnItemStack(worldIn, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, tile.storedItem.copy())
            tile.clear()
            worldIn.playSound(null, pos, SoundEvents.ENTITY_ITEMFRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1.0f, 1.0f)
            return true
        }
        return true
    }

    override fun breakBlock(worldIn: World, pos: BlockPos, state: IBlockState): Unit {
        val tile = worldIn.getTileEntity(pos) as? StatueTileEntity
        if (tile != null && !tile.storedItem.isEmpty) {
            InventoryHelper.spawnItemStack(worldIn, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5, tile.storedItem.copy())
            tile.clear()
        }
        super.breakBlock(worldIn, pos, state)
    }
}

internal class LegacyChunkLoaderBlock(path: String) : LegacyBaseBlock(path, Material.ROCK, SoundType.STONE) {
    init {
        setHardness(3.0f)
        setResistance(6.0f)
    }

    override fun createNewTileEntity(worldIn: World, meta: Int): TileEntity = ChunkLoaderTileEntity()

    override fun onBlockPlacedBy(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        placer: EntityLivingBase,
        stack: ItemStack,
    ) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack)
        if (!worldIn.isRemote && placer is EntityPlayer) {
            (worldIn.getTileEntity(pos) as? ChunkLoaderTileEntity)?.let {
                it.ownerUuid = placer.uniqueID
                it.ownerName = placer.name
                it.markDirty()
            }
            enforceChunkLoaderLimit(worldIn, pos, placer, stack)
        }
    }

    /** 超过每玩家放置上限时拆除刚放的加载器，返还物品并提示；创造与 0（不限）放行。 */
    private fun enforceChunkLoaderLimit(
        world: World,
        pos: BlockPos,
        player: EntityPlayer,
        stack: ItemStack,
    ) {
        val limit = LegacyConfigManager.server.chunkLoaderLimitPerPlayer
        if (limit <= 0 || player.capabilities.isCreativeMode) return
        val uuid = player.uniqueID
        val count = world.loadedTileEntityList.count {
            it is ChunkLoaderTileEntity && it.ownerUuid == uuid
        }
        if (count <= limit) return
        world.setBlockToAir(pos)
        if (!player.inventory.addItemStackToInventory(stack)) {
            InventoryHelper.spawnItemStack(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), stack)
        }
        player.sendMessage(TextComponentTranslation("message.tacz.chunk_loader.limit_reached", limit))
    }

    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
    ): Boolean {
        if (worldIn.isRemote) {
            return true
        }
        if (worldIn.getTileEntity(pos) !is ChunkLoaderTileEntity) {
            return true
        }
        playerIn.openGui(TACZLegacy, LegacyGuiIds.CHUNK_LOADER, worldIn, pos.x, pos.y, pos.z)
        return true
    }

    override fun onBlockAdded(worldIn: World, pos: BlockPos, state: IBlockState) {
        super.onBlockAdded(worldIn, pos, state)
        (worldIn.getTileEntity(pos) as? ChunkLoaderTileEntity)?.refresh()
    }

    override fun onNeighborChange(world: IBlockAccess, pos: BlockPos, neighbor: BlockPos) {
        val worldIn = world as? World ?: return
        (worldIn.getTileEntity(pos) as? ChunkLoaderTileEntity)?.refresh()
    }

    override fun breakBlock(worldIn: World, pos: BlockPos, state: IBlockState) {
        (worldIn.getTileEntity(pos) as? ChunkLoaderTileEntity)?.onBroken()
        super.breakBlock(worldIn, pos, state)
    }
}
