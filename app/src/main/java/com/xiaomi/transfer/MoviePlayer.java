package com.xiaomi.transfer;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.xiaomi.glbase.GlUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC;


/**
 * Plays the video track from a movie file to a Surface.
 * <p>
 * TODO: needs more advanced shuttle controls (pause/resume, skip)
 */
public class MoviePlayer implements AudioDecoder.AudioFrameCallback {
    private static final String TAG = "MoviePlayer";
    private static final boolean VERBOSE = true;

    // Declare this here to reduce allocations.
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    // May be set/read by different threads.
    private volatile boolean mIsStopRequested;

    private File mSourceFile;
    private Surface mOutputSurface;
    FrameCallback mFrameCallback;
    private boolean mLoop;
    private int mVideoWidth;
    private int mVideoHeight;
    private long mOutputFrames = 0;
    private boolean mEndOfDecoder = false;
    private long mStartTime = 0 ;

    private long mSeekPosMS = 0;

    private final Object mWaitEvent = new Object();

    private MediaFormat mAudioFromate = null;
    private MediaFormat mVideoFromate = null;
    private int mMaxSize = 0;
    private int mMaxAudioSize = 0;

    private static  final  int AUDIO_ONLY = 1;
    private  int VIDEO_ONLY = 2;
    private  int AUDIO_VIDEO = 3;
    private int mSelectStream = 3;

    private AudioDecoder mAudioDecoder;

    private volatile  boolean mNotwait = false;


    /**
     * Interface to be implemented by class that manages playback UI.
     * <p>
     * Callback methods will be invoked on the UI thread.
     */
    public interface PlayerFeedback {
        void playbackStopped();
    }

    public static class AudioFrame {
        public ByteBuffer buffer;
        public MediaCodec.BufferInfo info;
    }

    public MediaFormat getAudioFromate() {
        return mAudioFromate;
    }

    public MediaFormat getVideoFromate() {
        return mVideoFromate;
    }
    /**
     * Callback invoked when rendering video frames.  The MoviePlayer client must
     * provide one of these.
     */
    public interface FrameCallback {
        void onAudioFormat(MediaFormat format);
        void onAudioFrame(AudioFrame frame);
        /**
         * Called immediately before the frame is rendered.
         * @param presentationTimeUsec The desired presentation time, in microseconds.
         */
        void preRender(long presentationTimeUsec);

        /**
         * Called immediately after the frame render call returns.  The frame may not have
         * actually been rendered yet.
         * TODO: is this actually useful?
         */
        void postRender();

        /**
         * Called after the last frame of a looped movie has been rendered.  This allows the
         * callback to adjust its expectations of the next presentation time stamp.
         */
        void loopReset();
    }


//    public MoviePlayer(File sourceFile, Surface outputSurface, FrameCallback frameCallback, PlayMovieActivity acti)
//            throws IOException {
//
//        this(sourceFile, outputSurface, frameCallback);
//        //mActive = acti;
//
//    }

    /**
     * Constructs a MoviePlayer.
     *
     * @param sourceFile The video file to open.
     * @param outputSurface The Surface where frames will be sent.
     * @param frameCallback Callback object, used to pace output.
     * @throws IOException
     */
    public MoviePlayer(File sourceFile, Surface outputSurface, FrameCallback frameCallback, long startTimeMs)
            throws IOException {
        //sourceFile = new File("/sdcard/voip-data/VID_20190619_201101.mp4");
        //sourceFile = new File("/sdcard/voip-data/11.mp4");
        mSourceFile = sourceFile;
        mSeekPosMS = startTimeMs;

        if (frameCallback == null) {
            frameCallback = new SpeedControlCallback();
        }

        Log.i(TAG, "  == " + sourceFile.getAbsolutePath());
        mOutputSurface = outputSurface;
        mFrameCallback = frameCallback;

        // Pop the file open and pull out the video characteristics.
        // TODO: consider leaving the extractor open.  Should be able to just seek back to
        //       the start after each iteration of play.  Need to rearrange the API a bit --
        //       currently play() is taking an all-in-one open+work+release approach.
        MediaExtractor extractor = null;
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(sourceFile.toString());
            if ((mSelectStream & AUDIO_ONLY) == AUDIO_ONLY) {
                int audioIndex = selectTrack(extractor, "audio");
                if (audioIndex != -1) {
                    mAudioFromate = extractor.getTrackFormat(audioIndex);
                    mFrameCallback.onAudioFormat(mAudioFromate);
                }

            }

            if ((mSelectStream & VIDEO_ONLY) != VIDEO_ONLY) {
                return;
            }
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                return;
                //throw new RuntimeException("No video track found in " + mSourceFile);
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            mVideoFromate = format;
            GlUtil.mPictureRotation = 0;
            if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                GlUtil.mPictureRotation = format.getInteger(MediaFormat.KEY_ROTATION);
            }

