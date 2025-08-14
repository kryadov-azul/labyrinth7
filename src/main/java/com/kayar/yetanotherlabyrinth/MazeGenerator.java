package com.kayar.yetanotherlabyrinth;

import java.util.*;

/**
 * Maze generator supporting multiple algorithms.
 * Grid values: true = wall, false = passage.
 * Dimensions should be odd to ensure walls around passages.
 */
public final class MazeGenerator {

    public enum Algorithm { BACKTRACKER, WILSON, KRUSKAL, PRIM, ALDOUS_BRODER, ELLER }

    private MazeGenerator() {}

    public static boolean[][] generate(int width, int height, long seed) {
        return generate(width, height, seed, Algorithm.BACKTRACKER);
    }

    public static boolean[][] generate(int width, int height, long seed, Algorithm algorithm) {
        if (width % 2 == 0 || height % 2 == 0) {
            throw new IllegalArgumentException("Maze dimensions must be odd numbers");
        }

        boolean[][] grid = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            Arrays.fill(grid[x], true);
        }

        Random rnd = seed == 0 ? new Random() : new Random(seed);

        switch (algorithm) {
            case BACKTRACKER -> dfsBacktracker(grid, rnd);
            case PRIM -> prim(grid, rnd);
            case KRUSKAL -> kruskal(grid, rnd);
            case ALDOUS_BRODER -> aldousBroder(grid, rnd);
            case WILSON -> wilson(grid, rnd);
            case ELLER -> eller(grid, rnd);
        }

        // ensure exit cell is open
        grid[width - 2][height - 2] = false;
        // ensure entrance is open
        grid[1][1] = false;

