package com.example.padelVAR; // ← Change this to match YOUR package name

import android.content.Intent;           // Needed to switch between screens
import android.media.MediaPlayer;        // Needed to play sound
import android.os.Bundle;               // Needed for onCreate
import android.view.animation.Animation; // Needed for animation
import android.view.animation.AnimationUtils; // Loads our bounce.xml file
import android.widget.Button;           // Needed to use Button
import android.widget.ImageView;        // Needed to use ImageView
import androidx.appcompat.app.AppCompatActivity; // Base class all activities extend

public class MainActivity extends AppCompatActivity {

    // Declare variables at the top — think of these as "boxes" that hold things
    private MediaPlayer mediaPlayer;  // Will hold our sound
    private ImageView animatedBall;   // Will hold the ball image
    private Button btnLiveAnalysis;   // Will hold button 1
    private Button btnReplay;         // Will hold button 2

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // onCreate() is called AUTOMATICALLY when this screen opens
        // This is where you set everything up

        super.onCreate(savedInstanceState); // Always call this first — required
        setContentView(R.layout.activity_main); // Connect this Java file to the XML layout

        // ════════════════════════════════════════════
        // STEP A: Connect Java variables to XML views
        // R.id.xxx must match the android:id in your XML
        // ════════════════════════════════════════════
        animatedBall   = findViewById(R.id.animatedBall);
        btnLiveAnalysis = findViewById(R.id.btnLiveAnalysis);
        btnReplay      = findViewById(R.id.btnReplay);

        // ════════════════════════════════════════════
        // STEP B: Play intro sound when app opens
        // R.raw.intro_sound looks for intro_sound.mp3 in res/raw/
        // ════════════════════════════════════════════
        mediaPlayer = MediaPlayer.create(this, R.raw.intro_sound);
        if (mediaPlayer != null) {
            mediaPlayer.start(); // Start playing immediately
        }

        // ════════════════════════════════════════════
        // STEP C: Load and start the bounce animation
        // AnimationUtils.loadAnimation reads our bounce.xml file
        // ════════════════════════════════════════════
        Animation bounceAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        animatedBall.startAnimation(bounceAnim); // Apply the animation to the ball image

        // ════════════════════════════════════════════
        // STEP D: Set up Button 1 — goes to LiveAnalysisActivity
        // setOnClickListener means "when this button is tapped, do this"
        // ════════════════════════════════════════════
        btnLiveAnalysis.setOnClickListener(view -> {
            // Intent = a message that says "I want to open THIS activity"
            // 'this' = current screen, LiveAnalysisActivity.class = destination screen
            Intent intent = new Intent(this, LiveAnalysisActivity.class);
            startActivity(intent); // Actually opens the new screen
        });

        // ════════════════════════════════════════════
        // STEP E: Set up Button 2 — goes to ReplayActivity
        // ════════════════════════════════════════════
        btnReplay.setOnClickListener(view -> {
            Intent intent = new Intent(this, ReplayActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        // onDestroy() is called when the screen is closed/destroyed
        // ALWAYS release MediaPlayer here to free memory — very important!
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release(); // Free the memory used by sound
            mediaPlayer = null;
        }
    }
}