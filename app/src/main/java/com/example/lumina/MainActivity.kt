package com.example.lumina

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.safetydriveai.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi awal program saat aplikasi dibuka
        initAplikasi()
    }

    private fun initAplikasi() {
        // =================================================================
        // TODO: jessie (maps-config) & elen (core-logic)
        // Panggil fungsi inisialisasi awal di sini (misal: startCamera(), dll)
        // =================================================================
    }


    // =================================================================
    // 🧑‍💻 AREA KERJA: elen (CORE CAMERA LOGIC)
    // =================================================================
    fun startCamera() {
        // TODO: Aku - Tulis kodingan CameraX di sini untuk menyalakan kamera depan
    }

    fun aturLifecycleKamera() {
        // TODO: Aku - Atur binding daur hidup kamera di sini (onResume / onPause)
    }

    // =================================================================
    // 🔬 AREA KERJA: INDY (AI EXPERT & ALARM)
    // Nama Branch: ear-alarm
    // =================================================================
    fun setupFaceLandmarker() {
        // TODO: Indy - Tulis inisialisasi FaceLandmarker & RunningMode.LIVE_STREAM di sini
    }

    fun processFaceLandmarks(hasilLandmark: Any?) {
        // TODO: Indy - Tulis Rumus EAR, Filter Deteksi Gigi/Senyum, dan Deteksi Menoleh di sini
    }

    fun kelolaAlarmDanGetar(apakahMengantuk: Boolean) {
        // TODO: Indy - Tulis logika pemicu MediaPlayer (.mp3) dan Vibrator HP di sini
    }


    // =================================================================
    // 🗺️ AREA KERJA: JESSIE (GIS MAPS)
    // Nama Branch: maps-config
    // =================================================================
    fun inisialisasiGoogleMaps() {
        // TODO: Jessie - Tulis pengaturan OnMapReadyCallback untuk menampilkan peta di sini
    }

    fun ambilLokasiTerkini() {
        // TODO: Jessie - Tulis kodingan FusedLocationProviderClient untuk GPS di sini
    }

    fun ruteRestAreaTerdekat() {
        // TODO: Jessie - Tulis Intent untuk melempar pencarian Rest Area ke aplikasi Google Maps asli
    }
}