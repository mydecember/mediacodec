package com.xiaomi.muxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.xiaomi.demuxer.HWAVFrame;

import java.io.IOException;
import java.nio.ByteBuffer;

public class HWEncoder {
    private static String TAG = "hwencoder";
    private Surface mInputSurface;
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private long mEncoderFrames = 0;
    private boolean mUseSurface = false;
    private boolean mIsAudio = true;
    private EncoderCallBack mCallBack;
    private boolean mRunning = false;
    private final Object mLock = new Object();
    public interface EncoderCallBack {
        public void onEncoderEOF(boolean isAudio);
        public void onEncodedFrame(ByteBuffer buffer, MediaCodec.BufferInfo info, boolean isAudio);
        public void onNewFormat(MediaFormat format, boolean isAudio);
    }

    private static final int[] supportedColorList = {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            //MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    };

    public static class EncoderProperties {
        EncoderProperties(String codecName, int colorFormat) {
            this.codecName = codecName;
            this.color = colorFormat;
        }
        public int color;
        public String codecName; // OpenMax component name for HEVC codec.
    }

    public int setupEncoder(String codecName, MediaFormat format, EncoderCallBack callback, boolean useSurface) {
        Log.i(TAG," setup encoder format " + format);
        mUseSurface = useSurface;
        try {
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                mIsAudio = true;
            } else {
                mIsAudio = false;
            }

            //EncoderProperties properties = findHwEncoder(mime);

            if (codecName.isEmpty()) {
                mEncoder = MediaCodec.createEncoderByType(mime);
            } else {
                mEncoder = MediaCodec.createByCodecName(codecName);
            }
            mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            if (mUseSurface) {
                mInputSurface = mEncoder.createInputSurface();
            }
            //mEncoder.setCallback(mEncoderCallback);
            mEncoder.start();
            Log.i(TAG, "create encoder and start");
        } catch (IOException e) {
            release();
            e.printStackTrace();
            return -1;
        }
        mCallBack = callback;
        return 0;
    }

    public void start() {
        createOutputThread().start();
    }

    public void encodeFrame(HWAVFrame frame) {
        int index = mEncoder.dequeueInputBuffer(-1);
        if (index < 0) {
            Log.e(TAG, "get encoder queue error");
            return;
        }
        ByteBuffer buffer = mEncoder.getInputBuffer(index);
        if (frame.mStreamEOF) {
            mEncoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        } else {
            buffer.clear();
            //frame.mBuffer.flip();
            buffer.put(frame.mBuffer);
            buffer.flip();
            mEncoder.queueInputBuffer(index, 0, frame.mBufferSize, frame.mTimeStamp, 0);
        }

    }

    public void stopEncoder() {
        if (mUseSurface) {
            mEncoder.signalEndOfInputStream();
            return;
        }
        HWAVFrame frame = new HWAVFrame();
        frame.mStreamEOF = true;
        frame.mBufferSize = 0;
        encodeFrame(frame);
        synchronized (mLock) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {

            }
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
    }

    private Thread createOutputThread() {
        mRunning = true;
        return new Thread() {
            @Override
            public void run() {
                while (mRunning) {
                    deliverEncodedFrame();
                }
                mRunning = false;
                synchronized (mLock) {
                    mLock.notifyAll();
                }
                Log.i(TAG,"thread end is audio " +mIsAudio);
            }
        };
    }

    private void deliverEncodedFrame() {
        MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
        int oIdx = mEncoder.dequeueOutputBuffer(mBufferInfo, 100000);
        if (oIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
            //Log.i(TAG, "== AMEDIACODEC_INFO_TRY_AGAIN_LATER is audio " + mIsAudio);
        } else if (oIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = mEncoder.getOutputFormat();
            Log.d(TAG, "encoder output format changed: " + newFormat);
            mCallBack.onNewFormat(newFormat, mIsAudio);
        } else if (oIdx < 0) {
        } else {
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM )!= 0) {
                Log.i(TAG, "read eof");
                mCallBack.onEncoderEOF(mIsAudio);
                mRunning = false;
            } else {
                ByteBuffer buffer = mEncoder.getOutputBuffer(oIdx);
                if (buffer != null && mBufferInfo.size > 0) {
                    mCallBack.onEncodedFrame(buffer, mBufferInfo, mIsAudio);
                }
            }

