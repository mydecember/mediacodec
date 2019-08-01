package com.xiaomi.transfer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
//import android.support.annotation.NonNull;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel11;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel4;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel41;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel5;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel51;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel52;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
import static android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh;

public class VideoEncoder {
    private static String TAG = "videoencoder";
    private static final int IFRAME_INTERVAL = 1;

    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private int mTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private boolean mMuxerStarted = false;
    private int mWidth;
    private int mHeight;
    private int mFps;
    private int mBitrate = 0;
    private  String mPath;

    private String VIDEO_MIME_TYPE = "video/avc";
    private MediaCodec.Callback encoderCallback;
    private  long mEncoderFrames = 0;
    private VideoEncoderCallBack mCallBack;

    private MediaCodec.BufferInfo mBufferInfo;
    private int mNum = 0;

    private boolean mDump = false;
    private FileOutputStream mOutputStream;
    private String mDumpPath = "/sdcard/voip-data/dump.h264";

    private boolean mAsync = true;
    private Queue<MoviePlayer.AudioFrame> audioFramequeue = new LinkedList<MoviePlayer.AudioFrame>();

    public interface VideoEncoderCallBack {
        public void  onVideoEncoderEOF();
    }

    private void CheckNalU(byte[] buffer) {
        int index = 0;
        int pre = 0;
        for (int i = 0; i < buffer.length - 4; ) {
            if (buffer[i] == 0
            && buffer[i + 1] == 0
                    && buffer[i + 2] == 0
                    && buffer[i + 3] == 1
                    ) {
                if (VIDEO_MIME_TYPE == "video/avc") {
                    //Log.i(TAG, "get encoded frame first nalu " + (buffer[i + 4] & 0x1f) + " len " + (i - pre));
                }
                if (VIDEO_MIME_TYPE == "video/hevc") {
//                    Log.i(TAG, "get encoded frame first nalu " + ((buffer[i+4]>>1) & 0x3f) + " layerid " + (((buffer[i+4]<<5) & 0x10) | ((buffer[i+5]>>3) & 0x1f)) +
//                            " tid " + ((buffer[i+5]&0x03)) + " len " + (i - pre));
                }

                pre = i;
                i += 4;
            }
            else if(buffer[i] == 0
                    && buffer[i + 1] == 0
                    && buffer[i + 2] == 1) {
                Log.i(TAG, "get encoded frame nalu " + (buffer[i+3] & 0x1f));
                i += 3;
            }  else {
                ++i;
            }
        }
//        if (pre == 0) {
//            Log.i(TAG, "get encoded frame len " + (buffer.length));
//        }

    }

