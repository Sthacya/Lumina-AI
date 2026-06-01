package com.example.safetydriveai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.safetydriveai.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    // Fungsi buat inisialisasi dan buat nyalain kamera depan
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // 1. Ambil instance dari ProcessCameraProvider secara asynchronous
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 2. Buat objek Preview dan hubungkan surface provider-nya ke XML viewFinder
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // 3. Konfigurasi ImageAnalysis dengan resolusi target & strategi backpressure
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // 4. Atur Analyzer untuk menangkap frame kamera dan mengirimkannya ke AI Indy
            imageAnalysis.setAnalyzer(cameraExecutor!!) { imageProxy ->
                // Mengonversi frame kamera (ImageProxy) menjadi format yang dibutuhkan MediaPipe
                if (faceLandmarker != null) {
                    val frameTime = android.os.SystemClock.uptimeMillis()
                    val bitmap = imageProxy.toBitmap()
                    val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()

                    // Mengirimkan gambar bitmap ke fungsi pendeteksian asynchronously
                    faceLandmarker?.detectAsync(mpImage, frameTime)
                }

                // CRITICAL: Selalu tutup imageProxy agar frame berikutnya bisa diambil
                imageProxy.close()
            }

            // 5. Tentukan selektor kamera menggunakan kamera depan (Realme C65)
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // 6. Lakukan unbindAll() sebelum melakukan binding baru agar tidak bentrok
                cameraProvider.unbindAll()

                // 7. Ikat daur hidup kamera ke lifecycle MainActivity ini
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (exc: Exception) {
                android.util.Log.e("LuminaAI", "Binding kamera ke lifecycle gagal: ${exc.message}")
                runOnUiThread {
                    Toast.makeText(this, "Gagal membuka kamera depan.", Toast.LENGTH_SHORT).show()
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Fungsi tambahan untuk ngatur daur hidup kamera jika dipanggil secara manual
    fun aturLifecycleKamera() {
        // Kamera bakal otomatis berhenti (unbind) waktu onPause() / onDestroy()
        // dan menyala kembali pas onResume() berkat library AndroidX Camera2.
        android.util.Log.d("LuminaAI", "Daur hidup kamera otomatis dikelola oleh CameraX Lifecycle.")
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
    // Akses sensor internal GPS FusedLocation untuk ekstraksi koordinat bumi pengemudi
    private fun ambilLokasiTerkini() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    lokasiTerakhir = LatLng(lat, lng)

                    // Geser fokus kamera peta Google Maps ke koordinat aktual user
                    gMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(lokasiTerakhir!!, 16f))

                    // Konversi data koordinat angka menjadi baris string teks alamat fisik asli Indonesia
                    try {
                        val geocoder = Geocoder(this, Locale("id", "ID"))
                        val alamatList = geocoder.getFromLocation(lat, lng, 1)
                        if (!alamatList.isNullOrEmpty()) {
                            binding.tvLocation.text = "Lokasi: ${alamatList[0].getAddressLine(0)}"
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    // Melakukan setup layer maps begitu callback SDK Google Maps siap dijalankan
    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            gMap?.isMyLocationEnabled = true
            gMap?.uiSettings?.isMyLocationButtonEnabled = true
        }
    }
}