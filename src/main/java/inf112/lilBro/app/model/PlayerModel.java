package inf112.lilBro.app.model;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import inf112.lilBro.app.controller.ControllablePlayerModel;
import inf112.lilBro.app.event.Event;
import inf112.lilBro.app.event.EventBus;
import inf112.lilBro.app.event.EventHandler;
import inf112.lilBro.app.model.effect.Effect;
import inf112.lilBro.app.model.effect.EffectHpUp;
import inf112.lilBro.app.model.event.*;
import inf112.lilBro.app.model.item.ItemModel;
import inf112.lilBro.app.view.ViewableEffect;
import inf112.lilBro.app.view.ViewableItem;
import inf112.lilBro.app.view.ViewablePlayerModel;

import java.util.*;
import java.util.function.Predicate;

public class PlayerModel implements ControllablePlayerModel, ViewablePlayerModel, EventHandler, ContactListener {
    private static final String USERDATA_BOTTOM = "PlayerBottom";
    private static final String USERDATA_LEFT = "PlayerLeft";
    private static final String USERDATA_RIGHT = "PlayerRight";
    private static final String USERDATA_TOP = "PlayerTop";
    private static final String USERDATA_SENSOR = "PlayerSensor";
    private static final float WIDTH = 3;
    private static final float HEIGHT = 3;
    private static final float DX = 15;
    private static final float DY = 40;
    private static final float AIR_CONTROL = 0.5f;
    private static final float MAX_DX = 10;
    private static final float MAX_DY = 10;
    private static final float DENSITY = 0.5f;
    private static final float FRICTION = 0;
    private static final float FRICTION_BOTTOM = 10;
    private static final float RESTITUTION = 0;
    private static final Set<Object> USERDATA_SET;

    static {
        USERDATA_SET = createUserDataSet();
    }

    private final EventBus bus;
    private final World world;
    private final float void_height;
    private final Body body;
    private final List<Effect> effects;
    private final IHealth hp;
    private Shape shapeTop, shapeBottom, shapeLeft, shapeRight;
    private Shape shapeSensor;
    private PlayerState state;
    private boolean moveUp, moveDown, moveLeft, moveRight;
    private int contactCountSensor = 0;
    private ItemModel item;
    private float immunityCoolDown = 0;

    /**
     * @param world which the player-{@link Body} is created in
     * @param x     left-most position of player
     * @param y     bottom-most position of player
     */
    public PlayerModel(EventBus bus, World world, float x, float y) {
        this(bus, world, -20, x, y);
    }

    public PlayerModel(EventBus bus, World world, float void_height, float x, float y) {
        this.bus = bus;
        this.world = world;
        this.void_height = void_height;
        body = createBody(x + WIDTH / 2, y + HEIGHT / 2);
        state = PlayerState.IDLE_RIGHT;
        moveUp = false;
        moveDown = false;
        moveLeft = false;
        moveRight = false;
        effects = new ArrayList<>();
        hp = new Health(this, bus, 3, 5);

        bus.addEventHandler(this);
    }

    /**
     * A collection of all the playerUserDatas
     *
     * @return {@code HashSet<String>} of PlayerUserDatas
     */
    private static HashSet<Object> createUserDataSet() {
        HashSet<Object> set = new HashSet<>();
        set.add(USERDATA_LEFT);
        set.add(USERDATA_RIGHT);
        set.add(USERDATA_TOP);
        set.add(USERDATA_BOTTOM);
        set.add(USERDATA_SENSOR);
        return set;
    }

    /**
     * Checks if the {@link Fixture} belongs to the contactable player.
     *
     * @param fixture to check
     * @return if the {@link Fixture} belongs to the player
     */
    private boolean isContacted(Fixture fixture) {
        return USERDATA_SET.contains(fixture.getUserData());
    }

    @Override
    public void moveUp(boolean value) {
        moveUp = value;
    }

    @Override
    public void moveDown(boolean value) {
        moveDown = value;
    }

    @Override
    public void moveLeft(boolean value) {
        moveLeft = value;
    }

