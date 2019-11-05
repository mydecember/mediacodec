package com.example.transferdemon;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.xiaomi.muxertest.TestDemuxerSync;
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
        checkPermission();


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
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA}; // 选择你需要申请的权限
            for (int i = 0; i < permissions.length; i++) {
                int state = ContextCompat.checkSelfPermission(this, permissions[i]);
                if (state != PackageManager.PERMISSION_GRANTED) { // 判断权限的状态
                    ActivityCompat.requestPermissions(this, permissions, 200); // 申请权限
                    return;
                }
            }
        }
    }



    private void initThread()
    {
        mHandlerThread = new HandlerThread("check-message-coming");
        mHandlerThread.setPriority(Thread.MAX_PRIORITY);
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


    private Thread createOutputThread() {
        return new Thread("Mediacodec_outputThread") {
            public void run() {

                //AVDemuxer.TestAsync();
                TestDemuxerSync.Test();
                //AVDemuxer.TestReadfile();

            }
        };
    }

    private void start() {
        boolean sel = true;
        //createOutputThread().start();
        if (sel ) {
            //createOutputThread().start();
            //TestDemuxerSync.TestAsync();

            //TestDemuxerSync.Test();
            TestDemuxerSync.TestDemuxerMuxer();
            //TestDemuxerSync.TestDemuxerSurface();
            //TestDemuxerSync.TestDemuxerMuxerSurface();
        } else {
            mTranscode = new MiVideoTranscode();
            String source1 = "/sdcard/voip-data/VID_20190619_201101.mp4";
            String source2 = "/sdcard/voip-data/mi_h265_4k.mp4";
            String source3 = "/sdcard/voip-data/source_avc_1920_1080.mp4";
            String source4 = "/sdcard/voip-data/mi_720.mp4";
            String source5 = "/sdcard/voip-data/result_huawei.mp4";
            String source6 = "/sdcard/voip-data/VID_mi_num.mp4";
            String source7 = "/sdcard/voip-data/dou.mp4";
            String source8 = "/sdcard/voip-data/832_468.mp4";
            //String source9 = "/sdcard/voip-data/liyuan.mp4";
            //String source9 = "/sdcard/voip-data/tmp.mov";
            //String source9 = "/sdcard/voip-data/pingfandeyitian.mp3";
            //String source9 = "/sdcard/voip-data/demo.mkv";
            String source9 = "/sdcard/voip-data/video_360_640.mp4";
            String source10 = "/sdcard/voip-data/huawei_4k.mp4";


            //mTranscode.setTransferDurationTime(0, 10123);
            mTranscode.startTransfer(source7, "avc", 0, 0 , 0, "/sdcard/voip-data/result.mp4",
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
//

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
