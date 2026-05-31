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

private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
}

override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<String>, grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_CODE_PERMISSIONS) {
        if (allPermissionsGranted()) {
            startCamera()
            ambilLokasiTerkini()
        } else {
            Toast.makeText(this, "Izin ditolak oleh pengguna.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

override fun onStart() { super.onStart(); try { binding.mapView.onStart() } catch (e: Exception) {} }
override fun onResume() { super.onResume(); try { binding.mapView.onResume() } catch (e: Exception) {} }
override fun onPause() { super.onPause(); try { binding.mapView.onPause() } catch (e: Exception) {} }
override fun onStop() { super.onStop(); try { binding.mapView.onStop() } catch (e: Exception) {} }
override fun onDestroy() {
    super.onDestroy()
    cameraExecutor?.shutdown()
    matikanAlarm()
    try { binding.mapView.onDestroy() } catch (e: Exception) {}
}
override fun onLowMemory() { super.onLowMemory(); try { binding.mapView.onLowMemory() } catch (e: Exception) {} }

companion object {
    private const val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
}
}