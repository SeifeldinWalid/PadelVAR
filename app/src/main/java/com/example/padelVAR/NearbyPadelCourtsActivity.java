package com.example.padelVAR;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NearbyPadelCourtsActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST = 3001;

    // Radius filter buttons
    private Button chip1km, chip5km, chip10km, chip25km;
    private Button activeChip;

    private TextView tvCourtCount;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocation;
    private PlacesClient placesClient;
    private Location lastKnownLocation;

    // Track all markers so we can filter them
    private final List<Marker> courtMarkers = new ArrayList<>();

    // ─────────────────────────────────────────────
    // Demo court data for fallback when Places API
    // returns no results or location is unavailable
    // ─────────────────────────────────────────────
    private static final String[] DEMO_NAMES = {
            "Padel Club Cairo",
            "Elite Padel Courts",
            "Victory Padel Arena",
            "Pro Padel Alexandria",
            "Smash Padel Club"
    };
    private static final double[][] DEMO_OFFSETS = {
            { 0.012,  0.018},
            {-0.016,  0.010},
            { 0.022, -0.009},
            {-0.009, -0.021},
            { 0.005,  0.030}
    };
    private static final String[] DEMO_INFO = {
            "⭐ 4.8 · 3 courts · Indoor",
            "⭐ 4.5 · 2 courts · Outdoor",
            "⭐ 4.7 · 4 courts · Indoor",
            "⭐ 4.3 · 2 courts · Outdoor",
            "⭐ 4.6 · 3 courts · Mixed"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_padel_courts);

        // ────── Back button ──────
        findViewById(R.id.btnBackCourts).setOnClickListener(v -> finish());

        // ────── Court count label ──────
        tvCourtCount = findViewById(R.id.tvCourtCount);

        // ────── Radius chips ──────
        chip1km  = findViewById(R.id.chip1km);
        chip5km  = findViewById(R.id.chip5km);
        chip10km = findViewById(R.id.chip10km);
        chip25km = findViewById(R.id.chip25km);
        activeChip = chip1km; // default

        chip1km.setOnClickListener(v  -> setActiveChip(chip1km,  1));
        chip5km.setOnClickListener(v  -> setActiveChip(chip5km,  5));
        chip10km.setOnClickListener(v -> setActiveChip(chip10km, 10));
        chip25km.setOnClickListener(v -> setActiveChip(chip25km, 25));

        // ────── Location client ──────
        fusedLocation = LocationServices.getFusedLocationProviderClient(this);

        // ────── Places API ──────
        // Replace "YOUR_PLACES_API_KEY" with your key in strings.xml
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }
        placesClient = Places.createClient(this);

        // ────── Map ──────
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // ────── Bottom navigation ──────
        setupBottomNavigation();
    }

    // ─────────────────────────────────────────────
    // Map is ready — enable location and load courts
    // ─────────────────────────────────────────────
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;


        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);

        // Show info window when a marker is tapped
        googleMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return false;
        });

        checkLocationAndLoad();
    }

    // ─────────────────────────────────────────────
    // Permission check then load
    // ─────────────────────────────────────────────
    private void checkLocationAndLoad() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        } else {
            loadMapWithLocation();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void loadMapWithLocation() {
        googleMap.setMyLocationEnabled(true);
        tvCourtCount.setText("Locating you...");

        fusedLocation.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                lastKnownLocation = location;
                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(userLatLng, 13f));
                searchNearbyPadelCourts(location);
            } else {
                // Fallback: Cairo city center
                LatLng cairo = new LatLng(30.0444, 31.2357);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(cairo, 12f));
                addDemoMarkers(cairo);
                Toast.makeText(this,
                        "Could not get GPS location — showing demo courts",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ─────────────────────────────────────────────
    // Places API — find real padel courts nearby
    // ─────────────────────────────────────────────
    @SuppressWarnings("MissingPermission")
    private void searchNearbyPadelCourts(Location location) {
        tvCourtCount.setText("Searching...");

        List<Place.Field> fields = Arrays.asList(
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.TYPES);

        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(fields);

        placesClient.findCurrentPlace(request).addOnSuccessListener(response -> {
            clearMarkers();
            int count = 0;
            for (PlaceLikelihood likelihood : response.getPlaceLikelihoods()) {
                Place p = likelihood.getPlace();
                if (p.getName() == null || p.getLatLng() == null) continue;

                // Filter for padel courts specifically
                String nameLower = p.getName().toLowerCase();
                if (nameLower.contains("padel") ||
                        nameLower.contains("paddle") ||
                        nameLower.contains("sports club") ||
                        nameLower.contains("racket")) {
                    String snippet = (p.getAddress() != null ? p.getAddress() : "") +
                            (p.getRating() != null ? " ⭐ " + p.getRating() : "");
                    addCourt(p.getName(), p.getLatLng(), snippet);
                    count++;
                }
            }
            if (count == 0) {
                // No padel-specific results → show demo markers
                addDemoMarkers(new LatLng(
                        location.getLatitude(), location.getLongitude()));
            } else {
                tvCourtCount.setText(count + " courts found");
            }
        }).addOnFailureListener(e -> {
            // Places API call failed → fallback to demo data
            addDemoMarkers(new LatLng(location.getLatitude(), location.getLongitude()));
        });
    }

    // ─────────────────────────────────────────────
    // Add a real court marker (green pin)
    // ─────────────────────────────────────────────
    private void addCourt(String name, LatLng latLng, String snippet) {
        Marker m = googleMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(name)
                .snippet(snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN)));
        if (m != null) courtMarkers.add(m);
    }

    // ─────────────────────────────────────────────
    // Add demo markers when real data unavailable
    // ─────────────────────────────────────────────
    private void addDemoMarkers(LatLng center) {
        clearMarkers();
        for (int i = 0; i < DEMO_NAMES.length; i++) {
            LatLng pos = new LatLng(
                    center.latitude  + DEMO_OFFSETS[i][0],
                    center.longitude + DEMO_OFFSETS[i][1]);
            addCourt(DEMO_NAMES[i], pos, DEMO_INFO[i]);
        }
        tvCourtCount.setText(DEMO_NAMES.length + " courts nearby (demo)");
    }

    // ─────────────────────────────────────────────
    // Remove all map markers
    // ─────────────────────────────────────────────
    private void clearMarkers() {
        for (Marker m : courtMarkers) m.remove();
        courtMarkers.clear();
    }

    // ─────────────────────────────────────────────
    // Radius chip selection
    // ─────────────────────────────────────────────
    private void setActiveChip(Button chip, int radiusKm) {
        // Reset previous active chip
        activeChip.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF112240));
        // Activate new chip
        chip.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF64FFDA));
        activeChip = chip;

        Toast.makeText(this, "Showing courts within " + radiusKm + " km",
                Toast.LENGTH_SHORT).show();

        // Re-run search with new radius (for full implementation, pass
        // radius as a parameter to the Places Nearby Search API call)
        if (lastKnownLocation != null) {
            searchNearbyPadelCourts(lastKnownLocation);
        }
    }

    // ─────────────────────────────────────────────
    // Permission result
    // ─────────────────────────────────────────────
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMapWithLocation();
            } else {
                Toast.makeText(this,
                        "Location permission required to find nearby courts",
                        Toast.LENGTH_LONG).show();
                tvCourtCount.setText("Permission denied");
            }
        }
    }

    // ─────────────────────────────────────────────
    // Bottom navigation
    // ─────────────────────────────────────────────
    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setSelectedItemId(R.id.nav_courts);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_courts) { return true; }
            if (id == R.id.nav_home)   { finish(); return true; }
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
            return false;
        });
    }
}