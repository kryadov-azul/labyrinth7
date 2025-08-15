package com.kayar.yetanotherlabyrinth;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import com.almasb.fxgl.app.scene.SceneFactory;
import com.almasb.fxgl.texture.Texture;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.SubScene;
import javafx.scene.PerspectiveCamera;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.AmbientLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.Cursor;
import javafx.scene.robot.Robot;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.text.FontSmoothingType;
import javafx.util.Duration;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import static com.almasb.fxgl.dsl.FXGL.*;

public class LabyrinthApp extends GameApplication {

    private static LabyrinthApp instance;

    // Gameplay options
    private final BooleanProperty showMinimap = new SimpleBooleanProperty(true);
    private boolean minimapListenerInstalled = false;

    public LabyrinthApp() {
        instance = this;
    }

    public static LabyrinthApp getInstance() {
        return instance;
    }

    private static final int TILE = 64;
    private static final int W = 11; // must be odd
    private static final int H = 11; // must be odd

    private boolean[][] maze;
    private boolean[][] pits;
    private FirstPerson3DControl fpControl;
    private PerspectiveCamera camera;

    // Mouse capture state for pointer-lock behavior
    private Robot robot;
    private boolean captureMouse = true;
    private boolean isRecentering = false;
    private SubScene subScene3D;

    // Minimap
    private Canvas minimapStatic;
    private Canvas minimapOverlay;
    private StackPane minimapNode;
    private int gridW;
    private int gridH;
    private double cellPx;
    private double minimapW;
    private double minimapH;
    private int exitGX;
    private int exitGY;

    // Key state
    private int keyGX = -1;
    private int keyGY = -1;
    private boolean keyTaken = false;
    private int keyFrameIndex = 0;
    private Box keyBox3D;
    private ImageView keyIconView;
    private Image keyFrameImage;

    // Media player for main menu background music
    private MediaPlayer menuPlayer;

    // Media player for in-game background music
    private MediaPlayer gamePlayer;

    // Timeline for exit animation
    private Timeline exitAnim;
    // Timeline for key bobbing animation
    private Timeline keyHoverAnim;

    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private final double mouseSensitivity = 0.2; // degrees per pixel (both axes)
    
    // Level counter for roguelike progression
    private int currentLevel = 0;
    // Timestamp when current level started
    private long levelStartMillis = 0;

    // Player health system
    private int playerHealth = 100; // percent
    private boolean playerDead = false;
    private Canvas healthBarCanvas;
    private double healthBarWidth = 300;
    private double healthBarHeight = 10;

    private Point2D cellCenter(int gx, int gy) {
        return new Point2D(gx * TILE + TILE / 2.0, gy * TILE + TILE / 2.0);
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("-=Yet Another Labyrinth=-");
        settings.setVersion("1.0");
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setMainMenuEnabled(true);
        settings.setGameMenuEnabled(true);
        // Allow fullscreen toggle from FXGL settings menu
        settings.setFullScreenAllowed(true);
        // Start in full screen so window size equals screen size
        settings.setFullScreenFromStart(true);

        // Customize main menu to add centered background image
        final SceneFactory baseFactory = new SceneFactory();
        settings.setSceneFactory(new SceneFactory() {
            @Override
            public FXGLMenu newMainMenu() {
                LabyrinthApp.resetLevelCounter();
                //
                FXGLMenu menu = baseFactory.newMainMenu();

                // Load our image from /assets/textures and center it
                Texture img = texture("main-menu-lab.png");

                // Scale to 1/3 of screen while preserving aspect ratio
                double boxW = getAppWidth() / 3.0;
                double boxH = getAppHeight() / 3.0;
                img.setPreserveRatio(true);
                img.setFitWidth(boxW);
                img.setFitHeight(boxH);

                // Compute displayed size for centering based on intrinsic image ratio
                double imgW0 = img.getImage().getWidth();
                double imgH0 = img.getImage().getHeight();
                double scale = Math.min(boxW / imgW0, boxH / imgH0);
                double dispW = imgW0 * scale;
                double dispH = imgH0 * scale;

                double x = (getAppWidth() - dispW) / 2.0;
                double y = (getAppHeight() - dispH) / 2.0;
                img.setTranslateX(x);
                img.setTranslateY(y);

                // Add on top so it renders above default background; don't intercept mouse
                img.setMouseTransparent(true);
                menu.getContentRoot().getChildren().add(img);

                // Ensure level is reset whenever main menu is shown (not only on creation)
                menu.getContentRoot().sceneProperty().addListener((obs, oldScene, newScene) -> {
                    if (newScene != null) {
                        LabyrinthApp.resetLevelCounter();
                        stopGameMusic();
                        startMenuMusic();
                    }
                });

                return menu;
            }

            @Override
            public FXGLMenu newGameMenu() {
                FXGLMenu menu = baseFactory.newGameMenu();

                CheckBox cbMinimap = new CheckBox("Show minimap");
                cbMinimap.setSelected(showMinimap.get());
                cbMinimap.setTextFill(Color.WHITE);
                // Bind both ways so changes reflect in UI and logic
                cbMinimap.selectedProperty().bindBidirectional(showMinimap);

                // Place it near top-left of the game menu content
                cbMinimap.setTranslateX(40);
                cbMinimap.setTranslateY(220);

                addControlToGameMenu(menu, cbMinimap);
                return menu;
            }
        });
    }

