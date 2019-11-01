package com.xiaomi.muxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.xiaomi.demuxer.HWAVFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class AVMuxer implements  HWEncoder.EncoderCallBack{
    private final static String TAG = "AVMuxer";
    private String mPath;
    private boolean mAudioStreamEnd = true;
    private boolean mVideoStreamEnd = true;
    private HWEncoder mAudioEncoder;
    private HWEncoder mVideoEncoder;
    private MediaMuxer mMuxer;
    private int mAudioTrackID = -1;
    private int mVideoTrackID = -1;
    private HWEncoder.EncoderProperties mAudioProperties;
    private HWEncoder.EncoderProperties mVideoProperties;
    private volatile boolean mIsMuxerStarted = false;
    private Queue<CacheFrame> mFramequeue = new LinkedList<CacheFrame>();

    public static final int COLOR_FORMAT_YUV420P = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
    public static final int COLOR_FORMAT_YUV420SP = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    public static final int COLOR_FORMAT_YUV420P_AND_SP = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private class CacheFrame {
        public MediaCodec.BufferInfo info;
        public ByteBuffer buffer;
    }

    public AVMuxer(){}

    public int initialize(String filePath) {
        mPath = filePath;
        mIsMuxerStarted = false;
        mAudioStreamEnd = true;
        mVideoStreamEnd = true;
        try {
            mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return 0;
    }

    public boolean addAudioTrack(String codecName, int sampleRate, int channels, int bitrate) {
        MediaFormat format = null;
        if (codecName.equals("acc")) {
            format = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels);
        } else {
            return false;
        }

        mAudioProperties = HWEncoder.findHwEncoder(format.getString(MediaFormat.KEY_MIME));
        if (mAudioProperties == null) {
            return false;
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);//比特率
        mAudioEncoder = new HWEncoder();
        if (mAudioEncoder.setupEncoder(mAudioProperties.codecName, format, this, false) != 0) {
            Log.e(TAG, " setup audio track error");
            return false;
        }
        Log.e(TAG, " setup audio track ok");
        mAudioStreamEnd = false;
        mAudioEncoder.start();
        return true;
    }

    public boolean addVideoTrack(String codecName, boolean useSurface, int width, int height, int fps, int bitrate) {
        MediaFormat format = null;
        if (codecName.equals("avc")) {
            format = MediaFormat.createVideoFormat("video/avc", width, height);
        } else if (codecName.equals("hevc")) {
            format = MediaFormat.createVideoFormat("video/hevc", width, height);
        } else {
            return false;
        }

        mVideoProperties = HWEncoder.findHwEncoder(format.getString(MediaFormat.KEY_MIME));
        if (mVideoProperties == null) {
            return false;
        }
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoProperties.color);
        // Set some required properties. The media codec may fail if these aren't defined.
        if (useSurface) {
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        }
        if (bitrate <= 0) {
            bitrate = (int)(width*height*4*3);
        }
        if (fps == 0) {
            fps = 30;
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, fps);
        if (useSurface)
            format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,  1); // 1 seconds between I-frames
        mVideoEncoder = new HWEncoder();
        if (mVideoEncoder.setupEncoder(mVideoProperties.codecName, format, this, useSurface) != 0) {
            Log.e(TAG, " setup video track error");
            return false;
        }
        Log.e(TAG, " setup video track ok");
        mVideoStreamEnd = false;
        mVideoEncoder.start();
        return true;
    }

    public int getVideoSupportColor() {
        return mVideoProperties.color;
    }

    public boolean writeFrame(HWAVFrame frame) {
        if (frame.mIsAudio) {
            mAudioEncoder.encodeFrame(frame);
            //Log.i(TAG, "writeFrame audio " + frame.mBufferSize);
        } else {
            mVideoEncoder.encodeFrame(frame);
            //Log.i(TAG, "writeFrame video " + frame.mBufferSize);
        }
        return true;
    }

    private Object mLock = new Object();
    public void stop() {
        if (!mVideoStreamEnd) {
            mVideoEncoder.stopEncoder();
        }
        if (!mAudioStreamEnd) {
            mAudioEncoder.stopEncoder();
        }
        if (mMuxer != null) {
            Log.i(TAG, "to stop muxter");
            synchronized (mLock) {
                if (!mAudioStreamEnd || !mVideoStreamEnd) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                mMuxer.stop();
                Log.i(TAG, "to release muxter");
                mMuxer.release();

            } catch ( Exception e) {

            }
            mMuxer = null;
        }
    }

    @Override
    public synchronized void onEncoderEOF(boolean isAudio) {
        if (isAudio) {
            mAudioStreamEnd = true;
        } else {
            mVideoStreamEnd = true;
        }
        if (mVideoStreamEnd && mAudioStreamEnd) {
            synchronized (mLock) {
                Log.i(TAG, " ====muxer nofity end");
                mLock.notifyAll();
            }
        }
    }

    @Override
    public synchronized void onEncodedFrame(ByteBuffer buffer, MediaCodec.BufferInfo info, boolean isAudio) {
        if (!mIsMuxerStarted) {
            CacheFrame cacheFrame = new CacheFrame();
            cacheFrame.info = info;
            cacheFrame.buffer = ByteBuffer.allocate(buffer.capacity());
            cacheFrame.buffer.put(buffer);
            mFramequeue.add(cacheFrame);
            Log.i(TAG, "onEncodedFrame to cache freme " + isAudio);
            return;
        }
        if (isAudio) {
            Log.i(TAG," onEncodedFrame write audio sample " + info.size + " mIsMuxerStarted " + mIsMuxerStarted);
            mMuxer.writeSampleData(mAudioTrackID, buffer, info);
        } else {
            Log.i(TAG," onEncodedFrame write video sample " + info.size + " mIsMuxerStarted " + mIsMuxerStarted) ;
            mMuxer.writeSampleData(mVideoTrackID, buffer, info);
        }
    }

    @Override
    public synchronized void onNewFormat(MediaFormat format, boolean isAudio) {
        int selectId;
        if (isAudio) {
            Log.i(TAG," =====add audio track " + format.toString() );
            mAudioTrackID = mMuxer.addTrack(format);
            selectId = mVideoTrackID;
        } else {
            Log.i(TAG," =====add video track " + format.toString() );
            mVideoTrackID = mMuxer.addTrack(format);
            selectId = mAudioTrackID;
        }
        boolean audioOK = true;
        boolean videoOk = true;
        if ((!mAudioStreamEnd) && mAudioTrackID < 0) {
            audioOK = false;
        }
        if ((!mVideoStreamEnd) && mVideoTrackID < 0) {
            videoOk = false;
        }
        if (audioOK && videoOk) {
            mMuxer.start();
            mIsMuxerStarted = true;
            Log.i(TAG," SSSSSSSSSSSSSSSSSss mIsMuxerStarted " + mIsMuxerStarted);
            while(!mAudioStreamEnd && !mVideoStreamEnd && mFramequeue.size() > 0) {

                CacheFrame frame = mFramequeue.peek();
                Log.i(TAG," write cache sample " + frame.info.size);
                mMuxer.writeSampleData(selectId, frame.buffer, frame.info);
                mFramequeue.remove();
            }
        }
    }
}
