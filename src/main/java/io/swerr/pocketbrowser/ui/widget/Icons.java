package io.swerr.pocketbrowser.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Icons {
    public static final int SIZE = 9;

    public static final String[] BACK = {
            ".....#...",
            "....##...",
            "...##....",
            "..##.....",
            ".##......",
            "..##.....",
            "...##....",
            "....##...",
            ".....#...",
    };

    public static final String[] FORWARD = {
            "...#.....",
            "...##....",
            "....##...",
            ".....##..",
            "......##.",
            ".....##..",
            "....##...",
            "...##....",
            "...#.....",
    };

    public static final String[] RELOAD = {
            "..####.#.",
            ".##...###",
            "##.....#.",
            "#........",
            "#........",
            "#.......#",
            "##.....##",
            ".##...##.",
            "..#####..",
    };

    public static final String[] CROSS = {
            "##.....##",
            "###...###",
            ".###.###.",
            "..#####..",
            "...###...",
            "..#####..",
            ".###.###.",
            "###...###",
            "##.....##",
    };

    public static final String[] HOME = {
            "....#....",
            "...###...",
            "..#####..",
            ".#######.",
            "#########",
            ".#.....#.",
            ".#.###.#.",
            ".#.###.#.",
            ".#.###.#.",
    };

    public static final String[] PLUS = {
            "....#....",
            "....#....",
            "....#....",
            "....#....",
            "#########",
            "....#....",
            "....#....",
            "....#....",
            "....#....",
    };

    public static final String[] MINUS = {
            ".........",
            ".........",
            ".........",
            ".........",
            "#########",
            ".........",
            ".........",
            ".........",
            ".........",
    };

    public static final String[] STAR = {
            "....#....",
            "...###...",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            "..#####..",
            ".##...##.",
            ".#.....#.",
    };

    public static final String[] PIN = {
            "..#####..",
            "..#...#..",
            "..#...#..",
            ".#######.",
            "....#....",
            "....#....",
            "....#....",
            "....#....",
            ".........",
    };

    public static final String[] MENU = {
            "....##...",
            "....##...",
            ".........",
            "....##...",
            "....##...",
            ".........",
            "....##...",
            "....##...",
            ".........",
    };

    public static final String[] LOCK = {
            "..#####..",
            ".##...##.",
            ".#.....#.",
            "#########",
            "####.####",
            "####.####",
            "#########",
            "#########",
            ".........",
    };

    public static final String[] GLOBE = {
            "..#####..",
            ".#..#..#.",
            "#...#...#",
            "#...#...#",
            "#########",
            "#...#...#",
            "#...#...#",
            ".#..#..#.",
            "..#####..",
    };

    public static final String[] SEARCH = {
            ".#####...",
            "#.....#..",
            "#.....#..",
            "#.....#..",
            "#.....#..",
            ".#####...",
            "....#.#..",
            ".....#.#.",
            "......###",
    };

    public static final String[] PLAY = {
            "..#......",
            "..##.....",
            "..###....",
            "..####...",
            "..#####..",
            "..####...",
            "..###....",
            "..##.....",
            "..#......",
    };

    public static final String[] PAUSE = {
            ".##...##.",
            ".##...##.",
            ".##...##.",
            ".##...##.",
            ".##...##.",
            ".##...##.",
            ".##...##.",
            ".##...##.",
            ".........",
    };

    public static final String[] MAXIMIZE = {
            ".........",
            ".#######.",
            ".#######.",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.",
            ".#.....#.",
            ".#######.",
            ".........",
    };

    public static final String[] RESTORE = {
            "..#######",
            "..#######",
            "..#.....#",
            "#####...#",
            "#####...#",
            "#...#####",
            "#...#....",
            "#####....",
            ".........",
    };

    public static final String[] GRIP = {
            ".........",
            ".........",
            ".........",
            ".....#.#.",
            ".........",
            "...#.#.#.",
            ".........",
            ".#.#.#.#.",
            ".........",
    };

    private Icons() {
    }

    public static void draw(GuiGraphicsExtractor graphics, String[] mask, int x, int y, int color) {
        draw(graphics, mask, x, y, 1, color);
    }

    public static void draw(GuiGraphicsExtractor graphics, String[] mask, int x, int y, int scale, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        for (int row = 0; row < mask.length; row++) {
            String line = mask[row];
            int runStart = -1;
            for (int column = 0; column <= line.length(); column++) {
                boolean lit = column < line.length() && line.charAt(column) == '#';
                if (lit && runStart < 0) {
                    runStart = column;
                } else if (!lit && runStart >= 0) {
                    graphics.fill(
                            x + runStart * scale, y + row * scale,
                            x + column * scale, y + (row + 1) * scale,
                            color);
                    runStart = -1;
                }
            }
        }
    }

    public static void centered(GuiGraphicsExtractor graphics, String[] mask,
                                int x0, int y0, int x1, int y1, int color) {
        int x = x0 + (x1 - x0 - SIZE) / 2;
        int y = y0 + (y1 - y0 - SIZE) / 2;
        draw(graphics, mask, x, y, 1, color);
    }
}