            // not release now
            mEncoder.releaseOutputBuffer(oIdx, false);
        }
    }

//    private MediaCodec.Callback mEncoderCallback = new MediaCodec.Callback() {
//        @Override
//        public void onInputBufferAvailable(MediaCodec codec, int index) {
//            Log.d(TAG, " Input Buffer Avail");
//        }
//
//        @Override
//        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
//            ByteBuffer encodedData = mEncoder.getOutputBuffer(index);
//            if (encodedData == null) {
//                throw new RuntimeException("couldn't fetch buffer at index " + index);
//            }
//
//            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                info.size = 0;
//            }
//
//            if (info.size != 0) {
//                mEncoderFrames++;
//                Log.i(TAG, "get encoded frame " + info.size + " encode frame num " + mEncoderFrames
//                        + " pts " + info.presentationTimeUs
//                        + " offset " + info.offset
//                        + " pid " + Thread.currentThread().getId() + " nal type " + (encodedData.get(4) & 0x1f) + " " + encodedData.get(0)
//                        + " " + encodedData.get(1)
//                        + " " + encodedData.get(2)
//                        + " " + encodedData.get(3)
//                        + " " + encodedData.get(4)
//                        + " eof " + (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM));
//                mCallBack.onEncodedFrame(encodedData, info, mIsAudio);
//
//            }
//            mEncoder.releaseOutputBuffer(index, false);
//            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                //mGlHandler.sendEmptyMessage(CMD_RELEASE);
//                mCallBack.onEncoderEOF(mIsAudio);
//            }
//        };
//
//        @Override
//        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
//            Log.e(TAG, " MediaCodec " + codec.getName() + " onError:" + e.toString(), e);
//        }
//
//        @Override
//        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
//            Log.i(TAG, "new encoder format " + format.toString());
//            mCallBack.onNewFormat(format, mIsAudio);
//        }
//    };

    public static EncoderProperties findHwEncoder(String mime) {
        //displayDecoders();
        try {
            Log.i(TAG, "sdk version is: "+  Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                return null; // MediaCodec.setParameters is missing.

            for (int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (!info.isEncoder()) {
                    continue;
                }
                String name = null;
                for (String mimeType : info.getSupportedTypes()) {
                    //Log.i(TAG, "codec name: " + mimeType);
                    if (mimeType.equals(mime)) {
                        name = info.getName();
                        break;
                    }
                }
                if (name == null) {
                    continue;  // No VP8 support in this codec; try the next one.
                }


                Log.i(TAG, "Found candidate encoder " + mime + " name " + name);
                MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mime);

                if (mime.startsWith("video/")) {
                    MediaCodecInfo.VideoCapabilities cap  = capabilities.getVideoCapabilities();
                    for (int colorFormat : capabilities.colorFormats) {
                        Log.i(TAG, "   Color: 0x" + Integer.toHexString(colorFormat));
                    }

                    // Check if codec supports either yuv420 or nv12
                    for (int supportedColorFormat : supportedColorList) {
                        for (int codecColorFormat : capabilities.colorFormats) {
                            if (codecColorFormat == supportedColorFormat) {
                                // Found supported HW VP8 encoder
                                Log.i(TAG, "Found target encoder " + name +
                                        ". Color: 0x" + Integer.toHexString(codecColorFormat));
                                return new EncoderProperties(name, codecColorFormat);
                            }
                        }
                    }
                } else { // audio
                    return new EncoderProperties(name, -1);
                }
            }
            return null;
        }catch (Exception e) {
            Log.e(TAG, "find exception at findHwEncoder:", e);
            return null;
        }
    }
}
