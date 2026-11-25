package com.tacz.legacy.client.model.mesh;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Resolves a {@link MeshGroup#getMaterialKey() material key} produced by the
 * OBJ / glTF parser into the texture the render layer should bind before drawing
 * that group.
 *
 * <p>Returning {@code null} means "no override": keep whatever texture the
 * caller already bound (the legacy single-texture path). A {@code null}
 * material key on the group is never routed through the resolver.
 */
@FunctionalInterface
public interface MaterialResolver {

    @Nullable
    ResourceLocation resolve(@Nullable String materialKey);
}
