package io.github.hideyukimori.nenepixel.core.domain.layer

import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public class Layer private constructor(
    public val id: LayerId,
    public val name: LayerName,
    public val visibility: LayerVisibility,
    public val snapshot: PixelSnapshot,
) {
    public fun withName(name: LayerName): Layer = Layer(id, name, visibility, snapshot)

    public fun withVisibility(visibility: LayerVisibility): Layer = Layer(id, name, visibility, snapshot)

    public fun withSnapshot(snapshot: PixelSnapshot): Layer = Layer(id, name, visibility, snapshot)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is Layer &&
                    id == other.id &&
                    name == other.name &&
                    visibility == other.visibility &&
                    snapshot == other.snapshot
            )

    override fun hashCode(): Int =
        31 * (31 * (31 * id.hashCode() + name.hashCode()) + visibility.hashCode()) + snapshot.hashCode()

    override fun toString(): String = "Layer(id=$id, name=$name, visibility=$visibility, snapshot=$snapshot)"

    public companion object {
        public fun create(
            id: LayerId,
            name: LayerName,
            visibility: LayerVisibility,
            snapshot: PixelSnapshot,
        ): Layer = Layer(id, name, visibility, snapshot)
    }
}