    @Override
    public void moveRight(boolean value) {
        moveRight = value;
    }

    @Override
    public void useItem() {
        if (item == null) return;
        Effect effect = item.use();
        Predicate<? super Effect> same = (e) -> effect.getClass().equals(e.getClass());

        if (effect instanceof EffectHpUp) {
            hp.regenerate(1);
        } else if (effects.stream().anyMatch(same)) {
            List<Effect> newEffects = effects.stream().map((e) -> same.test(e) ? effect : e).toList();
            effects.clear();
            effects.addAll(newEffects);
        } else {
            effects.add(effect);
        }
    }

    @Override
    public float getX() {
        return body.getPosition().x - WIDTH / 2;
    }

    @Override
    public float getY() {
        return body.getPosition().y - HEIGHT / 2;
    }

    @Override
    public float getWidth() {
        return WIDTH;
    }

    @Override
    public float getHeight() {
        return HEIGHT;
    }

    @Override
    public void step(float timeStep) {
        // EFFECT
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect effect = effects.get(i);
            if (effect.hasEnded()) effects.remove(effect);
            else effect.step();
        }

        // CHECK FOR VOID
        if (getY() < void_height) {
            bus.post(new EventDeath(this));
        }

        // MOVEMENT
        float dx = DX;
        dx *= effects.stream().reduce((float) 1, (v, e) -> v * e.getSpeedBoost(), (a, b) -> a * b);
        dx *= isGrounded() ? 1 : AIR_CONTROL;
        float dy = DY;
        dy *= effects.stream().reduce((float) 1, (v, e) -> v * e.getJumpBoost(), (a, b) -> a * b);

        if (moveUp && !moveDown && isGrounded()) {
            move(0, dy);
            bus.post(new EventPlayerAction(PlayerAction.JUMP));
        }
        if (moveDown && !moveUp && !isGrounded() && body.getLinearVelocity().y > -MAX_DY) move(0, -MAX_DY);
        if (moveRight && !moveLeft) move(dx, 0);
        if (moveLeft && !moveRight) move(-dx, 0);

