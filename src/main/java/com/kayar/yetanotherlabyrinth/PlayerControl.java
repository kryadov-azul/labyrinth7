package com.kayar.yetanotherlabyrinth;

import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.entity.component.Component;
import javafx.geometry.Point2D;

/**
 * Handles top-down "FPS-style" movement with mouse look and WASD controls.
 * Collision is handled against the maze grid (true = wall).
 */
public class PlayerControl extends Component {

    private final boolean[][] maze; // [w][h] true=wall
    private final int gridW;
    private final int gridH;
    private final int tile;
    private final double radius;
    private final Point2D exitCenter;

    private double speed = 160; // units per second

    private long lastStepSound = 0;
    private long stepIntervalMs = 350;

    // movement state toggled from input bindings in the app
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveLeft;
    private boolean moveRight;

    public PlayerControl(boolean[][] maze, int tile, Point2D exitCenter) {
        this.maze = maze;
        this.gridW = maze.length;
        this.gridH = maze[0].length;
        this.tile = tile;
        this.radius = tile * 0.30;
        this.exitCenter = exitCenter;
    }

    @Override
    public void onUpdate(double tpf) {
        // Look direction via mouse position
        Point2D mouse = FXGL.getInput().getMousePositionWorld();
        Point2D center = entity.getCenter();
        double angle = Math.toDegrees(Math.atan2(mouse.getY() - center.getY(), mouse.getX() - center.getX()));
        entity.setRotation(angle);

        // Movement vector based on keys and facing direction
        Point2D forward = new Point2D(Math.cos(Math.toRadians(angle)), Math.sin(Math.toRadians(angle)));
        Point2D right = new Point2D(-forward.getY(), forward.getX());

        Point2D dir = Point2D.ZERO;
        if (moveForward) dir = dir.add(forward);
        if (moveBackward) dir = dir.subtract(forward);
        if (moveLeft) dir = dir.subtract(right);
        if (moveRight) dir = dir.add(right);

        boolean moving = false;
        if (dir.magnitude() > 0) {
            dir = dir.normalize().multiply(speed * tpf);
            moving = true;
        }

        // Move with simple grid collision, axis-separated for sliding
        if (dir != Point2D.ZERO) {
            tryMove(dir.getX(), 0);
            tryMove(0, dir.getY());
        }

        if (moving) maybePlayStep();

        // recompute center after movement
        center = entity.getCenter();

        // Win condition: reach the exit center within radius
        if (center.distance(exitCenter) < Math.max(radius, tile * 0.45)) {
            FXGL.getDialogService().showMessageBox("You found the exit!", () -> FXGL.getGameController().startNewGame());
        }
    }

    public void setMoveForward(boolean moveForward) { this.moveForward = moveForward; }
    public void setMoveBackward(boolean moveBackward) { this.moveBackward = moveBackward; }
    public void setMoveLeft(boolean moveLeft) { this.moveLeft = moveLeft; }
    public void setMoveRight(boolean moveRight) { this.moveRight = moveRight; }

    private void maybePlayStep() {
        long now = System.currentTimeMillis();
        if (now - lastStepSound >= stepIntervalMs) {
            FXGL.play("walk.wav");
            lastStepSound = now;
        }
    }

    private void tryMove(double dx, double dy) {
        double newX = entity.getX() + dx;
        double newY = entity.getY() + dy;
        double cx = newX + entity.getWidth() / 2.0;
        double cy = newY + entity.getHeight() / 2.0;

        if (!collidesWithWalls(cx, cy)) {
            entity.translate(dx, dy);
        }
    }

    private boolean collidesWithWalls(double cx, double cy) {
        int minX = (int) Math.floor((cx - radius) / tile);
        int maxX = (int) Math.floor((cx + radius) / tile);
        int minY = (int) Math.floor((cy - radius) / tile);
        int maxY = (int) Math.floor((cy + radius) / tile);

        for (int gx = minX; gx <= maxX; gx++) {
            for (int gy = minY; gy <= maxY; gy++) {
                if (gx < 0 || gy < 0 || gx >= gridW || gy >= gridH) return true; // treat outside as walls
                if (maze[gx][gy]) {
                    // precise circle vs AABB check
                    double tileCenterX = gx * tile + tile / 2.0;
                    double tileCenterY = gy * tile + tile / 2.0;
                    double nearestX = clamp(cx, tileCenterX - tile / 2.0, tileCenterX + tile / 2.0);
                    double nearestY = clamp(cy, tileCenterY - tile / 2.0, tileCenterY + tile / 2.0);
                    double dx = cx - nearestX;
                    double dy = cy - nearestY;
                    if (dx * dx + dy * dy <= radius * radius)
                        return true;
                }
            }
        }
        return false;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
