package com.xiaomi.demuxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HWDecoder {
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
        if (mDump) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int initialize(MediaFormat format, boolean isAsync) {
        mIsAsync = isAsync;
        String mine = format.getString(MediaFormat.KEY_MIME);
        Log.i(TAG, "hwdecoder to init format " + format.toString() + " use async " + mIsAsync);
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
        }

        if (false) {
            DecoderProperties properties = findHwEncoder(mine, false);
            if (properties == null) {
                return -1;
            }
            MediaFormat format1 = MediaFormat.createVideoFormat(mine, mFrame.mWidth, mFrame.mHeight);

            ByteBuffer csd0 = format.getByteBuffer("csd-0");
            ByteBuffer csd1 = format.getByteBuffer("csd-1");

            format1.setByteBuffer("csd-0", csd0);
            format1.setByteBuffer("csd-1", csd1);

            format1.setFloat(MediaFormat.KEY_FRAME_RATE, (float)30);

            Log.i(TAG, "hwdecoder to init format 11 " + format1.toString());

            try {
                mDecoder = MediaCodec.createByCodecName(properties.codecName);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (mDecoder == null) {
                Log.i("hevc decoder", "decoder init error null");
                return -1;
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
            mDecoder.configure(format, null, null,0);
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
            // not release now
            //mDecoder.releaseOutputBuffer(oIdx, false);
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
        mDecoder.releaseOutputBuffer(index, false);
        mFrame.mGotFrame = false;
        mFrame.mIsAudio = mIsAudio;
        mFrame.mIdx = -1;
        mFrame.mBuffer = null;
    }

    private static void displayDecoders() {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);//REGULAR_CODECS参考api说明
        MediaCodecInfo[] codecs = list.getCodecInfos();
        for (MediaCodecInfo codec : codecs) {
            //if (!codec.isEncoder())
            //   continue;
            Log.i(TAG, "displays:==== "+codec.getName());
        }
    }


    private static DecoderProperties findHwEncoder(String mime, boolean isEncoder) {
        //displayDecoders();
        try {
            Log.i(TAG, "sdk version is: "+  Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                return null; // MediaCodec.setParameters is missing.

            for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder() != isEncoder) {
                    continue;
                }
                for (String mimeType : info.getSupportedTypes()) {
                    Log.i(TAG, "codec name: " + mimeType + " company:" + info.getName());
                }
            }

            for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder() != isEncoder) {
                    continue;
                }
                String name = null;
                for (String mimeType : info.getSupportedTypes()) {
                    Log.i(TAG, "codec name: " + mimeType);
                    if (mimeType.equals(mime)) {
                        name = info.getName();
                        break;
                    }
                }
                if (name == null) {
                    continue;  // No VP8 support in this codec; try the next one.
                }


                Log.i(TAG, "Found candidate encoder " + name);
                MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mime);
                if (isEncoder) {

                    Log.i(TAG, "#####level###### " );
                    for (MediaCodecInfo.CodecProfileLevel level : capabilities.profileLevels) {
                        Log.i(TAG,"profile " + Integer.toHexString(level.profile) + " level " + Integer.toHexString(level.level));
                    }
                    Log.i(TAG, " cbr " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR));
                    Log.i(TAG, " vbr " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR));
                    Log.i(TAG, " cq " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ));
                    Log.i(TAG, " complex  " + capabilities.getEncoderCapabilities().getComplexityRange());
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                        Log.i(TAG, " quality  " +capabilities.getEncoderCapabilities().getQualityRange());
//                    }

                    Log.i(TAG, "#####video cap###### " );
                    MediaCodecInfo.VideoCapabilities cap  = capabilities.getVideoCapabilities();
                    Log.i(TAG, " bitrate " + cap.getBitrateRange());

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try{
                            cap.getAchievableFrameRatesFor(3840, 2160);
                        } catch (Exception e) {
                            Log.e(TAG, " getAchievableFrameRatesFor ", e);
                        }

                    }
                    Log.i(TAG, " fps " + 1);

                    Log.i(TAG, "width alignment " + cap.getWidthAlignment());
                    Log.i(TAG, "height alignment " +cap.getHeightAlignment());
                    Log.i(TAG, " bitrate " + cap.areSizeAndRateSupported(3840, 2160, 30));
                    Log.i(TAG, " support fps " + cap.getSupportedFrameRates());
                    Log.i(TAG, " support height " + cap.getSupportedHeights());
                    Log.i(TAG, " support width " + cap.getSupportedWidths());
                    Log.i(TAG, " size support " + cap.isSizeSupported(3840, 2160));

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.i(TAG, "#####max instance######" + capabilities.getMaxSupportedInstances());
                    }
                    Log.i(TAG, "#####corol######" );
                }



                for (int colorFormat : capabilities.colorFormats) {
                    Log.i(TAG, "   Color: 0x" + Integer.toHexString(colorFormat));
                }

                // Check if this is supported HW encoder
//                for (String hwCodecPrefix : supportedHwCodecPrefixes) {
//                    if (!name.startsWith(hwCodecPrefix)) {
//                        continue;
//                    }
                // Check if codec supports either yuv420 or nv12
                for (int supportedColorFormat : supportedColorList) {
                    for (int codecColorFormat : capabilities.colorFormats) {
                        if (codecColorFormat == supportedColorFormat) {
                            // Found supported HW VP8 encoder
                            Log.i(TAG, "Found target encoder " + name +
                                    ". Color: 0x" + Integer.toHexString(codecColorFormat));
                            return new DecoderProperties(name, codecColorFormat);
                        }
                    }
                }
                //     }
            }
            return null;  // No HW VP8 encoder.
        }catch (Exception e) {
            Log.e(TAG, "find exception at findHwEncoder:", e);
            return null;
        }
    }
}
