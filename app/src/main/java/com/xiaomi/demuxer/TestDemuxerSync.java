package com.xiaomi.demuxer;

import android.util.Log;

import com.xiaomi.muxer.AVMuxer;

public class TestDemuxerSync {
    static final  String TAG = "TestDemuxerSync";
    public static void Test() {
        int recvNums = 0;
        long tm = System.nanoTime();
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.initialize("/sdcard/voip-data/dou.mp4", 3, false);
        Log.i(TAG, "iiiiiiiii " +"SSSSSSSSSSSSSS 2957983");
        //demuxer.seekPos(2957983, 3);
        while(true) {
            HWAVFrame frame = demuxer.readFrame();
            if ( frame == null) {
                Log.i(TAG, "EEEEEEEEEEEEEEEee");
                break;
            }
            if (frame != null) {
                if (frame.mGotFrame == true) {
                    Log.i(TAG, "iiiiiiiii " + frame.mIdx + " tm " + frame.mTimeStamp + " got " + frame.mGotFrame + " " + (frame.mIsAudio ? " audio" : "video"));
                    demuxer.releaseFrame(frame);
                    recvNums++;
//                    if (5085034 == frame.mTimeStamp) {
//                        demuxer.seekPos(9520136, 2);
//                        Log.i(TAG, "to seek =============");
//                    }
                }
            }
        }
        demuxer.stop();
        long tm1 = System.nanoTime();
        Log.i(TAG, "iiiiiiiii Test end used " + (tm1 - tm) /1000/1000 + " recvNums " + recvNums);
    }

    public static void TestDemuxerMuxer() {
        int recvNums = 0;
        long tm = System.nanoTime();
        AVDemuxer demuxer = new AVDemuxer();
        AVMuxer muxer = new AVMuxer();

        demuxer.initialize("/sdcard/voip-data/dou.mp4", 3, false);
        muxer.initialize("/sdcard/voip-data/muxer.mp4");
        muxer.addVideoTrack("avc", false, demuxer.getWidth(),demuxer.getHeight(),0,0);
        muxer.addAudioTrack("acc", 44100, 2, 190000);
        while(true) {
            HWAVFrame frame = demuxer.readFrame();
            if ( frame == null) {
                Log.i(TAG, "EEEEEEEEEEEEEEEee");
                break;
            }
            if (frame != null) {
                if (frame.mGotFrame == true) {
                    //Log.i(TAG, "iiiiiiiii " + frame.mIdx + " tm " + frame.mTimeStamp + " got " + frame.mGotFrame + " " + (frame.mIsAudio ? " audio" : "video") + " tid " + Thread.currentThread().getId());

                    muxer.writeFrame(frame);
                    demuxer.releaseFrame(frame);
                    recvNums++;

                }
            }
        }
        muxer.stop();
        demuxer.stop();
        long tm1 = System.nanoTime();
        Log.i(TAG, "iiiiiiiii Test end used " + (tm1 - tm) /1000/1000 + " recvNums " + recvNums);
    }

    public static void TestAsync() {
        Log.i(TAG, "TTTTTTTTTTTT");
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.initialize("/sdcard/voip-data/dou.mp4", 3, true);
        demuxer.asyncStart();
    }
}
