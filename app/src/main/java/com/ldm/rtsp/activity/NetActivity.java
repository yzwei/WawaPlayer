package com.ldm.rtsp.activity;

import android.app.Activity;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Created by wyz on 2017/9/3.
 */

public class NetActivity implements Runnable {
    private Socket socket;
    private OutputStream os;
    private InputStream is;
    // 要发的数据
    Pkg pkg;

    public void run() {
        try {
            socket = new Socket("10.0.2.2", 8888);
            os = socket.getOutputStream();
            pkg = new Pkg(3, 1400, 6);
            os.write(pkg.toBytes());
            os.flush();

            is = socket.getInputStream();
            System.out.println();


        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                os.close();
            } catch(Exception ee) {
                ee.printStackTrace();
            }
        }
    }

    public static String bytes2hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        String tmp = null;
        for (byte b : bytes) {
            // 将每个字节与0xFF进行与运算，然后转化为10进制，然后借助于Integer再转化为16进制
            tmp = Integer.toHexString(0xFF & b);
            if (tmp.length() == 1) {
                tmp = "0" + tmp;
            }
            sb.append(tmp);
        }
        return sb.toString();
    }
}
enum CLIENT_TYPE {
    CTYPE_RASPI,
    CTYPE_REMOTE,
}
/*
enum PKG_CMD_TYPE implements Serializable {
    PKG_REGISTER_SERVER_REQ(1),
    PKG_REGISTER_SERVER_RES(2),
    PKG_REGISTER_CLIENT_REQ(3),
    PKG_REGISTER_CLIENT_RES(4),
    PKG_STREAM_DATA_REQ(5),
    PKG_STREAM_DATA_RES(6),
    PKG_STREAM_DATA_NTF(7),
    PKG_CHOOSE_SERVER_REQ(8),
    PKT_CHOOSE_SERVER_RES(9),
}
*/
class PKG_CMD_TYPE {
    public static final int PKG_REGISTER_SERVER_REQ = 1;
    public static final int PKG_REGISTER_SERVER_RES = 2;
    public static final int PKG_REGISTER_CLIENT_REQ = 3;
    public static final int PKG_REGISTER_CLIENT_RES = 4;
    public static final int PKG_STREAM_DATA_REQ = 5;
    public static final int PKG_STREAM_DATA_RES = 6;
    public static final int PKG_STREAM_DATA_NTF = 7;
    public static final int PKG_CHOOSE_SERVER_REQ = 8;
    public static final int PKT_CHOOSE_SERVER_RES = 9;
    public static final int PKG_START_SEND_DATA_NTF = 10;
}
class RegisterServerReq {
    CLIENT_TYPE clientType;
}
class RegisterServerRes {
    int result;
}
class RegisterClientReq {
    CLIENT_TYPE clientType;
}
class RegisterClientRes {
    int result;
    int fd[];
}
class StreamDataReq {
    char buf[];
}
class StreamDataRes {
    int result;
}
class StreamDataNtf {
    char buf[];
}
class ChooseServerReq {
    int choose_fd;
}
class ChooseServerRes {
    int result;
}
class StartSendDataNtf {
    int reserve;
}

class Head {
    int cmd;
    int len;
    int fd; // find channel by fd
}

class Body {
    RegisterServerReq registerServerReq;
    RegisterServerRes registerServerRes;
    RegisterClientReq registerClientReq;
    RegisterClientRes registerClientRes;
    StreamDataReq streamDataReq;
    StreamDataRes streamDataRes;
    StreamDataNtf streamDataNtf;
    ChooseServerReq chooseServerReq;
    ChooseServerRes chooseServerRes;
    StartSendDataNtf startSendDataNtf;
}

class Pkg {
    int cmd;
    int len;
    int fd = 0;
    int a = 0;
    int b = 0;
    int c = 0;
    int d = 0;
    int e = 0;
    int f = 0;
    int g = 0;
    int choice;
    public Pkg(int cmd, int len, int choice) {
        this.cmd = cmd;
        this.len = len;
        this.choice = choice;
    }

    public byte[] toBytes() {
        byte[] rst = new byte[44];
        for(int i = 0; i < 44; i++) {
            rst[i] = 0;
        }
        rst[0] = 8;
        rst[4] = 4;
        for(int i = 8; i < 44; i += 4) {
            rst[i] = 6;
        }
        //rst[8] = 6
        //rst[40] = 6;
        //rst[36] = 6;
        //rst[32] = 6;
        /*
        byte[] Bcmd = new byte[4];
        byte[] Blen = new byte[4];
        byte[] Bfd = new byte[4];
        Bcmd = intToByteArray(this.cmd);
        Blen = intToByteArray(this.len);
        Bfd = intToByteArray(this.fd);
        for(int i = 0; i < 4; i++) {
            rst[i] = Bcmd[i];
        }
        for(int i = 4; i < 8; i++) {
            rst[i] = Blen[i - 4];
        }
        for(int i = 8; i < 12; i++) {
            rst[i] = Bfd[i - 8];
        }
        */
        return rst;
    }

    public static byte[] intToByteArray(int i) {
        byte[] result = new byte[4];
        //由低位到高位
        result[3] = (byte)((i >> 24) & 0xFF);
        result[2] = (byte)((i >> 16) & 0xFF);
        result[1] = (byte)((i >> 8) & 0xFF);
        result[0] = (byte)(i & 0xFF);
        return result;
    }

    public static int byteArrayToInt(byte[] bytes) {
        int value= 0;
        //由高位到低位
        for (int i = 0; i < 4; i++) {
            int shift= (4 - 1 - i) * 8;
            value +=(bytes[i] & 0x000000FF) << shift;//往高位游
        }
        return value;
    }
}
