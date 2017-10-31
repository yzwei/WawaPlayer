package com.ldm.rtsp.activity;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.ldm.rtsp.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


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

    private Socket socket;
    private OutputStream os;
    private InputStream is;

    // 用来存放视频数据，网络不断往里写，解码器不断往外读
    private BlockingQueue<byte[]> videoDataQueue = new ArrayBlockingQueue<>(10000);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_rtsp);
        mSurface = (SurfaceView) findViewById(R.id.surfaceview);

        /**
         * 开一个线程读取网络流
         */
        new Thread() {
            public void run() {
                try {
                    System.out.println("Thread reading from network: " + this.getName());
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
                    // 向videoDataQueue中放入视频数据
                    try {
                        getBytes(inputStream);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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
                //https://developer.android.com/reference/android/media/MediaFormat.html#KEY_MAX_INPUT_SIZE
                //设置配置参数，参数介绍 ：
                // format	如果为解码器，此处表示输入数据的格式；如果为编码器，此处表示输出数据的格式。
                // surface	指定一个surface，可用作decode的输出渲染。
                // crypto	如果需要给媒体数据加密，此处指定一个crypto类.
                // flags	如果正在配置的对象是用作编码器，此处加上CONFIGURE_FLAG_ENCODE 标签。
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
        System.out.println("Thread decoding data: " + mDecodeThread.getName());
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

        private void decodeLoop() throws IOException {
            //存放目标文件的数据
            ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
            //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;
            byte[] marker0 = new byte[]{0, 0, 0, 1};
            byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
            byte[] streamBuffer = null;

            // 写一个文件出来跟原始数据对比
            //File videoFile = new File("/sdcard/wyz1.h264");
            //FileOutputStream fos = new FileOutputStream(videoFile);
            int bytes_cnt = 0;
            int cnt = 0;
            ArrayList<Byte> frameBytes = new ArrayList<Byte>();
            while (!mStopFlag) {
                // 如果视频数据队列不为空，则取出来
                try {
                    if (!videoDataQueue.isEmpty()) {
                        streamBuffer = videoDataQueue.take();
                        System.out.println("Dequeue videoDataQueue -- count:" + cnt++);
                    } else {
                        continue;
                    }
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }

                bytes_cnt = streamBuffer.length;
                // 写文件的数据
                //byte[] byteForFile = new byte[bytes_cnt];
                //System.arraycopy(streamBuffer, 0, byteForFile, 0, bytes_cnt);
                //fos.write(byteForFile);

                if (bytes_cnt == 0) {
                    streamBuffer = dummyFrame;
                }

                int startIndex = 0;
                int remaining = bytes_cnt;
                int lastNextFrameStart = -1;
                while (true) {
                    if (remaining == 0 || startIndex >= remaining) {
                        break;
                    }
                    //int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);

                    int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex, remaining);
                    if(nextFrameStart == lastNextFrameStart) { // 如果还是刚才那个，就往后找一个
                        nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                    }
                    if(0 == nextFrameStart) {
                        if(frameBytes.size() != 0) { // 把已经有的数据放到mediaCodec的inputBuffer中去
                            int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                            if(inIndex >= 0) {
                                ByteBuffer byteBuffer = inputBuffers[inIndex];
                                byteBuffer.clear();
                                byte[] tmp = new byte[frameBytes.size()];
                                //System.arraycopy(frameBytes.toArray(), 0, tmp, 0, frameBytes.size());
                                for(int i = 0; i < frameBytes.size(); i++) {
                                    tmp[i] = (byte)frameBytes.toArray()[i];
                                }
                                byteBuffer.put(tmp, 0, frameBytes.size());
                                mCodec.queueInputBuffer(inIndex, 0, tmp.length, 0, 0);
                                startIndex = nextFrameStart;
                                frameBytes.clear();
                            } else { continue; }
                        } else {// frameBytes.size() == 0
                            nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                            lastNextFrameStart = nextFrameStart;
                            if(-1 != nextFrameStart) { // 找到了标志位
                                Byte[] partialBytes = new Byte[nextFrameStart - startIndex];
                                for(int i = 0; i < nextFrameStart - startIndex; i++) {
                                    partialBytes[i] = Byte.valueOf(streamBuffer[i]);
                                }
                                frameBytes.addAll(Arrays.asList(partialBytes));

                                int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                                if(inIndex >= 0) {
                                    ByteBuffer byteBuffer = inputBuffers[inIndex];
                                    byteBuffer.clear();
                                    byte[] tmp = new byte[frameBytes.size()];
                                    for(int i = 0; i < frameBytes.size(); i++) {
                                        tmp[i] = (byte)frameBytes.toArray()[i];
                                    }
                                    byteBuffer.put(tmp, 0, frameBytes.size());
                                    mCodec.queueInputBuffer(inIndex, 0, tmp.length, 0, 0);
                                    startIndex = nextFrameStart;
                                    frameBytes.clear();
                                } else { continue; }
                            } else if(-1 == nextFrameStart) { // 剩下的数据中没有找到标志位，需要将没有标志位对应的数据暂存起来
                                Byte[] partialBytes = new Byte[remaining - startIndex];
                                for(int i = 0; i < remaining - startIndex; i++) {
                                    partialBytes[i] = Byte.valueOf(streamBuffer[i + startIndex]);
                                }
                                frameBytes.addAll(Arrays.asList(partialBytes));
                                startIndex = remaining;
                            }
                        }
                    } else if(-1 == nextFrameStart){
                        Byte[] partialBytes = new Byte[remaining - startIndex];
                        for(int i = 0; i < remaining - startIndex; i++) {
                            partialBytes[i] = Byte.valueOf(streamBuffer[i + startIndex]);
                        }
                        frameBytes.addAll(Arrays.asList(partialBytes));
                        startIndex = remaining;
                    } else {
                        Byte[] partialBytes = new Byte[nextFrameStart - startIndex];
                        for(int i = 0; i < nextFrameStart - startIndex; i++) {
                            partialBytes[i] = Byte.valueOf(streamBuffer[i]);
                        }
                        frameBytes.addAll(Arrays.asList(partialBytes));

                        int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                        if(inIndex >= 0) {
                            ByteBuffer byteBuffer = inputBuffers[inIndex];
                            byteBuffer.clear();
                            byte[] tmp = new byte[frameBytes.size()];
                            for(int i = 0; i < frameBytes.size(); i++) {
                                tmp[i] = (byte)frameBytes.toArray()[i];
                            }
                            byteBuffer.put(tmp, 0, frameBytes.size());
                            mCodec.queueInputBuffer(inIndex, 0, tmp.length, 0, 0);
                            startIndex = nextFrameStart;
                            frameBytes.clear();
                        } else { continue; }
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
                //mStopFlag = true;
            }
        }
    }

    public void getBytes(InputStream is) throws IOException {
        // 先过滤掉16个字节的响应包
        byte[] useless = new byte[16];
        is.read(useless);

        byte[] dataHead = new byte[12];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int len = 0;
        int count = 0;

        //File videoFile = new File("/sdcard/wyz.h264");
        //FileOutputStream fos = new FileOutputStream(videoFile);

        while(len < 12) {
            len += is.read(dataHead, len, 12 - len);
            if(len == 12)
            {
                count++;
                len = 0;
                byte[] dataLenArray = new byte[4];
                System.arraycopy(dataHead, 4, dataLenArray, 0, 4);

                int dataLen = ByteArrayToInt(dataLenArray);
                byte[] videoData = new byte[dataLen];
                int readLen = 0;
                while(readLen < dataLen) {
                    readLen += is.read(videoData, readLen, dataLen - readLen);
                }

                //byte[] byteForFile = new byte[dataLen];
                //System.arraycopy(videoData, 0, byteForFile, 0, dataLen);
                //fos.write(byteForFile);

                try {
                    videoDataQueue.put(videoData);
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else
            {
                continue;
            }
            System.out.println("Enqueue videoDataQueue -- count:" + count);
            if(count == 1000)
                break;
        }
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