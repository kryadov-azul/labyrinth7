package com.kayar.yetanotherlabyrinth;

import com.almasb.fxgl.audio.Sound;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;
import javafx.scene.PerspectiveCamera;

/**
 * First-person 3D controller using the maze grid for collision on XZ plane.
 * - Movement: WASD (forward/back/strafe), Q/E to turn left/right (yaw).
 * - Space to jump (simple vertical motion with gravity and ceiling clamp).
 * - Plays footstep sounds while moving.
 * - Triggers win when close to exit (by XZ distance).
 */
public class FirstPerson3DControl extends Component {

    private final boolean[][] maze; // [w][h] true = wall
    private final boolean[][] pits; // [w][h] true = pit (hole) on walkable cell
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

    // vertical motion (jump)
    private double yOffset = 0;      // positive => camera is higher than ground eye level
    private double yVelocity = 0;    // positive => moving up
    private boolean grounded = true; // on the floor
    private final double gravity;    // units/s^2 (acts downward)
    private final double jumpSpeed;  // initial upward speed

    // pits logic
    private final double safeJumpHeightFactor = 0.45; // must be higher than this fraction of tile to clear a pit
    private boolean gameOverTriggered = false;

    // inputs
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean turnLeft;
    private boolean turnRight;

    private long lastStepSound = 0;
    private long stepIntervalMs = 450;
    private final Sound jumpSfx;

    public FirstPerson3DControl(boolean[][] maze, boolean[][] pits, int tile, PerspectiveCamera camera, Point2D spawn2D, Point2D exitCenter2D) {
        this.maze = maze;
        this.pits = pits;
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

        // set jump constants relative to tile size
        this.gravity = tile * 7.0;      // tuned for feel, not real gravity
        this.jumpSpeed = tile * 3.2;    // enough to clear small bumps, below ceiling
        this.jumpSfx = FXGL.getAssetLoader().loadSound("jump1.mp3");
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
        if (moveForward) {
            vx -= fwdX;
            vz -= fwdZ;
        }
        if (moveBackward) {
            vx += fwdX;
            vz += fwdZ;
        }
        if (moveLeft) {
            vx -= rightX;
            vz -= rightZ;
        }
        if (moveRight) {
            vx += rightX;
            vz += rightZ;
        }

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

        // vertical motion (apply gravity and clamp to ceiling)
        // ceiling height approx: wallHeight - cameraHeight; wallHeight ~= tile * 1.8
        double maxHeadroom = Math.max(0, tile * 1.8 - cameraHeight - 4); // small margin
        if (!grounded || yVelocity != 0) {
            yVelocity -= gravity * tpf; // gravity pulls down
            yOffset += yVelocity * tpf;

            // ceiling clamp
            if (yOffset > maxHeadroom) {
                yOffset = maxHeadroom;
                if (yVelocity > 0) yVelocity = 0;
            }

            // floor collision
            if (yOffset <= 0) {
                yOffset = 0;
                yVelocity = 0;
                grounded = true;
            } else {
                grounded = false;
            }
        }

        // update camera transform (yaw + pitch)
        camera.setTranslateX(x);
        camera.setTranslateY(-cameraHeight - yOffset); // negative Y so that floor at 0 is "below"
        camera.setTranslateZ(z);
        camera.getTransforms().setAll(
                new javafx.scene.transform.Rotate(yaw, javafx.scene.transform.Rotate.Y_AXIS),
                new javafx.scene.transform.Rotate(pitch, javafx.scene.transform.Rotate.X_AXIS)
        );

        if (moving) maybePlayStep();

        // pit detection: if overlapping pit and not high enough, trigger game over
        // Allow jumping over pits: do not trigger while ascending (yVelocity > 0)
        if (!gameOverTriggered && isOnPit(x, z)) {
            double safeH = tile * safeJumpHeightFactor;
            if (yVelocity <= 0 && yOffset < safeH) {
                gameOverTriggered = true;
                FXGL.getDialogService().showMessageBox("You fell into a pit! Game Over", () -> {
                    LabyrinthApp.resetLevelCounter();
                    FXGL.getGameController().gotoMainMenu();
                });
                return;
            }
        }

        // win detection based on XZ distance to exit
        double dx = x - exitX;
        double dz = z - exitZ;
        if (dx * dx + dz * dz <= (tile * 0.5) * (tile * 0.5)) {
            FXGL.getDialogService().showMessageBox(LabyrinthApp.buildExitMessage(), () -> FXGL.getGameController().startNewGame());
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

    private boolean isOnPit(double px, double pz) {
        if (pits == null) return false;
        int minGX = (int) Math.floor((px - radius) / tile);
        int maxGX = (int) Math.floor((px + radius) / tile);
        int minGZ = (int) Math.floor((pz - radius) / tile);
        int maxGZ = (int) Math.floor((pz + radius) / tile);

        for (int gx = minGX; gx <= maxGX; gx++) {
            for (int gz = minGZ; gz <= maxGZ; gz++) {
                if (gx < 0 || gz < 0 || gx >= gridW || gz >= gridH) continue; // ignore outside
                if (pits[gx][gz]) {
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
    public void setMoveForward(boolean v) {
        this.moveForward = v;
    }

    public void setMoveBackward(boolean v) {
        this.moveBackward = v;
    }

    public void setMoveLeft(boolean v) {
        this.moveLeft = v;
    }

    public void setMoveRight(boolean v) {
        this.moveRight = v;
    }

    public void setTurnLeft(boolean v) {
        this.turnLeft = v;
    }

    public void setTurnRight(boolean v) {
        this.turnRight = v;
    }

    // Actions
    public void jump() {
        if (grounded) {
            yVelocity = jumpSpeed;
            grounded = false;
            FXGL.getAudioPlayer().playSound(jumpSfx);
        }
    }

    // Mouse look support (adjust yaw/pitch by given delta in degrees)
    public void addYaw(double deltaDegrees) {
        this.yaw += deltaDegrees;
    }

    public void addPitch(double deltaDegrees) {
        this.pitch += deltaDegrees;
    }

    // Getters for minimap
    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }
}