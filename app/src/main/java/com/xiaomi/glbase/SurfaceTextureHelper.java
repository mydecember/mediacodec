package com.xiaomi.glbase;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.xiaomi.demuxer.HWAVFrame;
import com.xiaomi.drawers.OriginalDrawer;

import java.util.concurrent.Callable;

public class SurfaceTextureHelper {
    private static final String TAG = "SurfaceTextureHelper";
    private final Handler handler;
    private final EglBase eglBase;
    private final SurfaceTexture surfaceTexture;
    private final int oesTextureId;

    private Object listener;
    // The possible states of this class.
    private boolean hasPendingTexture = false;
    private volatile boolean isTextureInUse = false;
    private boolean isQuitting = false;
    private int frameRotation;
    private int textureWidth;
    private int textureHeight;

    OriginalDrawer mOriginalDrawer;
    public interface VideoSink {
        void onFrame(HWAVFrame frame);
    }

    private Object pendingListener;
    final Runnable setListenerRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Setting listener to " + pendingListener);
            listener = pendingListener;
            pendingListener = null;
            // May have a pending frame from the previous capture session - drop it.
            if (hasPendingTexture) {
                // Calling updateTexImage() is neccessary in order to receive new frames.
                updateTexImage();
                hasPendingTexture = false;
            }
        }
    };

    public static SurfaceTextureHelper create(final String threadName, final EglBase.Context sharedContext, final int w, final int h) {
        final HandlerThread thread = new HandlerThread(threadName);
        thread.start();
        final Handler handler = new Handler(thread.getLooper());
        return ThreadUtils.invokeAtFrontUninterruptibly(handler, new Callable<SurfaceTextureHelper>() {
            @Override
            public SurfaceTextureHelper call() {
                try {
                    return new SurfaceTextureHelper(sharedContext, handler, w, h);
                } catch (RuntimeException e) {
                    Log.e(TAG, threadName + " create failure", e);
                    return null;
                }
            }
        });
    }

    private SurfaceTextureHelper(EglBase.Context sharedContext, Handler handler, int widht, int height) {
        if (handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("SurfaceTextureHelper must be created on the handler thread");
        }
        this.handler = handler;

        eglBase = EglBase.create(sharedContext);
        try {
            eglBase.createPbufferSurface(widht, height);
            eglBase.makeCurrent();
        } catch (RuntimeException e) {
            // Clean up before rethrowing the exception.
            eglBase.release();
            handler.getLooper().quit();
            throw e;
        }

        oesTextureId = GlesUtil.createCameraTexture();
        surfaceTexture = new SurfaceTexture(oesTextureId);
        /*
        (SurfaceTexture surfaceTexture,
                                                    SurfaceTexture.OnFrameAvailableListener listener, Handler handler
         */
        setOnFrameAvailableListener(surfaceTexture, new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                hasPendingTexture = true;
                tryDeliverTextureFrame();
            }
        }, handler);
