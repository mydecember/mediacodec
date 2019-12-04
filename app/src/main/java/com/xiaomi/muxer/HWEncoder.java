package com.xiaomi.muxer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import com.xiaomi.demuxer.HWAVFrame;
import com.xiaomi.drawers.VideoEncoderDrawer;
import com.xiaomi.glbase.EglBase;

import java.io.IOException;
import java.nio.ByteBuffer;

public class HWEncoder {
    private static String TAG = "hwencoder";
    private MediaMuxer mMuxer;
    private MediaCodec mEncoder;
    private long mEncoderFrames = 0;
    private boolean mIsAudio = true;
    private EncoderCallBack mCallBack;
    private boolean mRunning = false;
    private final Object mLock = new Object();
    private boolean mUseSurface = false;
    private VideoEncoderDrawer mVideoEncoderDrawer = new VideoEncoderDrawer();
    private Surface mInputSurface;
    private EglBase mEgl;
    private EglBase.Context mSharedContext;
    private MediaFormat mFormat;

    public interface EncoderCallBack {
        public void onEncoderEOF(boolean isAudio);
        public void onEncodedFrame(ByteBuffer buffer, MediaCodec.BufferInfo info, boolean isAudio);
        public void onNewFormat(MediaFormat format, boolean isAudio);
    }

    private static final int[] supportedColorList = {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            //MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            //MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
    };

    public static class EncoderProperties {
        EncoderProperties(String codecName, int colorFormat, Range<Integer> bits) {
            this.codecName = codecName;
            this.color = colorFormat;
            bitRange = bits;
        }
        Range<Integer> bitRange;
        public int color;
        public String codecName; // OpenMax component name for HEVC codec.
    }

    public int setupEncoder(String codecName, MediaFormat format, EncoderCallBack callback, boolean useSurface) {
        Log.i(TAG," setup encoder format " + format + " useSurface " + useSurface);
        mUseSurface = useSurface;
        mFormat = format;
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
                mSharedContext = EglBase.getCurrentContext();
                mEgl = EglBase.create(mSharedContext);
                mInputSurface = mEncoder.createInputSurface();
                mEgl.createSurface(mInputSurface);
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
        if (mUseSurface) {
            mEgl.makeCurrent();
            mVideoEncoderDrawer.create();
            mVideoEncoderDrawer.surfaceChangedSize(mFormat.getInteger(MediaFormat.KEY_WIDTH), mFormat.getInteger(MediaFormat.KEY_HEIGHT));
            mEgl.detachCurrent();
        }
        createOutputThread().start();
    }

