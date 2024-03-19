package inf112.skeleton.app.model.tiles.contactableTiles;

import com.badlogic.gdx.physics.box2d.World;
import inf112.skeleton.app.model.TileFactory;
import inf112.skeleton.app.model.tiles.TileModel;

public class Door2 extends TileModel {

    public static void loadStatic(){}

    static {
        TileFactory.register('8', (world, eventBus, x, y) -> {
            return new Door2(world, x + TILE_WIDTH / 2, y + TILE_HEIGHT / 4, TILE_WIDTH/2, TILE_HEIGHT/2);
        });
    }

    /**
     * Creates a {@link TileModel} and places its body in
     * the specified {@link World}.
     *
     * @param world that the body is added to
     * @param x     center position in the horizontal axis
     * @param y     center position in the vertical axis
     * @param w     width of body
     * @param h     height of body
     */
    public Door2(World world, float x, float y, float w, float h) {
        super(world, x, y, w, h);
    }
}