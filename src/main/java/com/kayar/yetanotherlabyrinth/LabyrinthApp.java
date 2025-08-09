package com.kayar.yetanotherlabyrinth;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.input.UserAction;
import javafx.geometry.Point2D;
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

import static com.almasb.fxgl.dsl.FXGL.*;

public class LabyrinthApp extends GameApplication {

    private static final int TILE = 64;
    private static final int W = 31; // must be odd
    private static final int H = 31; // must be odd

    private boolean[][] maze;
    private FirstPerson3DControl fpControl;
    private PerspectiveCamera camera;

    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private final double mouseSensitivity = 0.2; // degrees per pixel (both axes)

    private Point2D cellCenter(int gx, int gy) {
        return new Point2D(gx * TILE + TILE / 2.0, gy * TILE + TILE / 2.0);
    }

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setTitle("Labyrinth 7");
        settings.setVersion("1.0");
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setMainMenuEnabled(true);
        settings.setGameMenuEnabled(true);
    }

    @Override
    protected void initGame() {
        // Generate maze
        maze = MazeGenerator.generate(W, H, 0);

        int worldW = W * TILE;
        int worldH = H * TILE;

        // Build JavaFX 3D world
        Group root3D = new Group();

        double floorThickness = 4;
        PhongMaterial floorMat = new PhongMaterial(Color.DARKSLATEGRAY);
        Box floor = new Box(worldW, floorThickness, worldH);
        floor.setMaterial(floorMat);
        floor.setTranslateX(worldW / 2.0);
        floor.setTranslateY(0);
        floor.setTranslateZ(worldH / 2.0);
        root3D.getChildren().add(floor);

        // Ambient light
        root3D.getChildren().add(new AmbientLight(Color.color(0.6, 0.6, 0.6)));

        double wallHeight = TILE * 1.8;
        PhongMaterial wallMat1 = new PhongMaterial(Color.DARKSLATEBLUE);
        PhongMaterial wallMat2 = new PhongMaterial(Color.SLATEBLUE);

        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (maze[x][y]) {
                    Box wall = new Box(TILE, wallHeight, TILE);
                    wall.setMaterial(((x + y) % 2 == 0) ? wallMat1 : wallMat2);
                    wall.setTranslateX(x * TILE + TILE / 2.0);
                    wall.setTranslateY(-wallHeight / 2.0);
                    wall.setTranslateZ(y * TILE + TILE / 2.0);
                    root3D.getChildren().add(wall);
                }
            }
        }

        // Exit marker
        int exitGX = W - 2;
        int exitGY = H - 2;
        Point2D exitCenter = cellCenter(exitGX, exitGY);
        PhongMaterial exitMat = new PhongMaterial(Color.LIMEGREEN);
        double exitH = wallHeight * 0.6;
        Box exitBox = new Box(TILE * 0.8, exitH, TILE * 0.8);
        exitBox.setMaterial(exitMat);
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

        // Controller entity (no visual)
        Point2D spawn = cellCenter(1, 1);
        fpControl = new FirstPerson3DControl(maze, TILE, camera, spawn, exitCenter);
        entityBuilder()
                .type(EntityType.PLAYER)
                .with(fpControl)
                .buildAndAttach();

        // Mouse look: adjust yaw (X) and pitch (Y) based on mouse movement over the 3D subscene
        subScene.setOnMouseEntered(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            subScene.requestFocus();
        });
        subScene.setOnMouseExited(e -> { lastMouseX = Double.NaN; lastMouseY = Double.NaN; });
        subScene.setOnMouseMoved(e -> handleMouse(e.getX(), e.getY()));
        subScene.setOnMouseDragged(e -> handleMouse(e.getX(), e.getY()));

        // UI hint
        var hint = FXGL.getUIFactoryService().newText("WASD to move, Q/E to turn. Mouse to look. Find the exit.", Color.WHITE, 18);
        hint.setTranslateX(20);
        hint.setTranslateY(30);
        getGameScene().addUINode(hint);
    }

    private void handleMouse(double x, double y) {
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
    }

    public static void main(String[] args) {
        launch(args);
    }
}
