package com.xiaomi.transfer;

import android.opengl.GLES30;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class RecordRenderDrawer extends BaseRenderDrawer implements Runnable, VideoEncoder.VideoEncoderCallBack {
    private static String TAG = "RecordRenderDrawer";
    // 绘制的纹理 ID
    private int mTextureId;
    private VideoEncoder mVideoEncoder;
    private Handler mMsgHandler;
    private boolean isRecording;
    private EglBase mEgl;

    private int av_Position;
    private int af_Position;
    private int s_Texture;
    private EglBase.Context mSharedContext;
    private String mPath;
//    private  int mWidth;
//    private int mHeight;
    private int mFps;
    private String mCodecName;

    //private MoviePlayer mPlayer;
    private MiVideoTranscode mVideoTransfer;
//    public void setPlayer(MoviePlayer player) {
//        mPlayer = player;
//    }

    public RecordRenderDrawer(MiVideoTranscode transfer) {
        new Thread(this).start();
        mVideoTransfer = transfer;
    }

    public void setParams(String codecName, int width, int height, int fps, String path) {
        this.mVideoEncoder = null;
        //this.mEglHelper = null;
        this.mTextureId = 0;
        this.isRecording = false;
        this.mPath = path;
//        this.mWidth = width;
//        this.mHeight = height;
        this.width = width;
        this.height = height;
        this.mFps = fps;
        this.mCodecName = codecName;
    }

    @Override
    public void  onVideoEncoderEOF() {
        Log.d(TAG, "on recv encoder eof");
        mMsgHandler.sendEmptyMessage(MsgHandler.MSG_ENCODER_EOF);
    }

    @Override
    public void setInputTextureId(int textureId) {
        this.mTextureId = textureId;
        Log.d(TAG, "setInputTextureId: " + textureId);
    }

    @Override
    public int getOutputTextureId() {
        return mTextureId;
    }

    @Override
    public void create() {
        mSharedContext = EglBase.getCurrentContext();
    }

    public void startRecord() {
        //Log.d(TAG, "startRecord context : " + mEglContext.toString());
        Message msg = mMsgHandler.obtainMessage(MsgHandler.MSG_START_RECORD, width, height, mSharedContext);
        mMsgHandler.sendMessage(msg);
        isRecording = true;
    }

    public void stopRecord() {
        Log.d(TAG, "stopRecord");
        mMsgHandler.sendMessage(mMsgHandler.obtainMessage(MsgHandler.MSG_STOP_RECORD));
    }

    public void quit() {
        Log.i(TAG, " to quit Recoder thread ");
        mMsgHandler.sendMessage(mMsgHandler.obtainMessage(MsgHandler.MSG_QUIT));
    }

    @Override
    public void surfaceChangedSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void draw(long timestamp, float[] transformMatrix) {
        if (isRecording) {
            Log.d(TAG, "draw: ");
            Message msg = mMsgHandler.obtainMessage(MsgHandler.MSG_FRAME, timestamp);
            mMsgHandler.sendMessage(msg);
        }
    }

    @Override
    public void run() {
        Looper.prepare();
        mMsgHandler = new MsgHandler();
        Looper.loop();
    }

    private class MsgHandler extends Handler {
        public static final int MSG_START_RECORD = 1;
        public static final int MSG_STOP_RECORD = 2;
        public static final int MSG_UPDATE_CONTEXT = 3;
        public static final int MSG_UPDATE_SIZE = 4;
        public static final int MSG_FRAME = 5;
        public static final int MSG_QUIT = 6;
        public static final int MSG_ENCODER_EOF = 7;

        public MsgHandler() {

        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_RECORD:
                    prepareVideoEncoder((EglBase.Context) msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_STOP_RECORD:
                    stopVideoEncoder();
                    break;
                case MSG_UPDATE_CONTEXT:
                    updateEglContext((EglBase.Context) msg.obj);
                    break;
                case MSG_UPDATE_SIZE:
                    updateChangedSize(msg.arg1, msg.arg2);
                    break;
                case MSG_FRAME:
                    drawFrame((long)msg.obj);
                    break;
                case MSG_QUIT:
                    quitLooper();
                    break;
                case MSG_ENCODER_EOF:
                    handleEncoderEOF();
                    break;
                default:
                    break;
            }
        }
    }

    private void handleEncoderEOF() {
        Log.i(TAG, "handleEncoderEOF ");
        isRecording = false;
        mVideoTransfer.onRecoderEOF();
    }

    private void prepareVideoEncoder(EglBase.Context context, int width, int height) {

        mVideoEncoder = new VideoEncoder(width, height, mFps, mPath, mCodecName, this);
        mEgl = EglBase.create(context);
        mEgl.createSurface(mVideoEncoder.getInputSurface());
        mEgl.makeCurrent();
        onCreated();
    }

    private void stopVideoEncoder() {
        Log.i(TAG, "to signal stop encoder");
        mVideoEncoder.stopEncoder();
    }

    private void updateEglContext(EglBase.Context context) {
        mEgl.release();
        mEgl = EglBase.create(context);
        mEgl.createSurface(mVideoEncoder.getInputSurface());
        mEgl.makeCurrent();
    }

    private void drawFrame(long timeStamp) {
        Log.d(TAG, "drawFrame: " + timeStamp );
        mEgl.makeCurrent();
        mVideoEncoder.drainEncoder(false);
        onDraw();
        mEgl.setPresentTime(timeStamp);
        mEgl.swapBuffers();
        Log.d(TAG, "drawFrame end " + " pid " + Thread.currentThread().getId() );
        mVideoTransfer.getPlayer().getOneFrame();

    }

    private void updateChangedSize(int width, int height) {
        onChanged(width, height);
    }

    private void quitLooper() {
        mVideoEncoder.drainEncoder(true);
        if (mEgl != null) {
            mEgl.release();
            mVideoEncoder.release();
            mVideoEncoder = null;
            mEgl = null;
        }
        Looper.myLooper().quit();
    }

    @Override
    protected void onCreated() {
        mProgram = GlesUtil.createProgram(getVertexSource(), getFragmentSource());
        initVertexBufferObjects();
        av_Position = GLES30.glGetAttribLocation(mProgram, "av_Position");
        af_Position = GLES30.glGetAttribLocation(mProgram, "af_Position");
        s_Texture = GLES30.glGetUniformLocation(mProgram, "s_Texture");
        Log.d(TAG, "onCreated: av_Position " + av_Position);
        Log.d(TAG, "onCreated: af_Position " + af_Position);
        Log.d(TAG, "onCreated: s_Texture " + s_Texture);
        Log.e(TAG, "onCreated: error " + GLES30.glGetError());
    }

    @Override
    protected void onChanged(int width, int height) {

    }

    @Override
    protected void onDraw() {
        clear();
        useProgram();
        viewPort(0, 0, width, height);

        GLES30.glEnableVertexAttribArray(av_Position);
        GLES30.glEnableVertexAttribArray(af_Position);
//        GLES30.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES30.GL_FLOAT, false, VertexStride, mVertexBuffer);
//        GLES30.glVertexAttribPointer(af_Position, CoordsPerTextureCount, GLES30.GL_FLOAT, false, TextureStride, mDisplayTextureBuffer);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mVertexBufferId);
        GLES30.glVertexAttribPointer(av_Position, CoordsPerVertexCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mDisplayTextureBufferId);
        GLES30.glVertexAttribPointer(af_Position, CoordsPerTextureCount, GLES30.GL_FLOAT, false, 0, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureId);
        GLES30.glUniform1i(s_Texture, 0);
        // 绘制 GLES30.GL_TRIANGLE_STRIP:复用坐标
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, VertexCount);
        GLES30.glDisableVertexAttribArray(av_Position);
        GLES30.glDisableVertexAttribArray(af_Position);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
    }

    @Override
    protected String getVertexSource() {
        final String source = "attribute vec4 av_Position; " +
                "attribute vec2 af_Position; " +
                "varying vec2 v_texPo; " +
                "void main() { " +
                "    v_texPo = af_Position; " +
                "    gl_Position = av_Position; " +
                "}";
        return source;
    }

    @Override
    protected String getFragmentSource() {
        final String source = "precision mediump float;\n" +
                "varying vec2 v_texPo;\n" +
                "uniform sampler2D s_Texture;\n" +
                "void main() {\n" +
                "   vec4 tc = texture2D(s_Texture, v_texPo);\n" +
                "   gl_FragColor = texture2D(s_Texture, v_texPo);\n" +
                "}";
        return source;
    }
}
