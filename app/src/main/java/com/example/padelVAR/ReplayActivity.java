package com.example.padelVAR;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

// Firebase imports — add these after completing Firebase setup
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ReplayActivity extends AppCompatActivity {

    // ── Existing views ────────────────────────────────────────────────
    private Button   btnBack, btnShare, btnConfirmIn, btnConfirmOut, btnOpenWeb;
    private TextView tvBallSpeed, tvLastShot, tvDecisionResult;

    // ── Firebase stream views (NEW) ───────────────────────────────────
    private TextView tvFirebaseIndicator;   // ● Live / ● Connecting...
    private TextView tvFrameCounter;        // Frame 47/120
    private TextView tvFirebaseAiStatus;    // AI ANALYZING TRAJECTORY...
    private TextView tvStreamFps;           // FPS value
    private TextView tvStreamLatency;       // ms latency value
    private TextView tvTrajectoryPoints;    // Tracking points count
    private TextView tvFirebaseConfidence;  // AI confidence %

    // ── Firebase references ───────────────────────────────────────────
    private DatabaseReference  replayRef;
    private ValueEventListener replayListener;

    // ─── Firebase database path structure ────────────────────────────
    //  padel_var/
    //    current_match/
    //      replay/
    //        frame_current : 47
    //        frame_total   : 120
    //        fps           : 60
    //        latency_ms    : 18
    //        trajectory_pts: 94
    //        ai_confidence : 87
    //        ai_status     : "AI ANALYZING TRAJECTORY..."
    //        ball_speed    : 247
    //        last_shot     : "IN"
    // ─────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replay);

        // ════════════════════════════════════════════
        // A: Wire existing views
        // ════════════════════════════════════════════
        btnBack          = findViewById(R.id.btnBack);
        btnShare         = findViewById(R.id.btnShare);
        btnConfirmIn     = findViewById(R.id.btnConfirmIn);
        btnConfirmOut    = findViewById(R.id.btnConfirmOut);
        btnOpenWeb       = findViewById(R.id.btnOpenWeb);
        tvBallSpeed      = findViewById(R.id.tvBallSpeed);
        tvLastShot       = findViewById(R.id.tvLastShot);
        tvDecisionResult = findViewById(R.id.tvDecisionResult);

        // ════════════════════════════════════════════
        // B: Wire Firebase UI views (NEW)
        // ════════════════════════════════════════════
        tvFirebaseIndicator  = findViewById(R.id.tvFirebaseIndicator);
        tvFrameCounter       = findViewById(R.id.tvFrameCounter);
        tvFirebaseAiStatus   = findViewById(R.id.tvFirebaseAiStatus);
        tvStreamFps          = findViewById(R.id.tvStreamFps);
        tvStreamLatency      = findViewById(R.id.tvStreamLatency);
        tvTrajectoryPoints   = findViewById(R.id.tvTrajectoryPoints);
        tvFirebaseConfidence = findViewById(R.id.tvFirebaseConfidence);

        // ════════════════════════════════════════════
        // C: Read putExtra data sent from LiveAnalysis
        // ════════════════════════════════════════════
        Intent receivedIntent = getIntent();
        int    ballSpeed = receivedIntent.getIntExtra("ball_speed", 0);
        String lastShot  = receivedIntent.getStringExtra("last_shot");
        String source    = receivedIntent.getStringExtra("source");

        if (ballSpeed != 0) {
            tvBallSpeed.setText(ballSpeed + " km/h");
        } else {
            tvBallSpeed.setText("N/A");
        }

        if (lastShot != null) {
            tvLastShot.setText(lastShot);
            tvLastShot.setTextColor(lastShot.equals("IN") ? 0xFF2ECC71 : 0xFFFF4444);
        }

        if ("live_analysis".equals(source)) {
            Toast.makeText(this, "VAR Review — From Live Analysis",
                    Toast.LENGTH_SHORT).show();
        }

        // ════════════════════════════════════════════
        // D: Start Firebase real-time stream (NEW)
        // ════════════════════════════════════════════
        startFirebaseStream();

        // ════════════════════════════════════════════
        // E: Button listeners (existing logic)
        // ════════════════════════════════════════════
        btnBack.setOnClickListener(v -> finish());

        btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Padel VAR Replay Decision");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "🎾 Padel VAR Replay Review\n" +
                            "Ball Speed: " + tvBallSpeed.getText() + "\n" +
                            "Shot Result: " + tvLastShot.getText() + "\n" +
                            "Decision: " + tvDecisionResult.getText() + "\n\n" +
                            "Powered by AI Padel VAR System");
            startActivity(Intent.createChooser(shareIntent, "Share VAR Decision via"));
        });

        btnConfirmIn.setOnClickListener(v -> {
            tvDecisionResult.setText("✅ CONFIRMED: IN");
            tvDecisionResult.setTextColor(0xFF2ECC71);
            Toast.makeText(this, "Decision confirmed: IN", Toast.LENGTH_SHORT).show();
            // Push decision to Firebase so other devices see it live
            pushDecisionToFirebase("IN");
        });

        btnConfirmOut.setOnClickListener(v -> {
            tvDecisionResult.setText("❌ CONFIRMED: OUT");
            tvDecisionResult.setTextColor(0xFFFF4444);
            Toast.makeText(this, "Decision confirmed: OUT", Toast.LENGTH_SHORT).show();
            // Push decision to Firebase so other devices see it live
            pushDecisionToFirebase("OUT");
        });

        btnOpenWeb.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.worldpadeltour.com"))));

        // ════════════════════════════════════════════
        // F: Bottom navigation (NEW)
        // ════════════════════════════════════════════
        setupBottomNavigation();
    }

    // ─────────────────────────────────────────────────────────────────
    // startFirebaseStream()
    // Attaches a real-time listener to the Firebase Realtime Database.
    // The UI updates every time new data is written to the path.
    // ─────────────────────────────────────────────────────────────────
    private void startFirebaseStream() {

        // Update indicator to "Connecting..."
        tvFirebaseIndicator.setText("● Connecting...");
        tvFirebaseIndicator.setTextColor(0xFFFFD700);

        // Get reference to the replay node in Firebase
        // Make sure "padel_var/current_match/replay" exists in your DB
        replayRef = FirebaseDatabase.getInstance()
                .getReference("padel_var/current_match/replay");

        // Create a ValueEventListener — fires instantly and on every change
        replayListener = new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                // ── Update connection indicator to "LIVE" ──
                tvFirebaseIndicator.setText("● LIVE");
                tvFirebaseIndicator.setTextColor(0xFF2ECC71); // green

                // ── Frame counter ──
                Long frameCurrent = snapshot.child("frame_current").getValue(Long.class);
                Long frameTotal   = snapshot.child("frame_total").getValue(Long.class);
                if (frameCurrent != null && frameTotal != null) {
                    tvFrameCounter.setText("Frame " + frameCurrent + "/" + frameTotal);
                }

                // ── FPS ──
                Long fps = snapshot.child("fps").getValue(Long.class);
                if (fps != null) tvStreamFps.setText(fps + " fps");

                // ── Latency ──
                Long latency = snapshot.child("latency_ms").getValue(Long.class);
                if (latency != null) tvStreamLatency.setText(latency + " ms");

                // ── Trajectory tracking points ──
                Long tPoints = snapshot.child("trajectory_pts").getValue(Long.class);
                if (tPoints != null) tvTrajectoryPoints.setText(String.valueOf(tPoints));

                // ── AI confidence ──
                Long confidence = snapshot.child("ai_confidence").getValue(Long.class);
                if (confidence != null) {
                    tvFirebaseConfidence.setText(confidence + "%");
                    // Color-code: green > 80%, yellow 50-80%, red < 50%
                    if (confidence >= 80)      tvFirebaseConfidence.setTextColor(0xFF2ECC71);
                    else if (confidence >= 50) tvFirebaseConfidence.setTextColor(0xFFFFD700);
                    else                       tvFirebaseConfidence.setTextColor(0xFFFF4444);
                }

                // ── AI status text ──
                String aiStatus = snapshot.child("ai_status").getValue(String.class);
                if (aiStatus != null) tvFirebaseAiStatus.setText(aiStatus);

                // ── Ball speed (may come from Firebase stream) ──
                Long fbBallSpeed = snapshot.child("ball_speed").getValue(Long.class);
                if (fbBallSpeed != null && fbBallSpeed != 0) {
                    tvBallSpeed.setText(fbBallSpeed + " km/h");
                }

                // ── Last shot ──
                String fbLastShot = snapshot.child("last_shot").getValue(String.class);
                if (fbLastShot != null) {
                    tvLastShot.setText(fbLastShot);
                    tvLastShot.setTextColor(
                            fbLastShot.equals("IN") ? 0xFF2ECC71 : 0xFFFF4444);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Connection failed or security rules denied access
                tvFirebaseIndicator.setText("● Offline");
                tvFirebaseIndicator.setTextColor(0xFFFF4444);
                tvFirebaseAiStatus.setText("STREAM ERROR — CHECK CONNECTION");
                Toast.makeText(ReplayActivity.this,
                        "Firebase error: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };

        // Attach the listener — starts streaming immediately
        replayRef.addValueEventListener(replayListener);
    }

    // ─────────────────────────────────────────────────────────────────
    // pushDecisionToFirebase()
    // Writes the referee's VAR decision back to Firebase so all
    // connected devices (referee tablets, scoreboards) update live.
    // ─────────────────────────────────────────────────────────────────
    private void pushDecisionToFirebase(String decision) {
        DatabaseReference decisionRef = FirebaseDatabase.getInstance()
                .getReference("padel_var/current_match/var_decision");

        decisionRef.setValue(decision)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this,
                                "Decision pushed to Firebase ✅",
                                Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Firebase write failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ─────────────────────────────────────────────
    // Bottom navigation
    // ─────────────────────────────────────────────
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_replay);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_replay) { return true; }
            if (id == R.id.nav_home)   { finish(); return true; }
            if (id == R.id.nav_live) {
                startActivity(new Intent(this, LiveAnalysisActivity.class));
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

    // ─────────────────────────────────────────────
    // Lifecycle — detach Firebase listener to avoid memory leaks
    // ─────────────────────────────────────────────
    @Override
    protected void onStop() {
        super.onStop();
        if (replayRef != null && replayListener != null) {
            replayRef.removeEventListener(replayListener);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-attach if returning to this screen
        if (replayRef != null && replayListener != null) {
            replayRef.addValueEventListener(replayListener);
        }
    }
}