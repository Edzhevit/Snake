package com.example.snake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.SoundPool;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Random;

public class GameActivity extends Activity {

    Canvas canvas;
    SnakeView snakeView;

    Bitmap head;
    Bitmap body;
    Bitmap tail;
    Bitmap apple;
    Bitmap flower;

    //for animating the flower
    Rect flowerToBeDrawn;
    int frameHeight;
    int frameWidth;
    int flowerNumFrames = 2;
    int flowerFrameNumber;
    int flowerAnimTimer = 0;

    //Sound
    private SoundPool soundPool;
    int sample1 = -1;
    int sample2 = -1;
    int sample3 = -1;
    int sample4 = -1;

    //for snake movement
    int directionOfTravel = 0;
    // 0 = up, 1 = right, 2 = down, 3 = left

    int screenWidth;
    int screenHeight;
    int topGap;

    //stats
    long lastFrameTime;
    int fps;
    int score;
    int hi;

    //Game objects
    int[] snakeX;
    int[] snakeY;
    int[] snakeH;
    int snakeLength;
    int appleX;
    int appleY;

    //matrix objects will rotate our snake segments
    Matrix matrix90 = new Matrix();
    Matrix matrix180 = new Matrix();
    Matrix matrix270 = new Matrix();
    Matrix matrixHeadFlip = new Matrix();


    //and the pretty flowers
    int[] flowerX;
    int[] flowerY;