    @Override
    protected void onPreInit() {
        // Before game initialization, ensure in-game music is stopped and start menu music
        stopGameMusic();
        startMenuMusic();
    }

    private void startMenuMusic() {
        try {
            if (menuPlayer != null) {
                // already prepared/playing
                return;
            }
            var url = getClass().getResource("/assets/sounds/menu.mp3");
            if (url == null) {
                System.out.println("[DEBUG_LOG] Menu music resource not found: /assets/sounds/menu.mp3");
                return;
            }
            Media media = new Media(url.toExternalForm());
            menuPlayer = new MediaPlayer(media);
            menuPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            menuPlayer.setVolume(0.5);
            menuPlayer.setOnError(() -> System.out.println("[DEBUG_LOG] Menu MediaPlayer error: " + menuPlayer.getError()));
            menuPlayer.play();
            System.out.println("[DEBUG_LOG] Menu music started.");
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Failed to start menu music: " + e.getMessage());
        }
    }

    private void stopMenuMusic() {
        try {
            if (menuPlayer != null) {
                menuPlayer.stop();
                menuPlayer.dispose();
                menuPlayer = null;
                System.out.println("[DEBUG_LOG] Menu music stopped.");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Failed to stop menu music: " + e.getMessage());
        }
    }

    private void startGameMusic() {
        try {
            if (gamePlayer != null) {
                // already prepared/playing
                return;
            }
            var url = getClass().getResource("/assets/sounds/in-game.mp3");
            if (url == null) {
                System.out.println("[DEBUG_LOG] In-game music resource not found: /assets/sounds/in-game.mp3");
                return;
            }
            Media media = new Media(url.toExternalForm());
            gamePlayer = new MediaPlayer(media);
            gamePlayer.setCycleCount(MediaPlayer.INDEFINITE);
            gamePlayer.setVolume(0.5);
            gamePlayer.setOnError(() -> System.out.println("[DEBUG_LOG] Game MediaPlayer error: " + gamePlayer.getError()));
            gamePlayer.play();
            System.out.println("[DEBUG_LOG] In-game music started.");
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Failed to start in-game music: " + e.getMessage());
        }
    }

    private void stopGameMusic() {
        try {
            if (gamePlayer != null) {
                gamePlayer.stop();
                gamePlayer.dispose();
                gamePlayer = null;
                System.out.println("[DEBUG_LOG] In-game music stopped.");
            }
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Failed to stop in-game music: " + e.getMessage());
        }
    }

    @Override
    protected void initGame() {
        // Stop menu music if it's playing since we are starting the game now
        stopMenuMusic();
        // Start in-game music
        startGameMusic();

        // Stop previous exit animation if any
        if (exitAnim != null) {
            try { exitAnim.stop(); } catch (Exception ignored) {}
            exitAnim = null;
        }
        // Stop previous key hover animation if any
        if (keyHoverAnim != null) {
            try { keyHoverAnim.stop(); } catch (Exception ignored) {}
            keyHoverAnim = null;
        }

        // Prepare new level UI and state
        getGameScene().clearUINodes();
        currentLevel++;
        levelStartMillis = System.currentTimeMillis();

        // Compute labyrinth size for this level: starting at 10x10 blocks, +2 each level
        int blocks = 4 + 2 * (currentLevel - 1);
        int W = blocks * 2 + 1; // generator grid must be odd
        int H = blocks * 2 + 1;

        // Select maze algorithm rotating each level
        MazeGenerator.Algorithm[] algOrder = new MazeGenerator.Algorithm[] {
                MazeGenerator.Algorithm.BACKTRACKER,
                MazeGenerator.Algorithm.WILSON,
                MazeGenerator.Algorithm.KRUSKAL,
                MazeGenerator.Algorithm.PRIM,
                MazeGenerator.Algorithm.ALDOUS_BRODER,
                MazeGenerator.Algorithm.ELLER
        };
        MazeGenerator.Algorithm alg = algOrder[(currentLevel - 1) % algOrder.length];
        System.out.println("[DEBUG_LOG] Generating maze with algorithm: " + alg + " for level " + currentLevel);
        // Generate maze
        maze = MazeGenerator.generate(W, H, 0, alg);
        // store grid size for minimap
        this.gridW = W;
        this.gridH = H;

        // Generate pits on walkable cells (exclude spawn and exit)
        pits = new boolean[W][H];
        double pitChance = Math.min(0.06 + (currentLevel - 1) * 0.01, 0.12); // scale slightly with level
        pitChance *= 0.5; // decrease counts of pits by half
        int spawnGX = 1, spawnGY = 1;
        int exitGXLocal = W - 2, exitGYLocal = H - 2;
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (maze[x][y]) continue; // no pits in walls

                // Exclude spawn, exit and the other two walkable corner cells of the labyrinth
                boolean isCornerCell = (x == 1 && y == 1) || (x == W - 2 && y == H - 2) ||
                        (x == W - 2 && y == 1) || (x == 1 && y == H - 2);
                if (isCornerCell) continue;

                if (Math.random() < pitChance) {
                    boolean nearby = false;
                    for (int dx = -1; dx <= 1 && !nearby; dx++) {
                        for (int dy = -1; dy <= 1 && !nearby; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                            if (pits[nx][ny]) {
                                nearby = true; // prevent pits from being placed near each other (8-neighborhood)
                            }
                        }
                    }
                    if (!nearby) {
                        pits[x][y] = true;
                    }
                }
            }
        }