        // HP
        if (immunityCoolDown > 0) immunityCoolDown -= timeStep;
        else if (immunityCoolDown < 0) immunityCoolDown = 0;
        updateState();
    }

    private boolean isGrounded() {
        return contactCountSensor > 0;
    }

    /**
     * Applies the speed to the player-{@link Body}, taking into account
     * the active {@link Effect} to set the maximum possible speed.
     *
     * @param dx {@code impulseX} used in {@code Body::applyLinearImpulse}
     * @param dy {@code impulseY} used in {@code Body::applyLinearImpulse}
     */
    private void move(float dx, float dy) {
        Vector2 d = body.getLinearVelocity();

        float maxDx = MAX_DX;
        maxDx *= effects.stream().reduce((float) 1, (v, e) -> v * e.getSpeedBoost(), (a, b) -> a * b);
        float maxDy = MAX_DY;
        maxDy *= effects.stream().reduce((float) 1, (v, e) -> v * e.getJumpBoost(), (a, b) -> a * b);

        if (dx > 0 && d.x < maxDx || dx < 0 && d.x > -maxDx) {
            body.applyLinearImpulse(dx, 0, getX(), getY(), true);
        } else if (dx != 0) {
            body.setLinearVelocity((dx > 0 ? 1 : -1) * maxDx, d.y);
        }
        if (dy > 0 && d.y < maxDy || dy < 0 && d.y > -maxDy) {
            body.applyLinearImpulse(0, dy, getX(), getY(), true);
        } else if (dy != 0) {
            body.setLinearVelocity(d.x, (dy > 0 ? 1 : -1) * maxDy);
        }
    }

    /**
     * Initializes the {@code shape}-variable and creates
     * a {@link Body} with its fixture.
     *
     * @param x horizontal center-position of player
     * @param y vertical center-position of player
     * @return the newly created {@link Body}
     */
    private Body createBody(float x, float y) {
        createShapes();

        BodyDef bDef = new BodyDef();
        bDef.type = BodyDef.BodyType.DynamicBody;
        bDef.position.set(x, y);
        bDef.fixedRotation = true;

        Body body = world.createBody(bDef);
        createFixtures(body);

        return body;
    }

    /**
     * Sets the {@link Shape}-variables.
     */
    private void createShapes() {
        float e = 0.02f;

        // SENSOR
        PolygonShape shapeSensor = new PolygonShape();
        shapeSensor.setAsBox(WIDTH / 2 - e, e, new Vector2(0, -HEIGHT / 2), 0);
        this.shapeSensor = shapeSensor;

        // BOTTOM
        Vector2[] vecBottom = new Vector2[5];
        vecBottom[0] = new Vector2(0, 0); // C
        vecBottom[1] = new Vector2(WIDTH / 2 - e, -HEIGHT / 2 + e / 4); // BRT
        vecBottom[2] = new Vector2(WIDTH / 2 - 2 * e, -HEIGHT / 2); // BR
        vecBottom[3] = new Vector2(-WIDTH / 2 + 2 * e, -HEIGHT / 2); // BL
        vecBottom[4] = new Vector2(-WIDTH / 2 + e, -HEIGHT / 2 + e / 4); // BLT
        PolygonShape shapeBottom = new PolygonShape();
        shapeBottom.set(vecBottom);
        this.shapeBottom = shapeBottom;

        // TOP
        Vector2[] vecTop = new Vector2[3];
        vecTop[0] = new Vector2(0, 0); // C
        vecTop[1] = new Vector2(-WIDTH / 2, HEIGHT / 2); // TL
        vecTop[2] = new Vector2(WIDTH / 2, HEIGHT / 2); // TR
        PolygonShape shapeTop = new PolygonShape();
        shapeTop.set(vecTop);
        this.shapeTop = shapeTop;

        // LEFT
        Vector2[] vecLeft = new Vector2[3];
        vecLeft[0] = new Vector2(0, 0); // C
        vecLeft[1] = new Vector2(-WIDTH / 2, -HEIGHT / 2 + e); // BL
        vecLeft[2] = new Vector2(-WIDTH / 2, HEIGHT / 2 - e); // TL
        PolygonShape shapeLeft = new PolygonShape();
        shapeLeft.set(vecLeft);
        this.shapeLeft = shapeLeft;

        // RIGHT
        Vector2[] vecRight = new Vector2[3];
        vecRight[0] = new Vector2(0, 0); // C
        vecRight[1] = new Vector2(WIDTH / 2, HEIGHT / 2 - e); // TR
        vecRight[2] = new Vector2(WIDTH / 2, -HEIGHT / 2 + e); // BR
        PolygonShape shapeRight = new PolygonShape();
        shapeRight.set(vecRight);
        this.shapeRight = shapeRight;
    }

    /**
     * Creates fixtures and applies them to the {@link Body}.
     */
    private void createFixtures(Body body) {
        FixtureDef fDefSensor = new FixtureDef();
        fDefSensor.isSensor = true;
        fDefSensor.shape = shapeSensor;

        FixtureDef fDefBottom = new FixtureDef();
        fDefBottom.density = DENSITY;
        fDefBottom.friction = FRICTION_BOTTOM;
        fDefBottom.restitution = RESTITUTION;
        fDefBottom.shape = shapeBottom;

        FixtureDef fDefTop = new FixtureDef();
        fDefTop.density = DENSITY;
        fDefTop.friction = FRICTION;
        fDefTop.restitution = RESTITUTION;
        fDefTop.shape = shapeTop;

        FixtureDef fDefLeft = new FixtureDef();
        fDefLeft.density = DENSITY;
        fDefLeft.friction = FRICTION;
        fDefLeft.restitution = RESTITUTION;
        fDefLeft.shape = shapeLeft;

        FixtureDef fDefRight = new FixtureDef();
        fDefRight.density = DENSITY;
        fDefRight.friction = FRICTION;
        fDefRight.restitution = RESTITUTION;
        fDefRight.shape = shapeRight;

        body.createFixture(fDefSensor).setUserData(USERDATA_SENSOR);
        body.createFixture(fDefBottom).setUserData(USERDATA_BOTTOM);
        body.createFixture(fDefTop).setUserData(USERDATA_TOP);
        body.createFixture(fDefLeft).setUserData(USERDATA_LEFT);
        body.createFixture(fDefRight).setUserData(USERDATA_RIGHT);
    }

    @Override
    public void handleEvent(Event event) {
        if (event instanceof EventDispose) {
            shapeSensor.dispose();
            shapeBottom.dispose();
            shapeTop.dispose();
            shapeLeft.dispose();
            shapeRight.dispose();
        } else if (event instanceof EventItemContact e) {
            if (!isContacted(e.contact().getFixtureA()) && !isContacted(e.contact().getFixtureB())) return;
            if (item != null) return;
            item = e.item();
            bus.post(new EventItemPickedUp(item));
            bus.post(new EventPlayerAction(PlayerAction.PICKUP));
        } else if (event instanceof EventItemUsedUp e) {
            if (!e.item().equals(item)) return;
            item = null;
        } else if (event instanceof EventDamage e) {
            if (isContacted(e.fixture())) {
                handleDamage(e.damage());
            }
        }
    }

    /**
     * Handles if the player should
     * take damage whether the player has immunity or not
     *
     * @param damage the amount of damage the player should take
     */
    private void handleDamage(int damage) {
        if (immunityCoolDown == 0) {
            hp.damage(damage);
            bus.post(new EventPlayerAction(PlayerAction.DAMAGE));
            immunityCoolDown = 1;
        }
    }

    @Override
    public PlayerState getState() {
        return this.state;
    }

    private void updateState() {
        if (isGrounded()) {
            if (moveRight && !moveLeft) {
                state = PlayerState.RIGHT;
            } else if (moveLeft && !moveRight) {
                state = PlayerState.LEFT;
            } else {
                if (state.equals(PlayerState.LEFT) || state.equals(PlayerState.JUMP_LEFT)) {
                    state = PlayerState.IDLE_LEFT;
                } else if (state.equals(PlayerState.RIGHT) || state.equals(PlayerState.JUMP_RIGHT)) {
                    state = PlayerState.IDLE_RIGHT;
                }
            }
        } else {
            if (moveLeft && !moveRight) {
                state = PlayerState.JUMP_LEFT;
            } else if (moveRight && !moveLeft) {
                state = PlayerState.JUMP_RIGHT;
            } else {
                if (state.equals(PlayerState.IDLE_LEFT) || state.equals(PlayerState.LEFT) || state.equals(PlayerState.JUMP_LEFT)) {
                    state = PlayerState.JUMP_LEFT;
                } else {
                    state = PlayerState.JUMP_RIGHT;
                }
            }
        }
    }

    @Override
    public void beginContact(Contact contact) {
        if (isSensorToGroundContact(contact)) contactCountSensor++;
        if (isBottomToGroundContact(contact)) body.setLinearVelocity(body.getLinearVelocity().x, 0);
    }

    @Override
    public void endContact(Contact contact) {
        if (isSensorToGroundContact(contact)) contactCountSensor--;
    }

    @Override
    public void preSolve(Contact contact, Manifold manifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse contactImpulse) {
    }

    private boolean isSensorToGroundContact(Contact contact) {
        Fixture fA = contact.getFixtureA();
        Fixture fB = contact.getFixtureB();

        return (USERDATA_SENSOR.equals(fA.getUserData()) && !fB.isSensor())
                || (USERDATA_SENSOR.equals(fB.getUserData()) && !fA.isSensor());
    }

    private boolean isBottomToGroundContact(Contact contact) {
        Fixture fA = contact.getFixtureA();
        Fixture fB = contact.getFixtureB();

        return (USERDATA_BOTTOM.equals(fA.getUserData()) && !fB.isSensor())
                || (USERDATA_BOTTOM.equals(fB.getUserData()) && !fA.isSensor());
    }

    @Override
    public int getHp() {
        return hp.getHealth();
    }

    @Override
    public List<ViewableEffect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    @Override
    public ViewableItem getItem() {
        return item;
    }
}
