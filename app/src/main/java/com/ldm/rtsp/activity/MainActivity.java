package com.ldm.rtsp.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.webkit.ClientCertRequest;
import android.widget.EditText;
import android.widget.Toast;

import com.ldm.rtsp.R;
import com.ldm.rtsp.utils.Constant;

//检查权限
import android.Manifest;
import android.Manifest.permission;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;

public class MainActivity extends Activity {
    private EditText rtsp_edt;
    private MainActivity self = this;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rtsp_edt = (EditText) findViewById(R.id.rtsp_edt);
        findViewById(R.id.rtsp_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                //视频流地址，例如：rtsp://192.168.1.168:80/0
                String url = rtsp_edt.getText().toString().trim();
                if (TextUtils.isEmpty(url) || !url.startsWith("rtsp://")) {
                    Toast.makeText(MainActivity.this, "RTSP视频流地址错误！", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(MainActivity.this, RtspActivity.class);
                intent.putExtra(Constant.RTSP_URL, url);
                startActivity(intent);
                */
                System.out.println("Click on rstp button.\n");
                NetActivity client = new NetActivity();
                new Thread(client).start();
            }
        });
        findViewById(R.id.local_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int permissionCheck = ContextCompat.checkSelfPermission(self, permission.READ_EXTERNAL_STORAGE);
                if(PackageManager.PERMISSION_GRANTED != permissionCheck) {
                    ActivityCompat.requestPermissions(self, new String[]{permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                }
                startActivity(new Intent(MainActivity.this, LocalH264Activity.class));
            }
        });
    }
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("Permission granted.\n");
                } else {
                    System.out.println("Permission denied.\n");
                }
            }
        }
    }
}