        int worldW = W * TILE;
        int worldH = H * TILE;

        // Build JavaFX 3D world
        Group root3D = new Group();

        double floorThickness = 4;
        PhongMaterial floorMat = new PhongMaterial();
        if (currentLevel % 2 == 1) {
            floorMat.setDiffuseMap(image("floor-1.png"));
        } else {
            floorMat.setDiffuseMap(image("floor-2.png"));
        }
        Box floor = new Box(worldW, floorThickness, worldH);
        floor.setMaterial(floorMat);
        floor.setTranslateX(worldW / 2.0);
        floor.setTranslateY(0);
        floor.setTranslateZ(worldH / 2.0);
        root3D.getChildren().add(floor);

        // Add pit overlays on the floor with random textures (pit1 or pit2)
        if (pits != null) {
            PhongMaterial pitMat1 = new PhongMaterial();
            pitMat1.setDiffuseMap(image("pit1.png"));
            PhongMaterial pitMat2 = new PhongMaterial();
            pitMat2.setDiffuseMap(image("pit2.png"));

            double overlayH = 0.2; // very thin so it looks like drawn on the floor
            double epsilon = 0.02; // avoid z-fighting by moving slightly above floor top (negative Y is up)
            double overlayY = -floorThickness / 2.0 - overlayH / 2.0 - epsilon;

            for (int x = 0; x < W; x++) {
                for (int y = 0; y < H; y++) {
                    if (!maze[x][y] && pits[x][y]) {
                        Box pitOverlay = new Box(TILE, overlayH, TILE);
                        // choose random texture for this pit
                        pitOverlay.setMaterial(Math.random() < 0.5 ? pitMat1 : pitMat2);
                        pitOverlay.setTranslateX(x * TILE + TILE / 2.0);
                        pitOverlay.setTranslateY(overlayY);
                        pitOverlay.setTranslateZ(y * TILE + TILE / 2.0);
                        root3D.getChildren().add(pitOverlay);
                    }
                }
            }
        }

        // Ambient light
        root3D.getChildren().add(new AmbientLight(Color.color(0.6, 0.6, 0.6)));

        double wallHeight = TILE * 1.8;

        // Ceiling with sky texture
        PhongMaterial skyMat = new PhongMaterial();
        skyMat.setDiffuseMap(image("sky-3.png"));
        double ceilingThickness = 4;
        Box ceiling = new Box(worldW, ceilingThickness, worldH);
        ceiling.setMaterial(skyMat);
        ceiling.setTranslateX(worldW / 2.0);
        ceiling.setTranslateY(-wallHeight);
        ceiling.setTranslateZ(worldH / 2.0);
        root3D.getChildren().add(ceiling);

        // Wall materials
        String[] wallTextures = {"wall-1.png", "wall-2.png", "wall-3.png", "wall-4.png", "wall-5.png"};
        PhongMaterial[] wallMats = new PhongMaterial[wallTextures.length];
        for (int i = 0; i < wallMats.length; i++) {
            wallMats[i] = new PhongMaterial();
            wallMats[i].setDiffuseMap(image(wallTextures[i]));
        }
        int wallIndex = (currentLevel-1) % wallMats.length;

