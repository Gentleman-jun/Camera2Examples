package com.linkin.camera2examples.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.linkin.camera2examples.R;
import com.linkin.camera2examples.camera.Camera1Preview;
import com.linkin.camera2examples.camera.Camera2Preview;
import com.linkin.camera2examples.camera.CameraView;
import com.linkin.camera2examples.util.DeviceUtils;

/**
 * Created by Linkin on 2018/8/9.
 * 作者: 刘忠俊
 * 日期: 2018年08月09日
 * 描述: 首页面选择
 */

public class CameraShowActivity extends AppCompatActivity {


    public static final String OPEN_CAMERA2 = "openCamera2";
    private boolean isOpenCamera2;


    private CameraView mCameraPreview;
    private FrameLayout parentView;

    public static void startActivity(Activity activity, boolean openCamera2) {
        Intent intent = new Intent(activity, CameraShowActivity.class);
        intent.putExtra(OPEN_CAMERA2, openCamera2);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
//        hideBottomUIMenu();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_show);
        isOpenCamera2 = getIntent().getBooleanExtra(OPEN_CAMERA2, false);
        if (!DeviceUtils.checkCameraHardware(this)) {
            Toast.makeText(this, "当前设备无法不支持相机！", Toast.LENGTH_SHORT).show();
            return;
        }

        parentView = (FrameLayout) findViewById(R.id.camera_preview);


        if (isOpenCamera2) {
            if (Build.VERSION.SDK_INT < 21) {
                Toast.makeText(this, "必须在Android 5.0及其以上才能使用 Camear2 API！", Toast.LENGTH_SHORT).show();
                return;
            }
            mCameraPreview = new Camera2Preview(this);
        } else {
            mCameraPreview = new Camera1Preview(this);
        }

        parentView.addView((View) mCameraPreview);


        findViewById(R.id.button_capture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraPreview.takePicture(new CameraView.TakePictureCallback() {
                    @Override
                    public void success(String picturePath) {
                        Toast.makeText(CameraShowActivity.this, "图片保存地址：" + picturePath, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void error(String error) {
                        Toast.makeText(CameraShowActivity.this, "错误信息：" + error, Toast.LENGTH_SHORT).show();

                    }
                });
            }
        });

        findViewById(R.id.button_switch).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraPreview.switchCameraFacing();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraPreview.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraPreview.onPause();
    }

    /**
     * 隐藏虚拟按键，并且全屏
     */
    protected void hideBottomUIMenu() {
        //隐藏虚拟按键，并且全屏
        if (Build.VERSION.SDK_INT < 19) { // lower api
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
//                    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    | View.SYSTEM_UI_FLAG_IMMERSIVE;
            decorView.setSystemUiVisibility(uiOptions);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }


}
