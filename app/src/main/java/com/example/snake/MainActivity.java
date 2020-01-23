package com.example.snake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class MainActivity extends Activity {

    Canvas canvas;
    SnakeAnimView snakeAnimView;

    //The snake head sprite sheet
    Bitmap headAnimation;
    Bitmap bodyAnimation;
    Bitmap tailAnimation;
    //The portion of the bitmap to be drawn in the current frame
    Rect rectToBeDrawn;
    //The dimensions of a single frame
    int frameHeight = 64;
    int frameWidth = 64;
    int numFrames = 6;
    int frameNumber;

    int screenWidth;
    int screenHeight;

    //Stats
    long lastFrameTime;
    int fps;
    int hi;

    //To start the game onTouchEvent
    Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Find out the width and height of the screen
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;

        headAnimation = BitmapFactory.decodeResource(getResources(), R.drawable.head_sprite_sheet);
        bodyAnimation = BitmapFactory.decodeResource(getResources(), R.drawable.body);
        bodyAnimation = Bitmap.createScaledBitmap(bodyAnimation, 200, 200, false);
        tailAnimation = BitmapFactory.decodeResource(getResources(), R.drawable.tail);
        tailAnimation = Bitmap.createScaledBitmap(tailAnimation, 200, 200, false);

        snakeAnimView = new SnakeAnimView(this);
        setContentView(snakeAnimView);
        intent = new Intent(this, GameActivity.class);
    }

    @Override
    protected void onStop(){
        super.onStop();
        while (true){
            snakeAnimView.pause();
            break;
        }

        finish();
    }

    @Override
    protected void onResume(){
        super.onResume();
        snakeAnimView.resume();
    }

    @Override
    protected  void onPause(){
        super.onPause();
        snakeAnimView.pause();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event){
        if (keyCode == KeyEvent.KEYCODE_BACK){
            snakeAnimView.pause();
            finish();
            return true;
        }

        return false;
    }

    class SnakeAnimView extends SurfaceView implements Runnable{

        Thread ourThread = null;
        SurfaceHolder ourHolder;
        volatile boolean playingSnake;
        Paint paint;

        public SnakeAnimView(Context context) {
            super(context);
            ourHolder = getHolder();
            paint = new Paint();
            frameWidth = headAnimation.getWidth() / numFrames;
            frameHeight = headAnimation.getHeight();
        }

        @Override
        public void run() {
            while (playingSnake){
                update();
                drawSnake();
                controlFPS();
            }
        }

        public void update(){
            //which frame should we draw
            rectToBeDrawn = new Rect((frameNumber * frameWidth) - 1, 0,
                    (frameNumber * frameWidth + frameWidth) - 1, frameHeight);

            //now the next frame
            frameNumber++;

            //don't try and draw frames that don't exist
            if (frameNumber == numFrames){
                frameNumber = 0;
            }
        }

        public void drawSnake(){
            if (ourHolder.getSurface().isValid()){
                canvas = ourHolder.lockCanvas();
                //Paint paint = new Paint();
                canvas.drawColor(Color.argb(255, 186, 230, 177));
                paint.setColor(Color.argb(255, 255, 255, 255));
                paint.setTextSize(150);
                canvas.drawText("Snake", 40, 150, paint);
                paint.setTextSize(45);
                canvas.drawText("  Hi Score: " + hi, 40, screenHeight - 50, paint);

                //Draw the snake head
                Rect destRect = new Rect(screenWidth / 2 + 100, screenHeight / 2 - 100,
                                        screenWidth / 2 + 300, screenHeight / 2 + 100);
                canvas.drawBitmap(headAnimation, rectToBeDrawn, destRect, paint);
                canvas.drawBitmap(bodyAnimation, screenWidth / 2 - 100, screenHeight / 2 - 100, paint);
                canvas.drawBitmap(tailAnimation, screenWidth / 2 - 300, screenHeight / 2 - 100, paint);
                ourHolder.unlockCanvasAndPost(canvas);
            }
        }

        public void controlFPS(){
            long timeThisFrame = (System.currentTimeMillis() - lastFrameTime);
            long timeToSleep = 500 - timeThisFrame;
            if (timeThisFrame > 0){
                fps = (int) (1000 / timeThisFrame);
            }

            if (timeToSleep > 0){
                try {
                    ourThread.sleep(timeToSleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            lastFrameTime = System.currentTimeMillis();
        }

        public void pause(){
            playingSnake = false;
            try {
                ourThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void resume(){
            playingSnake = true;
            ourThread = new Thread(this);
            ourThread.start();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event){
            startActivity(intent);
            finish();
            return true;
        }
    }
}