            if (GlUtil.mPictureRotation == 90 || GlUtil.mPictureRotation== 270) {
                mVideoHeight = format.getInteger(MediaFormat.KEY_WIDTH);
                mVideoWidth = format.getInteger(MediaFormat.KEY_HEIGHT);
            } else {
                mVideoWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                mVideoHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
            }

            GlUtil.mWidht = mVideoWidth;
            GlUtil.mHeight = mVideoHeight;

            Log.i(TAG, " MoviePlayer play url " + sourceFile.getAbsolutePath()+ " width " + mVideoWidth + " height " + mVideoHeight + " rotation " + GlUtil.mPictureRotation);
            if (VERBOSE) {
                Log.d(TAG, "Video size is " + mVideoWidth + "x" + mVideoHeight);
            }
        } finally {
            if (extractor != null) {
                extractor.release();
            }
        }
    }


    /**
     * Returns the width, in pixels, of the video.
     */
    public int getVideoWidth() {
        return mVideoWidth;
    }

    /**
     * Returns the height, in pixels, of the video.
     */
    public int getVideoHeight() {
        return mVideoHeight;
    }

    /**
     * Sets the loop mode.  If true, playback will loop forever.
     */
    public void setLoopMode(boolean loopMode) {
        mLoop = loopMode;
    }

    /**
     * Asks the player to stop.  Returns without waiting for playback to halt.
     * <p>
     * Called from arbitrary thread.
     */
    public void requestStop() {
        mIsStopRequested = true;
    }

    /**
     * Decodes the video stream, sending frames to the surface.
     * <p>
     * Does not return until video playback is complete, or we get a "stop" signal from
     * frameCallback.
     */

    @Override
    public void onAudioFrameDecoded(ByteBuffer data, MediaCodec.BufferInfo info) {
        AudioFrame frame = new AudioFrame();
        frame.buffer = data;
        frame.info = info;
        mFrameCallback.onAudioFrame(frame);
    }

    int maudioTrack = -1;
    public void play() throws IOException {
        MediaExtractor extractor = null;
        MediaCodec decoder = null;

        // The MediaExtractor error messages aren't very useful.  Check to see if the input
        // file exists so we can throw a better one if it's not there.
        if (!mSourceFile.canRead()) {
            throw new FileNotFoundException("Unable to read " + mSourceFile);
        }

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(mSourceFile.toString());
            if ((mSelectStream & AUDIO_ONLY) == AUDIO_ONLY)  {
                maudioTrack = selectTrack(extractor, "audio");
                if (maudioTrack != -1) {
                    MediaFormat mediaFormat = extractor.getTrackFormat(maudioTrack);
                    Log.i(TAG, " find audio track " + mediaFormat);
                    String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    Log.i(TAG, " get audio codec " + mime);
                    int audioChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    Log.i(TAG, " get audio channels " + audioChannels);
                    int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    Log.i(TAG, " get audio sampleRate " + audioSampleRate);
                    mMaxAudioSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    Log.i(TAG, " get audio input size  " + mMaxAudioSize);

                    extractor.selectTrack(maudioTrack);
                    mAudioDecoder = new AudioDecoder();
                    mAudioDecoder.registerCallback(this);
                    mAudioDecoder.InitAudioDecoder(mediaFormat);
                }
            }


            if ((mSelectStream & VIDEO_ONLY) == VIDEO_ONLY) {
                int trackIndex = selectTrack(extractor);
                if (trackIndex < 0) {
                    //throw new RuntimeException("No video track found in " + mSourceFile);
                }
                if (trackIndex >= 0) {
                    extractor.selectTrack(trackIndex);

                    MediaFormat format = extractor.getTrackFormat(trackIndex);
                    Log.i(TAG, " find video track " + format);
                    mMaxSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    // Create a MediaCodec decoder, and configure it with the MediaFormat from the
                    // extractor.  It's very important to use the format from the extractor because
                    // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(format, mOutputSurface, null, 0);
                    //decoder.configure(format, null, null, 0);
                    decoder.start();
                }
            }

            doExtract(extractor, -1, decoder, mFrameCallback);
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, " " + "eerrr0r");
        }
        finally {

            // release everything we grabbed
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
        }
    }

    /**
     * Selects the video track, if any.
     *
     * @return the track index, or -1 if no video track is found.
     */
    private static int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }

        return -1;
    }

    private static int selectTrack(MediaExtractor extractor, String type) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith(type +"/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }

        return -1;
    }

    public void getOneFrame() {
        if (mEndOfDecoder) {
            return;
        }
        synchronized (mWaitEvent) {
            mWaitEvent.notifyAll();
            mNotwait = true;
        }

    }

    /**
     * Work loop.  We execute here until we run out of video or are told to stop.
     */
    private void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
                           FrameCallback frameCallback) {



        final int TIMEOUT_USEC = 10000;
        //ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        int inputChunk = 0;
        long firstInputTimeNsec = -1;

        long decoder_used_time = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        boolean audioDone = false;

        long start_time = System.currentTimeMillis();

        extractor.seekTo(mSeekPosMS*1000, SEEK_TO_PREVIOUS_SYNC);
        while (!outputDone || !audioDone) {
            //if (VERBOSE) Log.d(TAG, "loop");
            if (mIsStopRequested) {
                Log.d(TAG, "Stop requested");
                mAudioDecoder.release();
                break;
            }
            long presentTime = 0;

            int Index = extractor.getSampleTrackIndex();
            Log.i(TAG, "get index " + Index);
            if (Index == maudioTrack && Index > 0) {
                int index = mAudioDecoder.getNextDecoderBufferIndex();

                int audioSize = extractor.readSampleData(mAudioDecoder.getNextDecoderBuffer(index), 0);
                //    public void queueInputBuffer(int index, int samples, long timeStamp, int flags)

                if (audioSize <= 0 || mIsStopRequested) {
                    Log.i(TAG,"EEEEEEEEEEEEEEE ");
                    mAudioDecoder.queueInputBuffer(index, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    audioDone = true;

                } else {
                    //Log.i(TAG, "get audio sample from mp4 size " + audioSize + " time " + extractor.getSampleTime() + " flags " + extractor.getSampleFlags());
                    mAudioDecoder.queueInputBuffer(index, audioSize, extractor.getSampleTime(), extractor.getSampleFlags());
                    // event
                    extractor.advance();
                    continue;
                }
            }

            // Feed more data to the decoder.
            if (!inputDone && !outputDone && decoder != null) {
                int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inputBufIndex >= 0) {
                    if (firstInputTimeNsec == -1) {
                        firstInputTimeNsec = System.nanoTime();
                    }
                    ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);//decoderInputBuffers[inputBufIndex];
                    // Read the sample data into the ByteBuffer.  This neither respects nor
                    // updates inputBuf's position, limit, etc.
                    int chunkSize = extractor.readSampleData(inputBuf, 0);
                    //inputBuf.put(byteBuf);
                    decoder_used_time = System.currentTimeMillis();
                    if (chunkSize < 0 || mIsStopRequested) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                        if (maudioTrack > 0) {
//                            int index = mAudioDecoder.getNextDecoderBufferIndex();
//                            mAudioDecoder.queueInputBuffer(index, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                            audioDone = true;
//                        }

                        inputDone = true;
                        Log.d(TAG, "sent input EOS");
                    } else {
//                        if (extractor.getSampleTrackIndex() != trackIndex) {
//                            Log.w(TAG, "WEIRD: got sample from track " +
//                                    extractor.getSampleTrackIndex() + ", expected " + trackIndex);
//                        }
                        long presentationTimeUs = extractor.getSampleTime();
                        //Log.i(TAG, " get video decoder present time " + presentationTimeUs);
                        presentTime = presentationTimeUs;
                        if (mStartTime == 0) {
                            mStartTime = presentationTimeUs;
                        }

                        decoder.queueInputBuffer(inputBufIndex, 0, chunkSize,
                                (presentationTimeUs), 0 /*flags*/);
                        if (VERBOSE) {
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" +
                                    chunkSize);
                        }
                        inputChunk++;
                        extractor.advance();
//                        if (mActive != null) {
//                            mActive.onFrameAvailable();
//                        }
                    }
                } else {
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                     Log.d(TAG, "no output from decoder available");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not important for us, since we're using Surface
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = decoder.getOutputFormat();
                    Log.d(TAG, "decoder output format changed: " + newFormat);

                    if(newFormat.containsKey("crop-top"))
                    {
                        int cropTop = newFormat.getInteger("crop-top");
                        Log.d(TAG, "Crop-top:" + cropTop);
                    }
                    if(newFormat.containsKey("crop-bottom"))
                    {
                        int cropBottom = newFormat.getInteger("crop-bottom");
                        Log.d(TAG, "Crop-bottom:" + cropBottom);
                    }
                    if(newFormat.containsKey("crop-left"))
                    {
                        int cropLeft = newFormat.getInteger("crop-left");
                        Log.d(TAG, "Crop-left:" + cropLeft);
                    }
                    if(newFormat.containsKey("crop-right"))
                    {
                        int cropRight = newFormat.getInteger("crop-right");
                        Log.d(TAG, "Crop-right:" + cropRight);
                    }
                    int width = newFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int height = newFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    Log.d(TAG, "width :" + width + " height:" + height );
                    // 判断输出格式是否支持
                    if (newFormat.containsKey(MediaFormat.KEY_COLOR_FORMAT)) {
                        newFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                        Log.d(TAG, "Color format:" + newFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT));
                    }
                    int keyStride = newFormat.getInteger(MediaFormat.KEY_STRIDE);
                    int keyStrideHeight = newFormat.getInteger(MediaFormat.KEY_SLICE_HEIGHT);
                    Log.d(TAG, " stride:" + keyStride +  " height stride:" + keyStrideHeight );


                } else if (decoderStatus < 0) {
                    throw new RuntimeException(
                            "unexpected result from decoder.dequeueOutputBuffer: " +
                                    decoderStatus);
                } else { // decoderStatus >= 0
                    if (firstInputTimeNsec != 0) {
                        // Log the delay from the first buffer of input to the first buffer
                        // of output.
                        long nowNsec = System.nanoTime();
                        Log.d(TAG, "startup lag " + ((nowNsec-firstInputTimeNsec) / 1000000.0) + " ms");
                        firstInputTimeNsec = 0;
                    }
                    boolean doLoop = false;
                    if (VERBOSE)
                        Log.d(TAG, "surface decoder given buffer " + decoderStatus +
                            " (size=" + mBufferInfo.size + ")" + decoder.getOutputBuffer(decoderStatus));
                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        long end = System.currentTimeMillis();
                         Log.i(TAG, "output EOS used " + (end - start_time));
                        //mAudioDecoder.release();
                        if (mLoop) {
                            doLoop = true;
                        } else {
                            outputDone = true;
                        }
                    }

                    boolean doRender = (mBufferInfo.size != 0);

                    // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                    // to SurfaceTexture to convert to a texture.  We can't control when it
                    // appears on-screen, but we can manage the pace at which we release
                    // the buffers.
                   // Log.i("pppp", "ppppppp pre" + " pid "  + Thread.currentThread().getId());
                    if (doRender && frameCallback != null) {
                         //frameCallback.preRender(mBufferInfo.presentationTimeUs);
                    }
                    //Log.i(TAG, " to release");
                    decoder.releaseOutputBuffer(decoderStatus, doRender);
                    //Log.i(TAG, " to release end");
                    //decoder.releaseOutputBuffer(decoderStatus, false);
                    if (doRender && frameCallback != null) {
                        //frameCallback.postRender();
                    }

                    long t1 = System.currentTimeMillis();
                    mOutputFrames++;
