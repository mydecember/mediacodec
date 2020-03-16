package com.xiaomi.muxer;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

public class CodecInfo {
    final static String TAG = "codecinfo";
    public static void displayDecoders(boolean finaEncoder, String mime) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);//REGULAR_CODECS参考api说明
        MediaCodecInfo[] codecs = list.getCodecInfos();
        for (MediaCodecInfo codec : codecs) {

            if (finaEncoder && !codec.isEncoder())
               continue;
            if (!finaEncoder && codec.isEncoder()) {
                continue;
            }
            String name = null;
            String[] types = codec.getSupportedTypes();
            for (String type : types) {
                if (type.equals(mime)) {
                    name = codec.getName();
                    break;
                }
            }
            if (name == null) {continue;}
            Log.i(TAG, "find codec name---> " + name);
            MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);

            Log.i(TAG, "default format---->" + caps.getDefaultFormat().toString());
            Log.i(TAG, "support colors --->");
            for (int color : caps.colorFormats) {
                Log.i(TAG, "color: " + Integer.toHexString(color));
            }
            Log.i(TAG, "support profiles --->");
            for (MediaCodecInfo.CodecProfileLevel level : caps.profileLevels) {
                Log.i(TAG,"profile " + Integer.toHexString(level.profile) + " level " + Integer.toHexString(level.level));
            }
            if (finaEncoder) {
                Log.i(TAG, "encoder capabilities----->");
                MediaCodecInfo.EncoderCapabilities encoderCap = caps.getEncoderCapabilities();
                Log.i(TAG, " cbr " + encoderCap.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR));
                Log.i(TAG, " vbr " + encoderCap.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR));
                Log.i(TAG, " cq " + encoderCap.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ));
                Log.i(TAG, " complex  " + encoderCap.getComplexityRange());

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Log.i(TAG,"qulity " +  encoderCap.getQualityRange());
                }
            } else {
            }
            MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
            Log.i(TAG, "video capabilities----->");
            Log.i(TAG, "frame rate range "+videoCaps.getSupportedFrameRates());
            Log.i(TAG, "bitrate range " +videoCaps.getBitrateRange());
            Log.i(TAG, "width range " +   videoCaps.getSupportedWidths());
            Log.i(TAG, "height range " +   videoCaps.getSupportedHeights());
            Log.i(TAG, "width alignment " +   videoCaps.getWidthAlignment());
            Log.i(TAG, "height alignment " +   videoCaps.getHeightAlignment());

            return;
        }
    }
}

