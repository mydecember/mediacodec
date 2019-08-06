package com.xiaomi.transfer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioEncoder {
    private String TAG = "AudioEncoder";
    private String mEncodeType = "audio/mp4a-latm";
    private MediaCodec mCodec;
    private MediaCodec.Callback mEncoderCallback;

    private AudioEncoderCallback mAudioCallback;

    private boolean mDump = false;
    private FileOutputStream mOutputStream;
    private String mDumpPath = "/sdcard/voip-data/pre-encode.pcm";

    private ConcurrentLinkedQueue<Integer> mIndexQueue = new ConcurrentLinkedQueue<Integer>();
    public interface AudioEncoderCallback {
        public void onAudioEncodedFrame(ByteBuffer bytes, MediaCodec.BufferInfo info);
        public void onAudioFormatChanged(MediaFormat format);
       // public void onAudioF
    }



    public AudioEncoder() {
        if (mDump) {
            try {
                mOutputStream = new FileOutputStream(mDumpPath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        mEncoderCallback = new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable( MediaCodec codec, int index) {
                mIndexQueue.add(index);
                codec.getInputBuffer(index);
                Log.i(TAG, "input onInputBufferAvailable " + index);
            }

            @Override
            public void onOutputBufferAvailable( MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                Log.i(TAG, "input onOutputBufferAvailable " + info.size);
            }

            @Override
            public void onError( MediaCodec codec, MediaCodec.CodecException e) {
                Log.i(TAG, "input onError");
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                Log.i(TAG, "input onOutputFormatChanged " + format);
            }
        };
    }

    public void registerCallback(AudioEncoderCallback callback) {
        mAudioCallback = callback;
    }

    public void initAACMediaEncode(MediaFormat encodeFormat) {
        try {
//            MediaFormat encodeFormat = MediaFormat.createAudioFormat(mEncodeType, 44100, 2);//参数对应-> mime type、采样率、声道数
//            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000);//比特率
//            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
//            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100 * 1024);//作用于inputBuffer的大小
            String mime = encodeFormat.getString(MediaFormat.KEY_MIME);
            mCodec = MediaCodec.createEncoderByType(mime);
            mCodec.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mCodec.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mCodec == null) {
            Log.e(TAG, "create mediaEncode failed");
            return;
        }
    }

    public void encodeAudioFrame(ByteBuffer data, MediaCodec.BufferInfo inputinfo) {
        int index = mCodec.dequeueInputBuffer(-1);
        if(index >= 0){
            ByteBuffer inbuffer = mCodec.getInputBuffer(index);
            inbuffer.clear();
            data.flip();
            inbuffer.put(data);
            if (mDump) {
                try {
                    data.flip();
                    byte[] bb = new byte[inputinfo.size];
                    data.get(bb);
                    mOutputStream.write(bb,0, inputinfo.size);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mCodec.queueInputBuffer(index, inputinfo.offset, inputinfo.size, inputinfo.presentationTimeUs, inputinfo.flags);
        } else {
            Log.i(TAG, "dequeueInputBuffer error ");
        }


        //MediaCodec.BufferInfo mInfo=new MediaCodec.BufferInfo();
        int outIndex;
        do{
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            outIndex = mCodec.dequeueOutputBuffer(info,0);
            if(outIndex>=0){
                ByteBuffer buffer = mCodec.getOutputBuffer(outIndex);
                buffer.position(info.offset);
                buffer.limit(info.offset + info.size);
                //buffer.position(mEncodeBufferInfo.offset);
                //AAC编码，需要加数据头，AAC编码数据头固定为7个字节
//                byte[] temp=new byte[info.size+7];
//                addADTStoPacket(temp, info.size+7);
//                buffer.get(temp,7,info.size);
                //buffer.clear();

                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                // 数据流结束标志，结束本次循环
                if((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0){
                    Log.i(TAG,"audio decoder stream end");
                    break;
                }

                if (mAudioCallback != null && info.size != 0) {
                    ByteBuffer tmp = ByteBuffer.allocate(info.size);
                    tmp.put(buffer);
                    //Log.i(TAG, "get audio encode frame offset " + info.offset + " size " + info.size);
                    mAudioCallback.onAudioEncodedFrame( tmp, info);
                }
                //buffer.clear();
                mCodec.releaseOutputBuffer(outIndex,false);
            }else if(outIndex ==MediaCodec.INFO_TRY_AGAIN_LATER){
                break;
                //TODO something
            }else if(outIndex==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                if (mAudioCallback != null) {
                    mAudioCallback.onAudioFormatChanged(mCodec.getOutputFormat());
                }

                //break;
                //TODO something
            } else {
                break;
            }
        }while (outIndex>=0);

    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        int freqIdx = 4;  //44.1KHz
        int chanCfg = 2;  //CPE
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
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


}
