package com.tacz.legacy.client.resource.obj

import com.tacz.legacy.client.resource.mesh.StaticMeshData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjMeshParserTest {

    @Test
    fun `triangle plus quad are merged, fanned and flat-normalized`() {
        val obj = """
            # test plane
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            vt 0 0
            vt 1 0
            vt 1 1
            vt 0 1
            f 1/1 2/2 3/3
            f 1/1 2/2 3/3 4/4
        """.trimIndent()

        val mesh: StaticMeshData = ObjMeshParser.parse(obj)

        assertEquals(4, mesh.vertexCount)
        assertEquals(12, mesh.positions.size)
        assertEquals(8, mesh.uvs.size)
        // one triangle + one quad fanned into two triangles = 3 triangles = 9 indices
        assertEquals(9, mesh.indices.size)

        val normals = mesh.normals
        for (v in 0 until mesh.vertexCount) {
            val nx = normals[v * 3]
            val ny = normals[v * 3 + 1]
            val nz = normals[v * 3 + 2]
            val len = Math.sqrt((nx * nx + ny * ny + nz * nz).toDouble())
            assertTrue("normal $v is a unit vector, got $len", len > 0.99 && len < 1.01)
        }
    }

    @Test
    fun `negative indices resolve relative to end and explicit normals are kept`() {
        val obj = """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            vn 0 0 1
            vn 0 0 1
            vn 0 0 1
            f -1/-1/-1 -2/-2/-2 -3/-3/-3
        """.trimIndent()

        val mesh: StaticMeshData = ObjMeshParser.parse(obj)

        assertEquals(3, mesh.vertexCount)
        assertEquals(3, mesh.indices.size)
        // explicit +Z normals must survive
        for (v in 0 until mesh.vertexCount) {
            assertEquals(0.0F, mesh.normals[v * 3], 1.0E-6F)
            assertEquals(0.0F, mesh.normals[v * 3 + 1], 1.0E-6F)
            assertEquals(1.0F, mesh.normals[v * 3 + 2], 1.0E-6F)
        }
    }

    @Test
    fun `face without texture coordinates falls back to zero uv`() {
        val obj = """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
        """.trimIndent()

        val mesh: StaticMeshData = ObjMeshParser.parse(obj)

        assertEquals(3, mesh.vertexCount)
        for (i in 0 until mesh.uvs.size) {
            assertEquals(0.0F, mesh.uvs[i], 1.0E-6F)
        }
    }
}
