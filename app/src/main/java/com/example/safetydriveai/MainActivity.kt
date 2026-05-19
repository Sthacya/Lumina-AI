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

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private var maxSmileNormal: Float = 0.0f
    private var batasThresholdSenyum: Float = 0.0f
    private lateinit var binding: ActivityMainBinding
    private var cameraExecutor: ExecutorService? = null
    private var faceLandmarker: FaceLandmarker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var gMap: GoogleMap? = null
    private var lokasiTerakhir: LatLng? = null

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var waktuMulaiMerem: Long = 0
    private var sedangAlarm: Boolean = false

    // KALIBRASI ADAPTIF & LOGIKA LUMINA AI
    private var maxEARUser: Float = 0.0f
    private var counterKalibrasi = 0
    private var sudahKalibrasi = false
    private var batasThresholdKantuk: Float = 0.015f
    private var lebarMulutNormal: Float = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            binding.mapView.onCreate(savedInstanceState)
            binding.mapView.getMapAsync(this)

            setupFaceLandmarker()
            setupButtonListeners()

            if (allPermissionsGranted()) {
                startCamera()
                ambilLokasiTerkini()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }

            cameraExecutor = Executors.newSingleThreadExecutor()
        } catch (e: Exception) {
            Log.e("LuminaAI", "Error pada onCreate: ${e.message}")
        }
    }

    private var waktuAmanMulai: Long = 0L

    private fun setupButtonListeners() {
        binding.btnStopAlarm.setOnClickListener {
            matikanAlarm() // Mematikan suara & getar sirine
            waktuMulaiMerem = 0L
            binding.tvStatus.text = "Lumina AI: Alarm Dimatikan Manual"
            binding.tvStatus.setTextColor(android.graphics.Color.GREEN)

            // KASUS 7 PERBAIKAN: Tombol TIDAK LANGSUNG hilang di sini agar UI tetap rapi
            // Kita serahkan tugas menghilangkan tombol secara barengan ke timer 5 detik aman
            waktuAmanMulai = System.currentTimeMillis()
        }

        binding.btnFindRest.setOnClickListener {
            lokasiTerakhir?.let { gps ->
                val gmmIntentUri = Uri.parse("geo:${gps.latitude},${gps.longitude}?q=Rest+Area+Indomaret+Alfamart+Alfamidi")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                }
            }
        }
    }

    private fun setupFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath("face_landmarker.task").build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> processFaceLandmarks(result) }
                .build()
            faceLandmarker = FaceLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e("LuminaAI", "Gagal load model AI: ${e.message}")
        }
    }

    private fun processFaceLandmarks(result: FaceLandmarkerResult) {
        if (result.faceLandmarks().isEmpty()) {
            waktuMulaiMerem = 0L
            runOnUiThread {
                binding.tvStatus.text = "Lumina AI: Wajah Terhalang / Tidak Terdeteksi ⚠️"
                binding.tvStatus.setTextColor(android.graphics.Color.YELLOW)
            }
            return
        }

        val landmarks = result.faceLandmarks()[0]

        // 1. HITUNG MATA (EAR) & TINGGI WAJAH
        val leftEyeUpper = landmarks[159].y()
        val leftEyeLower = landmarks[145].y()
        val leftEAR = leftEyeLower - leftEyeUpper

        val rightEyeUpper = landmarks[386].y()
        val rightEyeLower = landmarks[374].y()
        val rightEAR = rightEyeLower - rightEyeUpper
        val avgEAR = (leftEAR + rightEAR) / 2.0f

        val dahiY = landmarks[10].y()
        val daguY = landmarks[152].y()
        val jarakWajahY = daguY - dahiY

        // Normalisasi EAR biar anti-bias jarak jauh/dekat
        val earNormal = if (jarakWajahY > 0) avgEAR / jarakWajahY else avgEAR

        // 2. KNOWLEDGE REPRESENTATION BARU: DETEKSI GIGI / MULUT TERBUKA PAS TERSENYUM
        // Titik 13 = Bibir atas bagian dalam tengah, Titik 14 = Bibir bawah bagian dalam tengah
        val bibirAtasDalamY = landmarks[13].y()
        val bibirBawahDalamY = landmarks[14].y()
        val jarakVertikalBibirDalam = Math.abs(bibirBawahDalamY - bibirAtasDalamY)

        // Normalisasi jarak bibir terhadap tinggi wajah agar kebal jarak HP jauh/dekat
        val bukaanBibirNormal = if (jarakWajahY > 0) jarakVertikalBibirDalam / jarakWajahY else jarakVertikalBibirDalam

        // TAHAP 1: KALIBRASI ADAPTIF MATA (Muka Rileks, Mulut Mingkem Normal)
        if (!sudahKalibrasi) {
            counterKalibrasi++
            if (earNormal > maxEARUser) maxEARUser = earNormal

            runOnUiThread {
                binding.tvStatus.text = "Lumina AI: Kalibrasi Mata... Mohon Rileks ($counterKalibrasi/60)"
            }
            if (counterKalibrasi >= 60) {
                sudahKalibrasi = true
                batasThresholdKantuk = maxEARUser * 0.48f // Batas mata merem (48% dari melek maksimal)
                runOnUiThread {
                    Toast.makeText(this, "Lumina AI Siap! Sistem Lebih Akurat.", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        // --- EKSTRAKSI KNOWLEDGE BERDASARKAN ATURAN BARU ---
        // Jika mulut terbuka sedikit saja (jarak bibir dalam > 0.025 setelah dinormalisasi), gigi pasti terlihat karena senyum/tertawa
        val gigiTerlihat = bukaanBibirNormal > 0.025f

        // Deteksi Menoleh Samping (Tetap dipertahankan agar aman)
        val hidungX = landmarks[4].x()
        val pipiKiriX = landmarks[234].x()
        val pipiKananX = landmarks[454].x()
        val jarakKiri = Math.abs(hidungX - pipiKiriX)
        val jarakKanan = Math.abs(pipiKananX - hidungX)
        val sedangMenolehSamping = (jarakKiri > jarakKanan * 2.2f) || (jarakKanan > jarakKiri * 2.2f)

        val mataTerpejam = earNormal < batasThresholdKantuk
        val kepalaMenunduk = jarakWajahY < 0.22f // Deteksi kepala nunduk drastis

        runOnUiThread {
            // IMPLEMENTASI RULE-BASED BARU YANG KAMU REQUEST:
            // Seseorang fix mengantuk HANYA JIKA mata terpejam DAN gigi TIDAK terlihat (TIDAK sedang senyum)
            if (mataTerpejam && !gigiTerlihat && !sedangMenolehSamping) {
                waktuAmanMulai = 0L

                if (waktuMulaiMerem == 0L) {
                    waktuMulaiMerem = System.currentTimeMillis()
                }
                val durasiMerem = System.currentTimeMillis() - waktuMulaiMerem

                if ((durasiMerem >= 2000 || kepalaMenunduk) && !sedangAlarm) {
                    binding.tvStatus.text = "Lumina AI: STATUS MENGANTUK!! ⚠"
                    binding.tvStatus.setTextColor(android.graphics.Color.RED)
                    pemicuAlarmBahaya()
                }
            } else {
                // KONDISI USER MELEK / AMAN
                if (!sedangAlarm) {
                    waktuMulaiMerem = 0L

                    if (sedangMenolehSamping) {
                        binding.tvStatus.text = "Lumina AI: Status Aman 👀 (Menoleh)"
                        binding.tvStatus.setTextColor(android.graphics.Color.GREEN)
                    } else if (mataTerpejam && gigiTerlihat) {
                        binding.tvStatus.text = "Lumina AI: Status Aman 😊 (Tertawa/Gigi Terlihat)"
                        binding.tvStatus.setTextColor(android.graphics.Color.GREEN)
                    } else {
                        binding.tvStatus.text = "Lumina AI: Status Aman 👍"
                        binding.tvStatus.setTextColor(android.graphics.Color.GREEN)
                    }

                    // KASUS 7 PERBAIKAN: Jika tombol aksi sedang muncul di layar
                    if (binding.btnFindRest.visibility == View.VISIBLE) {
                        if (waktuAmanMulai == 0L) {
                            waktuAmanMulai = System.currentTimeMillis()
                        }
                        // Cek apakah user sudah melek/aman stabil selama 5 detik berturut-turut
                        if (System.currentTimeMillis() - waktuAmanMulai >= 5000) {
                            sembunyikanTombolAksi() // Menyembunyikan KEDUA tombol sekaligus secara rapi
                            waktuAmanMulai = 0L
                        }
                    }
                }
            }
        }
    }

    private fun pemicuAlarmBahaya() {
        sedangAlarm = true
        tampilkanTombolAksi()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
        } catch (e: Exception) {}

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            }
        } catch (e: Exception) {}
    }

    private fun matikanAlarm() {
        sedangAlarm = false
        try {
            vibrator?.cancel()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }

    private fun tampilkanTombolAksi() {
        binding.btnStopAlarm.visibility = View.VISIBLE
        binding.btnFindRest.visibility = View.VISIBLE
    }

    private fun sembunyikanTombolAksi() {
        binding.btnStopAlarm.visibility = View.GONE
        binding.btnFindRest.visibility = View.GONE
    }

    private fun ambilLokasiTerkini() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    lokasiTerakhir = LatLng(lat, lng)

                    gMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(lokasiTerakhir!!, 16f))

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

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            gMap?.isMyLocationEnabled = true
            gMap?.uiSettings?.isMyLocationButtonEnabled = true
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { it.setAnalyzer(cameraExecutor!!) { imageProxy -> detectEyes(imageProxy) } }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectEyes(imageProxy: ImageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }
        val frameTime = SystemClock.uptimeMillis()
        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        faceLandmarker?.detectAsync(mpImage, frameTime)
        imageProxy.close()
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