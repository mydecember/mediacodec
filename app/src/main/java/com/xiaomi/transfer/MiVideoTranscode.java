package com.xiaomi.transfer;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.Queue;

public class MiVideoTranscode implements SurfaceTexture.OnFrameAvailableListener, MoviePlayer.PlayerFeedback  {
    private static final String TAG = "MiVideoTranscode";

    private static final int CMD_INIT = 0x001;
    private static final int CMD_FBO_DRAW = 0x002;
    private static final int CMD_SCREEN_DRAW = 0x003;
    private static final int CMD_RELEASE = 0x004;
    private static final int CMD_READFILE_END = 0x005;
    private static final int CMD_RECODER_END = 0x006;
    private HandlerThread mThread;
    private GLHandler mGlHandler;
    private EglBase mEgl;
    private MoviePlayer player = null;
    private SurfaceTexture mSurfaceTexture;
    private RecordRenderDrawer mRecordDrawer;
    private WaterRenderDrawer mWaterDraer;
    //private String mSourceFile = "/sdcard/voip-data/VID_20190619_201101.mp4";
    //private String mSourceFile = "/sdcard/voip-data/mi_h265_4k.mp4";//
    private String mSourceFile = "/sdcard/voip-data/source_avc_1920_1080.mp4";
    private int mRecoderWidth = 1920;//3840;//1920;
    private int mRecoderHeight = 1080;//2160;//1080;
    private String mPath = "/sdcard/voip-data/result.mp4";
    private String mCodecName = "avc";
    private TransferCallBack mCallBack;
    private boolean mPlayerExit = false;

    private boolean mExit = false;

    private int mFrameNums = 0;
    public MiVideoTranscode() {
        mRecordDrawer = new RecordRenderDrawer(this);
        mOriginalDrawer = new OriginalRenderDrawer();
        mWaterDraer = new WaterRenderDrawer(null);
//        mThread = new HandlerThread("GL thread");
//        mThread.start();
//        mGlHandler = new GLHandler(mThread.getLooper());
//        Message msg = mGlHandler.obtainMessage(CMD_INIT);
//        mGlHandler.removeMessages(CMD_INIT);
//        mGlHandler.sendMessageDelayed(msg, 0);
    }

    public interface TransferCallBack {
        public void onTransferEnd();
        public void onTransferFailed();
    }

    public MoviePlayer getPlayer() {
        return player;
    }

    boolean mIsStarted = false;

    public void startTransfer(String sourcePath, String codecName, int targetWidth, int targetHeight, String targetPath, TransferCallBack callBack) {
        mCallBack = callBack;
        mSourceFile = sourcePath;
        mRecoderWidth = targetWidth;
        mRecoderHeight = targetHeight;
        mPath = targetPath;
        mCodecName = codecName;

        mExit = false;mIsStarted = true;

        mThread = new HandlerThread("GL thread");
        mThread.start();
        mGlHandler = new GLHandler(mThread.getLooper());
        Message msg = mGlHandler.obtainMessage(CMD_INIT);
        mGlHandler.removeMessages(CMD_INIT);
        mGlHandler.sendMessageDelayed(msg, 1000);
        mRecordDrawer.setParams(codecName, mRecoderWidth, mRecoderHeight, 30, mPath);
        Log.i(TAG, " startTransfer");
        mPlayerExit = false;
    }

