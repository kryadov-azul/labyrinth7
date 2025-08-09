package com.kayar.yetanotherlabyrinth;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.PerspectiveCamera;

/**
 * First-person 3D controller using the maze grid for collision on XZ plane.
 * - Movement: WASD (forward/back/strafe), Q/E to turn left/right (yaw).
 * - Plays footstep sounds while moving.
 * - Triggers win when close to exit (by XZ distance).
 */
public class FirstPerson3DControl extends Component {

    private final boolean[][] maze; // [w][h] true = wall
    private final int gridW;
    private final int gridH;
    private final int tile;

    private final double radius;          // collision radius
    private final double cameraHeight;    // eye height (Y)

    private final double baseSpeed = 180; // units per second
    private double speed = baseSpeed;

    private final PerspectiveCamera camera;

    private final double exitX;
    private final double exitZ;

    // position and orientation (degrees)
    private double x;
    private double z;
    private double yaw; // degrees, 0 means facing +Z
    private double pitch; // degrees, 0 means level; positive = look up

    // inputs
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean turnLeft;
    private boolean turnRight;

    private long lastStepSound = 0;
    private long stepIntervalMs = 450;

    public FirstPerson3DControl(boolean[][] maze, int tile, PerspectiveCamera camera, Point2D spawn2D, Point2D exitCenter2D) {
        this.maze = maze;
        this.gridW = maze.length;
        this.gridH = maze[0].length;
        this.tile = tile;
        this.radius = tile * 0.30;
        this.cameraHeight = tile * 0.85;
        this.camera = camera;

        this.x = spawn2D.getX();
        this.z = spawn2D.getY();
        this.yaw = 0; // facing +Z initially

        this.exitX = exitCenter2D.getX();
        this.exitZ = exitCenter2D.getY();
    }

    @Override
    public void onUpdate(double tpf) {
        // turning with Q/E (or mapped keys)
        double turnSpeed = 120; // degrees/sec
        if (turnLeft && !turnRight) yaw -= turnSpeed * tpf;
        if (turnRight && !turnLeft) yaw += turnSpeed * tpf;

        // compute forward/right vectors from yaw (XZ plane)
        double yawRad = Math.toRadians(yaw);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = -Math.cos(yawRad); // JavaFX camera looks along -Z at yaw=0
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        double vx = 0;
        double vz = 0;
        if (moveForward) { vx -= fwdX; vz -= fwdZ; }
        if (moveBackward) { vx += fwdX; vz += fwdZ; }
        if (moveLeft) { vx -= rightX; vz -= rightZ; }
        if (moveRight) { vx += rightX; vz += rightZ; }

        boolean moving = false;
        if (Math.hypot(vx, vz) > 1e-6) {
            double len = Math.hypot(vx, vz);
            vx = (vx / len) * speed * tpf;
            vz = (vz / len) * speed * tpf;
            moving = true;
        }

        // axis-separated movement for sliding along walls
        if (Math.abs(vx) > 1e-9) tryMove(vx, 0);
        if (Math.abs(vz) > 1e-9) tryMove(0, vz);

        // update camera transform (yaw + pitch)
        camera.setTranslateX(x);
        camera.setTranslateY(-cameraHeight); // negative Y so that floor at 0 is "below"
        camera.setTranslateZ(z);
        camera.getTransforms().setAll(
                new javafx.scene.transform.Rotate(yaw, javafx.scene.transform.Rotate.Y_AXIS),
                new javafx.scene.transform.Rotate(pitch, javafx.scene.transform.Rotate.X_AXIS)
        );

        if (moving) maybePlayStep();

        // win detection based on XZ distance to exit
        double dx = x - exitX;
        double dz = z - exitZ;
        if (dx * dx + dz * dz <= (tile * 0.5) * (tile * 0.5)) {
            FXGL.getDialogService().showMessageBox("You found the exit!", () -> FXGL.getGameController().startNewGame());
        }
    }

    private void tryMove(double dx, double dz) {
        double nx = this.x + dx;
        double nz = this.z + dz;
        if (!collidesWithWalls(nx, nz)) {
            this.x = nx;
            this.z = nz;
        }
    }

    private boolean collidesWithWalls(double px, double pz) {
        int minGX = (int) Math.floor((px - radius) / tile);
        int maxGX = (int) Math.floor((px + radius) / tile);
        int minGZ = (int) Math.floor((pz - radius) / tile);
        int maxGZ = (int) Math.floor((pz + radius) / tile);

        for (int gx = minGX; gx <= maxGX; gx++) {
            for (int gz = minGZ; gz <= maxGZ; gz++) {
                if (gx < 0 || gz < 0 || gx >= gridW || gz >= gridH) return true; // treat outside as walls
                if (maze[gx][gz]) {
                    double tileCenterX = gx * tile + tile / 2.0;
                    double tileCenterZ = gz * tile + tile / 2.0;
                    double nearestX = clamp(px, tileCenterX - tile / 2.0, tileCenterX + tile / 2.0);
                    double nearestZ = clamp(pz, tileCenterZ - tile / 2.0, tileCenterZ + tile / 2.0);
                    double dx = px - nearestX;
                    double dz = pz - nearestZ;
                    if (dx * dx + dz * dz <= radius * radius)
                        return true;
                }
            }
        }
        return false;
    }

    private void maybePlayStep() {
        long now = System.currentTimeMillis();
        if (now - lastStepSound >= stepIntervalMs) {
            FXGL.play("walk.wav");
            lastStepSound = now;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // Input toggles
    public void setMoveForward(boolean v) { this.moveForward = v; }
    public void setMoveBackward(boolean v) { this.moveBackward = v; }
    public void setMoveLeft(boolean v) { this.moveLeft = v; }
    public void setMoveRight(boolean v) { this.moveRight = v; }
    public void setTurnLeft(boolean v) { this.turnLeft = v; }
    public void setTurnRight(boolean v) { this.turnRight = v; }

    // Mouse look support (adjust yaw/pitch by given delta in degrees)
    public void addYaw(double deltaDegrees) { this.yaw += deltaDegrees; }
    public void addPitch(double deltaDegrees) { this.pitch += deltaDegrees; }
}