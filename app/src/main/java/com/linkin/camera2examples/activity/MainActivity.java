package com.linkin.camera2examples.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.linkin.camera2examples.R;


/**
 * @author Linkin
 */
public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_open_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraShowActivity.startActivity(MainActivity.this, false);
            }
        });

        findViewById(R.id.btn_open_camera2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraShowActivity.startActivity(MainActivity.this, true);
            }
        });
    }
}