        var thisLevelWall = wallMats[wallIndex];
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (maze[x][y]) {
                    Box wall = new Box(TILE, wallHeight, TILE);
                    wall.setMaterial(thisLevelWall);
                    wall.setTranslateX(x * TILE + TILE / 2.0);
                    wall.setTranslateY(-wallHeight / 2.0);
                    wall.setTranslateZ(y * TILE + TILE / 2.0);
                    root3D.getChildren().add(wall);
                }
            }
        }

        // Exit marker with animated texture
        this.exitGX = W - 2;
        this.exitGY = H - 2;
        Point2D exitCenter = cellCenter(this.exitGX, this.exitGY);

        double exitH = wallHeight * 0.6;
        Box exitBox = new Box(TILE * 0.8, exitH, TILE * 0.8);

        // Load spritesheet and slice into 5 frames (40x40) horizontally
        Image sheet = image("exit.png"); // From /assets/textures
        int frameW = 32, frameH = 40, framesCount = 5;
        PixelReader pr = sheet.getPixelReader();
        WritableImage[] frames = new WritableImage[framesCount];
        for (int i = 0; i < framesCount; i++) {
            frames[i] = new WritableImage(pr, i * frameW, 0, frameW, frameH);
        }
        PhongMaterial exitMat = new PhongMaterial();
        exitMat.setDiffuseMap(frames[0]);
        exitMat.setSpecularColor(Color.WHITE);
        exitBox.setMaterial(exitMat);

        // Animate frames with short delay to emulate animation
        final int[] idx = {0};
        exitAnim = new Timeline(new KeyFrame(Duration.millis(150), e2 -> {
            idx[0] = (idx[0] + 1) % framesCount;
            exitMat.setDiffuseMap(frames[idx[0]]);
        }));
        exitAnim.setCycleCount(Timeline.INDEFINITE);
        exitAnim.play();

        exitBox.setTranslateX(exitCenter.getX());
        exitBox.setTranslateY(-exitH / 2.0);
        exitBox.setTranslateZ(exitCenter.getY());
        root3D.getChildren().add(exitBox);

        // Key placement and rendering
        keyTaken = false;
        keyBox3D = null;
        keyIconView = null;
        keyFrameIndex = (currentLevel - 1) % 4;
        Image keySheet = image("keys.png");
        PixelReader kpr = keySheet.getPixelReader();
        int kFrameW = 32, kFrameH = 32;
        keyFrameImage = new WritableImage(kpr, keyFrameIndex * kFrameW, 0, kFrameW, kFrameH);

        // pick a random walkable, non-pit tile that isn't spawn or exit or other corners
        java.util.List<int[]> candidates = new java.util.ArrayList<>();
        for (int gx = 0; gx < W; gx++) {
            for (int gy = 0; gy < H; gy++) {
                if (maze[gx][gy]) continue; // wall
                if (pits[gx][gy]) continue; // avoid pits
                boolean isCorner = (gx == 1 && gy == 1) || (gx == W-2 && gy == H-2) || (gx == W-2 && gy == 1) || (gx == 1 && gy == H-2);
                if (isCorner) continue;
                if ((gx == 1 && gy == 1) || (gx == exitGX && gy == exitGY)) continue;
                candidates.add(new int[]{gx, gy});
            }
        }
        if (!candidates.isEmpty()) {
            int idxC = (int) Math.floor(Math.random() * candidates.size());
            int[] pick = candidates.get(idxC);
            keyGX = pick[0];
            keyGY = pick[1];
            Point2D keyCenter = cellCenter(keyGX, keyGY);

            // Create an upright key billboard and position it mid-air with bobbing animation
            double keyW = TILE * 0.7;
            double keyH = TILE * 0.7;
            double keyD = TILE * 0.12;
            keyBox3D = new Box(keyW, keyH, keyD);
            PhongMaterial keyMat = new PhongMaterial();
            keyMat.setDiffuseMap(keyFrameImage);
            keyBox3D.setMaterial(keyMat);
            keyBox3D.setTranslateX(keyCenter.getX());
            // Place key near the floor and keep gentle bobbing without intersecting the floor
            double floorTopY = -floorThickness / 2.0;
            double baseY = floorTopY - TILE * 0.48; // a bit above the floor
            keyBox3D.setTranslateY(baseY);
            keyBox3D.setTranslateZ(keyCenter.getY());
            root3D.getChildren().add(keyBox3D);

            double amp = TILE * 0.04; // smaller amplitude to stay above the floor
            keyHoverAnim = new Timeline(
                    new KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(keyBox3D.translateYProperty(), baseY - amp)),
                    new KeyFrame(Duration.seconds(1.6), new javafx.animation.KeyValue(keyBox3D.translateYProperty(), baseY + amp))
            );
            keyHoverAnim.setAutoReverse(true);
            keyHoverAnim.setCycleCount(Timeline.INDEFINITE);
            keyHoverAnim.play();
        } else {
            keyGX = -1; keyGY = -1;
        }

        // Camera and 3D subscene
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(10000);
        camera.setFieldOfView(65);

        SubScene subScene = new SubScene(root3D, getAppWidth(), getAppHeight(), true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.setCamera(camera);
        subScene.setCursor(Cursor.NONE); // hide cursor for FPS feel
        subScene.setFocusTraversable(true);
        getGameScene().addUINode(subScene);
        this.subScene3D = subScene;

        // Controller entity (no visual)
        Point2D spawn = cellCenter(1, 1);
        Point2D keyCenterAll = (keyGX >= 0 ? cellCenter(keyGX, keyGY) : null);
        fpControl = new FirstPerson3DControl(maze, pits, TILE, camera, spawn, exitCenter, keyCenterAll);
        entityBuilder()
                .type(EntityType.PLAYER)
                .with(fpControl)
                .buildAndAttach();

        // Mouse look: adjust yaw (X) and pitch (Y) based on mouse movement over the 3D subscene
        subScene.setOnMouseEntered(e -> {
            subScene.requestFocus();
            if (captureMouse) {
                centerCursor();
            } else {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }
        });
        subScene.setOnMouseExited(e -> { lastMouseX = Double.NaN; lastMouseY = Double.NaN; });
        if(System.getProperty("os.name", "generic").toLowerCase().contains("mac")) {
            subScene.setOnMouseMoved(e -> handleMouseMac(e.getX(), e.getY()));
            subScene.setOnMouseDragged(e -> handleMouseMac(e.getX(), e.getY()));
        } else {
            subScene.setOnMouseMoved(e -> handleMouse(e.getX(), e.getY()));
            subScene.setOnMouseDragged(e -> handleMouse(e.getX(), e.getY()));
        }

        // UI hint
        var hint = FXGL.getUIFactoryService().newText("WASD to move, Q/E to turn, Space to jump. Mouse to look. Avoid pits: jump over or fall! Find the exit.", Color.WHITE, 18);
        hint.setTranslateX(20);
        hint.setTranslateY(30);
        getGameScene().addUINode(hint);

        // Init health system
        playerDead = false;
        playerHealth = 100;
        initHealthUI();
        drawHealthBar();

        // Build minimap overlay
        if (!minimapListenerInstalled) {
            showMinimap.addListener((obs, wasShown, isShown) -> {
                if (isShown != null && isShown) {
                    buildMinimap();
                } else {
                    removeMinimap();
                }
            });
            minimapListenerInstalled = true;
        }
        if (showMinimap.get()) {
            buildMinimap();
        }

        // Display level start message and ensure focus
        Platform.runLater(() -> {
            getNotificationService().pushNotification("Level " + currentLevel + ": " + alg);
            // Request focus to ensure input works after level load
            getGameScene().getRoot().requestFocus();
        });
    }

    private void buildMinimap() {
        try {
            // Remove previous minimap if exists
            if (minimapNode != null) {
                getGameScene().removeUINode(minimapNode);
                minimapNode = null;
            }

            if (maze == null || gridW <= 0 || gridH <= 0) return;

            double screenArea = getAppWidth() * getAppHeight();
            double maxArea = screenArea / 48.0; // must not exceed 1/12 of screen
            // Derive pixels per cell from area constraint
            cellPx = Math.sqrt(Math.max(1.0, maxArea) / (gridW * gridH));
            // Avoid too tiny rendering
            if (cellPx < 1.0) cellPx = 1.0;

            minimapW = gridW * cellPx;
            minimapH = gridH * cellPx;

            minimapStatic = new Canvas(minimapW, minimapH);
            minimapOverlay = new Canvas(minimapW, minimapH);

            // Draw static background and walls
            GraphicsContext gs = minimapStatic.getGraphicsContext2D();
            gs.setFill(Color.color(0, 0, 0, 0.45));
            gs.fillRect(0, 0, minimapW, minimapH);

            // draw walls
            gs.setFill(Color.LIGHTGRAY);
            for (int x = 0; x < gridW; x++) {
                for (int y = 0; y < gridH; y++) {
                    if (maze[x][y]) {
                        gs.fillRect(x * cellPx, y * cellPx, cellPx, cellPx);
                    }
                }
            }
            // draw pits on walkable tiles
            if (pits != null) {
                gs.setFill(Color.DARKRED);
                for (int x = 0; x < gridW; x++) {
                    for (int y = 0; y < gridH; y++) {
                        if (!maze[x][y] && pits[x][y]) {
                            gs.fillRect(x * cellPx, y * cellPx, cellPx, cellPx);
                        }
                    }
                }
            }
            // Highlight exit cell
            if (exitGX >= 0 && exitGY >= 0) {
                gs.setFill(Color.LIMEGREEN);
                gs.fillRect(exitGX * cellPx, exitGY * cellPx, cellPx, cellPx);
            }
            // Border
            gs.setStroke(Color.color(1,1,1,0.8));
            gs.setLineWidth(Math.max(1.0, cellPx * 0.08));
            gs.strokeRect(0.5, 0.5, minimapW - 1, minimapH - 1);

            minimapNode = new StackPane(minimapStatic, minimapOverlay);
            minimapNode.setMouseTransparent(true);
            minimapNode.setPickOnBounds(false);

            double margin = 10;
            minimapNode.setTranslateX(getAppWidth() - minimapW - margin);
            minimapNode.setTranslateY(margin);

            getGameScene().addUINode(minimapNode);
        } catch (Exception ex) {
            System.out.println("[DEBUG_LOG] Failed to build minimap: " + ex.getMessage());
        }
    }

    private void removeMinimap() {
        try {
            if (minimapNode != null) {
                getGameScene().removeUINode(minimapNode);
            }
        } catch (Exception ignored) {}
        minimapNode = null;
        minimapOverlay = null;
        minimapStatic = null;
    }

    @Override
    protected void onUpdate(double tpf) {
        // Make the key billboard always face the player (camera)
        if (keyBox3D != null && camera != null) {
            double dx = camera.getTranslateX() - keyBox3D.getTranslateX();
            double dz = camera.getTranslateZ() - keyBox3D.getTranslateZ();
            double angleY = Math.toDegrees(Math.atan2(dx, dz));
            keyBox3D.setRotationAxis(javafx.scene.transform.Rotate.Y_AXIS);
            keyBox3D.setRotate(angleY);
        }

        // Draw dynamic markers on the minimap
        if (minimapOverlay != null && fpControl != null) {
            GraphicsContext go = minimapOverlay.getGraphicsContext2D();
            go.clearRect(0, 0, minimapOverlay.getWidth(), minimapOverlay.getHeight());

            // draw key marker if not yet taken
            if (!keyTaken && keyGX >= 0 && keyGY >= 0) {
                go.setFill(Color.BLUE);
                go.fillRect(keyGX * cellPx, keyGY * cellPx, cellPx, cellPx);
            }

            double px = (fpControl.getX() / TILE) * cellPx;
            double py = (fpControl.getZ() / TILE) * cellPx;
            double r = Math.max(2.0, cellPx * 0.35);

            go.setFill(Color.RED);
            go.fillOval(px - r / 2.0, py - r / 2.0, r, r);
        }
    }

    public void onKeyPicked() {
        keyTaken = true;
        // Stop key hover animation before removing the node
        try {
            if (keyHoverAnim != null) {
                keyHoverAnim.stop();
                keyHoverAnim = null;
            }
        } catch (Exception ignored) { }
        try {
            if (keyBox3D != null) {
                if (keyBox3D.getParent() instanceof Group) {
                    ((Group) keyBox3D.getParent()).getChildren().remove(keyBox3D);
                }
                keyBox3D = null;
            }
        } catch (Exception ignored) { }

        if (keyIconView == null && keyFrameImage != null) {
            keyIconView = new ImageView(keyFrameImage);
            keyIconView.setFitWidth(32);
            keyIconView.setFitHeight(32);
            double margin = 10;
            keyIconView.setTranslateX(margin);
            keyIconView.setTranslateY(getAppHeight() - keyIconView.getFitHeight() - margin);
            getGameScene().addUINode(keyIconView);
        }
    }

    private void handleMouse(double x, double y) {
        if (captureMouse) {
            if (subScene3D == null) return;
            if (isRecentering) {
                // Ignore the synthetic event caused by Robot.mouseMove
                isRecentering = false;
                return;
            }
            double centerX = subScene3D.getWidth() / 2.0;
            double centerY = subScene3D.getHeight() / 2.0;
            double dx = x - centerX;
            double dy = y - centerY;
            if (fpControl != null) {
                fpControl.addYaw(dx * mouseSensitivity);
                fpControl.addPitch(-dy * mouseSensitivity); // invert Y for natural look
            }
            centerCursor();
            return;
        }

        // Non-capture mode: use relative movement based on last mouse position
        if (Double.isNaN(lastMouseX) || Double.isNaN(lastMouseY)) {
            lastMouseX = x;
            lastMouseY = y;
            return;
        }
        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        lastMouseX = x;
        lastMouseY = y;
        if (fpControl != null) {
            fpControl.addYaw(dx * mouseSensitivity);
            fpControl.addPitch(-dy * mouseSensitivity); // invert Y for natural look
        }
    }

    private void handleMouseMac(double x, double y) {
        if (captureMouse) {
            if (subScene3D == null) return;

            if (isRecentering) {
                // Synthetic event from Robot, ignore movement, update last known pos
                isRecentering = false;
                lastMouseX = x;
                lastMouseY = y;
                return;
            }

            // Standard relative mouse movement calculation
            if (Double.isNaN(lastMouseX) || Double.isNaN(lastMouseY)) {
                lastMouseX = x;
                lastMouseY = y;
                return;
            }
            double dx = x - lastMouseX;
            double dy = y - lastMouseY;
            lastMouseX = x;
            lastMouseY = y;

            if (fpControl != null) {
                fpControl.addYaw(dx * mouseSensitivity);
                fpControl.addPitch(-dy * mouseSensitivity); // invert Y for natural look
            }

            // Recenter cursor only if it's near the edge of the subscene
            double margin = 10; // pixels from edge
            if (x < margin || x > subScene3D.getWidth() - margin ||
                    y < margin || y > subScene3D.getHeight() - margin) {
                centerCursor();
            }
            return;
        }

        // Non-capture mode: use relative movement based on last mouse position
        if (Double.isNaN(lastMouseX) || Double.isNaN(lastMouseY)) {
            lastMouseX = x;
            lastMouseY = y;
            return;
        }
        double dx = x - lastMouseX;
        double dy = y - lastMouseY;
        lastMouseX = x;
        lastMouseY = y;
        if (fpControl != null) {
            fpControl.addYaw(dx * mouseSensitivity);
            fpControl.addPitch(-dy * mouseSensitivity); // invert Y for natural look
        }
    }

    private void centerCursor() {
        if (subScene3D == null) return;
        if (robot == null) {
            try {
                robot = new Robot();
            } catch (Exception ex) {
                // If Robot is not available, disable capture mode gracefully
                captureMouse = false;
                return;
            }
        }
        double centerX = subScene3D.getWidth() / 2.0;
        double centerY = subScene3D.getHeight() / 2.0;
        javafx.geometry.Point2D p = subScene3D.localToScreen(centerX, centerY);
        if (p != null) {
            isRecentering = true;
            robot.mouseMove(p.getX(), p.getY());
        }
    }

    @Override
    protected void initInput() {
        // Bind actions for WASD + Q/E
        getInput().addAction(new UserAction("Move Forward") {
            @Override public void onActionBegin() { fpControl.setMoveForward(true); }
            @Override public void onActionEnd() { fpControl.setMoveForward(false); }
        }, KeyCode.W);

        getInput().addAction(new UserAction("Move Backward") {
            @Override public void onActionBegin() { fpControl.setMoveBackward(true); }
            @Override public void onActionEnd() { fpControl.setMoveBackward(false); }
        }, KeyCode.S);

        getInput().addAction(new UserAction("Move Left") {
            @Override public void onActionBegin() { fpControl.setMoveLeft(true); }
            @Override public void onActionEnd() { fpControl.setMoveLeft(false); }
        }, KeyCode.A);

        getInput().addAction(new UserAction("Move Right") {
            @Override public void onActionBegin() { fpControl.setMoveRight(true); }
            @Override public void onActionEnd() { fpControl.setMoveRight(false); }
        }, KeyCode.D);

        getInput().addAction(new UserAction("Turn Left") {
            @Override public void onActionBegin() { fpControl.setTurnLeft(true); }
            @Override public void onActionEnd() { fpControl.setTurnLeft(false); }
        }, KeyCode.Q);

        getInput().addAction(new UserAction("Turn Right") {
            @Override public void onActionBegin() { fpControl.setTurnRight(true); }
            @Override public void onActionEnd() { fpControl.setTurnRight(false); }
        }, KeyCode.E);

        // Jump
        getInput().addAction(new UserAction("Jump") {
            @Override public void onActionBegin() { if (fpControl != null) fpControl.jump(); }
        }, KeyCode.SPACE);
    }

    // ---- Health System ----
    private void initHealthUI() {
        try {
            if (healthBarCanvas != null) {
                getGameScene().removeUINode(healthBarCanvas);
            }
        } catch (Exception ignored) { }
        healthBarCanvas = new Canvas(healthBarWidth, healthBarHeight);
        healthBarCanvas.setMouseTransparent(true);
        double margin = 20;
        healthBarCanvas.setTranslateX((getAppWidth() - healthBarWidth) / 2.0);
        healthBarCanvas.setTranslateY(getAppHeight() - healthBarHeight - margin);
        getGameScene().addUINode(healthBarCanvas);
    }

    private void drawHealthBar() {
        if (healthBarCanvas == null) return;
        GraphicsContext g = healthBarCanvas.getGraphicsContext2D();
        double w = healthBarWidth;
        double h = healthBarHeight;
        g.clearRect(0, 0, w, h);
        // background
        g.setFill(Color.color(0, 0, 0, 0.55));
        g.fillRoundRect(0, 0, w, h, h, h);
        // red fill proportional to health
        double p = Math.max(0, Math.min(100, playerHealth)) / 100.0;
        double fw = w * p;
        g.setFill(Color.DARKRED);
        g.setStroke(Color.DARKVIOLET);
        g.setFontSmoothingType(FontSmoothingType.LCD);
        g.setGlobalAlpha(0.8);
        g.fillRoundRect(0, 0, fw, h, h, h);
        // border
        g.setStroke(Color.color(1, 1, 1, 0.85));
        g.setLineWidth(2);
        g.strokeRoundRect(1, 1, w - 2, h - 2, h, h);
    }

    public void damagePlayer(int amount) {
        if (playerDead) return;
        if (amount < 0) amount = 0;
        playerHealth = Math.max(0, playerHealth - amount);
        drawHealthBar();
        if (playerHealth <= 0) {
            onPlayerDeath();
        }
    }

    public boolean isPlayerDead() {
        return playerDead;
    }

    public void onPlayerDeath() {
        if (playerDead) return;
        playerDead = true;
        getDialogService().showMessageBox("Game Over", () -> {
            LabyrinthApp.resetLevelCounter();
            getGameController().gotoMainMenu();
        });
    }

    public static String buildExitMessage() {
        LabyrinthApp app = instance;
        int level = (app != null) ? app.currentLevel : 0;
        long millis = (app != null) ? (System.currentTimeMillis() - app.levelStartMillis) : 0;
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String timeStr = String.format("%02d:%02d", minutes, seconds);
        return "Level " + level + " completed!\nTime: " + timeStr;
    }

    public static void resetLevelCounter() {
        if (instance != null) {
            instance.currentLevel = 0;
            instance.levelStartMillis = 0;
        }
    }

    private void addControlToGameMenu(com.almasb.fxgl.app.scene.FXGLMenu menu, javafx.scene.Node node) {
        // Try attaching to the dedicated menu content root if available (varies across FXGL versions)
        try {
            java.lang.reflect.Method m = menu.getClass().getMethod("getMenuContentRoot");
            Object root = m.invoke(menu);
            if (root instanceof javafx.scene.Parent) {
                javafx.scene.Parent parent = (javafx.scene.Parent) root;
                if (parent instanceof javafx.scene.layout.Pane) {
                    ((javafx.scene.layout.Pane) parent).getChildren().add(node);
                    return;
                } else if (parent instanceof javafx.scene.Group) {
                    ((javafx.scene.Group) parent).getChildren().add(node);
                    return;
                }
            }
        } catch (Exception ignored) { }
        // Try older/different API name
        try {
            java.lang.reflect.Method m2 = menu.getClass().getMethod("getMenuContent");
            Object root2 = m2.invoke(menu);
            if (root2 instanceof javafx.scene.Parent) {
                javafx.scene.Parent parent = (javafx.scene.Parent) root2;
                if (parent instanceof javafx.scene.layout.Pane) {
                    ((javafx.scene.layout.Pane) parent).getChildren().add(node);
                    return;
                } else if (parent instanceof javafx.scene.Group) {
                    ((javafx.scene.Group) parent).getChildren().add(node);
                    return;
                }
            } else if (root2 instanceof javafx.scene.Node) {
                // If it's some node, fallback to main root to ensure visibility
                Platform.runLater(() -> {
                    menu.getContentRoot().getChildren().add(node);
                    node.toFront();
                });
                return;
            }
        } catch (Exception ignored) { }
        // Fallback: add to main root on next pulse so it stays on top of framework's layers
        Platform.runLater(() -> {
            menu.getContentRoot().getChildren().add(node);
            node.toFront();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
