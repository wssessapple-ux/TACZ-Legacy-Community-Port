package com.tacz.legacy.client.resource.mesh

import com.tacz.legacy.client.model.bedrock.BedrockCube
import com.tacz.legacy.client.model.bedrock.BedrockCubeBox
import com.tacz.legacy.client.model.bedrock.BedrockPart
import net.minecraft.client.renderer.BufferBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BedrockMeshBakerTest {

    private fun boxCube(): BedrockCube {
        // 16x16x16 bedrock box, no inflate, plain mirror=false layout, 64x64 texture.
        return BedrockCubeBox(0f, 0f, 0f, 0f, 0f, 16f, 16f, 16f, 0f, false, 64f, 64f)
    }

    private fun unsupportedCube(): BedrockCube {
        // Anything that is not a box/per-face is treated as curved and rejected whole-model.
        return object : BedrockCube {
            override fun compile(buffer: BufferBuilder?) = Unit
        }
    }

    @Test
    fun `box cube bakes six quads into non-empty mesh`() {
        val root = BedrockPart("root")
        root.cubes.add(boxCube())

        val mesh = BedrockMeshBaker.bakeRoots(listOf(root))

        assertNotNull(mesh)
        // 6 faces x 4 vertices = 24 vertices; 6 faces x 2 triangles x 3 indices = 36 indices.
        assertEquals(24, mesh!!.vertexCount)
        assertEquals(36, mesh.indices.size)
        assertEquals(72, mesh.positions.size)
        assertTrue(mesh.normals.size >= 3)
    }

    @Test
    fun `curved cube rejects whole model to immediate mode`() {
        val root = BedrockPart("root")
        root.cubes.add(unsupportedCube())

        assertNull(BedrockMeshBaker.bakeRoots(listOf(root)))
    }

    @Test
    fun `empty root list bakes to null`() {
        assertNull(BedrockMeshBaker.bakeRoots(emptyList()))
    }

    @Test
    fun `hidden subtree bakes to null`() {
        val root = BedrockPart("root")
        root.visible = false
        root.cubes.add(boxCube())

        assertNull(BedrockMeshBaker.bakeRoots(listOf(root)))
    }

    @Test
    fun `child cube inherits parent matrix and still bakes`() {
        val parent = BedrockPart("parent")
        val child = BedrockPart("child")
        child.cubes.add(boxCube())
        parent.children.add(child)

        val mesh = BedrockMeshBaker.bakeRoots(listOf(parent))

        assertNotNull(mesh)
        assertEquals(24, mesh!!.vertexCount)
    }
}
