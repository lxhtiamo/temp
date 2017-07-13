package com.linewell.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //SpeechUtility.createUtility(this, "appid=5900325a");
        SpeechUtility.createUtility(this, SpeechConstant.APPID +"=5900325a");
        setContentView(R.layout.activity_main);
        TextView viewById = (TextView) findViewById(R.id.tv);
        viewById.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(getApplicationContext(),BaseVideoActivity.class);
                startActivity(intent);

            }
        });
    }
}