//                    Log.i(TAG, "post output frames " + mOutputFrames + " size " + mBufferInfo.size +  " pid " + Thread.currentThread().getId()
//                                + " used " + (t1 - decoder_used_time)
//                                + " pid " + Thread.currentThread().getId()
//                                + " pts " + presentTime
//                                + " new pts " + (presentTime - mStartTime)
//                                + " eof " + (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM));
                    if (!outputDone) {
                        synchronized (mWaitEvent) {
                            try {
                                if (mNotwait == false) {
                                    mWaitEvent.wait(1000);
                                    mNotwait = false;
                                }

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        Log.i(TAG, "wait= " + (System.currentTimeMillis() - t1));
                    }


                    if (doLoop) {
                        Log.d(TAG, "Reached EOS, looping");
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        inputDone = false;
                        decoder.flush();    // reset decoder state
                        frameCallback.loopReset();
                    }
                }
            }
        }

        mEndOfDecoder = true;
        Log.i(TAG, " end of decoder ");
    }

    /**
     * Thread helper for video playback.
     * <p>
     * The PlayerFeedback callbacks will execute on the thread that creates the object,
     * assuming that thread has a looper.  Otherwise, they will execute on the main looper.
     */
    public static class PlayTask implements Runnable {
        private static final int MSG_PLAY_STOPPED = 0;

        private MoviePlayer mPlayer;
        private PlayerFeedback mFeedback;
        private boolean mDoLoop;
        private Thread mThread;
        private LocalHandler mLocalHandler;

        private final Object mStopLock = new Object();

        private boolean mStopped = false;

        /**
         * Prepares new PlayTask.
         *
         * @param player The player object, configured with control and output.
         * @param feedback UI feedback object.
         */
        public PlayTask(MoviePlayer player, PlayerFeedback feedback) {
            mPlayer = player;
            mFeedback = feedback;

            mLocalHandler = new LocalHandler();
        }

        /**
         * Sets the loop mode.  If true, playback will loop forever.
         */
        public void setLoopMode(boolean loopMode) {
            mDoLoop = loopMode;
        }

        /**
         * Creates a new thread, and starts execution of the player.
         */
        public void execute() {
            mPlayer.setLoopMode(mDoLoop);
            mThread = new Thread(this, "Movie Player");
            mThread.start();
        }

        /**
         * Requests that the player stop.
         * <p>
         * Called from arbitrary thread.
         */
        public void requestStop() {
            mPlayer.requestStop();
        }

        /**
         * Wait for the player to stop.
         * <p>
         * Called from any thread other than the PlayTask thread.
         */
        public void waitForStop() {
            synchronized (mStopLock) {
                while (!mStopped) {
                    try {
                        mStopLock.wait();
                    } catch (InterruptedException ie) {
                        // discard
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                mPlayer.play();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            } finally {
                // tell anybody waiting on us that we're done
                synchronized (mStopLock) {
                    mStopped = true;
                    mStopLock.notifyAll();
                }

                // Send message through Handler so it runs on the right thread.
                mLocalHandler.sendMessage(
                        mLocalHandler.obtainMessage(MSG_PLAY_STOPPED, mFeedback));
            }
        }

        private static class LocalHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                int what = msg.what;

                switch (what) {
                    case MSG_PLAY_STOPPED:
                        PlayerFeedback fb = (PlayerFeedback) msg.obj;
                        fb.playbackStopped();
                        break;
                    default:
                        throw new RuntimeException("Unknown msg " + what);
                }
            }
        }
    }


    public class SpeedControlCallback implements MoviePlayer.FrameCallback {
        private static final String TAG = "SpeedControlCallback";
        private static final boolean CHECK_SLEEP_TIME = false;

        private static final long ONE_MILLION = 1000000L;

        private long mPrevPresentUsec;
        private long mPrevMonoUsec;
        private long mFixedFrameDurationUsec;
        private boolean mLoopReset;

        /**
         * Sets a fixed playback rate.  If set, this will ignore the presentation time stamp
         * in the video file.  Must be called before playback thread starts.
         */
        public void setFixedPlaybackRate(int fps) {
            mFixedFrameDurationUsec = ONE_MILLION / fps;
        }

        @Override
        public void onAudioFormat(MediaFormat format) {

        }

        @Override
        public void onAudioFrame(AudioFrame frame) {

        }

        // runs on decode thread
        @Override
        public void preRender(long presentationTimeUsec) {
            // For the first frame, we grab the presentation time from the video
            // and the current monotonic clock time.  For subsequent frames, we
            // sleep for a bit to try to ensure that we're rendering frames at the
            // pace dictated by the video stream.
            //
            // If the frame rate is faster than vsync we should be dropping frames.  On
            // Android 4.4 this may not be happening.

            if (mPrevMonoUsec == 0) {
                // Latch current values, then return immediately.
                mPrevMonoUsec = System.nanoTime() / 1000;
                mPrevPresentUsec = presentationTimeUsec;
            } else {
                // Compute the desired time delta between the previous frame and this frame.
                long frameDelta;
                if (mLoopReset) {
                    // We don't get an indication of how long the last frame should appear
                    // on-screen, so we just throw a reasonable value in.  We could probably
                    // do better by using a previous frame duration or some sort of average;
                    // for now we just use 30fps.
                    mPrevPresentUsec = presentationTimeUsec - ONE_MILLION / 30;
                    mLoopReset = false;
                }
                if (mFixedFrameDurationUsec != 0) {
                    // Caller requested a fixed frame rate.  Ignore PTS.
                    frameDelta = mFixedFrameDurationUsec;
                } else {
                    frameDelta = presentationTimeUsec - mPrevPresentUsec;
                }
                if (frameDelta < 0) {
                    Log.w(TAG, "Weird, video times went backward");
                    frameDelta = 0;
                } else if (frameDelta == 0) {
                    // This suggests a possible bug in movie generation.
                    Log.i(TAG, "Warning: current frame and previous frame had same timestamp");
                } else if (frameDelta > 10 * ONE_MILLION) {
                    // Inter-frame times could be arbitrarily long.  For this player, we want
                    // to alert the developer that their movie might have issues (maybe they
                    // accidentally output timestamps in nsec rather than usec).
                    Log.i(TAG, "Inter-frame pause was " + (frameDelta / ONE_MILLION) +
                            "sec, capping at 5 sec");
                    frameDelta = 5 * ONE_MILLION;
                }

                long desiredUsec = mPrevMonoUsec + frameDelta;  // when we want to wake up
                long nowUsec = System.nanoTime() / 1000;
                while (nowUsec < (desiredUsec - 100) /*&& mState == RUNNING*/) {
                    // Sleep until it's time to wake up.  To be responsive to "stop" commands
                    // we're going to wake up every half a second even if the sleep is supposed
                    // to be longer (which should be rare).  The alternative would be
                    // to interrupt the thread, but that requires more work.
                    //
                    // The precision of the sleep call varies widely from one device to another;
                    // we may wake early or late.  Different devices will have a minimum possible
                    // sleep time. If we're within 100us of the target time, we'll probably
                    // overshoot if we try to sleep, so just go ahead and continue on.
                    long sleepTimeUsec = desiredUsec - nowUsec;
                    if (sleepTimeUsec > 500000) {
                        sleepTimeUsec = 500000;
                    }
                    try {
                        if (CHECK_SLEEP_TIME) {
                            long startNsec = System.nanoTime();
                            Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                            long actualSleepNsec = System.nanoTime() - startNsec;
                            Log.d(TAG, "sleep=" + sleepTimeUsec + " actual=" + (actualSleepNsec/1000) +
                                    " diff=" + (Math.abs(actualSleepNsec / 1000 - sleepTimeUsec)) +
                                    " (usec)");
                        } else {
                            Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                        }
                    } catch (InterruptedException ie) {}
                    nowUsec = System.nanoTime() / 1000;
                }

                // Advance times using calculated time values, not the post-sleep monotonic
                // clock time, to avoid drifting.
                mPrevMonoUsec += frameDelta;
                mPrevPresentUsec += frameDelta;
            }
        }

        // runs on decode thread
        @Override public void postRender() {}

        @Override
        public void loopReset() {
            mLoopReset = true;
        }
    }
}