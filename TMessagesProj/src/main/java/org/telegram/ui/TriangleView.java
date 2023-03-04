package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class TriangleView extends View {

    private Paint paint;
    private Path path;
    private int size = 100;
    private int color = Color.BLACK;
    private Orientation orientation = Orientation.UP;

    public TriangleView(Context context) {
        super(context);
        init();
    }

    public TriangleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TriangleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        path = new Path();
    }

    public void setSize(int size) {
        this.size = size;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int halfWidth = getMeasuredWidth() / 2;
        int halfHeight = getMeasuredWidth() / 2;

        paint.setColor(color);

        path.reset();

        switch (orientation) {
            case UP:
                path.moveTo(halfWidth, halfHeight - (size / 2));
                path.lineTo(halfWidth + (size / 2), halfHeight + (size / 2));
                path.lineTo(halfWidth - (size / 2), halfHeight + (size / 2));
                break;
            case DOWN:
                path.moveTo(halfWidth, halfHeight + (size / 2));
                path.lineTo(halfWidth + (size / 2), halfHeight - (size / 2));
                path.lineTo(halfWidth - (size / 2), halfHeight - (size / 2));
                break;
            case LEFT:
                path.moveTo(halfWidth - (size / 2), halfHeight);
                path.lineTo(halfWidth + (size / 2), halfHeight + (size / 2));
                path.lineTo(halfWidth + (size / 2), halfHeight - (size / 2));
                break;
            case RIGHT:
                path.moveTo(halfWidth + (size / 2), halfHeight);
                path.lineTo(halfWidth - (size / 2), halfHeight + (size / 2));
                path.lineTo(halfWidth - (size / 2), halfHeight - (size / 2));
                break;
        }

        path.close();

        canvas.drawPath(path, paint);
    }

    public enum Orientation {
        UP,
        DOWN,
        LEFT,
        RIGHT
    }
}
