package com.xiaomi.demuxer;

import java.nio.ByteBuffer;

public class HWAVFrame {
    private static String TAG = "HWAVFrame";
    public boolean mIsAudio;

    public int mWidth;
    public int mHeight;
    public int mColorFomat;
    public int mStride;
    public int mStrideHeight;
    public int mCropTop;
    public int mCropButtom;
    public int mCropLeft;
    public int mCropRight;

    public int mAudioChannels;
    public int mAudioSampleRate;

    public long mTimeStamp;
    public ByteBuffer mBuffer;
    public int mBufferSize;

    public boolean mGotFrame;
    public int mIdx;

    public boolean mStreamEOF;
    public HWAVFrame(){}
    public HWAVFrame(boolean isAudio, long time, ByteBuffer buffer, int bufferSize) {
        mIsAudio = isAudio;
        mTimeStamp = time;
        mBuffer = buffer;
        mBufferSize = bufferSize;
    }
}
