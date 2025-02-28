package inf112.lilBro.app.view.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FillViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import inf112.lilBro.app.event.EventBus;
import inf112.lilBro.app.model.event.EventDispose;
import inf112.lilBro.app.model.event.EventStep;
import inf112.lilBro.app.view.*;
import inf112.lilBro.app.view.texturepack.ITexturePack;
import inf112.lilBro.app.view.texturepack.TexturePack;

import java.util.List;

public class GameScreen implements Screen {
    private static final float VIEWPORT_WIDTH = 20;
    private static final float VIEWPORT_HEIGHT = 20;
    private final EventBus eventBus;
    private final ViewableLevel level;
    private final OrthographicCamera gameCam;
    private final Viewport gamePort;
    private final SpriteBatch batch;
    private final SpriteBatch batchHud;
    private final ITexturePack texturePack;
    private final Stage hud;
    private final Stage BackgroundLayers;

    /**
     * Creates a gamescreen for the gameplay
     *
     * @param level     The level the screen should display
     * @param bus       The eventBus
     * @param processor The input processor
     */
    public GameScreen(ViewableLevel level, EventBus bus, InputProcessor processor) {
        Gdx.graphics.setForegroundFPS(60);
        Gdx.input.setInputProcessor(processor);

        this.level = level;
        eventBus = bus;

        gameCam = new OrthographicCamera();
        gamePort = new FillViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, gameCam);

        texturePack = new TexturePack();

        batch = new SpriteBatch();
        batchHud = new SpriteBatch();

        BackgroundLayers = new Stage(new ScreenViewport());
        Array<Texture> layers = new Array<>();
        addBackgroundLayers(layers);
        Background background = new Background(layers);
        BackgroundLayers.addActor(background);
        hud = new Hud(batch, level, texturePack);
    }

    @Override
    public void show() {
        gameCam.zoom = 3f;
        updateCamToPlayer();

    }

    @Override
    public void render(float delta) {
        eventBus.post(new EventStep(delta));
        updateCamToPlayer();

        ViewablePlayerModel player = level.getViewablePlayer();

        ScreenUtils.clear(0, 0, 0, 0);

        gamePort.apply();

        BackgroundLayers.act();
        BackgroundLayers.draw();
        batch.begin();
        renderTiles(level.getBackgroundTiles(), 0.4f);
        renderTiles(level.getForegroundTiles(), 1);
        renderItems();
        // Draws the player
        batch.draw(
                texturePack.getPlayerTexture(player.getState(), Gdx.graphics.getDeltaTime()),
                player.getX(),
                player.getY(),
                player.getWidth(),
                player.getHeight()
        );

        batch.end();

        hud.getViewport().apply();
        hud.draw();
    }

    private void updateCamToPlayer() {
        ViewablePlayerModel p = level.getViewablePlayer();
        gameCam.position.set(p.getX() + p.getWidth() / 2, p.getY() + p.getHeight() / 2, 0);
        batch.setProjectionMatrix(gameCam.combined);
        batchHud.setProjectionMatrix(gameCam.combined);
        gameCam.update();
    }

    private void renderTiles(List<ViewableTile> tiles, float alpha) {
        for (ViewableTile tile : tiles) {
            renderTile(tile, alpha);
        }
    }

    private void renderTile(ViewableTile tile, float alpha) {
        TextureRegion tileTexture = texturePack.getTileTexture(tile);
        if (tileTexture != null) {
            batch.setColor(alpha, alpha, alpha, 1);
            batch.draw(tileTexture, tile.getX(), tile.getY(), tile.getWidth(), tile.getHeight());
        }
    }


    private void renderItems() {
        for (ViewableItem item : level.getItems()) {
            renderItem(item);
        }
    }

    private void renderItem(ViewableItem item) {
        TextureRegion itemTexture = texturePack.getItemTexture(item);
        if (itemTexture != null) {
            batch.draw(itemTexture, item.getX(), item.getY(), item.getWidth(), item.getHeight());
        }
    }

    private void addBackgroundLayers(Array<Texture> textures) {
        for (int i = 1; i <= 6; i++) {
            textures.add(new Texture(Gdx.files.internal("Backgrounds/background_00" + i + ".png")));
        }
    }


    @Override
    public void dispose() {
        batch.dispose();
        eventBus.post(new EventDispose());
    }

    @Override
    public void resize(int width, int height) {
        gamePort.update(width, height);
        hud.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

}

