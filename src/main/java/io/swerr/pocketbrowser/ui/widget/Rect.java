package io.swerr.pocketbrowser.ui.widget;

public record Rect(int x0, int y0, int x1, int y1) {
    public static final Rect NONE = new Rect(0, 0, 0, 0);

    public boolean contains(double x, double y) {
        return x1 > x0 && y1 > y0 && x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    public int centreX() {
        return (x0 + x1) / 2;
    }

    public int centreY() {
        return (y0 + y1) / 2;
    }

    public int width() {
        return x1 - x0;
    }

    public int height() {
        return y1 - y0;
    }

    public boolean isEmpty() {
        return x1 <= x0 || y1 <= y0;
    }
}
