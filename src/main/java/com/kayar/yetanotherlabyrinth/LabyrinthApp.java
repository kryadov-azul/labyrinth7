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
import javafx.util.Duration;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import static com.almasb.fxgl.dsl.FXGL.*;

public class LabyrinthApp extends GameApplication {

    private static LabyrinthApp instance;

    public LabyrinthApp() {
        instance = this;
    }

    private static final int TILE = 64;
    private static final int W = 11; // must be odd
    private static final int H = 11; // must be odd

    private boolean[][] maze;
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

    // Media player for main menu background music
    private MediaPlayer menuPlayer;

    // Media player for in-game background music
    private MediaPlayer gamePlayer;

    // Timeline for exit animation
    private Timeline exitAnim;

    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private final double mouseSensitivity = 0.2; // degrees per pixel (both axes)
    
    // Level counter for roguelike progression
    private int currentLevel = 0;
    // Timestamp when current level started
    private long levelStartMillis = 0;

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

        int worldW = W * TILE;
        int worldH = H * TILE;

        // Build JavaFX 3D world
        Group root3D = new Group();

        double floorThickness = 4;
        PhongMaterial floorMat = new PhongMaterial();
        floorMat.setDiffuseMap(image("floor-1.png"));
        Box floor = new Box(worldW, floorThickness, worldH);
        floor.setMaterial(floorMat);
        floor.setTranslateX(worldW / 2.0);
        floor.setTranslateY(0);
        floor.setTranslateZ(worldH / 2.0);
        root3D.getChildren().add(floor);

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
        String[] wallTextures = {"wall-1.png", "wall-2.png", "wall-3.png"};
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
        fpControl = new FirstPerson3DControl(maze, TILE, camera, spawn, exitCenter);
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
        var hint = FXGL.getUIFactoryService().newText("WASD to move, Q/E to turn, Space to jump. Mouse to look. Find the exit.", Color.WHITE, 18);
        hint.setTranslateX(20);
        hint.setTranslateY(30);
        getGameScene().addUINode(hint);

        // Build minimap overlay
        buildMinimap();

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

    @Override
    protected void onUpdate(double tpf) {
        // Draw dynamic player marker on the minimap
        if (minimapOverlay == null || fpControl == null) return;
        GraphicsContext go = minimapOverlay.getGraphicsContext2D();
        go.clearRect(0, 0, minimapOverlay.getWidth(), minimapOverlay.getHeight());

        double px = (fpControl.getX() / TILE) * cellPx;
        double py = (fpControl.getZ() / TILE) * cellPx;
        double r = Math.max(2.0, cellPx * 0.35);

        go.setFill(Color.RED);
        go.fillOval(px - r/2.0, py - r/2.0, r, r);
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

    public static void main(String[] args) {
        launch(args);
    }
}
