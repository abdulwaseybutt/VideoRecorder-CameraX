package vvv.testing.mediarecorder

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import com.google.common.util.concurrent.ListenableFuture

class VideoRecorderActivity : AppCompatActivity() {

    private lateinit var service: ExecutorService
    private var recording: Recording? = null
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var capture: ImageButton
    private lateinit var toggleFlash: ImageButton
    private lateinit var flipCamera: ImageButton
    private lateinit var previewView: PreviewView
    private var cameraFacing: Int = CameraSelector.LENS_FACING_BACK

    private var isRecording = false
    private var startTimeMillis: Long = 0
    private lateinit var timerTextView: TextView

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean? ->
        if (ActivityCompat.checkSelfPermission(
                this@VideoRecorderActivity, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera(cameraFacing)
        }
    }

    private fun startRecordingTimer() {
        isRecording = true
        startTimeMillis = System.currentTimeMillis()

        timerTextView.visibility = View.VISIBLE
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis
                    val formattedTime = formatTime(elapsedTimeMillis)
                    timerTextView.text = formattedTime

                    if (elapsedTimeMillis >= 11000) {
                        captureVideo() // Stop recording after 10 seconds
                        return
                    }

                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun stopRecordingTimer() {
        isRecording = false
        timerTextView.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_recorder)
        timerTextView = findViewById(R.id.timerTextView)

        previewView = findViewById(R.id.viewFinder)
        capture = findViewById(R.id.capture)
        toggleFlash = findViewById(R.id.toggleFlash)
        flipCamera = findViewById(R.id.flipCamera)
        capture.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this@VideoRecorderActivity, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.CAMERA)
            } else if (ActivityCompat.checkSelfPermission(
                    this@VideoRecorderActivity, Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ActivityCompat.checkSelfPermission(
                    this@VideoRecorderActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                captureVideo()
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this@VideoRecorderActivity, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityResultLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startCamera(cameraFacing)
        }

        flipCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera(cameraFacing)
        }
        service = Executors.newSingleThreadExecutor()
    }

    private fun captureVideo() {
        capture.setImageResource(R.drawable.round_stop_circle_24)

        val recording1: Recording? = recording
        if (recording1 != null) {
            recording1.stop()
            recording = null
            stopRecordingTimer()
            return
        } else {
            startRecordingTimer()
        }

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(
            System.currentTimeMillis()
        )
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/lstLearn")

        val options: MediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        recording = videoCapture.output.prepareRecording(this@VideoRecorderActivity, options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this@VideoRecorderActivity)) { videoRecordEvent ->
                if (videoRecordEvent is VideoRecordEvent.Start) {
                    capture.isEnabled = true
                } else if (videoRecordEvent is VideoRecordEvent.Finalize) {
                    if (!videoRecordEvent.hasError()) {

                        /*val msg =
                                "Video capture succeeded: " + videoRecordEvent.outputResults.outputUri
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()*/

                        val recordedVideoUri = videoRecordEvent.outputResults.outputUri
                        val resultIntent = Intent().apply {
                            putExtra("recorded_video_uri", recordedVideoUri.toString())
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()

                    } else {
                        recording?.close()
                        recording = null
                        val msg = "Error: " + videoRecordEvent.error
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    capture.setImageResource(R.drawable.round_fiber_manual_record_24)
                }
            }
    }

    private fun startCamera(cameraFacing: Int) {
        val processCameraProvider: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this@VideoRecorderActivity)
        processCameraProvider.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = processCameraProvider.get()
                val preview: Preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val recorder: Recorder =
                    Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build()
                videoCapture = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                val cameraSelector: CameraSelector =
                    CameraSelector.Builder().requireLensFacing(cameraFacing).build()
                val camera: Camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                toggleFlash.setOnClickListener {
                    toggleFlash(
                        camera
                    )
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this@VideoRecorderActivity))
    }

    private fun toggleFlash(camera: Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value === 0) {
                camera.cameraControl.enableTorch(true)
                toggleFlash.setImageResource(R.drawable.round_flash_off_24)
            } else {
                camera.cameraControl.enableTorch(false)
                toggleFlash.setImageResource(R.drawable.round_flash_on_24)
            }
        } else {
            runOnUiThread {
                Toast.makeText(
                    this@VideoRecorderActivity, "Flash is not available", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service.shutdown()
    }

}