    private HWAVFrame dropStride(HWAVFrame frame) {
        if (frame.mIsAudio) {
            return frame;
        }
        int len = frame.mWidth * frame.mHeight * 3 / 2;
        if (frame.mBufferSize == len) {
            return frame;
        }
        if (frame.mColorFomat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            //Log.i(TAG, "######### change format COLOR_FormatYUV420SemiPlanar");
            byte[] src = new byte[frame.mBuffer.remaining()];
            frame.mBuffer.get(src, 0, src.length);

            byte[] nv12 = new byte[len];
            for (int i = 0; i < frame.mHeight; ++i) {
                System.arraycopy(src , frame.mStride*i, nv12, i*frame.mWidth,  frame.mWidth);
            }
            int srcLumaSize = frame.mStride * frame.mStrideHeight;
            int dstLumaSize = frame.mWidth * frame.mHeight;

            for (int i = 0; i < frame.mHeight / 2; ++ i) {
                System.arraycopy(src , srcLumaSize + frame.mStride * i, nv12, dstLumaSize + frame.mWidth * i,  frame.mWidth );
            }

            frame.mBuffer = ByteBuffer.wrap(nv12);
            frame.mBufferSize = len;
            return frame;

        } else if (frame.mColorFomat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            //Log.i(TAG, "######### change format COLOR_FormatYUV420Planar");
            byte[] src = new byte[frame.mBuffer.remaining()];
            frame.mBuffer.get(src, 0, src.length);

            byte[] i420 = new byte[len];
            for (int i = 0; i < frame.mHeight; ++i) {
                System.arraycopy(src , frame.mStride*i, i420, i*frame.mWidth,  frame.mWidth);
            }
            int srcLumaSize = frame.mStride * frame.mStrideHeight;
            int dstLumaSize = frame.mWidth * frame.mHeight;

            for (int i = 0; i < frame.mHeight / 2; ++ i) {
                System.arraycopy(src , srcLumaSize + frame.mStride /2 * i, i420, dstLumaSize + frame.mWidth /2 * i,  frame.mWidth / 2 );
            }

            for (int i = 0; i < frame.mHeight / 2; ++ i) {
                System.arraycopy(src , srcLumaSize + srcLumaSize /4 + frame.mStride /2 * i, i420, dstLumaSize + dstLumaSize/4 + frame.mWidth /2 * i,  frame.mWidth / 2 );
            }

            frame.mBuffer = ByteBuffer.wrap(i420);
            frame.mBufferSize = len;
            return frame;
        } else {
            return null;
        }

    }
    public void encodeFrame(HWAVFrame frame) {
        if (mUseSurface) {
            encodeTextureBuffer(frame);
            return;
        }
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
            //Log.i(TAG," yuan pos " + buffer.position() + " limit " + buffer.limit() + " capcity " + buffer.capacity());
            //frame.mBuffer.flip();
            dropStride(frame);
            //Log.i(TAG," drop pos " + frame.mBuffer.position() + " limit " + frame.mBuffer.limit() + " capcity " + frame.mBuffer.capacity());
            buffer.put(frame.mBuffer);
            buffer.flip();
            mEncoder.queueInputBuffer(index, 0, frame.mBufferSize, frame.mTimeStamp, 0);
        }
    }

    private void encodeTextureBuffer(HWAVFrame frame) {
        //Log.i(TAG, "to draw frame id " + frame.mTextureId + " tm " + frame.mTimeStamp);
        mEgl.makeCurrent();
        mVideoEncoderDrawer.setInputTextureId(frame.mTextureId);
        mVideoEncoderDrawer.setSRCWidthAndHeight(frame.mWidth, frame.mHeight);
        mVideoEncoderDrawer.drawFrame(frame.mTimeStamp * 1000);
        mEgl.setPresentTime(frame.mTimeStamp*1000);
        mEgl.swapBuffers();
        mEgl.detachCurrent();
    }

    public void stopEncoder() {
        if (mUseSurface) {
            mEncoder.signalEndOfInputStream();
        } else {
            HWAVFrame frame = new HWAVFrame();
            frame.mStreamEOF = true;
            frame.mBufferSize = 0;
            encodeFrame(frame);
        }
        synchronized (mLock) {
            if (mRunning) {
                try {
                    mLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
        if (mUseSurface) {
            mEgl.makeCurrent();
            mVideoEncoderDrawer.release();
            mEgl.detachCurrent();
            mEgl.release();
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
            Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED encoder output format changed: " + newFormat);
            mCallBack.onNewFormat(newFormat, mIsAudio);
        } else if (oIdx < 0) {
        } else {
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM )!= 0) {
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
                    Log.i(TAG, "bit range " + cap.getBitrateRange().toString());

                    // Check if codec supports either yuv420 or nv12
                    for (int supportedColorFormat : supportedColorList) {
                        for (int codecColorFormat : capabilities.colorFormats) {
                            if (codecColorFormat == supportedColorFormat) {
                                // Found supported HW VP8 encoder
                                Log.i(TAG, "Found target encoder " + name +
                                        ". Color: 0x" + Integer.toHexString(codecColorFormat));
                                return new EncoderProperties(name, codecColorFormat, cap.getBitrateRange());
                            }
                        }
                    }
                } else { // audio
                    return new EncoderProperties(name, -1, null);
                }
            }
            return null;
        }catch (Exception e) {
            Log.e(TAG, "find exception at findHwEncoder:", e);
            return null;
        }
    }
}
