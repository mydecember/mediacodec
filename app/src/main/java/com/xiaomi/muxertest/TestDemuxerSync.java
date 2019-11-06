package com.xiaomi.muxertest;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.util.Log;

import com.xiaomi.demuxer.AVDemuxer;
import com.xiaomi.demuxer.HWAVFrame;
import com.xiaomi.drawers.OriginalDrawer;
import com.xiaomi.glbase.EglBase;
import com.xiaomi.glbase.GlUtil;
import com.xiaomi.muxer.AVMuxer;
import com.xiaomi.transfer.OriginalRenderDrawer;
import com.xiaomi.transfer.WaterRenderDrawer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TestDemuxerSync {
    static final  String TAG = "TestDemuxerSync";
    public static void Test() {
        int recvNums = 0;
        long tm = System.nanoTime();
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.start("/sdcard/voip-data/dou.mp4", 3, false, false);
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
        demuxer.open("/sdcard/voip-data/dou.mp4");
        demuxer.start("/sdcard/voip-data/dou.mp4", 3, false, false);
        AVMuxer muxer = new AVMuxer();
        muxer.open("/sdcard/voip-data/muxer.mp4");
        muxer.addVideoTrack("hevc", false, 0, demuxer.getWidth(),demuxer.getHeight(),0,0);
        muxer.addAudioTrack("acc", 44100, 2, 190000);

        Log.i(TAG, " ++++++++++++++ encoder color " + muxer.getVideoSupportColor());
        muxer.start();
        while(true) {
            HWAVFrame frame = demuxer.readFrame();
            if ( frame == null) {
                Log.i(TAG, "EEEEEEEEEEEEEEEee tm " + (System.nanoTime() - tm) /1000/1000);
                break;
            }
            if (frame != null) {
                if (frame.mGotFrame == true) {
                    Log.i(TAG, "iiiiiiiii " + frame.mIdx + " tm " + frame.mTimeStamp
                            + " got " + frame.mGotFrame
                            + " color " + frame.mColorFomat
                            + " " + (frame.mIsAudio ? " audio" : "video")
                            + " tid " + Thread.currentThread().getId());

                    muxer.writeFrame(frame);
                    demuxer.releaseFrame(frame);
                    recvNums++;

                }
            }
        }
        Log.i(TAG, "iiiiiiiii Test end used 1111111111111" );
        demuxer.stop();
        Log.i(TAG, "iiiiiiiii Test end used 2222222222" );
        muxer.stop();
        long tm1 = System.nanoTime();
        Log.i(TAG, "iiiiiiiii Test end used " + (tm1 - tm) /1000/1000 + " recvNums " + recvNums);
    }

    public static void TestAsync() {
        Log.i(TAG, "TTTTTTTTTTTT");
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.start("/sdcard/voip-data/dou.mp4", 3, true, false);
        demuxer.asyncStart();
    }

    public static void TestDemuxerSurface() {
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.open("/sdcard/voip-data/dou.mp4");
        int width = demuxer.getWidth();
        int height = demuxer.getHeight();
        EglBase mEgl = EglBase.create();
        mEgl.createPbufferSurface(width, height);
        mEgl.makeCurrent();
        OriginalDrawer mOriginalDrawer = new OriginalDrawer();
        mOriginalDrawer.create();
        mOriginalDrawer.surfaceChangedSize(width, height);

        int recvNums = 0;
        long tm = System.nanoTime();


        demuxer.start("/sdcard/voip-data/dou.mp4", 3, false, true);
        mEgl.detachCurrent();
        Log.i(TAG, "iiiiiiiii " +"SSSSSSSSSSSSSS 2957983");
        //demuxer.seekPos(2957983, 3);
        int audioNums = 0;
        int videoNums = 0;
        int mCaptureOne = 5;
        while(true) {
            Log.i(TAG, "to read");
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
                    if (frame.mIsAudio) {
                        audioNums++;
                    } else {
                        videoNums++;
//                        mEgl.makeCurrent();
//                        mOriginalDrawer.setInputTextureId(frame.mTextureId);
//                        mOriginalDrawer.draw(frame.mTimeStamp, null);
//                        videoNums++;
//
//                        if (mCaptureOne < 5) {
//                            // save bmp
//                            ByteBuffer mBuffer = ByteBuffer.allocateDirect(width * height * 4);
//                            //GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, mOriginalDrawer.getOutputTextureId());
//                            mOriginalDrawer.bindFrame();
//                            ByteBuffer buf =  mBuffer;
//                            buf.order(ByteOrder.LITTLE_ENDIAN);
//                            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf);
//                            buf.rewind();
//
//                            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                            bmp.copyPixelsFromBuffer(buf);
//                            //afterDraw();
//                            //GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
//                            mOriginalDrawer.unBindFrame();
//                            mCaptureOne++;
//                            GlUtil.saveFile(bmp, "/sdcard/kk", "kkk" + mCaptureOne+ ".jpeg");
//                        }
//                        mEgl.detachCurrent();
                    }
                } else {
                    Log.i(TAG, " not got");
                }
            }
        }
        demuxer.stop();
        long tm1 = System.nanoTime();
        mEgl.release();
        Log.i(TAG, "iiiiiiiii Test end used " + (tm1 - tm) /1000/1000 + " recvNums " + recvNums + " audio " + audioNums + " video " + videoNums);
    }

    public static void TestDemuxerMuxerSurface() {
        AVDemuxer demuxer = new AVDemuxer();
        demuxer.open("/sdcard/voip-data/dou.mp4");
        int width = demuxer.getWidth();
        int height = demuxer.getHeight();
        int degree = demuxer.getRotation();
        EglBase mEgl = EglBase.create();
        mEgl.createPbufferSurface(1, 1);
        mEgl.makeCurrent();


        int recvNums = 0;
        long tm = System.nanoTime();


        demuxer.start("/sdcard/voip-data/dou.mp4", 3, false, true);

        AVMuxer muxer = new AVMuxer();
        muxer.open("/sdcard/voip-data/muxer.mp4");
        muxer.addVideoTrack("avc", true, degree, demuxer.getWidth(),demuxer.getHeight(),0,0);
        muxer.addAudioTrack("acc", 44100, 2, 190000);


        Log.i(TAG, " ++++++++++++++ encoder color " + muxer.getVideoSupportColor());
        muxer.start();
        mEgl.detachCurrent();
        Log.i(TAG, "iiiiiiiii " +"SSSSSSSSSSSSSS 2957983");
        //demuxer.seekPos(2957983, 3);
        int audioNums = 0;
        int videoNums = 0;
        int mCaptureOne = 5;
        while(true) {
            HWAVFrame frame = demuxer.readFrame();
            if ( frame == null) {
                Log.i(TAG, "EEEEEEEEEEEEEEEee " +  (System.nanoTime() - tm) / 1000 / 1000);
                break;
            }
            if (frame != null) {
                if (frame.mGotFrame == true) {
                    //Log.i(TAG, "iiiiiiiii " + frame.mIdx + " tm " + frame.mTimeStamp + " got " + frame.mGotFrame + " " + (frame.mIsAudio ? " audio" : "video"));

                    muxer.writeFrame(frame);
                    demuxer.releaseFrame(frame);
                    recvNums++;


                    if (frame.mIsAudio) {
                        audioNums++;
                    } else {
                        videoNums++;

                    }
                }
            }
        }
        demuxer.stop();
        muxer.stop();
        mEgl.release();
        long tm1 = System.nanoTime();
        Log.i(TAG, "iiiiiiiii Test end used " + (tm1 - tm) /1000/1000 + " recvNums " + recvNums + " audio " + audioNums + " video " + videoNums);
    }
}
