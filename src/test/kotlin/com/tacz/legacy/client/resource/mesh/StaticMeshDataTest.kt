package com.tacz.legacy.client.resource.mesh

import com.tacz.legacy.client.resource.mesh.MeshGroup
import com.tacz.legacy.client.resource.mesh.StaticMeshData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticMeshDataTest {

    private fun triangle(px: Float): MeshGroup {
        val positions = floatArrayOf(px, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val uvs = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val indices = intArrayOf(0, 1, 2)
        return MeshGroup("mat-$px", positions, uvs, normals, indices)
    }

    @Test
    fun `legacy single-group constructor keeps null material key and array getters`() {
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val mesh = StaticMeshData(positions, floatArrayOf(0f, 0f), floatArrayOf(0f, 0f, 1f), intArrayOf(0, 1, 2))

        assertEquals(1, mesh.groupCount)
        assertNull(mesh.groups[0].materialKey)
        assertTrue(mesh.groups[0].isEmpty().not())
        assertEquals(3, mesh.vertexCount)
        assertEquals(positions, mesh.positions)
        assertEquals(3, mesh.indices.size)
    }

    @Test
    fun `multi-group constructor keeps independent geometry per group`() {
        val first = triangle(0f)
        val second = triangle(2f)
        val mesh = StaticMeshData(listOf(first, second))

        assertEquals(2, mesh.groupCount)
        assertEquals("mat-0.0", mesh.groups[0].materialKey)
        assertEquals("mat-2.0", mesh.groups[1].materialKey)

        // Legacy array getters still reflect the first group.
        assertEquals(3, mesh.vertexCount)
        assertEquals(first.positions, mesh.positions)
        assertEquals(first.indices, mesh.indices)

        // The second group stays reachable through the group list.
        assertEquals(second.positions, mesh.groups[1].positions)
    }

    @Test
    fun `mesh is non-empty when only a later group carries geometry`() {
        val emptyGroup = MeshGroup("mat-empty", floatArrayOf(), floatArrayOf(), floatArrayOf(), intArrayOf())
        val mesh = StaticMeshData(listOf(emptyGroup, triangle(1f)))

        assertEquals(2, mesh.groupCount)
        assertTrue(mesh.isEmpty().not())
        // Legacy array getters still reflect the first (empty) group.
        assertEquals(0, mesh.vertexCount)
    }

    @Test
    fun `empty group list degrades to an empty mesh without NPE`() {
        val mesh = StaticMeshData(emptyList())

        assertEquals(0, mesh.groupCount)
        assertTrue(mesh.isEmpty())
        assertEquals(0, mesh.vertexCount)
    }
}