        return grid;
    }

    // --- Helpers ---

    private static boolean inBoundsCell(int x, int y, int w, int h) {
        return x > 0 && x < w - 1 && y > 0 && y < h - 1 && (x % 2 == 1) && (y % 2 == 1);
    }

    private static List<int[]> neighborCells2(int x, int y, int w, int h) {
        List<int[]> n = new ArrayList<>(4);
        if (x - 2 > 0) n.add(new int[]{x - 2, y});
        if (x + 2 < w - 1) n.add(new int[]{x + 2, y});
        if (y - 2 > 0) n.add(new int[]{x, y - 2});
        if (y + 2 < h - 1) n.add(new int[]{x, y + 2});
        return n;
    }

    private static void carveBetween(boolean[][] g, int x1, int y1, int x2, int y2) {
        g[x1][y1] = false;
        g[(x1 + x2) / 2][(y1 + y2) / 2] = false;
        g[x2][y2] = false;
    }

    private static int totalCells(int w, int h) {
        int cols = (w - 1) / 2;
        int rows = (h - 1) / 2;
        return Math.max(0, cols * rows);
    }

    private static int cellId(int x, int y, int w) {
        int col = (x - 1) / 2;
        int row = (y - 1) / 2;
        int cols = (w - 1) / 2;
        return row * cols + col;
    }

    // --- Algorithms ---

    private static void dfsBacktracker(boolean[][] grid, Random rnd) {
        int w = grid.length;
        int h = grid[0].length;
        int sx = 1;
        int sy = 1;
        grid[sx][sy] = false;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            int cx = cell[0], cy = cell[1];
            List<int[]> neighbors = new ArrayList<>();
            if (cx - 2 > 0 && grid[cx - 2][cy]) neighbors.add(new int[]{cx - 2, cy});
            if (cx + 2 < w - 1 && grid[cx + 2][cy]) neighbors.add(new int[]{cx + 2, cy});
            if (cy - 2 > 0 && grid[cx][cy - 2]) neighbors.add(new int[]{cx, cy - 2});
            if (cy + 2 < h - 1 && grid[cx][cy + 2]) neighbors.add(new int[]{cx, cy + 2});
            if (!neighbors.isEmpty()) {
                int[] next = neighbors.get(rnd.nextInt(neighbors.size()));
                carveBetween(grid, cx, cy, next[0], next[1]);
                stack.push(new int[]{next[0], next[1]});
            } else {
                stack.pop();
            }
        }
    }

    private static void prim(boolean[][] grid, Random rnd) {
        int w = grid.length, h = grid[0].length;
        // Pick random start cell
        int sx = 2 * (rnd.nextInt((w - 1) / 2)) + 1;
        int sy = 2 * (rnd.nextInt((h - 1) / 2)) + 1;
        grid[sx][sy] = false;
        boolean[][] inMaze = new boolean[w][h];
        inMaze[sx][sy] = true;
        List<int[]> frontier = new ArrayList<>();
        boolean[][] inFrontier = new boolean[w][h];
        for (int[] n : neighborCells2(sx, sy, w, h)) {
            frontier.add(n);
            inFrontier[n[0]][n[1]] = true;
        }
        while (!frontier.isEmpty()) {
            int idx = rnd.nextInt(frontier.size());
            int[] cell = frontier.remove(idx);
            inFrontier[cell[0]][cell[1]] = false;
            // find neighbors in maze
            List<int[]> inMazeNeighbors = new ArrayList<>(4);
            for (int[] n : neighborCells2(cell[0], cell[1], w, h)) {
                if (inMaze[n[0]][n[1]]) inMazeNeighbors.add(n);
            }
            if (inMazeNeighbors.isEmpty()) continue; // should not happen often
            int[] attach = inMazeNeighbors.get(rnd.nextInt(inMazeNeighbors.size()));
            carveBetween(grid, attach[0], attach[1], cell[0], cell[1]);
            inMaze[cell[0]][cell[1]] = true;
            for (int[] n : neighborCells2(cell[0], cell[1], w, h)) {
                if (!inMaze[n[0]][n[1]] && !inFrontier[n[0]][n[1]]) {
                    frontier.add(n);
                    inFrontier[n[0]][n[1]] = true;
                }
            }
        }
    }

    private static void kruskal(boolean[][] grid, Random rnd) {
        int w = grid.length, h = grid[0].length;
        int cols = (w - 1) / 2;
        int rows = (h - 1) / 2;
        int n = cols * rows;
        if (n == 0) return;
        // DSU
        int[] parent = new int[n];
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        java.util.function.IntUnaryOperator find = new java.util.function.IntUnaryOperator() {
            @Override public int applyAsInt(int x) { return parent[x] == x ? x : (parent[x] = applyAsInt(parent[x])); }
        };
        java.util.function.BiConsumer<Integer, Integer> union = (a, b) -> {
            int ra = find.applyAsInt(a);
            int rb = find.applyAsInt(b);
            if (ra == rb) return;
            if (rank[ra] < rank[rb]) parent[ra] = rb; else if (rank[ra] > rank[rb]) parent[rb] = ra; else { parent[rb] = ra; rank[ra]++; }
        };
        // Build edges between neighboring cells (east and south)
        List<int[]> edges = new ArrayList<>();
        for (int y = 1; y < h; y += 2) {
            for (int x = 1; x < w; x += 2) {
                if (x + 2 < w) edges.add(new int[]{x, y, x + 2, y});
                if (y + 2 < h) edges.add(new int[]{x, y, x, y + 2});
            }
        }
        Collections.shuffle(edges, rnd);
        // Ensure starting cell is open
        grid[1][1] = false;
        for (int[] e : edges) {
            int x1 = e[0], y1 = e[1], x2 = e[2], y2 = e[3];
            int id1 = cellId(x1, y1, w);
            int id2 = cellId(x2, y2, w);
            int r1 = find.applyAsInt(id1);
            int r2 = find.applyAsInt(id2);
            if (r1 != r2) {
                union.accept(r1, r2);
                carveBetween(grid, x1, y1, x2, y2);
            }
        }
    }

    private static void aldousBroder(boolean[][] grid, Random rnd) {
        int w = grid.length, h = grid[0].length;
        int cols = (w - 1) / 2;
        int rows = (h - 1) / 2;
        int total = cols * rows;
        if (total == 0) return;
        boolean[][] visited = new boolean[w][h];
        int cx = 2 * rnd.nextInt(cols) + 1;
        int cy = 2 * rnd.nextInt(rows) + 1;
        grid[cx][cy] = false;
        visited[cx][cy] = true;
        int visitedCount = 1;
        while (visitedCount < total) {
            List<int[]> nbs = neighborCells2(cx, cy, w, h);
            int[] n = nbs.get(rnd.nextInt(nbs.size()));
            if (!visited[n[0]][n[1]]) {
                carveBetween(grid, cx, cy, n[0], n[1]);
                visited[n[0]][n[1]] = true;
                visitedCount++;
            }
            cx = n[0];
            cy = n[1];
        }
    }

    private static void wilson(boolean[][] grid, Random rnd) {
        int w = grid.length, h = grid[0].length;
        int cols = (w - 1) / 2;
        int rows = (h - 1) / 2;
        int total = cols * rows;
        if (total == 0) return;
        boolean[][] inTree = new boolean[w][h];
        // Pick a random cell to seed the tree
        int sx = 2 * rnd.nextInt(cols) + 1;
        int sy = 2 * rnd.nextInt(rows) + 1;
        grid[sx][sy] = false;
        inTree[sx][sy] = true;
        int inTreeCount = 1;
        while (inTreeCount < total) {
            // pick a random unvisited start
            int cx, cy;
            do {
                cx = 2 * rnd.nextInt(cols) + 1;
                cy = 2 * rnd.nextInt(rows) + 1;
            } while (inTree[cx][cy]);
            // loop-erased random walk
            List<int[]> path = new ArrayList<>();
            Map<Integer, Integer> indexById = new HashMap<>();
            int id = cellId(cx, cy, w);
            path.add(new int[]{cx, cy});
            indexById.put(id, 0);
            int px = cx, py = cy;
            while (!inTree[px][py]) {
                List<int[]> nbs = neighborCells2(px, py, w, h);
                int[] n = nbs.get(rnd.nextInt(nbs.size()));
                int nid = cellId(n[0], n[1], w);
                Integer existing = indexById.get(nid);
                if (existing != null) {
                    // erase loop by truncating path after existing index
                    for (int k = path.size() - 1; k > existing; k--) {
                        int[] rem = path.remove(k);
                        indexById.remove(cellId(rem[0], rem[1], w));
                    }
                    px = path.get(existing)[0];
                    py = path.get(existing)[1];
                } else {
                    path.add(new int[]{n[0], n[1]});
                    indexById.put(nid, path.size() - 1);
                    px = n[0];
                    py = n[1];
                }
            }
            // carve along the path until it hits the tree (last element is inTree)
            for (int i = 0; i < path.size() - 1; i++) {
                int[] a = path.get(i);
                int[] b = path.get(i + 1);
                carveBetween(grid, a[0], a[1], b[0], b[1]);
                if (!inTree[a[0]][a[1]]) { inTree[a[0]][a[1]] = true; inTreeCount++; }
                if (!inTree[b[0]][b[1]]) { inTree[b[0]][b[1]] = true; inTreeCount++; }
            }
        }
    }

    private static void eller(boolean[][] grid, Random rnd) {
        int w = grid.length, h = grid[0].length;
        int cols = (w - 1) / 2;
        int rows = (h - 1) / 2;
        if (cols <= 0 || rows <= 0) return;

        int nextSetId = 1;
        int[] setId = new int[cols];
        // Initialize first row with distinct sets
        for (int c = 0; c < cols; c++) setId[c] = nextSetId++;

        for (int r = 0; r < rows; r++) {
            int y = 2 * r + 1;
            // Ensure all cells in current row are open
            for (int c = 0; c < cols; c++) {
                int x = 2 * c + 1;
                grid[x][y] = false;
            }

            // Join adjacent cells randomly to the right (except last col)
            for (int c = 0; c < cols - 1; c++) {
                int x = 2 * c + 1;
                if (setId[c] != setId[c + 1] && (r == rows - 1 || rnd.nextBoolean())) {
                    // carve east
                    carveBetween(grid, x, y, x + 2, y);
                    int from = setId[c + 1];
                    int to = setId[c];
                    // merge sets across the entire row
                    for (int k = 0; k < cols; k++) if (setId[k] == from) setId[k] = to;
                }
            }

            if (r == rows - 1) break; // last row done

            // For each set, create at least one vertical connection down
            Map<Integer, List<Integer>> membersBySet = new HashMap<>();
            for (int c = 0; c < cols; c++) {
                membersBySet.computeIfAbsent(setId[c], k -> new ArrayList<>()).add(c);
            }

            boolean[] carvedDown = new boolean[cols];
            for (Map.Entry<Integer, List<Integer>> e : membersBySet.entrySet()) {
                List<Integer> members = e.getValue();
                // choose at least one to carve downward
                int countDown = 1 + rnd.nextInt(Math.max(1, members.size()));
                Collections.shuffle(members, rnd);
                for (int i = 0; i < members.size(); i++) {
                    int c = members.get(i);
                    if (i < countDown || rnd.nextBoolean()) {
                        int x = 2 * c + 1;
                        carveBetween(grid, x, y, x, y + 2);
                        carvedDown[c] = true;
                    }
                }
            }

            // Prepare next row's set assignments
            int[] nextRow = new int[cols];
            for (int c = 0; c < cols; c++) {
                if (carvedDown[c]) {
                    nextRow[c] = setId[c]; // carry set down
                } else {
                    nextRow[c] = nextSetId++; // new set for isolated cell
                }
            }
            setId = nextRow;
        }
    }
}