    public void stopTransfer(TransferCallBack callBack) {
        if (!mIsStarted) {
            return;
        }
        if (player != null) {
            player.requestStop();
        }
        mIsStarted = false;

        //mGlHandler.sendEmptyMessage(CMD_READFILE_END);
        //mGlHandler.sendEmptyMessage(CMD_RELEASE);
        while(true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mExit == true && mPlayerExit == true)
                break;
        }
        mThread.quit();
        mThread = null;
        Log.i(TAG, "startTransfer stopTransfer");
    }

    private float mFps = 30;

    private long mCaptureFrameDel = 0;

    public class Item {
        public long value;
        public Item(long v) {
            value = v;
        }
    }
    private Queue<Item> mQueue = new LinkedList<Item>();

    public void onRecoderEOF() {
        mGlHandler.sendEmptyMessage(CMD_RELEASE);
        mExit = true;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (!mIsStarted) {
            return;
        }
        mGlHandler.removeMessages(CMD_FBO_DRAW);
        mGlHandler.sendEmptyMessage(CMD_FBO_DRAW);

        long t1 = System.currentTimeMillis();
        Item item = new Item(t1);
        if (mCaptureFrameDel == 0) {
            mCaptureFrameDel = t1;
        } else {
            Log.i(TAG, "capture delta " + (t1 - mCaptureFrameDel));
        }

        mQueue.add(item);
        if (mQueue.size() > 15)
            mQueue.poll();
        double fps = 1;
        if (mQueue.size() > 1)
        fps = 1.0*(item.value - mQueue.peek().value) / mQueue.size();


        if (t1 - mCaptureFrameDel != 0)
            mFps = (mFps*14 + 1000.0f/(t1 - mCaptureFrameDel)) / 15;
        Log.i(TAG, "real fps = " + mFps + " pid "  + Thread.currentThread().getId() + " avg fps " + 1000.0/fps);
        mCaptureFrameDel = t1;
    }

    private int mCaptureOne = 0;

    @Override
    public void playbackStopped() {
        mPlayerExit = true;
        mGlHandler.sendEmptyMessage(CMD_READFILE_END);
    }

    private int mNums = 0;

    private int mCameraTextureId;
    private EGLSurface mGLSurface;
    private OriginalRenderDrawer mOriginalDrawer;
    private int mFrameBuffer;
    private long mStartTime = 0;
    private class GLHandler extends Handler {

        private GLHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CMD_INIT:
                    mEgl = EglBase.create();
                    mEgl.createPbufferSurface(GlUtil.mWidht, GlUtil.mHeight);
                    mEgl.makeCurrent();
                    mFrameBuffer = GlesUtil.createFrameBuffer();


                    mCameraTextureId = GlesUtil.createCameraTexture();
                    mSurfaceTexture = new SurfaceTexture(mCameraTextureId);
                    mSurfaceTexture.setOnFrameAvailableListener(MiVideoTranscode.this);
                    Play();

                    mOriginalDrawer.create();
                    mOriginalDrawer.setInputTextureId(mCameraTextureId);
                    mOriginalDrawer.surfaceChangedSize(GlUtil.mWidht, GlUtil.mHeight);
                    int textureId = mOriginalDrawer.getOutputTextureId();

                    //GlesUtil.bindFrameTexture(mFrameBuffer, textureId);

//                    mWaterDraer.create();
//                    mWaterDraer.setInputTextureId(textureId);
//                    mWaterDraer.surfaceChangedSize(GlUtil.mWidht, GlUtil.mHeight);

                    mRecordDrawer.setParams(mCodecName, mRecoderWidth, mRecoderHeight, 30, mPath);
                    mRecordDrawer.create();
                    mRecordDrawer.surfaceChangedSize(mRecoderWidth, mRecoderHeight);
                    mRecordDrawer.setInputTextureId(textureId);
                    mRecordDrawer.startRecord();
                    break;
                case CMD_FBO_DRAW:
                    mSurfaceTexture.updateTexImage();
                    float[] mtx = new float[16];;
                    mSurfaceTexture.getTransformMatrix(mtx);
                    long timeStamp = mSurfaceTexture.getTimestamp();
                    GlesUtil.bindFrameBuffer(mFrameBuffer, mOriginalDrawer.getOutputTextureId());

                    mOriginalDrawer.draw(timeStamp, mtx);
                    GLES30.glFlush();
                    GlesUtil.unBindFrameBuffer();


//                    GlesUtil.bindFrameBuffer(mFrameBuffer, mWaterDraer.getOutputTextureId());
//                    mFrameNums++;
//                    mWaterDraer.setWater(timeStamp/1000/1000 + " num:" + mFrameNums );
//                    mWaterDraer.draw(timeStamp, mtx);
//                    GLES30.glFlush();
//                    GlesUtil.unBindFrameBuffer();

                    if (mCaptureOne < 0) {
                        // save bmp
                        ByteBuffer mBuffer = ByteBuffer.allocateDirect(GlUtil.mWidht * GlUtil.mHeight * 4);
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mFrameBuffer);
                        ByteBuffer buf =  mBuffer;
                        buf.order(ByteOrder.LITTLE_ENDIAN);
                        GLES30.glReadPixels(0, 0, GlUtil.mWidht, GlUtil.mHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf);
                        buf.rewind();

                        Bitmap bmp = Bitmap.createBitmap(GlUtil.mWidht, GlUtil.mHeight, Bitmap.Config.ARGB_8888);
                        bmp.copyPixelsFromBuffer(buf);
                        //afterDraw();
                        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                        mCaptureOne++;
                        GlUtil.saveFile(bmp, "/sdcard/kk", "kkk" + mCaptureOne+ ".jpeg");
                    }
                    mNums++;
                    Log.i(TAG, " to draw present " + mNums + " time " + timeStamp + " " );

                    mRecordDrawer.draw(timeStamp, mtx);

                    break;
                case CMD_SCREEN_DRAW:
                    break;
                case CMD_READFILE_END:
                    if (mRecordDrawer != null)
                        mRecordDrawer.stopRecord();


                    break;
                case CMD_RELEASE:
                    if (mOriginalDrawer != null) {
                        GlesUtil.deleteFrameBuffer(mFrameBuffer, mOriginalDrawer.getOutputTextureId());
                        Log.i(TAG, " detete frame ");
                    }

                    if (mRecordDrawer != null) {
                        mRecordDrawer.quit();
                        mRecordDrawer = null;
                    }
                    mOriginalDrawer = null;
                    if (mEgl != null) {
                        mEgl.release();
                    }
                    mExit = true;
                    mEgl = null;
                    Log.i(TAG, " recoder end ");

                    if (mCallBack != null) {
                        mCallBack.onTransferEnd();
                    }
                    break;
            }
        }
    }

    MoviePlayer.PlayTask mPlayTask;
    public void Play() {
        Surface surface = new Surface(mSurfaceTexture);
        try {
            player = new MoviePlayer(new File(mSourceFile), surface, null);
        } catch (IOException ioe) {
            Log.e(TAG, "Unable to play movie", ioe);
            surface.release();
            return;
        }
        if (mRecoderWidth == 0 || mRecoderHeight == 0) {
            mRecoderWidth = player.getVideoWidth();
            mRecoderHeight = player.getVideoHeight();
        }

        mPlayTask = new MoviePlayer.PlayTask(player, this);

        mPlayTask.execute();
        Log.i(TAG, "start play");
    }

}
