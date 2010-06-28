/**
 * Copyright (C) 2010 Jesse Wilson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicobject.shush;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.MotionEvent;
import android.view.View;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * A slider around a circle, to select a time between now and 12 hours from now.
 */
final class ClockSlider extends View {

    private static final int MAX_SIZE = 200;
    private static final int INSETS = 6;
    private static final int MINUTES_PER_HALF_DAY = 720;

    private int width;
    private int height;
    private int centerX;
    private int centerY;
    private int diameter;
    private RectF outerCircle;
    private RectF buttonCircle;
    private Path clip;

    private Paint lightGrey = new Paint();
    private Paint pink = new Paint();
    private Paint white = new Paint();
    private Paint duration = new Paint();
    private Paint durationUnits = new Paint();
    private Paint unshushTime = new Paint();
    private Paint buttonCirclePaint = new Paint();

    private Calendar start = new GregorianCalendar();
    private int startAngle = 0;
    private Calendar end = new GregorianCalendar();

    /** minutes to shush. */
    private int minutes = 0;
    private boolean upPushed;
    private boolean downPushed;

    public ClockSlider(Context context) {
        super(context);

        lightGrey.setColor(Color.rgb(115, 115, 115));
        lightGrey.setAntiAlias(true);
        pink.setColor(Color.rgb(255, 0, 165));
        pink.setAntiAlias(true);
        white.setColor(Color.WHITE);
        white.setAntiAlias(true);
        duration.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        duration.setSubpixelText(true);
        duration.setAntiAlias(true);
        duration.setColor(Color.WHITE);
        duration.setTextAlign(Paint.Align.CENTER);
        durationUnits = new Paint(duration);
        durationUnits.setTypeface(Typeface.SANS_SERIF);
        unshushTime = new Paint(duration);
        unshushTime.setColor(lightGrey.getColor());
        buttonCirclePaint.setColor(Color.argb(102, 115, 115, 115));
        buttonCirclePaint.setAntiAlias(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getWidth() != width || getHeight() != height) {
            width = getWidth();
            height = getHeight();
            centerX = width / 2;
            centerY = height / 2;

            diameter = Math.min(width, height) - (2 * INSETS);
            int thickness = diameter / 15;

            int left = (width - diameter) / 2;
            int top = (height - diameter) / 2;
            outerCircle = new RectF(left, top, left + diameter, top + diameter);

            int innerDiameter = diameter - thickness * 2;
            RectF innerCircle = new RectF(left + thickness, top + thickness,
                    left + thickness + innerDiameter, top + thickness + innerDiameter);

            int offset = thickness * 2;
            int buttonDiameter = diameter - offset * 2;
            buttonCircle = new RectF(left + offset, top + offset,
                    left + offset + buttonDiameter, top + offset + buttonDiameter);

            clip = new Path();
            clip.addRect(outerCircle, Path.Direction.CW);
            clip.addOval(innerCircle, Path.Direction.CCW);

            duration.setTextSize(diameter * 0.32f);
            durationUnits.setTextSize(diameter * 0.10f);
            unshushTime.setTextSize(diameter * 0.13f);
        }

        drawClock(canvas);
        drawTextAndButtons(canvas);
    }

    public Date getStart() {
        return start.getTime();
    }

    public void setStart(Date now) {
        start.setTime(now);
        int minuteOfHalfDay = start.get(Calendar.HOUR_OF_DAY) * 60 + start.get(Calendar.MINUTE);
        if (minuteOfHalfDay > MINUTES_PER_HALF_DAY) {
            minuteOfHalfDay -= MINUTES_PER_HALF_DAY;
        }
        int angle = minuteOfHalfDay / 2; // 720 minutes per half-day -> 360 degrees per circle
        angle += 270; // clocks start at 12:00, but our angles start at 3:00
        startAngle = angle % 360;
        postInvalidate();
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        if (minutes == this.minutes) {
            return; // avoid unnecessary repaints
        }
        this.minutes = minutes;
        end.setTimeInMillis(start.getTimeInMillis() + (this.minutes * 60 * 1000L));
        postInvalidate();
    }

    public Date getEnd() {
        return end.getTime();
    }

    /**
     * Draw a circle and an arc of the selected duration from start thru end.
     */
    private void drawClock(Canvas canvas) {
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawOval(outerCircle, lightGrey);
        int sweepAngle = minutes / 2;
        canvas.drawArc(outerCircle, startAngle, sweepAngle, true, pink);
        canvas.drawArc(outerCircle, startAngle + sweepAngle - 1, 2, true, white);
        canvas.restore();
    }

