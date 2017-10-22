package com.ldm.rtsp.activity;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.ldm.rtsp.R;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;


/**
 * @description 播放本地H264视频文件
 * @time 2016/12/19 15:22 参考翻译文档：http://www.cnblogs.com/Xiegg/p/3428529.html
 */
public class LocalH264Activity extends Activity {
    private SurfaceView mSurface = null;
    private SurfaceHolder mSurfaceHolder;
    private Thread mDecodeThread;
    private MediaCodec mCodec;
    private boolean mStopFlag = false;
    private DataInputStream mInputStream;
    private InputStream inputStream;
    private String FileName = "test.h264";
    private static final int VIDEO_WIDTH = 720;
    private static final int VIDEO_HEIGHT = 240;
    private int FrameRate = 15;
    private Boolean UseSPSandPPS = false;
    private String filePath = "/sdcard/Movies" + "/" + FileName;

    private Socket socket;
    private OutputStream os;
    private InputStream is;
    boolean local = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_rtsp);
        mSurface = (SurfaceView) findViewById(R.id.surfaceview);

        if(local) {
            /**
             *  读取本地文件
             */
            File f = new File(filePath);
            if (null == f || !f.exists() || f.length() == 0) {
                Toast.makeText(this, "视频文件不存在", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                //获取文件输入流
                mInputStream = new DataInputStream(new FileInputStream(new File(filePath)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                try {
                    if (null != mInputStream) {
                        mInputStream.close();
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        } else {
            /**
             * 读取网络流
             */
            new Thread() {
                public void run() {
                    try {
                        // 建立连接
                        //socket = new Socket("10.0.2.2", 8888);
                        socket = new Socket("122.152.213.73", 8888);
                        os = socket.getOutputStream();
                        // 发送握手命令
                        Pkg_Client_Req pkg_client_req = new Pkg_Client_Req(1, 4, 0, 1);
                        os.write(pkg_client_req.toBytes());
                        os.flush();
                        // 拿到服务器返回的数据
                        inputStream = socket.getInputStream();
                        byte buff[] = new byte[48];
                        int len = inputStream.read(buff);
                        byte[] fdArray = new byte[4];
                        fdArray[0] = buff[16];
                        fdArray[1] = buff[17];
                        fdArray[2] = buff[18];
                        fdArray[3] = buff[19];
                        int fd = ByteArrayToInt(fdArray);

                        // 发送fd指令
                        Pkg_Client_Req chooseServerReq = new Pkg_Client_Req(4, 4, 0, fd);
                        os.write(chooseServerReq.toBytes());

                        // 接下来就是接收数据了

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            //os.close();
                        } catch (Exception ee) {
                            ee.printStackTrace();
                        }
                    }
                }
            }.start();
        }
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try
                {
                    //通过多媒体格式名创建一个可用的解码器
                    mCodec = MediaCodec.createDecoderByType("video/avc");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //初始化编码器
                //final MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);
                MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);
                //获取h264中的pps及sps数据
                if (UseSPSandPPS) {
                    byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
                    byte[] header_pps = {0, 0, 0, 1, 104, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
                    mediaformat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                    mediaformat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
                }
                //设置帧率
                mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, FrameRate);
                //mediaformat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                //https://developer.android.com/reference/android/media/MediaFormat.html#KEY_MAX_INPUT_SIZE
                //设置配置参数，参数介绍 ：
                // format	如果为解码器，此处表示输入数据的格式；如果为编码器，此处表示输出数据的格式。
                //surface	指定一个surface，可用作decode的输出渲染。
                //crypto	如果需要给媒体数据加密，此处指定一个crypto类.
                //   flags	如果正在配置的对象是用作编码器，此处加上CONFIGURE_FLAG_ENCODE 标签。
                mCodec.configure(mediaformat, holder.getSurface(), null, 0);
                startDecodingThread();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mCodec.stop();
                mCodec.release();
            }
        });
    }


    private void startDecodingThread() {
        mCodec.start();
        mDecodeThread = new Thread(new decodeThread());
        mDecodeThread.start();
    }

    /**
     * @author ldm
     * @description 解码线程
     * @time 2016/12/19 16:36
     */
    private class decodeThread implements Runnable {
        @Override
        public void run() {
            try {
                decodeLoop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void decodeLoop() {
            //存放目标文件的数据
            ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
            //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;
            byte[] marker0 = new byte[]{0, 0, 0, 1};
            byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
            byte[] streamBuffer = null;
            try {
                //streamBuffer = getBytes(mInputStream);
                streamBuffer = getBytes(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int bytes_cnt = 0;
            while (!mStopFlag) {
                bytes_cnt = streamBuffer.length;
                if (bytes_cnt == 0) {
                    streamBuffer = dummyFrame;
                }

                int startIndex = 0;
                int remaining = bytes_cnt;
                while (true) {
                    if (remaining == 0 || startIndex >= remaining) {
                        break;
                    }
                    int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                    if (nextFrameStart == -1) {
                        nextFrameStart = remaining;
                    } else {
                    }

                    int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                    if (inIndex >= 0) {
                        ByteBuffer byteBuffer = inputBuffers[inIndex];
                        byteBuffer.clear();
                        byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
                        //在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
                        mCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                        startIndex = nextFrameStart;
                    } else {
                        continue;
                    }

                    int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
                    if (outIndex >= 0) {
                        //帧控制是不在这种情况下工作，因为没有PTS H264是可用的
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        boolean doRender = (info.size != 0);
                        //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                        mCodec.releaseOutputBuffer(outIndex, doRender);
                    } else {
                    }
                }
                mStopFlag = true;
            }
        }
    }

    public byte[] getBytes(InputStream is) throws IOException {
        System.out.println("Get in getBytes function...");
        // 先过滤掉16个字节的响应包
        byte[] useless = new byte[16];
        is.read(useless);

        //while(true) {} // 不间断播放？不知道这么写特么的行不行...fuck


        byte[] dataHead = new byte[12];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len;
        int count = 0;
        File file = new File("/sdcard/wyz.h264");
        FileOutputStream fos = new FileOutputStream(file);

        while((len = is.read(dataHead)) != -1) {
            count++;
            System.out.println("Loop index: " + count + "------------------");
            byte[] dataLenArray = new byte[4];
            System.arraycopy(dataHead, 4, dataLenArray, 0, 4);
            System.out.print("dataLenArray: ");
            for(int i = 0; i < 4; i++) {
                System.out.print(dataLenArray[i] + " ");
            }
            System.out.println("");
            if(dataLenArray[0] == 3 && dataLenArray[1] == 0) {
                System.out.println("Here exception#############################");
            }
            int dataLen = ByteArrayToInt(dataLenArray);
            System.out.println("Video data len: " + dataLen);
            byte[] videoData = new byte[dataLen];
            int readLen = 0;
            while(readLen < dataLen) {
                readLen += is.read(videoData, readLen, dataLen - readLen);
            }
            System.out.println("Alread read: " + readLen);
            // 先写个文件出来
            //fos.write(videoData);
            bos.write(videoData, 0, dataLen);
            if(count == 1000)
                break;
        }
        inputStream.close();
        return bos.toByteArray();
    }

    int KMPMatch(byte[] pattern, byte[] bytes, int start, int remain) {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int[] lsp = computeLspTable(pattern);

        int j = 0;  // Number of chars matched in pattern
        for (int i = start; i < remain; i++) {
            while (j > 0 && bytes[i] != pattern[j]) {
                // Fall back in the pattern
                j = lsp[j - 1];  // Strictly decreasing
            }
            if (bytes[i] == pattern[j]) {
                // Next char matched, increment position
                j++;
                if (j == pattern.length)
                    return i - (j - 1);
            }
        }

        return -1;  // Not found
    }

    int[] computeLspTable(byte[] pattern) {
        int[] lsp = new int[pattern.length];
        lsp[0] = 0;  // Base case
        for (int i = 1; i < pattern.length; i++) {
            // Start by assuming we're extending the previous LSP
            int j = lsp[i - 1];
            while (j > 0 && pattern[i] != pattern[j])
                j = lsp[j - 1];
            if (pattern[i] == pattern[j])
                j++;
            lsp[i] = j;
        }
        return lsp;
    }

    int ByteArrayToInt(byte[] b) {
        return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8) | ((b[2] & 0xFF) << 16) | ((b[3] << 24 & 0xFF) << 24);
    }
}