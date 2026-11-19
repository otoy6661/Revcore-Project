package com.example.hellorevcore;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Contoh minimal untuk memahami siklus aplikasi Android, Activity, View, dan
 * pembuatan APK. Ini bukan aplikasi RevCore dan tidak berkomunikasi dengan ECU.
 */
public class MainActivity extends Activity {

    private int counter = 0;
    private TextView counterView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Hello RevCore Sample");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Contoh aplikasi mandiri. Tidak ada USB, ECU, atau data kendaraan.");
        explanation.setTextSize(14);
        explanation.setGravity(Gravity.CENTER);
        explanation.setPadding(0, padding / 2, 0, padding);
        root.addView(explanation);

        counterView = new TextView(this);
        counterView.setText("Tombol ditekan: 0");
        counterView.setTextSize(18);
        counterView.setGravity(Gravity.CENTER);
        root.addView(counterView);

        Button button = new Button(this);
        button.setText("Tekan saya");
        button.setOnClickListener(v -> {
            counter++;
            counterView.setText("Tombol ditekan: " + counter);
        });
        root.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }
}