//        setOnFrameAvailableListener(surfaceTexture, (SurfaceTexture st) -> {
//            hasPendingTexture = true;
//            tryDeliverTextureFrame();
//        }, handler);

        mOriginalDrawer = new OriginalDrawer();
        mOriginalDrawer.create();
        mOriginalDrawer.surfaceChangedSize(widht, height);
        mOriginalDrawer.setInputTextureId(oesTextureId);
    }

    private void updateTexImage() {
        synchronized (EglBase.lock) {
            surfaceTexture.updateTexImage();
        }
    }

    private static void setOnFrameAvailableListener(SurfaceTexture surfaceTexture,
                                                    SurfaceTexture.OnFrameAvailableListener listener, Handler handler) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            surfaceTexture.setOnFrameAvailableListener(listener, handler);
        } else {
            surfaceTexture.setOnFrameAvailableListener(listener);
        }
    }

    public void setTextureSize(int textureWidth1, int textureHeight1) {
        if (textureWidth1 <= 0) {
            throw new IllegalArgumentException("Texture width must be positive, but was " + textureWidth1);
        }
        if (textureHeight1 <= 0) {
            throw new IllegalArgumentException(
                    "Texture height must be positive, but was " + textureHeight1);
        }
        surfaceTexture.setDefaultBufferSize(textureWidth1, textureHeight1);
        final SurfaceTextureHelper help = this;
        handler.post(new Runnable() {
            @Override
            public void run() {
                textureWidth = textureWidth1;
                textureHeight = textureHeight1;
            }
        });
//        handler.post(() -> {
//            this.textureWidth = textureWidth;
//            this.textureHeight = textureHeight;
//        });
    }

    public void setFrameRotation(int rotation) {
        //final  SurfaceTextureHelper help = this;
        handler.post(new Runnable() {
            @Override
            public void run() {
                frameRotation = rotation;
            }
        });
        //handler.post(() -> this.frameRotation = rotation);
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public Handler getHandler() {
        return handler;
    }

    public boolean isTextureInUse() {
        return isTextureInUse;
    }

    public void dispose() {
        Log.d(TAG, "dispose()");
        ThreadUtils.invokeAtFrontUninterruptibly(handler, new Runnable() {
            @Override
            public void run() {
                isQuitting = true;
                if (!isTextureInUse) {
                    release();
                }
            }
        });
//        ThreadUtils.invokeAtFrontUninterruptibly(handler, () -> {
//            isQuitting = true;
//            if (!isTextureInUse) {
//                release();
//            }
//        });
    }

    public void returnTextureFrame() {
        SurfaceTextureHelper help = this;
        handler.post(new Runnable() {
            @Override
            public void run() {
                isTextureInUse = false;
                if (isQuitting) {
                    release();
                } else {
                    tryDeliverTextureFrame();
                }
            }
        });

//        handler.post(() -> {
//            isTextureInUse = false;
//            if (isQuitting) {
//                release();
//            } else {
//                tryDeliverTextureFrame();
//            }
//        });
    }

    private void tryDeliverTextureFrame() {
        if (handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("Wrong thread.");
        }
        if (isQuitting || !hasPendingTexture || isTextureInUse || listener == null) {
            return;
        }
        isTextureInUse = true;
        hasPendingTexture = false;
        updateTexImage();
        final float[] transformMatrix = new float[16];
        surfaceTexture.getTransformMatrix(transformMatrix);
        final long timestampNs = surfaceTexture.getTimestamp();

        mOriginalDrawer.draw(timestampNs, transformMatrix);

        int textureId = mOriginalDrawer.getOutputTextureId();
        //textureId = oesTextureId;

        if (listener instanceof VideoSink) {
            if (textureWidth == 0 || textureHeight == 0) {
                throw new RuntimeException("Texture size has not been set.");
            }
            HWAVFrame frame = new HWAVFrame();
            frame.mColorFomat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
            frame.mTimeStamp = timestampNs / 1000;
            frame.mIsAudio = false;
            frame.mWidth = textureWidth;
            frame.mHeight = textureHeight;
            frame.mTextureId = textureId;
            ((VideoSink) listener).onFrame(frame);
            //frame.release();
            returnTextureFrame();
        }
    }

    private void release() {
        if (handler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException("Wrong thread.");
        }
        if (isTextureInUse || !isQuitting) {
            throw new IllegalStateException("Unexpected release.");
        }
        GLES20.glDeleteTextures(1, new int[] {oesTextureId}, 0);
        surfaceTexture.release();
        mOriginalDrawer.release();
        eglBase.release();
        handler.getLooper().quit();
    }

    public void startListening(final VideoSink listener) {
        startListeningInternal(listener);
    }

    private void startListeningInternal(Object listener) {
        if (this.listener != null || this.pendingListener != null) {
            throw new IllegalStateException("SurfaceTextureHelper listener has already been set.");
        }
        this.pendingListener = listener;
        handler.post(setListenerRunnable);
    }

    public void stopListening() {
        Log.d(TAG, "stopListening()");
        handler.removeCallbacks(setListenerRunnable);
        ThreadUtils.invokeAtFrontUninterruptibly(handler, new Runnable() {
            @Override
            public void run() {
                listener = null;
                pendingListener = null;
            }
        });
//        ThreadUtils.invokeAtFrontUninterruptibly(handler, () -> {
//            listener = null;
//            pendingListener = null;
//        });
    }
}

