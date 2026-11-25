package com.tacz.legacy.client.renderer.gltf

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.model.mesh.MaterialResolver
import com.tacz.legacy.client.model.mesh.Mesh
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.gltf.GltfMeshParser
import com.tacz.legacy.client.resource.mesh.StaticMeshData
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.nio.charset.StandardCharsets

/**
 * Render-side bridge for decoded static glTF meshes.
 *
 * <p>Two sources:
 * <ul>
 *   <li>[renderBundled]: built-in classpath model under
 *       {@code assets/<ns>/models/gltf/<file>.gltf}, cached per file name. Used by
 *       the bundled static-item renderers that read their own classpath resource.</li>
 *   <li>[renderManaged]: gun-pack model resolved through [TACZClientAssetManager],
 *       which caches the parsed [com.tacz.legacy.client.resource.mesh.StaticMeshData].</li>
 * </ul>
 *
 * <p>The caller is expected to have already pushed the matrix, bound the texture
 * and enabled lighting / blend; this bridge only opens a GL_TRIANGLES batch,
 * compiles the mesh and draws it.
 */
@SideOnly(Side.CLIENT)
internal object GltfMeshRenderer {

    private const val BUNDLED_DIR = "models/gltf"

    /** Cached built-in meshes keyed by file name; null means "tried and missing/invalid". */
    private val bundledMeshes = LinkedHashMap<String, Mesh?>()

    /** Cached managed glTF meshes keyed by model location; null means missing/invalid. */
    private val managedGltfMeshes = LinkedHashMap<ResourceLocation, Mesh?>()

    /** Cached managed OBJ meshes keyed by model location; null means missing/invalid. */
    private val managedObjMeshes = LinkedHashMap<ResourceLocation, Mesh?>()

    /**
     * Optional per-group material-key → texture resolver attached to every cached
     * mesh. Null (the default) leaves each group on the texture the caller already
     * bound; wire this up when a multi-texture model needs per-group texture binds.
     */
    @Volatile
    var materialResolver: MaterialResolver? = null

    /**
     * Draw a built-in classpath glTF model.
     *
     * @param fileName model file name without the {@code .gltf} extension
     * @param scale metres-to-bedrock-unit scale handed to the mesh
     * @param flipY whether to flip the glTF Y-up axis to Minecraft Y-down
     * @return true when a non-empty glTF mesh was found and drawn; false to fall back to bedrock
     */
    fun tryRenderBundled(fileName: String, scale: Float = 16.0F, flipY: Boolean = true): Boolean {
        val mesh = resolveBundled(fileName) ?: return false
        if (mesh.isEmpty()) return false
        mesh.setScale(scale)
        mesh.setFlipY(flipY)
        draw(mesh)
        return true
    }

    /**
     * Draw a gun-pack glTF model resolved through the client asset manager.
     *
     * @param id model location; resolved against {@code geo_models/<path>.gltf}
     * @return true when a non-empty glTF mesh was found and drawn
     */
    fun tryRenderManaged(id: ResourceLocation, scale: Float = 16.0F, flipY: Boolean = true): Boolean {
        val mesh = resolveManaged(managedGltfMeshes, id, "gltf:$id") { TACZClientAssetManager.getGltfMesh(it) } ?: return false
        if (mesh.isEmpty()) return false
        mesh.setScale(scale)
        mesh.setFlipY(flipY)
        draw(mesh)
        return true
    }

    /**
     * Draw a gun-pack OBJ model resolved through the client asset manager.
     *
     * @param id model location; resolved against {@code geo_models/<path>.obj}
     * @return true when a non-empty OBJ mesh was found and drawn
     */
    fun tryRenderManagedObj(id: ResourceLocation, scale: Float = 16.0F, flipY: Boolean = true): Boolean {
        val mesh = resolveManaged(managedObjMeshes, id, "obj:$id") { TACZClientAssetManager.getObjMesh(it) } ?: return false
        if (mesh.isEmpty()) return false
        mesh.setScale(scale)
        mesh.setFlipY(flipY)
        draw(mesh)
        return true
    }

    /** Resolve a managed mesh, reusing the cached [Mesh] so its VBO survives across frames. */
    private fun resolveManaged(
        cache: LinkedHashMap<ResourceLocation, Mesh?>,
        id: ResourceLocation,
        tag: String,
        loader: (ResourceLocation) -> StaticMeshData?,
    ): Mesh? {
        cache[id]?.let { return it }
        val data = loader(id)
        val mesh = data?.let { Mesh(it, tag).apply { materialResolver = this@GltfMeshRenderer.materialResolver } }
        cache[id] = mesh
        return mesh
    }

    private fun resolveBundled(fileName: String): Mesh? {
        bundledMeshes[fileName]?.let { return it }
        val gltfPath = "assets/${TACZLegacy.MOD_ID}/$BUNDLED_DIR/$fileName.gltf"
        val json = GltfMeshRenderer::class.java.classLoader
            .getResourceAsStream(gltfPath)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
        val mesh = if (json == null) {
            null
        } else {
            runCatching {
                val data = GltfMeshParser.parse(json) { uri ->
                    val binPath = resolveSidecar(gltfPath, uri)
                    GltfMeshRenderer::class.java.classLoader
                        .getResourceAsStream(binPath)
                        ?.use { it.readBytes() }
                }
                Mesh(data, "bundled:$fileName").apply {
                    materialResolver = this@GltfMeshRenderer.materialResolver
                }
            }.onFailure { error ->
                TACZLegacy.logger.warn("[GltfMeshRenderer] Failed to parse bundled glTF {}: {}", fileName, error.message)
            }.getOrNull()
        }
        bundledMeshes[fileName] = mesh
        return mesh
    }

    /** Resolve a sidecar {@code .bin} URI relative to the glTF file's own directory. */
    private fun resolveSidecar(gltfPath: String, uri: String): String {
        val normalized = uri.replace('\\', '/').removePrefix("./")
        if (normalized.contains(':')) {
            val resource = ResourceLocation(normalized)
            return "assets/${resource.namespace}/$BUNDLED_DIR/${resource.path}"
        }
        val parent = gltfPath.substringBeforeLast('/', "")
        return if (parent.isEmpty()) "assets/${TACZLegacy.MOD_ID}/$BUNDLED_DIR/$normalized"
        else "$parent/$normalized"
    }

    private fun draw(mesh: Mesh) {
        if (mesh.isEmpty()) return
        mesh.draw()
    }

    /**
     * Release every cached mesh's VBO/IBO and drop all three caches.
     *
     * <p>Called when gun-pack assets are reloaded and when the client stops, so GL buffers
     * do not outlive the loaded snapshot. Must run on the render thread, same as the draw
     * path. Meshes are rebuilt lazily on the next frame after release.
     */
    fun releaseAll() {
        for (mesh in bundledMeshes.values) mesh?.destroyVbo()
        for (mesh in managedGltfMeshes.values) mesh?.destroyVbo()
        for (mesh in managedObjMeshes.values) mesh?.destroyVbo()
        bundledMeshes.clear()
        managedGltfMeshes.clear()
        managedObjMeshes.clear()
    }
}
