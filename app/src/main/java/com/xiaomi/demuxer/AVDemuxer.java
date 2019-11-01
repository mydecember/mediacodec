package com.xiaomi.demuxer;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AVDemuxer {
    private static final String TAG = "AVDemuxer";

    public static final int DEMUXER_AUDIO = 1;
    public static final int DEMUXER_VIDEO = 2;
    public static final int DEMUXER_AUDIO_VIDEO = 3;

    private MediaFormat mAudioFromat = null;
    private MediaFormat mVideoFromat = null;
    private int mMaxSize = 0;
    private int mMaxAudioSize = 0;
    private HWDecoder mAudioDecoder = new HWDecoder();
    private HWDecoder mVideoDecoder = new HWDecoder();
    MediaExtractor mExtractor = null;
    private int mDemuxerType = DEMUXER_AUDIO_VIDEO;

    private boolean mAudioStreamEnd = true;
    private boolean mVideoStreamEnd = true;
    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    boolean mFileEof = true;

    private boolean mIsAsync = true;
    private  String mFilePath;
    private boolean mPrecisionSeek = false; // 0 pre 1 after 2 near 3  Precision
    private long mSeekPos = 0;

    private long mAudioDuration;
    private long mVideoDuration;
    private int mRotation;
    private int mAudioChannels;
    private int mAudioSampleRate;
    private int mWidth;
    private int mHeight;


    long output_audio_frame_count_ = 0;
    long output_video_frame_count_ = 0;

    public AVDemuxer(){}

    public void stop() {
        mVideoDecoder.release();
        mAudioDecoder.release();
    }

    public int initialize(String filePath, int demuxer_media_type, boolean isAsync) {
        Log.i(TAG, "to initialize ");
        mIsAsync = isAsync;
        mDemuxerType = demuxer_media_type;
        mFilePath = filePath;
        mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        int ntrack = mExtractor.getTrackCount();
        for (int i = 0; i < ntrack; ++i) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mineType = format.getString(MediaFormat.KEY_MIME);
            if (mineType.startsWith("video/") && mVideoTrackIndex == -1 && ((demuxer_media_type & DEMUXER_VIDEO) != 0)) {
                if (mVideoDecoder.initialize(format, mIsAsync) == 0) {
                    mExtractor.selectTrack(i);
                    mVideoTrackIndex = i;
                    mVideoStreamEnd = false;
                    mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);;
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                        mRotation = format.getInteger(MediaFormat.KEY_ROTATION);
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        mVideoDuration = format.getLong(MediaFormat.KEY_DURATION);
                    }
                    Log.i(TAG, "video rotation " + mRotation + " mVideoDuration " + mVideoDuration);
                } else {
                    Log.i(TAG, " init video decoder error " + format.toString());
                }

            } else if (mineType.startsWith("audio/") && mAudioTrackIndex == -1 && ((demuxer_media_type & DEMUXER_AUDIO) != 0)){
                if (mAudioDecoder.initialize(format, mIsAsync) == 0) {
                    mExtractor.selectTrack(i);
                    mAudioTrackIndex = i;
                    mAudioStreamEnd = false;

                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        mAudioDuration = format.getLong(MediaFormat.KEY_DURATION);
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        mAudioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        mAudioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    Log.i(TAG, " get audio channels " + mAudioChannels + " sample rate " + mAudioSampleRate + " mAudioDuration " + mAudioDuration);

                } else {
                    Log.i(TAG, " init audio decoder error " + format.toString());
                }

            } else {
                Log.i(TAG, "jump track " + i + " format " + format.toString());
            }

        }
        if (mAudioTrackIndex == -1 && mVideoTrackIndex == -1) {
            Log.e(TAG, "== decoder video track not found");
            return -1;
        }
        Log.i(TAG, "initiate ok");
        mFileEof = false;
        return 0;
    }

    public int getRotation() {
        return mRotation;
    }

    public long getAudioDuration() {
        return mAudioDuration;
    }

    public long getVideoDuration() {
        return mVideoDuration;
    }

    public int getChannels() {
        return mAudioChannels;
    }

    public int getSampleRate() {
        return mAudioSampleRate;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    boolean mExit = false;
    public void asyncStart() {
        mExit = false;
        final long tm = System.nanoTime();
        HWDecoder.HWDecoderCallback callback = new HWDecoder.HWDecoderCallback() {
            @Override
            public void onHWDecoderFrame(HWAVFrame frame) {
                if (frame.mGotFrame) {
                    //Log.i(TAG, "got frame ");
                    if (frame.mIsAudio) {
                        output_audio_frame_count_++;
                    } else {
                        output_video_frame_count_++;
                    }
                }
                if ( frame.mIsAudio && frame.mStreamEOF == true) {
                    mAudioStreamEnd = true;
                    Log.i(TAG, "read audio codec end");
                }

                if ( !frame.mIsAudio && frame.mStreamEOF == true) {
                    mVideoStreamEnd = true;
                    Log.i(TAG, "read video codec end");
                }
                if (mAudioStreamEnd && mVideoStreamEnd) {
                    Log.i(TAG, " streams end kkkkkkkkkk" );
                    Log.i(TAG, "get decoder frame count audio:" + output_audio_frame_count_  + " video:" + output_video_frame_count_);
                    mExit = true;
                    long tm1 = System.nanoTime();
                    Log.i(TAG, "Test end used " + (tm1 - tm) /1000/1000 );
                }
            }
        };
        mAudioDecoder.setCallBack(callback);
        mVideoDecoder.setCallBack(callback);
        Log.i(TAG, " start read sample");
        while(!mFileEof) {
            int track_id = mExtractor.getSampleTrackIndex();
            if (mAudioTrackIndex >= 0 && track_id == mAudioTrackIndex) {
                SendSamplesToDecoder(mAudioDecoder);
            } else if (mVideoTrackIndex >= 0 && track_id == mVideoTrackIndex) {
                SendSamplesToDecoder(mVideoDecoder);
            } else {

                if (mAudioTrackIndex >= 0) {
                    setDecoderEnd(mAudioDecoder);
                }
                if (mVideoTrackIndex >= 0) {
                    setDecoderEnd(mVideoDecoder);
                }
                mFileEof = true;
                Log.i(TAG, "read file  end and set mFileEof " +  mFileEof);
            }
        }
        while(!mExit) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long tm1 = System.nanoTime();
        Log.i(TAG, "Test end used ## " + (tm1 - tm) /1000/1000 );
    }

    public void releaseFrame(HWAVFrame  frame) {
        if (frame.mIsAudio) {
            mAudioDecoder.releaseFream(frame.mIdx);
        } else {
            mVideoDecoder.releaseFream(frame.mIdx);
        }
    }

    public void seekPos(long pos, int mode) {
        mSeekPos = pos;
        Log.i(TAG, "seek to "+ pos + " mode " + mode);
        if (!mAudioStreamEnd) {
            mAudioDecoder.reset();
        }
        if (!mVideoStreamEnd) {
            mVideoDecoder.reset();
        }

        if (mode == 3) { //precision seek
            //mPrecisionSeek = true;
            mExtractor.seekTo(mSeekPos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        } else {
            mExtractor.seekTo(pos, mode);
        }
    }

    public  HWAVFrame readFrame() {
        if (mFileEof) {
            HWAVFrame frame  = null;
            if (!mAudioStreamEnd) {
                frame = Process(mAudioDecoder, mFileEof);
                if ( frame == null || frame.mStreamEOF == true) {
                    mAudioStreamEnd = true;
                    Log.i(TAG, "read audio codec end");
                }
                return frame;
            }
            if (!mVideoStreamEnd) {
                frame = Process(mVideoDecoder, mFileEof);
                if ( frame == null || frame.mStreamEOF == true) {
                    mVideoStreamEnd = true;
                    Log.i(TAG, "read video codec end");
                }
                return frame;
            }
            if (mAudioStreamEnd && mVideoStreamEnd) {
                Log.i(TAG, "get decoder frame count audio:" + output_audio_frame_count_  + " video:" + output_video_frame_count_);
                return null;
            }
            return frame;
        } else {
            int track_id = mExtractor.getSampleTrackIndex();
            if (mAudioTrackIndex >= 0 && track_id == mAudioTrackIndex) {
                return Process(mAudioDecoder, mFileEof);
            } else if (mVideoTrackIndex >= 0 && track_id == mVideoTrackIndex) {
                return Process(mVideoDecoder, mFileEof);
            } else {
                Log.i(TAG, "read file  end" +  mExtractor.getSampleTime());
                if (mAudioTrackIndex >= 0) {
                    setDecoderEnd(mAudioDecoder);
                }
                if (mVideoTrackIndex >= 0) {
                    setDecoderEnd(mVideoDecoder);
                }

                mFileEof = true;
                return readFrame();
            }
        }
    }

    private boolean SendSamplesToDecoder(HWDecoder decoder) {
        int index;
        long t1 = System.currentTimeMillis();
        index = decoder.getNextDecoderBufferIndex();
        long t2 = System.currentTimeMillis();
        ByteBuffer ibuf = decoder.getNextDecoderBuffer(index);
        long t3 = System.currentTimeMillis();
        if (ibuf == null) {
            Log.i(TAG, "get decoder buffer error");
            return false;
        }

        int cnt = mExtractor.readSampleData(ibuf, 0);
        long tm =  mExtractor.getSampleTime();
        if (decoder.getIsAudio()) {
            //Log.i(TAG, " read sample size " + cnt + " tm " + tm + " audio " );
        } else {
            //Log.i(TAG, " read sample size " + cnt + " tm " + tm + " video " + " used " + (t2 - t1)  + " " + (t3-t1) + " index " + index);
        }
        mExtractor.advance();
        decoder.queueInputBuffer(index, cnt, tm, false);

        return true;
    }

    private boolean setDecoderEnd(HWDecoder decoder) {
        int index;
        index = decoder.getNextDecoderBufferIndex();
        ByteBuffer ibuf = decoder.getNextDecoderBuffer(index);
        if (ibuf == null) {
            Log.i(TAG, "get decoder buffer error");
            return false;
        }
        decoder.queueInputBuffer(index, 0, 0, true);
        //decoder.EnqueueBuffer(index, 0, 0, true);
        return true;
    }

    HWAVFrame  Process(HWDecoder decoder, boolean eof) {
        HWAVFrame frame = null;
        if (!eof) {
            if (!SendSamplesToDecoder(decoder)) {
                Log.i(TAG, "zfq SendSamplesToDecoder error ");
                return frame;
            }
        }
        frame = decoder.ReadFrame();
        if (frame.mGotFrame) {
            String tp = frame.mIsAudio? "audio": "video";
            //Log.i(TAG, "zfq got frame size " + frame.mBuffer.limit()  + " " + tp + " tm " + frame.mTimeStamp);
            if (frame.mIsAudio) {
                output_audio_frame_count_++;
            } else {
                output_video_frame_count_++;
            }
        }
        return frame;
    }

}
