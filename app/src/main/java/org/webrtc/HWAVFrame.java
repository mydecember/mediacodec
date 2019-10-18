package org.webrtc;

import java.nio.ByteBuffer;

public class HWAVFrame {
    private static String TAG = "HWAVFrame";
    public boolean mIsAudio;

    public int mWidth;
    public int mHeight;
    public int mColorFomat;
    public int mStride;
    public int mCropTop;
    public int mCropButtom;
    public int mCropLeft;
    public int mCropRight;

    public int mAudioChannels;
    public int mAudioSampleRate;

    public long mTimeStamp;
    public ByteBuffer mBuffer;

    public boolean mGotFrame;
    public int mIdx;

    public boolean mStreamEOF;
}
