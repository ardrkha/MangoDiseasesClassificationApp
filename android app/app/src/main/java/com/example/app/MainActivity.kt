package com.example.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import com.example.app.ml.MangoFruitDDS
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var camera: LinearLayout
    private lateinit var gallery: LinearLayout
    private lateinit var imageView: ImageView
    private lateinit var result: TextView
    private val imageSize = 224

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        camera = findViewById(R.id.button)
        gallery = findViewById(R.id.button2)
        result = findViewById(R.id.result)
        imageView = findViewById(R.id.image_view)

        camera.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent, 3)
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        }

        gallery.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, 1)
        }
    }

    private fun classifyImage(image: Bitmap) {
        try {
            val model = MangoFruitDDS.newInstance(applicationContext)

            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(imageSize * imageSize)
            image.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)
            var pixel = 0

            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val value = intValues[pixel++]
                    byteBuffer.putFloat(((value shr 16) and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat(((value shr 8) and 0xFF) * (1f / 255f))
                    byteBuffer.putFloat((value and 0xFF) * (1f / 255f))
                }
            }

            inputFeature0.loadBuffer(byteBuffer)
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            val confidences = outputFeature0.floatArray
            var maxPos = 0
            var maxConfidence = 0f
            confidences.forEachIndexed { index, confidence ->
                if (confidence > maxConfidence) {
                    maxConfidence = confidence
                    maxPos = index
                }
            }

            val classes = arrayOf("Alternaria", "Anthracnose", "Black Mould Rot", "Healthy", "Stem End Rot")
            result.text = classes[maxPos]

            model.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == 3) {
                val image = data?.extras?.get("data") as? Bitmap
                image?.let {
                    val dimension = minOf(it.width, it.height)
                    val thumbnail = ThumbnailUtils.extractThumbnail(it, dimension, dimension)
                    imageView.setImageBitmap(thumbnail)

                    val scaledImage = Bitmap.createScaledBitmap(thumbnail, imageSize, imageSize, false)
                    classifyImage(scaledImage)
                }
            } else {
                val uri = data?.data
                uri?.let {
                    try {
                        val image = MediaStore.Images.Media.getBitmap(contentResolver, it)
                        imageView.setImageBitmap(image)

                        val scaledImage = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
                        classifyImage(scaledImage)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
