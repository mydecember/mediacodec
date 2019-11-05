package com.xiaomi.demuxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.xiaomi.glbase.EglBase;
import com.xiaomi.glbase.SurfaceTextureHelper;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HWDecoder implements SurfaceTextureHelper.VideoSink{
    private static final String TAG = "HWDecoder";

    private boolean mDump = false;
    private String mDumpPath = "/sdcard/voip-data/dump_muxer";
    private FileOutputStream mOutputStream;

    private static final int[] supportedColorList = {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
    };

    private HWAVFrame mFrame = new HWAVFrame();
    private boolean mIsAudio = true;
    private MediaCodec mDecoder;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private HWDecoderCallback mCallback;
    private boolean mIsAsync = false;
    private Surface mOutputSurface;
    private EglBase.Context mSharedContext;
    private EglBase mEgl;
    private SurfaceTextureHelper mSurfaceHelper;
    private final Object renderLock = new Object();
    private final Object mWaitEvent = new Object();
    private volatile boolean mNoWait = false;
    private HWAVFrame mTextureFrame = new HWAVFrame();

    public void setCallBack(HWDecoderCallback call) {
        mCallback = call;
    }

    interface HWDecoderCallback {
        public void onHWDecoderFrame(HWAVFrame frame);
    }

    private static class DecoderProperties {
        DecoderProperties(String codecName, int colorFormat) {
            this.codecName = codecName;
            this.colorFormat = colorFormat;
        }
        public final String codecName; // OpenMax component name for HEVC codec.
        public final int colorFormat;  // Color format supported by codec.
    }

    private void initDump() {
        if (mDump) {
            try {
                if (mIsAudio)
                    mOutputStream = new FileOutputStream(mDumpPath + ".pcm");
                else
                    mOutputStream = new FileOutputStream(mDumpPath + ".yuv");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

    }

    public void release() {
        if (mOutputSurface != null) {
            mOutputSurface.release();
            mOutputSurface = null;
            mSurfaceHelper.stopListening();
            mSurfaceHelper.dispose();
            mSurfaceHelper = null;
        }
        if (mDump) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int initialize(MediaFormat format, boolean isAsync, boolean useSurface) {
        mIsAsync = isAsync;
        String mine = format.getString(MediaFormat.KEY_MIME);
        Log.i(TAG, "hwdecoder to init format " + format.toString() + " use async " + mIsAsync);
        //format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
        //findHwEncoder(mine, false);
        if (mine.startsWith("audio/")) {
            mIsAudio = true;

        } else {
            mFrame.mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mFrame.mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
            mFrame.mStride = mFrame.mWidth;
            mFrame.mCropTop = 0;
            mFrame.mCropButtom = mFrame.mHeight - 1;
            mFrame.mCropLeft = 0;
            mFrame.mCropRight = mFrame.mWidth - 1;
            mIsAudio = false;

            if (useSurface) {
                mSharedContext = EglBase.getCurrentContext();
                mSurfaceHelper = SurfaceTextureHelper.create("surface-decoder", mSharedContext, mFrame.mWidth, mFrame.mHeight);
                mOutputSurface = new Surface(mSurfaceHelper.getSurfaceTexture());
                mSurfaceHelper.startListening(this);
            }
        }

        try {
            mDecoder = MediaCodec.createDecoderByType(mine);
            Log.i(TAG, "created decoder name is " + mDecoder.getName() + " color " + mDecoder.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        try {
            mDecoder.configure(format, mOutputSurface, null,0);
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        mDecoder.start();
        running = true;
        if (mIsAsync) {
            createOutputThread().start();
        }
        Log.i(TAG, "created decoder ok name is " + mDecoder.getName() + " format " + mDecoder.toString());
        initDump();
        return 0;
    }

    public void reset() {
        mDecoder.flush();
    }

    public boolean getIsAudio() {
        return mIsAudio;
    }

    public int getNextDecoderBufferIndex() {

        int inputIndex = -1;
        try {
            inputIndex = mDecoder.dequeueInputBuffer(-1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return inputIndex;
    }

    public ByteBuffer getNextDecoderBuffer(int index) {
        ByteBuffer buf = mDecoder.getInputBuffer(index);
        buf.clear();
        return buf;
    }

    public void queueInputBuffer(int index, int samples, long timeStamp, boolean end) {
        mDecoder.queueInputBuffer(index, 0, samples, timeStamp, end == true ? MediaCodec.BUFFER_FLAG_END_OF_STREAM:0);

    }
    private  boolean running = false;

    private Thread createOutputThread() {
        return new Thread("Mediacodec_outputThread") {
            public void run() {
                while (running) {
                    deliverDecodedFrame();
                    if (!running) {
                        Log.i(TAG, "decoder end");
                    }
                }
            }
        };
    }

    private  void deliverDecodedFrame() {
       // Log.i(TAG," to read frame");
        HWAVFrame frame = InternalReadFrame();
        if (mCallback!=null) {
            mCallback.onHWDecoderFrame(frame);
        }
        if (frame.mGotFrame) {
            releaseFream(frame.mIdx);
        }
    }

    HWAVFrame InternalReadFrame() {
        mFrame.mGotFrame = false;
        mFrame.mIsAudio = mIsAudio;
        mFrame.mIdx = -1;
        mFrame.mBuffer = null;
        int wait = 0;
        if (mIsAsync) {
            wait = 200000;
        } else {
            wait = 0;
        }
        int oIdx = mDecoder.dequeueOutputBuffer(mBufferInfo, wait);
        if (oIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
            //Log.i(TAG, "== AMEDIACODEC_INFO_TRY_AGAIN_LATER is audio " + mIsAudio);
        } else if (oIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = mDecoder.getOutputFormat();
            Log.i(TAG, "== AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED " + newFormat.toString() );
            if (!mIsAudio) {

                Log.d(TAG, "decoder output format changed: " + newFormat);

                if(newFormat.containsKey("crop-top"))
                {
                    int cropTop = newFormat.getInteger("crop-top");
                    mFrame.mCropTop = cropTop;
                    Log.d(TAG, "Crop-top:" + cropTop);
                }
                if(newFormat.containsKey("crop-bottom"))
                {
                    int cropBottom = newFormat.getInteger("crop-bottom");
                    mFrame.mCropButtom = cropBottom;
                    Log.d(TAG, "Crop-bottom:" + cropBottom);
                }
                if(newFormat.containsKey("crop-left"))
                {
                    int cropLeft = newFormat.getInteger("crop-left");
                    mFrame.mCropLeft = cropLeft;
                    Log.d(TAG, "Crop-left:" + cropLeft);
                }
                if(newFormat.containsKey("crop-right"))
                {
                    int cropRight = newFormat.getInteger("crop-right");
                    mFrame.mCropRight = cropRight;
                    Log.d(TAG, "Crop-right:" + cropRight);
                }
                int width = newFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = newFormat.getInteger(MediaFormat.KEY_HEIGHT);
                Log.d(TAG, "width :" + width + " height:" + height );
                // 判断输出格式是否支持
                if (newFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                    newFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                    Log.d(TAG, "Color format:" + newFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT));
                    mFrame.mColorFomat = newFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                }
                int keyStride = newFormat.getInteger(MediaFormat.KEY_STRIDE);
                int keyStrideHeight = newFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT);
                mFrame.mWidth = mFrame.mCropRight - mFrame.mCropLeft + 1;
                mFrame.mHeight = mFrame.mCropButtom - mFrame.mCropTop + 1;
                mFrame.mStride = keyStride;
                mFrame.mStrideHeight = keyStrideHeight;
                Log.d(TAG, " stride:" + keyStride +  " height stride:" + keyStrideHeight );
            } else {
                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    mFrame.mAudioChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
                if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    mFrame.mAudioSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                }
                Log.i(TAG, "zfq get audio channels=" +  mFrame.mAudioChannels + " sample_rate=" + mFrame.mAudioSampleRate);

            }

        } else if (oIdx < 0) {
            Log.i(TAG,"should not happen oIdx==" + oIdx);
        } else {
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM )!= 0) {
                mFrame.mStreamEOF = true;
                mDecoder.releaseOutputBuffer( oIdx, false);
                running = false;
                return mFrame;
            }

            if (mOutputSurface != null) {
                return processTextureFrame(oIdx, mBufferInfo);
            } else {
                return processByteFrame(oIdx, mBufferInfo);
            }
            // not release now
            //mDecoder.releaseOutputBuffer(oIdx, false);
        }
        return mFrame;
    }

    private HWAVFrame processTextureFrame(final int index, MediaCodec.BufferInfo info) {
        final int width;
        final int height;
        width = mFrame.mWidth;
        height = mFrame.mHeight;
        mTextureFrame = mFrame;
        synchronized (renderLock) {
            mSurfaceHelper.setTextureSize(width, height);
            mDecoder.releaseOutputBuffer(index, true);
        }
        long s = System.currentTimeMillis();
        synchronized (mWaitEvent) {
            if (!mNoWait) {
                try {
                    mWaitEvent.wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mNoWait = false;
        }
        long e = System.currentTimeMillis();
        releaseFream(index);
        //Log.i(TAG, "get frame " + mTextureFrame.mTimeStamp + " tetureid " + mTextureFrame.mTextureId + " wait " + (e -s));
        return mTextureFrame;
    }

    @Override
    public void onFrame(HWAVFrame frame) {
        synchronized (mWaitEvent) {
            mTextureFrame = frame;
            mTextureFrame.mGotFrame = true;
            mNoWait = true;
            mWaitEvent.notifyAll();
        }
    }

    private HWAVFrame processByteFrame(final int oIdx, MediaCodec.BufferInfo info) {
        ByteBuffer buffer = mDecoder.getOutputBuffer(oIdx);
        if (buffer != null && mBufferInfo.size > 0) {
            mFrame.mBufferSize = mBufferInfo.size;
            mFrame.mBuffer = buffer;
            mFrame.mTimeStamp = mBufferInfo.presentationTimeUs;
            mFrame.mGotFrame = true;
            mFrame.mIdx = oIdx;
            //Log.i(TAG, " decoder got frame " + oIdx + " tm " + mFrame.mTimeStamp );
            if (mIsAudio && mDump) {
                try {
                    ByteBuffer res = ByteBuffer.allocate(20000);
                    res.put(buffer);
                    mOutputStream.write(res.array(), 0, mBufferInfo.size);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            mDecoder.releaseOutputBuffer(oIdx, false);
        }
        return mFrame;
    }

    public HWAVFrame ReadFrame() {
        return InternalReadFrame();
    }

    public void releaseFream(int index) {
        if (mDecoder == null) {
            Log.i(TAG, " this is null object ");
            return;
        }
        if (mSharedContext == null ) {
            mDecoder.releaseOutputBuffer(index, false);
        }
        mFrame.mGotFrame = false;
        mFrame.mIsAudio = mIsAudio;
        mFrame.mIdx = -1;
        mFrame.mBuffer = null;
    }
}
