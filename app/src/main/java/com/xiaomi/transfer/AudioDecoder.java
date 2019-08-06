package com.xiaomi.transfer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;
import static android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED;
import static android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
import static android.media.MediaCodec.INFO_TRY_AGAIN_LATER;

public class AudioDecoder {
    private String TAG = "audiodecoder";
    MediaCodec mCodec;
    MediaCodec.Callback mMediacodecCallback;
    private Thread mOutputThread;
    private volatile boolean running = false;
    private AudioFrameCallback mCallback;

    private boolean mDump = false;
    private String mDumpPath = "/sdcard/voip-data/dump.pcm";
    private FileOutputStream mOutputStream;

    public boolean getRunning() {
        return running;
    }

    public interface AudioFrameCallback {
        void onAudioFrameDecoded(ByteBuffer data, MediaCodec.BufferInfo info);
    }

    public void registerCallback(AudioFrameCallback call) {
        mCallback = call;
    }

    public boolean InitAudioDecoder(MediaFormat format) {
        if (mDump) {
            try {
                mOutputStream = new FileOutputStream(mDumpPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (!mime.startsWith("audio/")) {
            Log.i(TAG, " foramt error for audio " + format);
            return false;
        }
        Log.i(TAG, " foramt for audio " + format);
        try {
            mCodec = MediaCodec.createDecoderByType(mime);//创建Decode解码器
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        mMediacodecCallback = new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable( MediaCodec codec, int index) {
                Log.i(TAG, "onInputBufferAvailable " + index );
            }

            @Override
            public void onOutputBufferAvailable( MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                Log.i(TAG, "onOutputBufferAvailable");
            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
                Log.i(TAG, "onError");
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                Log.i(TAG, "onOutputFormatChanged");
            }
        };

        //mCodec.setCallback(mMediacodecCallback);
        mCodec.configure(format, null, null, 0);
        mCodec.start();
        running = true;
        mOutputThread = createOutputThread();
        mOutputThread.start();
        return true;
    }

    public void release() {
        if (mDump) {
            try {
                mOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            Log.i(TAG, " to release audiodecoder");
            running = false;
            mCodec.stop();
            mCodec.release();

        } catch (Exception e ) {
            e.printStackTrace();
        }

    }

    public  Thread createOutputThread() {
        return new Thread("audiodecoder") {
            public void run() {
                while(running) {
                    deliverDecoderFrame();
                }
            }
        };
    }

    public void deliverDecoderFrame() {
        try{
            MediaCodec.BufferInfo mDecoderBufferInfo = new MediaCodec.BufferInfo();
            int outputIndex = mCodec.dequeueOutputBuffer(mDecoderBufferInfo, 100000);
            if (outputIndex == INFO_TRY_AGAIN_LATER) {
                //Log.i(TAG, "audio try again");
            } else if (outputIndex == INFO_OUTPUT_FORMAT_CHANGED) {
                Log.i(TAG, " output format change to " + mCodec.getOutputFormat());
            } else if (outputIndex == INFO_OUTPUT_BUFFERS_CHANGED) {

            } else if (outputIndex < 0) {
                Log.i(TAG, "some thing wrong " + outputIndex);
            } else {
                Log.i(TAG, " decode audio frame " + mDecoderBufferInfo.size);
                if ((mDecoderBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    running = false;
                    Log.i(TAG, "audio recv end");
                    mCodec.releaseOutputBuffer(outputIndex, false);
                    return;
                }

                if (mCallback != null) {
                    ByteBuffer res = ByteBuffer.allocate(5000);
                    res.put(mCodec.getOutputBuffer(outputIndex));
                    //res.flip();
                    if (mDump) {
                        try {
                            mOutputStream.write(res.array(), 0, mDecoderBufferInfo.size);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    mCallback.onAudioFrameDecoded(res, mDecoderBufferInfo);
                    //Log.i(TAG, "get decoder audio time " + mDecoderBufferInfo.presentationTimeUs);

                }
                mCodec.releaseOutputBuffer(outputIndex, false);
            }
        } catch (Exception e) {
            running = false;
            e.printStackTrace();
        }


    }
    public int getNextDecoderBufferIndex() {

        int inputIndex = -1;
        try {
            inputIndex = mCodec.dequeueInputBuffer(-1);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return inputIndex;
    }

    public ByteBuffer getNextDecoderBuffer(int index) {
        ByteBuffer buf = mCodec.getInputBuffer(index);
        buf.clear();
        return buf;
    }

    public void queueInputBuffer(int index, int samples, long timeStamp, int flags) {
        mCodec.queueInputBuffer(index, 0, samples, timeStamp, flags);

    }

}