    //the size in pixels of a place on the game board
    int blockSize;
    int numBlocksWide;
    int numBlocksHigh;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSound();
        configureDisplay();
        snakeView = new SnakeView(this);
        setContentView(snakeView);
    }

    @Override
    protected void onStop(){
        super.onStop();
        while (true){
            snakeView.pause();
            break;
        }

        finish();
    }

    @Override
    protected void onResume(){
        super.onResume();
        snakeView.resume();
    }

    @Override
    protected  void onPause(){
        super.onPause();
        snakeView.pause();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event){
        if (keyCode == KeyEvent.KEYCODE_BACK){
            snakeView.pause();
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
            finish();
            return true;
        }

        return false;
    }

    public void loadSound(){
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        try {
            AssetManager assetManager = getAssets();
            AssetFileDescriptor descriptor;

            descriptor = assetManager.openFd("sample1.ogg");
            sample1 = soundPool.load(descriptor, 0);

            descriptor = assetManager.openFd("sample2.ogg");
            sample2 = soundPool.load(descriptor, 0);

            descriptor = assetManager.openFd("sample3.ogg");
            sample3 = soundPool.load(descriptor, 0);

            descriptor = assetManager.openFd("sample4.ogg");
            sample4 = soundPool.load(descriptor, 0);
        } catch (IOException e) {
            Log.e("error", "failed to load sound files");
        }
    }

    public void configureDisplay(){
        //find out the width and height of the screen
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
        topGap = screenHeight/14;

        //Determine the size of each block/place on the game board
        blockSize = screenWidth/30;

        //Determine how many game blocks will fit into the height and width
        //Leave one block for the score at the top
        numBlocksWide = 30;
        numBlocksHigh = ((screenHeight - topGap ))/blockSize;

        //Load and scale bitmaps
        head = BitmapFactory.decodeResource(getResources(), R.drawable.head);
        body = BitmapFactory.decodeResource(getResources(), R.drawable.body);
        //tailBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.tail);
        apple = BitmapFactory.decodeResource(getResources(), R.drawable.apple);

        //scale the bitmaps to match the block size
        head = Bitmap.createScaledBitmap(head, blockSize, blockSize, false);
        body = Bitmap.createScaledBitmap(body, blockSize, blockSize, false);
        //tailBitmap = Bitmap.createScaledBitmap(tailBitmap, blockSize, blockSize, false);
        apple = Bitmap.createScaledBitmap(apple, blockSize, blockSize, false);

        //for the tail
        tail = BitmapFactory.decodeResource(getResources(), R.drawable.tail_sprite_sheet);
        tail = Bitmap.createScaledBitmap(tail, blockSize*flowerNumFrames, blockSize, false);

        //for the flower
        flower = BitmapFactory.decodeResource(getResources(), R.drawable.flower_sprite_sheet);
        flower = Bitmap.createScaledBitmap(flower, blockSize*flowerNumFrames, blockSize, false);

        //These two lines work for the flower and the tail
        frameWidth=flower.getWidth()/flowerNumFrames;
        frameHeight=flower.getHeight();

        //Initialize matrix objects ready for us in drawGame
        matrix90.postRotate(90);
        matrix180.postRotate(180);
        matrix270.postRotate(270);
        //And now the head flipper
        matrixHeadFlip.setScale(-1,1);
        matrixHeadFlip.postTranslate(head.getWidth(),0);

        //setup the first frame of the flower drawing
        flowerToBeDrawn = new Rect((flowerFrameNumber * frameWidth), 0,
                (flowerFrameNumber * frameWidth +frameWidth)-1, frameHeight);


    }



    class SnakeView extends SurfaceView implements Runnable{

        Thread ourThread = null;
        SurfaceHolder ourHolder;
        volatile boolean playingSnake;
        Paint paint;

        public SnakeView(Context context) {
            super(context);
            ourHolder = getHolder();
            paint = new Paint();
            snakeX = new int[200];
            snakeY = new int[200];
            snakeH = new int[200];

            plantFlowers();
            getSnake();
            getApple();
        }

        public void plantFlowers(){
            Random random = new Random();
            int x = 0;
            int y = 0;
            flowerX = new int[200];
            flowerY = new int[200];
            for (int i = 0; i < 10; i++) {
                x = random.nextInt(numBlocksWide - 1) + 1;
                y = random.nextInt(numBlocksHigh - 1) + 1;
                flowerX[i] = x;
                flowerY[i] = y;
            }
        }

        public void getSnake(){
            snakeLength = 3;
            //start snake head in the middle of the screen
            snakeX[0] = numBlocksWide / 2;
            snakeY[0] = numBlocksHigh / 2;

            //the the body
            snakeX[1] = snakeX[0] - 1;
            snakeY[1] = snakeY[0];

            //and the tail
            snakeX[1] = snakeX[1] - 1;
            snakeY[1] = snakeY[0];
        }
        public void getApple(){
            Random random = new Random();
            appleX = random.nextInt(numBlocksWide - 1) + 1;
            appleY = random.nextInt(numBlocksHigh - 1) + 1;
        }

        @Override
        public void run() {
            while (playingSnake){
                updateGame();
                drawGame();
                controlFPS();
            }
        }

        public void updateGame(){
            //did the player get the apple
            if (snakeX[0] == appleX && snakeY[0] == appleY){
                //grow the snake
                snakeLength++;
                //replace the apple
                getApple();
                //add to score
                score += snakeLength;
                soundPool.play(sample1, 1, 1, 0, 0, 1);
            }

            for (int i = snakeLength; i > 0; i--) {
                snakeX[i] = snakeX[i - 1];
                snakeY[i] = snakeY[i - 1];
                snakeH[i] = snakeH[i - 1];

            }

            //move the head to appropriate direction
            switch (directionOfTravel){
                //up
                case 0:
                    snakeY[0]--;
                    snakeH[0] = 0;
                    break;
                //right
                case 1:
                    snakeX[0]++;
                    snakeH[0] = 1;
                    break;
                //down
                case 2:
                    snakeY[0]++;
                    snakeH[0] = 2;
                    break;
                //left
                case 3:
                    snakeX[0]--;
                    snakeH[0] = 3;
                    break;
            }

            //have we had an accident
            boolean dead = false;
            //with a wall
            if (snakeX[0] == -1){
                dead = true;
            }
            if (snakeX[0] >= numBlocksWide){
                dead = true;
            }
            if (snakeY[0] == -1){
                dead = true;
            }
            if (snakeY[0] == numBlocksHigh){
                dead = true;
            }
            //or eaten ourselves
            for (int i = snakeLength - 1; i > 0; i--) {
                if ((i > 4) && (snakeX[0] == snakeX[i]) && (snakeY[0] == snakeY[i])){
                    dead = true;
                }

            }

            if (dead){
                //start again
                soundPool.play(sample4, 1, 1, 0, 0, 1);
                score = 0;
                getSnake();
            }
        }

        public void drawGame(){
            if (ourHolder.getSurface().isValid()){
                canvas = ourHolder.lockCanvas();
                canvas.drawColor(Color.argb(255,186,230,177));
                paint.setColor(Color.argb(255, 255, 255, 255));
                paint.setTextSize(topGap / 2);
                canvas.drawText("Score: " + score + " Hi: " + hi, 10, topGap - 6, paint);

                //draw a border - 4 lines, top right, bottom, left
                paint.setStrokeWidth(3);
                canvas.drawLine(1, topGap, screenWidth - 1, topGap, paint);
                canvas.drawLine(screenWidth - 1, topGap, screenWidth - 1,
                        topGap + (numBlocksHigh * blockSize), paint);
                canvas.drawLine(screenWidth - 1, topGap + (numBlocksHigh * blockSize),
                        1, topGap + (numBlocksHigh * blockSize), paint);
                canvas.drawLine(1, topGap, 1, topGap + (numBlocksHigh * blockSize), paint);

                //draw the flower
                Rect destRect;
                Bitmap rotatedBitmap;
                Bitmap rotatedTailBitmap;

                for (int i = 0; i < 10; i++) {
                    destRect = new Rect(flowerX[i]*blockSize, (flowerY[i]*blockSize)+topGap,
                            (flowerX[i]*blockSize)+blockSize, (flowerY[i]*blockSize)+topGap+blockSize);
                    canvas.drawBitmap(flower, flowerToBeDrawn, destRect, paint);
                }

                //draw the snake
                rotatedBitmap = head;
                switch (snakeH[0]){
                    case 0://up
                        rotatedBitmap = Bitmap.createBitmap(rotatedBitmap , 0, 0, rotatedBitmap .getWidth(), rotatedBitmap .getHeight(), matrix270, true);
                        break;
                    case 1://right
                        //no rotation necessary

                        break;
                    case 2://down
                        rotatedBitmap = Bitmap.createBitmap(rotatedBitmap , 0, 0, rotatedBitmap .getWidth(), rotatedBitmap .getHeight(), matrix90, true);
                        break;

                    case 3://left
                        rotatedBitmap = Bitmap.createBitmap(rotatedBitmap , 0, 0, rotatedBitmap .getWidth(), rotatedBitmap .getHeight(), matrixHeadFlip, true);
                        break;


                }
                canvas.drawBitmap(rotatedBitmap, snakeX[0]*blockSize, (snakeY[0]*blockSize)+topGap, paint);
                //draw the body
                rotatedBitmap = body;
                for(int i = 1; i < snakeLength-1;i++){

                    switch (snakeH[i]){
                        case 0://up
                            rotatedBitmap = Bitmap.createBitmap(body , 0, 0, body .getWidth(), body .getHeight(), matrix270, true);
                            break;
                        case 1://right
                            //no rotation necessary

                            break;
                        case 2://down
                            rotatedBitmap = Bitmap.createBitmap(body , 0, 0, body .getWidth(), body .getHeight(), matrix90, true);
                            break;

                        case 3://left
                            rotatedBitmap = Bitmap.createBitmap(body , 0, 0, body .getWidth(), body .getHeight(), matrix180, true);
                            break;


                    }

                    canvas.drawBitmap(rotatedBitmap, snakeX[i]*blockSize, (snakeY[i]*blockSize)+topGap, paint);
                }
                //draw the tail
                rotatedTailBitmap = Bitmap.createBitmap(tail, flowerToBeDrawn.left, flowerToBeDrawn.top, flowerToBeDrawn.right - flowerToBeDrawn.left, flowerToBeDrawn.bottom);

                switch (snakeH[snakeLength-1]){
                    case 0://up
                        rotatedTailBitmap = Bitmap.createBitmap(rotatedTailBitmap , 0, 0, rotatedTailBitmap .getWidth(), rotatedTailBitmap .getHeight(), matrix270, true);
                        break;
                    case 1://right
                        //no rotation necessary

                        break;
                    case 2://down
                        rotatedTailBitmap = Bitmap.createBitmap(rotatedTailBitmap , 0, 0, rotatedTailBitmap .getWidth(), rotatedTailBitmap .getHeight(), matrix90, true);
                        break;

                    case 3://left
                        rotatedTailBitmap = Bitmap.createBitmap(rotatedTailBitmap , 0, 0, rotatedTailBitmap .getWidth(), rotatedTailBitmap .getHeight(), matrix180, true);
                        break;


                }

                canvas.drawBitmap(rotatedTailBitmap, snakeX[snakeLength-1]*blockSize, (snakeY[snakeLength-1]*blockSize)+topGap, paint);

                //draw the apple
                canvas.drawBitmap(apple, appleX * blockSize, (appleY * blockSize) + topGap, paint);

                ourHolder.unlockCanvasAndPost(canvas);
            }
        }

        public void controlFPS(){
            long timeThisFrame = (System.currentTimeMillis() - lastFrameTime);
            long timeToSleep = 100 - timeThisFrame;
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

            //control the flower animation
            flowerAnimTimer++;
            //change the frame every 6 game frames
            if(flowerAnimTimer == 6){
                //which frame should we draw
                if(flowerFrameNumber == 1){
                    flowerFrameNumber = 0;
                }else{
                    flowerFrameNumber =1;
                }

                flowerToBeDrawn = new Rect((flowerFrameNumber * frameWidth), 0,
                        (flowerFrameNumber * frameWidth +frameWidth)-1, frameHeight);

                flowerAnimTimer = 0;
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
            switch (event.getAction() & MotionEvent.ACTION_MASK){
                case MotionEvent.ACTION_UP:
                    if (event.getX() >= screenWidth / 2){
                        //turn right
                        directionOfTravel++;
                        if (directionOfTravel == 4){
                            //loop back to 0
                            directionOfTravel = 0;
                        }
                    } else {
                        //turn left
                        directionOfTravel--;
                        if (directionOfTravel == -1){
                            directionOfTravel = 3;
                        }
                    }
            }
            return true;
        }
    }
}
