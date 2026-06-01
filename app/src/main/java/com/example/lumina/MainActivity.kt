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

    // =================================================================
    // 🔬 AREA KERJA: INDY (AI EXPERT & ALARM) - Branch: ear-alarm
    // =================================================================

    // Memuat basis pengetahuan CNN MediaPipe dari berkas .task di assets
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

    // Penerapan Metode Representasi Pengetahuan: Production Rules (IF-THEN)
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

        // 1. Ekstraksi koordinat kelopak mata untuk kalkulasi matematika EAR
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

        // Normalisasi proporsional nilai mata terhadap ukuran kedekatan muka ke kamera
        val earNormal = if (jarakWajahY > 0) avgEAR / jarakWajahY else avgEAR

        // 2. Pendeteksian bukaan celah bibir bagian dalam (Pembeda Senyum/Menguap vs Merem Ngantuk)
        val bibirAtasDalamY = landmarks[13].y()
        val bibirBawahDalamY = landmarks[14].y()
        val jarakVertikalBibirDalam = Math.abs(bibirBawahDalamY - bibirAtasDalamY)
        val bukaanBibirNormal = if (jarakWajahY > 0) jarakVertikalBibirDalam / jarakWajahY else jarakVertikalBibirDalam

        // ATURAN 1: Fase Kalibrasi Nilai Batas Rileks Pengguna (60 frame awal)
        if (!sudahKalibrasi) {
            counterKalibrasi++
            if (earNormal > maxEARUser) maxEARUser = earNormal

            runOnUiThread {
                binding.tvStatus.text = "Lumina AI: Kalibrasi Mata... Mohon Rileks ($counterKalibrasi/60)"
            }
            if (counterKalibrasi >= 60) {
                sudahKalibrasi = true
                batasThresholdKantuk = maxEARUser * 0.48f // Threshold ditarik 48% dari bukaan mata maks
                runOnUiThread {
                    Toast.makeText(this, "Lumina AI Siap! Sistem Lebih Akurat.", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        // ATURAN 2: Ekstraksi Fakta Kondisi (Anticedent)
        val gigiTerlihat = bukaanBibirNormal > 0.025f
// Logika Deteksi Geometri Wajah Menoleh ke Kiri/Kanan
        val hidungX = landmarks[4].x()
        val pipiKiriX = landmarks[234].x()
        val pipiKananX = landmarks[454].x()
        val jarakKiri = Math.abs(hidungX - pipiKiriX)
        val jarakKanan = Math.abs(pipiKananX - hidungX)
        val sedangMenolehSamping = (jarakKiri > jarakKanan * 2.2f)  (jarakKanan > jarakKiri * 2.2f)

        val mataTerpejam = earNormal < batasThresholdKantuk
        val kepalaMenunduk = jarakWajahY < 0.22f

        // ATURAN 3: Pengambilan Keputusan Akhir (Consequent) Melalui Forward Chaining
        runOnUiThread {
            // LOGIKA UTAMA: Pengemudi valid mengantuk JIKA mata merem, TIDAK sedang senyum, dan TIDAK sedang menoleh
            if (mataTerpejam && !gigiTerlihat && !sedangMenolehSamping) {
                waktuAmanMulai = 0L

                if (waktuMulaiMerem == 0L) {
                    waktuMulaiMerem = System.currentTimeMillis()
                }
                val durasiMerem = System.currentTimeMillis() - waktuMulaiMerem

                // Pemicu aksi ketika durasi terpejam kontinu terlewati (2 Detik atau Kepala Nunduk Drastis)
                if ((durasiMerem >= 2000  kepalaMenunduk) && !sedangAlarm) {
                    binding.tvStatus.text = "Lumina AI: STATUS MENGANTUK!! ⚠️"
                    binding.tvStatus.setTextColor(android.graphics.Color.RED)
                    pemicuAlarmBahaya()
                }
            } else {
                // LOGIKA SEBALIKNYA: Pengemudi dalam Kondisi Aman / Melek Normal
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

                    // Manajemen otomatisasi hilangnya tombol bantuan darurat pasca 5 detik kondisi aman konstan
                    if (binding.btnFindRest.visibility == View.VISIBLE) {
                        if (waktuAmanMulai == 0L) {
                            waktuAmanMulai = System.currentTimeMillis()
                        }
                        if (System.currentTimeMillis() - waktuAmanMulai >= 5000) {
                            sembunyikanTombolAksi()
                            waktuAmanMulai = 0L
                        }
                    }
                }
            }
        }
    }

    // Mengeksekusi keluaran media sirine audio MP3 dan pola getaran hardware HP
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

    // Merilis kembali resource player audio dan menghentikan intervensi hardware vibrator
    private fun matikanAlarm() {
        sedangAlarm = false
        try {
            vibrator?.cancel()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }
    // Fungsi visual helper untuk manajemen penampakan elemen tombol bantuan UI
    private fun tampilkanTombolAksi() {
        binding.btnStopAlarm.visibility = View.VISIBLE
        binding.btnFindRest.visibility = View.VISIBLE
    }

    private fun sembunyikanTombolAksi() {
        binding.btnStopAlarm.visibility = View.GONE
        binding.btnFindRest.visibility = View.GONE
    }

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