    public VideoEncoder(int width, int height, int fps, int bitrate, String path, String codecName, VideoEncoderCallBack callBack) {
        if (codecName.equals("hevc")) {
            VIDEO_MIME_TYPE = "video/hevc";
        } else {
            VIDEO_MIME_TYPE = "video/avc";
        }
        mCallBack = callBack;
        mWidth = width;
        mHeight = height;
        mFps = fps;
        mPath = path;
        mBitrate = bitrate;
        if (mDump) {
            try {
                mOutputStream = new FileOutputStream(mDumpPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        findHwEncoder(VIDEO_MIME_TYPE);
        encoderCallback = new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                Log.d(TAG, " Input Buffer Avail");
            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(index);
                if (encodedData == null) {
                    throw new RuntimeException("couldn't fetch buffer at index " + index);
                }
                //Log.i(TAG, "get encoded frame " + (info.flags&MediaCodec.BUFFER_FLAG_KEY_FRAME));
                byte[] out = new byte[info.size];
                encodedData.get(out);
                CheckNalU(out);
                if (mDump) {
                    try {
                        mOutputStream.write(out, 0, info.size);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                    Log.i(TAG, "get encoded frame sps or pps " + "size" + info.size + " ofset " + info.offset + " nal type " + (encodedData.get(4) & 0x1f) + " " + encodedData.get(0)
//                            + " " + encodedData.get(1)
//                            + " " + encodedData.get(1)
//                            + " " + encodedData.get(2)
//                            + " " + encodedData.get(3)
//                            + " " + encodedData.get(4));
                    info.size = 0;
                }

                if (info.size != 0) {
                    if (mMuxerStarted) {
                        mEncoderFrames++;
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        Log.i(TAG, "get encoded frame " + info.size + " encode frame num " + mEncoderFrames
                                + " pts " + info.presentationTimeUs
                                + " offset " + info.offset
                                + " pid " + Thread.currentThread().getId() + " nal type " + (encodedData.get(4) & 0x1f) + " " + encodedData.get(0)
                                + " " + encodedData.get(1)
                                + " " + encodedData.get(2)
                                + " " + encodedData.get(3)
                                + " " + encodedData.get(4)
                                + " eof " + (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM));
                        //if (mEncoderFrames < 3)
                        mMuxer.writeSampleData(mTrackIndex, encodedData, info);


                    }
                }
                mEncoder.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    //mGlHandler.sendEmptyMessage(CMD_RELEASE);
                    if (mCallBack != null) {
                        Log.i(TAG, "encode EOF mEncoderNums " + mEncoderFrames);
                        mCallBack.onVideoEncoderEOF();
                        mTrackIndex = -1;

                        if (mDump) {
                            try {
                                mOutputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Log.e(TAG, " MediaCodec " + codec.getName() + " onError:" + e.toString(), e);
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

                if (mTrackIndex >= 0) {
                    throw new RuntimeException("format changed twice");
                }
                if (mEncoder.equals(codec)) {
                    Log.i(TAG, "EEEEEEEEEE WWWWWWWWW thread id " + Thread.currentThread().getId() );
                }


                mTrackIndex = mMuxer.addTrack(mEncoder.getOutputFormat());
                Log.i(TAG, "add video track " + format + " trackid " + mTrackIndex);
                if (!mMuxerStarted && mTrackIndex >= 0) {
                    mMuxer.start();
                    Log.i(TAG, " MMMMMMMM muxer started ");

                }

                while(audioFramequeue.size() > 0) {
                    MoviePlayer.AudioFrame frame = audioFramequeue.peek();
                    mMuxer.writeSampleData(mAudioTrackIndex, frame.buffer, frame.info);
                    audioFramequeue.remove();
                }
                mMuxerStarted = true;
            }
        };
        setupEncoder();

    }

    public void flush() {
        mEncoder.flush();
        if (mAsync)
        mEncoder.start();
    }

    private void setupEncoder() {
        if (!mAsync)
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, mWidth, mHeight);
        int frameRate = mFps;
        // Set some required properties. The media codec may fail if these aren't defined.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //format.setInteger(MediaFormat.KEY_PROFILE, AVCProfileHigh);
        //format.setInteger(MediaFormat.KEY_LEVEL, AVCLevel52);
        if (mBitrate <= 0) {
            mBitrate = (int)(mWidth*mHeight*4*2);
        }
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,  IFRAME_INTERVAL); // 1 seconds between I-frames
        // use cbr default is vbr
        //format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);

        Log.i(TAG," set video encoder format " + format);
        // Create a MediaCodec encoder and configure it. Get a Surface we can use for recording into.
        try {
            mEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            //mEncoder = MediaCodec.createByCodecName("OMX.qcom.video.encoder.avc");////("OMX.google.h264.encoder");
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
            if (mAsync)
                mEncoder.setCallback(encoderCallback);
            Log.i(TAG, "create encoder and start");
            //mEncoder.reset();
            mEncoder.start();
            mMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            release();
            e.printStackTrace();
        }
    }
    public void addAudioTrack(MediaFormat format) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime.startsWith("audio" +"/")) {
            mAudioTrackIndex = mMuxer.addTrack(format);
            Log.i(TAG, "add audio track " + format + " trackid " + mAudioTrackIndex);
        } else {
            //mTrackIndex = mMuxer.addTrack(format);
        }
        if (mTrackIndex != -1 && mAudioTrackIndex != -1) {
            Log.i(TAG, "WWWWWWWWWWWWWWWWw");
//            mMuxer.start();
//            mMuxerStarted = true;
        }

    }

    public void writeAudioSample(MoviePlayer.AudioFrame frame) {
        if (!mMuxerStarted) {
            Log.i(TAG, "add queue pid " +  Thread.currentThread().getId());
            audioFramequeue.add(frame);
            return;
        } else {

        }
        mMuxer.writeSampleData(mAudioTrackIndex, frame.buffer, frame.info);
    }

    public void stopEncoder() {
        Log.i(TAG, "stopEncoder");
        if (mAsync) {
            mEncoder.signalEndOfInputStream();
        }
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void release() {
        Log.d(TAG, "releasing encoder objects");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mMuxer != null) {
            Log.i(TAG, "to stop muxter");
            try {
                mMuxer.stop();
                Log.i(TAG, "to release muxter");
                mMuxer.release();

            } catch ( Exception e) {

            }
            mMuxer = null;
        }
    }

    public void drainEncoder(boolean endOfStream) {
        if (mAsync)
            return;
        //Log.i(TAG, "11111111111111");
        final int TIMEOUT_USEC = 10000;
        if (endOfStream) {
            Log.d(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG, "MediaCodec.INFO_TRY_AGAIN_LATER");
                // no output available yet
                if (!endOfStream) {
                    break;
                } else {
                    Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.i(TAG, "encoder output format changed: " + newFormat);

                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
            } else {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    //Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" + mBufferInfo.presentationTimeUs);
                    Log.i(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" + mBufferInfo.presentationTimeUs
                            + " " + encodedData.get(0) + " " + encodedData.get(1) + " " + encodedData.get(2)
                            + " " + encodedData.get(3) + " type " + (encodedData.get(4)&0x1f) + " mTrackIndex " + mTrackIndex + " mNum " + mNum);
                    mNum++;
                } else {
                    Log.i(TAG, "drainEncoder mBufferInfo: " + mBufferInfo.size);
                }
                mEncoder.releaseOutputBuffer(encoderStatus, false);
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {

                    if (mCallBack != null) {
                        Log.i(TAG, "encode EOF mEncoderNums " + mEncoderFrames);
                        mCallBack.onVideoEncoderEOF();
                    }
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        Log.d(TAG, "end of stream reached");
                    }
                    break;
                }
            }
        }
    }