    /**
     * Write labels in the middle of the circle like so:
     *
     *    2 1/2
     *    hours
     *  10:15 PM
     */
    private void drawTextAndButtons(Canvas canvas) {
        // up/down button backgrounds
        if (upPushed) {
            canvas.drawArc(buttonCircle, 270, 180, true, buttonCirclePaint);
        }
        if (downPushed) {
            canvas.drawArc(buttonCircle, 90, 180, true, buttonCirclePaint);
        }

        String durationText;
        String durationUnitsText;
        long timeInMillis = end.getTimeInMillis();
        String onAtText = DateUtils.formatSameDayTime(timeInMillis, timeInMillis,
                DateFormat.SHORT, DateFormat.SHORT).toString();
        if (minutes < 60) {
            durationText = Integer.toString(minutes);
            durationUnitsText = "minutes";
        } else if (minutes == 60) {
            durationText = "1";
            durationUnitsText = "hour";
        } else if (minutes % 60 == 0) {
            durationText = Integer.toString(minutes / 60);
            durationUnitsText = "hours";
        } else if (minutes % 60 == 15) {
            durationText = minutes / 60 + "\u00BC"; // 1/4
            durationUnitsText = "hours";
        } else if (minutes % 60 == 30) {
            durationText = minutes / 60 + "\u00BD"; // 1/2
            durationUnitsText = "hours";
        } else if (minutes % 60 == 45) {
            durationText = minutes / 60 + "\u00BE"; // 3/4
            durationUnitsText = "hours";
        } else {
            throw new AssertionError();
        }
        canvas.drawText(durationText,      centerX, centerY - (diameter * 0.08f), duration);
        canvas.drawText(durationUnitsText, centerX, centerY + (diameter * 0.06f), durationUnits);
        canvas.drawText(onAtText,          centerX, centerY + (diameter * 0.25f), unshushTime);

        // up/down buttons
        Paint downPaint = downPushed ? white : lightGrey;
        canvas.drawRect(centerX - diameter * 0.32f, centerY - diameter * 0.01f,
                        centerX - diameter * 0.22f, centerY + diameter * 0.01f, downPaint);
        Paint upPaint = upPushed ? white : lightGrey;
        canvas.drawRect(centerX + diameter * 0.22f, centerY - diameter * 0.01f,
                        centerX + diameter * 0.32f, centerY + diameter * 0.01f, upPaint);
        canvas.drawRect(centerX + diameter * 0.26f, centerY - diameter * 0.05f,
                        centerX + diameter * 0.28f, centerY + diameter * 0.05f, upPaint);
    }

    /**
     * Accept a touches near the circle's edge, translate it to an angle, and
     * update the sweep angle.
     */
    @Override public boolean onTouchEvent(MotionEvent event) {
        upPushed = false;
        downPushed = false;
        int touchX = (int) event.getX();
        int touchY = (int) event.getY();

        int distanceFromCenterX = centerX - touchX;
        int distanceFromCenterY = centerY - touchY;
        int distanceFromCenterSquared = distanceFromCenterX * distanceFromCenterX
                + distanceFromCenterY * distanceFromCenterY;
        float maxSlider = (diameter * 1.3f) / 2;
        float maxUpDown = (diameter * 0.8f) / 2;

        // handle increment/decrement
        if (distanceFromCenterSquared < (maxUpDown * maxUpDown)) {
            boolean up = touchX > centerX;

            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                if (up) {
                    upPushed = true;
                } else {
                    downPushed = true;
                }
                postInvalidate();
                return true;
            }

            int angle = up ? (15 + minutes) : (705 + minutes);
            if (angle > 720) {
                angle -= 720;
            }
            setMinutes(angle);
            return true;

        // if it's on the slider, handle that
        } else if (distanceFromCenterSquared < (maxSlider * maxSlider)) {
            int angle = pointToAngle(touchX, touchY);
            /*
             * Convert the angle into a sweep angle. The sweep angle is a positive
             * angle between the start angle and the touched angle.
             */
            angle = 360 + angle - startAngle;
            int angleX2 = angle * 2;
            angleX2 = roundToNearest15(angleX2);
            if (angleX2 > 720) {
                angleX2 = angleX2 - 720; // avoid mod because we prefer 720 over 0
            }
            setMinutes(angleX2);
            return true;

        } else {
            return false;
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, Math.min(height, MAX_SIZE));
    }

    /**
     * Returns the number of degrees (0-359) for the given point, such that
     * 3pm is 0 and 9pm is 180.
     */
    private int pointToAngle(int x, int y) {

        /* Get the angle from a triangle by dividing opposite by adjacent
         * and taking the atan. This code is careful not to divide by 0.
         *
         *
         *      adj | opp
         *          |
         * opp +180 | +270 adj
         * _________|_________
         *          |
         * adj  +90 | +0   opp
         *          |
         *      opp | adj
         *
         */

        if (x >= centerX && y < centerY) {
            double opp = x - centerX;
            double adj = centerY - y;
            return 270 + (int) Math.toDegrees(Math.atan(opp / adj));
        } else if (x > centerX && y >= centerY) {
            double opp = y - centerY;
            double adj = x - centerX;
            return (int) Math.toDegrees(Math.atan(opp / adj));
        } else if (x <= centerX && y > centerY) {
            double opp = centerX - x;
            double adj = y - centerY;
            return 90 + (int) Math.toDegrees(Math.atan(opp / adj));
        } else if (x < centerX && y <= centerY) {
            double opp = centerY - y;
            double adj = centerX - x;
            return 180 + (int) Math.toDegrees(Math.atan(opp / adj));
        }

        throw new IllegalArgumentException();
    }

    /**
     * Rounds the angle to the nearest 7.5 degrees, which equals 15 minutes on
     * a clock. Not strictly necessary, but it discourages fat-fingered users
     * from being frustrated when trying to select a fine-grained period.
     */
    private int roundToNearest15(int angleX2) {
        return ((angleX2 + 8) / 15) * 15;
    }
}
