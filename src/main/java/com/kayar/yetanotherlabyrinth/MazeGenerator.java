package com.kayar.yetanotherlabyrinth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Stack;

/**
 * Simple randomized depth-first search (backtracker) maze generator.
 * Grid values: true = wall, false = passage.
 * Dimensions should be odd to ensure walls around passages.
 */
public final class MazeGenerator {

    private MazeGenerator() {}

    public static boolean[][] generate(int width, int height, long seed) {
        if (width % 2 == 0 || height % 2 == 0) {
            throw new IllegalArgumentException("Maze dimensions must be odd numbers");
        }

        boolean[][] grid = new boolean[width][height];
        // fill with walls
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = true;
            }
        }

        Random rnd = seed == 0 ? new Random() : new Random(seed);

        // start at (1,1)
        int sx = 1;
        int sy = 1;
        grid[sx][sy] = false;

        Stack<int[]> stack = new Stack<>();
        stack.push(new int[]{sx, sy});

        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            int cx = cell[0];
            int cy = cell[1];

            List<int[]> neighbors = new ArrayList<>();
            if (cx - 2 > 0 && grid[cx - 2][cy]) neighbors.add(new int[]{cx - 2, cy});
            if (cx + 2 < width - 1 && grid[cx + 2][cy]) neighbors.add(new int[]{cx + 2, cy});
            if (cy - 2 > 0 && grid[cx][cy - 2]) neighbors.add(new int[]{cx, cy - 2});
            if (cy + 2 < height - 1 && grid[cx][cy + 2]) neighbors.add(new int[]{cx, cy + 2});

            if (!neighbors.isEmpty()) {
                Collections.shuffle(neighbors, rnd);
                int[] next = neighbors.get(0);
                int nx = next[0];
                int ny = next[1];
                // carve passage between
                grid[(cx + nx) / 2][(cy + ny) / 2] = false;
                grid[nx][ny] = false;
                stack.push(new int[]{nx, ny});
            } else {
                stack.pop();
            }
        }

        // ensure exit cell is open
        grid[width - 2][height - 2] = false;

        return grid;
    }
}