    private static final int[] supportedColorList = {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
    };

    private static final String[] supportedHwCodecPrefixes =
            {"OMX.qcom.", "OMX.Nvidia.", "OMX.IMG.TOPAZ", "OMX.Exynos", "OMX.MTK", "OMX.hantro", "OMX.Intel", "OMX.ARM"};//OMX.ARM xiaomi pengpai S1
    // Bitrate mode
    private static final int VIDEO_ControlRateConstant = 2;

    private static void displayDecoders() {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);//REGULAR_CODECS参考api说明
        MediaCodecInfo[] codecs = list.getCodecInfos();
        for (MediaCodecInfo codec : codecs) {
            if (!codec.isEncoder())
                continue;
            Log.i(TAG, "displayDecoders: "+codec.getName());
        }
    }

    private static void findHwEncoder(String mime) {
        displayDecoders();
        try {
            Log.i(TAG, "sdk version is: "+  Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                return ; // MediaCodec.setParameters is missing.

            for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (!info.isEncoder()) {
                    continue;
                }
                for (String mimeType : info.getSupportedTypes()) {
                    Log.i(TAG, "codec name: " + mimeType + " company:" + info.getName());
                }
            }

            for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (!info.isEncoder()) {
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

                Log.i(TAG, "#####level###### " );
                for (MediaCodecInfo.CodecProfileLevel level : capabilities.profileLevels) {
                    Log.i(TAG,"profile " + Integer.toHexString(level.profile) + " level " + Integer.toHexString(level.level));
                }
                Log.i(TAG, " cbr " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR));
                Log.i(TAG, " vbr " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR));
                Log.i(TAG, " cq " + capabilities.getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ));
                Log.i(TAG, " complex  " + capabilities.getEncoderCapabilities().getComplexityRange());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Log.i(TAG, " quality  " +capabilities.getEncoderCapabilities().getQualityRange());
                }

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
                                return;
                            }
                        }
                    }
           //     }
            }
            return ;  // No HW VP8 encoder.
        }catch (Exception e) {
            Log.e(TAG, "find exception at findHwEncoder:", e);
            return ;
        }
    }
}