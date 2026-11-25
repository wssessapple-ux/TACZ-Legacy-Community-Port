package com.tacz.legacy.client.resource.gltf

import com.tacz.legacy.client.resource.mesh.StaticMeshData
import org.junit.Assert.assertEquals
import org.junit.Test

class GltfMeshParserTest {

    @Test
    fun `primitives split by material into named groups`() {
        val json = """
            {
              "asset": {"version": "2.0"},
              "materials": [{"name": "mat_a"}, {"name": "mat_b"}],
              "meshes": [{
                "primitives": [
                  {"attributes": {"POSITION": 0}, "material": 0, "mode": 4},
                  {"attributes": {"POSITION": 1}, "material": 1, "mode": 4}
                ]
              }],
              "accessors": [
                {"componentType": 5126, "count": 3, "type": "VEC3"},
                {"componentType": 5126, "count": 3, "type": "VEC3"}
              ],
              "bufferViews": [],
              "buffers": []
            }
        """.trimIndent()

        val mesh: StaticMeshData = GltfMeshParser.parse(json, null)

        assertEquals(2, mesh.groupCount)
        assertEquals("mat_a", mesh.groups[0].materialKey)
        assertEquals("mat_b", mesh.groups[1].materialKey)
        // Each primitive is one triangle: 3 vertices, 3 indices, kept in its own group.
        assertEquals(3, mesh.groups[0].vertexCount)
        assertEquals(3, mesh.groups[0].indices.size)
        assertEquals(3, mesh.groups[1].vertexCount)
        assertEquals(3, mesh.groups[1].indices.size)
    }

    @Test
    fun `primitive without material falls into the default null-key group`() {
        val json = """
            {
              "asset": {"version": "2.0"},
              "meshes": [{
                "primitives": [
                  {"attributes": {"POSITION": 0}, "mode": 4},
                  {"attributes": {"POSITION": 0}, "mode": 4}
                ]
              }],
              "accessors": [
                {"componentType": 5126, "count": 3, "type": "VEC3"}
              ],
              "bufferViews": [],
              "buffers": []
            }
        """.trimIndent()

        val mesh: StaticMeshData = GltfMeshParser.parse(json, null)

        assertEquals(1, mesh.groupCount)
        assertEquals(null, mesh.groups[0].materialKey)
        // Two primitives merged into the single default group: 6 vertices, 6 indices.
        assertEquals(6, mesh.groups[0].vertexCount)
        assertEquals(6, mesh.groups[0].indices.size)
    }
}
