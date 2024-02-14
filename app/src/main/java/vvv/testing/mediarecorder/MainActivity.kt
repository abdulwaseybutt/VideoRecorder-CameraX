package vvv.testing.mediarecorder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val REQUEST_VIDEO_CAPTURE = 1
    private lateinit var videoPreview: VideoView
    private lateinit var mediaController: MediaController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.openVideo).setOnClickListener {

            val intent = Intent(this, VideoRecorderActivity::class.java)
            startActivityForResult(intent, REQUEST_VIDEO_CAPTURE)
        }
        videoPreview = findViewById(R.id.videoPreview)
        mediaController = MediaController(this)
        mediaController.setAnchorView(videoPreview)
        videoPreview.setMediaController(mediaController)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == Activity.RESULT_OK) {
            val recordedVideoUri = data?.getStringExtra("recorded_video_uri")
            recordedVideoUri?.let {
                // Display the recorded video in VideoView
                videoPreview.setVideoURI(Uri.parse(recordedVideoUri))
                videoPreview.start()
            }
        }
    }

}