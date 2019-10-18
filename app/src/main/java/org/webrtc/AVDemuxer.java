package org.webrtc;

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
    private boolean mAsync =  false;


    private  String mFilePath;


    long output_audio_frame_count_ = 0;
    long output_video_frame_count_ = 0;

    public int initialize(String filePath, int demuxer_media_type) {
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
                if (mVideoDecoder.initialize(format) == 0) {
                    mExtractor.selectTrack(i);
                    mVideoTrackIndex = i;
                    mVideoStreamEnd = false;
                } else {
                    Log.i(TAG, " init video decoder error " + format.toString());
                }

            } else if (mineType.startsWith("audio/") && mAudioTrackIndex == -1 && ((demuxer_media_type & DEMUXER_AUDIO) != 0)){
                if (mAudioDecoder.initialize(format) == 0) {
                    mExtractor.selectTrack(i);
                    mAudioTrackIndex = i;
                    mAudioStreamEnd = false;
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

    public static void Test() {
        long tm = System.nanoTime();
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.initialize("/sdcard/voip-data/dou.mp4", 2);
        while(true) {
            HWAVFrame frame = demuxer.readFrame();
            if ( frame == null) {
                Log.i(TAG, "EEEEEEEEEEEEEEEee");
                break;
            }
            if (frame != null) {
                if (frame.mGotFrame == true) {
                    //Log.i(TAG, "iiiiiiiii " + frame.mIdx + " tm " + frame.mTimeStamp + " got " + frame.mGotFrame);
                    demuxer.releaseFrame(frame);
                }
            }
        }
        long tm1 = System.nanoTime();
        Log.i(TAG, "Test end used " + (tm1 - tm) /1000/1000 );
    }

    public static void TestReadfile() {
        long tm = System.nanoTime();
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.initialize("/sdcard/voip-data/dou.mp4", 2);
        while(true) {
            if (demuxer.TestRead() == -1)
                break;
        }
        long tm1 = System.nanoTime();
        Log.i(TAG, "Test end used " + (tm1 - tm) /1000/1000 );
    }
    public static void TestAsync() {
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.initialize("/sdcard/voip-data/dou.mp4", 2);
        demuxer.syncStart();
    }

    private ByteBuffer bio = ByteBuffer.allocateDirect(3620*2048*3);
    public int TestRead() {
        int track_id = mExtractor.getSampleTrackIndex();
        int cnt = mExtractor.readSampleData(bio, 0);
        if (cnt >=0)
        mExtractor.advance();
        bio.clear();
        if (cnt <= 0) {
            return -1;
        }
        return 0;
    }

    boolean mExit = false;
    public void syncStart() {
        long tm = System.nanoTime();
        HWDecoderCallback callback = new HWDecoderCallback() {
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
                Log.i(TAG, "read file  end" +  mExtractor.getSampleTime());
                if (mAudioTrackIndex >= 0) {
                    setDecoderEnd(mAudioDecoder);
                }
                if (mVideoTrackIndex >= 0) {
                    setDecoderEnd(mVideoDecoder);
                }
                mFileEof = true;
            }
        }
        while(!mExit) {

        }
        long tm1 = System.nanoTime();
        Log.i(TAG, "Test end used " + (tm1 - tm) /1000/1000 );
    }

    public void releaseFrame(HWAVFrame  frame) {
        if (frame.mIsAudio) {
            mAudioDecoder.releaseFream(frame.mIdx);
        } else {
            mVideoDecoder.releaseFream(frame.mIdx);
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
//        if(frame == null) {
//            Log.i(TAG, " fream is none");
//        }
    }


    boolean SendSamplesToDecoder(HWDecoder decoder) {
        int index;
        index = decoder.getNextDecoderBufferIndex();
        ByteBuffer ibuf = decoder.getNextDecoderBuffer(index);
        if (ibuf == null) {
            Log.i(TAG, "get decoder buffer error");
            return false;
        }

        int cnt = mExtractor.readSampleData(ibuf, 0);
        long tm =  mExtractor.getSampleTime();
        if (decoder.getIsAudio()) {
            //Log.i(TAG, " read sample size " + cnt + " tm " + tm + " audio " );
        } else {
            //Log.i(TAG, " read sample size " + cnt + " tm " + tm + " video " );
        }
        decoder.queueInputBuffer(index, cnt, tm, false);
        mExtractor.advance();
        return true;
    }

    boolean setDecoderEnd(HWDecoder decoder) {
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
