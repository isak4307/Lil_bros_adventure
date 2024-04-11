package inf112.skeleton.app.model.tiles.contactableTiles;

import com.badlogic.gdx.physics.box2d.*;
import inf112.skeleton.app.event.EventBus;
import inf112.skeleton.app.model.TileFactory;
import inf112.skeleton.app.model.event.EventReachedDoor;
import inf112.skeleton.app.model.tiles.TileModel;

public class Door1 extends TileModel implements ContactableTiles {
    private static final float DOOR_WIDTH = TILE_WIDTH / 2;
    private static final float DOOR_HEIGHT = TILE_HEIGHT / 2;

    static {
        TileFactory.register('9', (world, eventBus, x, y) -> {
            return new Door1(world, eventBus, x + TILE_WIDTH / 2, y - TILE_HEIGHT / 4, DOOR_WIDTH, DOOR_HEIGHT);
        });
    }

    private final EventBus eventBus;
    private final String USERDATA = "Door1";

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
    public Door1(World world, EventBus bus, float x, float y, float w, float h) {
        super(world, x, y, w, h);
        eventBus = bus;
        eventBus.addEventHandler(this);
    }

    public static void loadStatic() {
    }

    @Override
    protected Body createBody(float x, float y) {
        BodyDef bDef = new BodyDef();
        bDef.type = BodyDef.BodyType.StaticBody;
        bDef.position.set(x, y);

        FixtureDef fDef = new FixtureDef();
        fDef.density = 1;
        fDef.friction = 0.5f;
        fDef.restitution = 0;
        fDef.shape = super.createShape(DOOR_WIDTH, DOOR_HEIGHT);

        Body b = world.createBody(bDef);
        Fixture doorFixture = b.createFixture(fDef);
        doorFixture.setUserData(USERDATA);
        return b;
    }

    @Override
    public void beginContact(Contact contact) {
        Fixture fB = contact.getFixtureB();

        if (USERDATA.equals(fB.getUserData())) {
            eventBus.post(new EventReachedDoor());
        }
    }

    @Override
    public void endContact(Contact contact) {

    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {

    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {

    }
}
