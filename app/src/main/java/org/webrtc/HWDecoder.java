package org.webrtc;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

interface HWDecoderCallback {
    public void onHWDecoderFrame(HWAVFrame frame);
}
public class HWDecoder {
    private static final String TAG = "HWDecoder";
    private HWAVFrame mFrame = new HWAVFrame();
    private boolean mIsAudio = true;
    private MediaCodec mDecoder;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private HWDecoderCallback mCallback;
    public void setCallBack(HWDecoderCallback call) {
        mCallback = call;
    }
    public int initialize(MediaFormat format) {
        String mine = format.getString(MediaFormat.KEY_MIME);
        Log.i(TAG, "hwdecoder to init format " + format.toString());
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
        }
        try {
            mDecoder = MediaCodec.createDecoderByType(mine);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        try {
            mDecoder.configure(format, null, null,0);
        } catch (Exception e) {
            e.printStackTrace();
            return -2;
        }
        mDecoder.start();
        running = true;
        createOutputThread().start();
        return 0;
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
                }

            }
        };
    }

    private  void deliverDecodedFrame() {
       // Log.i(TAG," to read frame");
        HWAVFrame frame = ReadFrame();
        if (mCallback!=null) {
            mCallback.onHWDecoderFrame(frame);
        }
        if (frame.mGotFrame) {
            releaseFream(frame.mIdx);
        }

    }

    HWAVFrame ReadFrame() {
        mFrame.mGotFrame = false;
        mFrame.mIsAudio = mIsAudio;
        mFrame.mIdx = -1;
        mFrame.mBuffer = null;
        int oIdx = mDecoder.dequeueOutputBuffer(mBufferInfo, 100000);
        if (oIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
            Log.i(TAG, "== AMEDIACODEC_INFO_TRY_AGAIN_LATER is audio " + mIsAudio);
        } else if (oIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

            //MediaFormat format = mDecoder.getOutputFormat();
            MediaFormat newFormat = mDecoder.getOutputFormat();
            Log.i(TAG, "== AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED " + newFormat.toString() );
            //AMediaFormat_getString(format_, AMEDIAFORMAT_KEY_MIME, &mine);

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
                }
                int keyStride = newFormat.getInteger(MediaFormat.KEY_STRIDE);
                int keyStrideHeight = newFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT);
                mFrame.mStride = keyStride;
                Log.d(TAG, " stride:" + keyStride +  " height stride:" + keyStrideHeight );
            } else {
                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    mFrame.mAudioChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
                if (newFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    mFrame.mAudioSampleRate = newFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
                }
                Log.i(TAG, "####### zfq get audio channels=" +  mFrame.mAudioChannels + " sample_rate=" + mFrame.mAudioSampleRate);

            }

        } else if (oIdx < 0) {
            Log.i(TAG,"== should not happen oIdx==" + oIdx);
        } else {
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM )!= 0) {
                Log.i(TAG, "zfq ############ decoder recv AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM");
                mFrame.mStreamEOF = true;
                mDecoder.releaseOutputBuffer( oIdx, false);
                running = false;
                return mFrame;
            }

            ByteBuffer buffer = mDecoder.getOutputBuffer(oIdx);
            if (buffer != null && mBufferInfo.size > 0) {
               // mFrame.mBuffer = buffer;

                mFrame.mTimeStamp = mBufferInfo.presentationTimeUs;
                mFrame.mGotFrame = true;
                mFrame.mIdx = oIdx;
               // Log.i(TAG, " decoder got frame " + oIdx + " tm " + mFrame.mTimeStamp );


            } else {
                mDecoder.releaseOutputBuffer(oIdx, false);
            }
            // not release now
            //mDecoder.releaseOutputBuffer(oIdx, false);
        }
        return mFrame;
    }

    public void releaseFream(int index) {
      //  Log.i(TAG, " to release  index = " + mFrame.mIdx + " audio " + mIsAudio + " -> " + mFrame.mIsAudio);
        if (mDecoder == null) {
            Log.i(TAG, " this is null object ");
            return;
        }
        mDecoder.releaseOutputBuffer(index, false);
        mFrame.mGotFrame = false;
        mFrame.mIsAudio = mIsAudio;
        mFrame.mIdx = -1;
        mFrame.mBuffer = null;
    }

}
