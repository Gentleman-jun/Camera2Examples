package com.linkin.camera2examples.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.linkin.camera2examples.R;


public class MainActivity extends AppCompatActivity {

    private Button btnCamera;
    private Button btnCamera2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCamera = (Button) findViewById(R.id.btn_open_camera);
        btnCamera2 = (Button) findViewById(R.id.btn_open_camera2);


        btnCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraShowActivity.startActivity(MainActivity.this, false);
            }
        });

        btnCamera2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraShowActivity.startActivity(MainActivity.this, true);
            }
        });
    }
}
