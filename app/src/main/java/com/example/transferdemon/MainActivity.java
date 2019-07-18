package com.example.transferdemon;

import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.xiaomi.transfer.MiVideoTranscode;

public class MainActivity extends AppCompatActivity {
    private static final int CMD_START = 0x01;
    private static final int CMD_STOP = 0x02;

    MiVideoTranscode mTranscode;
    private Button mStartButton;
    private Button mStopButton;
    boolean mIsLoop = false;
    MediaMuxer muxer;


    private HandlerThread mHandlerThread;
    //子线程中的handler
    private Handler mThreadHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initThread();



        mStartButton = (Button) findViewById(R.id.start);
        mStartButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mThreadHandler.sendEmptyMessage(CMD_START);

            }
        });
        mStopButton = (Button) findViewById(R.id.stop);

        mStopButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mIsLoop = false;
                mThreadHandler.sendEmptyMessage(CMD_STOP);


            }
        });
    }

    private void initThread()
    {
        mHandlerThread = new HandlerThread("check-message-coming");
        mHandlerThread.start();

        mThreadHandler = new Handler(mHandlerThread.getLooper())
        {
            @Override
            public void handleMessage(Message msg)
            {
                switch (msg.what) {
                    case CMD_START:
                        start();
                        break;
                    case CMD_STOP:
                        stop();
                        break;
                }

            }
        };

    }


    private void start() {
        mTranscode = new MiVideoTranscode();
        String source1 = "/sdcard/voip-data/VID_20190619_201101.mp4";
        String source2 = "/sdcard/voip-data/mi_h265_4k.mp4";
        String source3 = "/sdcard/voip-data/source_avc_1920_1080.mp4";
        String source4 = "/sdcard/voip-data/mi_720.mp4";
        String source5 = "/sdcard/voip-data/result_huawei.mp4";
        String source6 = "/sdcard/voip-data/VID_mi_num.mp4";

        //mTranscode.setTransferDurationTime(3152, 5121);
        mTranscode.startTransfer(source1, "avc", 1920, 1080 , "/sdcard/voip-data/result.mp4",
                new MiVideoTranscode.TransferCallBack() {
                    public void onTransferEnd() {
                        Log.i("TTTTTTT", " generate target end");
                        if (mIsLoop)
                            mThreadHandler.sendEmptyMessage(CMD_STOP);
                    }
                    public void onTransferFailed() {

                    }
                });
    }
    private  void stop() {
        if (mTranscode != null) {
            mTranscode.stopTransfer(null);
            mTranscode = null;
        }
        if (mIsLoop)
            mThreadHandler.sendEmptyMessage(CMD_START);
    }
}
