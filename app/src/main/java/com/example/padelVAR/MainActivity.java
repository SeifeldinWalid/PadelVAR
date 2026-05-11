package com.example.padelVAR;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // Fields — "boxes" that hold our views and sound
    private MediaPlayer mediaPlayer;
    private ImageView   animatedBall;
    private Button      btnLiveAnalysis;
    private Button      btnReplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ════════════════════════════════════════════
        // STEP A: Connect Java variables to XML views
        // ════════════════════════════════════════════
        animatedBall    = findViewById(R.id.animatedBall);
        btnLiveAnalysis = findViewById(R.id.btnLiveAnalysis);
        btnReplay       = findViewById(R.id.btnReplay);

        // ════════════════════════════════════════════
        // STEP B: Play intro sound when app opens
        // ════════════════════════════════════════════
        mediaPlayer = MediaPlayer.create(this, R.raw.intro_sound);
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }

        // ════════════════════════════════════════════
        // STEP C: Load and start the bounce animation
        // ════════════════════════════════════════════
        Animation bounceAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        animatedBall.startAnimation(bounceAnim);

        // ════════════════════════════════════════════
        // STEP D: Button 1 — go to LiveAnalysisActivity
        // ════════════════════════════════════════════
        btnLiveAnalysis.setOnClickListener(view -> {
            Intent intent = new Intent(this, LiveAnalysisActivity.class);
            startActivity(intent);
        });

        // ════════════════════════════════════════════
        // STEP E: Button 2 — go to ReplayActivity
        // ════════════════════════════════════════════
        btnReplay.setOnClickListener(view -> {
            Intent intent = new Intent(this, ReplayActivity.class);
            startActivity(intent);
        });

        // ════════════════════════════════════════════
        // INTENT — Phone Call
        // ACTION_DIAL opens the phone dialer with the
        // number pre-filled. User must press the green
        // call button to actually place the call.
        // ════════════════════════════════════════════
        Button btnCall = findViewById(R.id.btnCall);
        btnCall.setOnClickListener(view -> {
            Uri phoneNumber = Uri.parse("tel:+201271329650");
            Intent callIntent = new Intent(Intent.ACTION_DIAL, phoneNumber);
            startActivity(callIntent);
        });

        // ════════════════════════════════════════════
        // BONUS INTENT — Long press logo opens Camera
        // ════════════════════════════════════════════
        ImageView logoImage = findViewById(R.id.logoImage);
        logoImage.setOnLongClickListener(view -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(cameraIntent);
            } else {
                Toast.makeText(this, "No camera found", Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        // ════════════════════════════════════════════
        // STEP F: Wire up the bottom navigation
        // (the METHOD is defined below — this just CALLS it)
        // ════════════════════════════════════════════
        setupBottomNavigation();
    }
    // ────────── END OF onCreate ──────────


    // ════════════════════════════════════════════════════════════
    // setupBottomNavigation()
    // Declared at the CLASS level (sibling of onCreate),
    // NOT inside onCreate. Methods can't be nested in Java.
    // ════════════════════════════════════════════════════════════
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_home); // Home is the active tab on this screen

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                // Already on home — do nothing
                return true;
            }
            if (id == R.id.nav_live) {
                startActivity(new Intent(this, LiveAnalysisActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            if (id == R.id.nav_replay) {
                startActivity(new Intent(this, ReplayActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            if (id == R.id.nav_courts) {
                startActivity(new Intent(this, NearbyPadelCourtsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release(); // Free memory used by sound
            mediaPlayer = null;
        }
    }
}