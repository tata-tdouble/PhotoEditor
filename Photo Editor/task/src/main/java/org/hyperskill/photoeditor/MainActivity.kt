package org.hyperskill.photoeditor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    private var lastJob: Job? = null

    private lateinit var currentImage: ImageView

    private lateinit var galleryButton: Button
    private lateinit var saveButton: Button

    private lateinit var brightnessSlider: Slider
    private lateinit var contrastSlider: Slider
    private lateinit var saturationSlider: Slider
    private lateinit var gammaSlider: Slider

    private lateinit var currentImgBitmap: Bitmap
    private lateinit var modifiedBitmap : Bitmap

    private var brightnessValue: Float = 0F
    private var contrastValue: Float = 0F
    private var saturationValue: Float = 0F
    private var gammaValue: Float = 1F

    private val storagePermissionCode = 0

    private val activityResultLauncher =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val photoUri = result.data?.data ?: return@registerForActivityResult
                // code to update ivPhoto with loaded image
                currentImgBitmap = getBitmapFromUri(photoUri, contentResolver)!!
                modifiedBitmap = getBitmapFromUri(photoUri, contentResolver)!!
                currentImage.setImageURI(photoUri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()

        //do not change this line
        currentImage.setImageBitmap(createBitmap())
        modifiedBitmap = createBitmap()
        currentImgBitmap = createBitmap()

        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            activityResultLauncher.launch(intent)
        }

        saveButton.setOnClickListener {
            requestStoragePermission(modifiedBitmap)
        }

        brightnessSlider.addOnChangeListener { _, value, _ ->

            brightnessValue = value // Update the brightness value
            applyFilters()
        }

        contrastSlider.addOnChangeListener{ _, value, _ ->

            contrastValue = value // Update the contrast value
            applyFilters()
        }

        saturationSlider.addOnChangeListener{ _, value, _ ->

            saturationValue = value // Update the contrast value
            applyFilters()
        }

        gammaSlider.addOnChangeListener{ _, value, _ ->

            gammaValue = value // Update the contrast value
            applyFilters()
        }

    }

    private fun applyFilters() {

        println("Started")

        lastJob?.cancel()

        // Always start from the original bitmap
        modifiedBitmap = currentImgBitmap.copy(Bitmap.Config.RGB_565, true)

        // Apply filters in sequence
        lastJob = CoroutineScope(Dispatchers.Default).launch {


            val brightnessCopy = this.async {
                println("Started 1")
                addBrightnessFilter(modifiedBitmap, brightnessValue)
            }.await()

            val contrastCopy = this.async {
                println("Started 2")
                addContrastFilter(brightnessCopy, contrastValue)
            }.await()

            val saturationCopy = this.async {
                println("Started 3")
                addSaturationFilter(contrastCopy, saturationValue)
            }.await()

            val gammaCopy = this.async {
                println("Started 4")
                addGammaFilter(saturationCopy, gammaValue)
            }.await()

            modifiedBitmap = gammaCopy


            ensureActive()

            withContext(Dispatchers.Main) {
                // Set the modified image as the current image
                currentImage.setImageBitmap(modifiedBitmap)
            }
        }
    }

    private fun bindViews() {
        currentImage = findViewById(R.id.ivPhoto)
        galleryButton = findViewById(R.id.btnGallery)
        saveButton = findViewById(R.id.btnSave)
        brightnessSlider = findViewById(R.id.slBrightness)
        contrastSlider = findViewById(R.id.slContrast)
        saturationSlider = findViewById(R.id.slSaturation)
        gammaSlider = findViewById(R.id.slGamma)
    }

    private fun requestStoragePermission(bitmap: Bitmap) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Permission is already granted
            saveImageToExternalStorage(bitmap)
        } else {
            // Requesting the permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), storagePermissionCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == storagePermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveButton.callOnClick()
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImageToExternalStorage(bitmap: Bitmap) {
        // your Bitmap image here
        val filename = "your_image_name.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val resolver = contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream) // 100 for maximum quality
            }
        }
    }

    fun addBrightnessFilter(bitmap: Bitmap, value: Float): Bitmap {

        val width = bitmap.width
        val height = bitmap.height
        val mutableBitmap = bitmap.copy(Bitmap.Config.RGB_565, true) // Make the Bitmap mutable

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = mutableBitmap.getPixel(x, y)

                // Extract original color components
                var red = Color.red(pixel)
                var green = Color.green(pixel)
                var blue = Color.blue(pixel)

                // Convert to float values
                val mRed = red.toFloat()
                val mGreen = green.toFloat()
                val mBlue = blue.toFloat()

                // Adjust brightness and clamp between 0 and 255
                red = (mRed+ value).coerceIn(0f, 255f).toInt()
                green = (mGreen + value).coerceIn(0f, 255f).toInt()
                blue = (mBlue + value).coerceIn(0f, 255f).toInt()

                // Set the new pixel color
                val newPixel = Color.rgb(red, green, blue)
                mutableBitmap.setPixel(x, y, newPixel)
            }
        }

        return mutableBitmap
    }

    fun addContrastFilter(bitmap: Bitmap, contrast: Float): Bitmap {

        val width = bitmap.width
        val height = bitmap.height
        val mutableBitmap = bitmap.copy(Bitmap.Config.RGB_565, true)

        // Calculate the average brightness (avgBright)
        var totalBrightness: Long = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                totalBrightness += brightness.toLong()
            }
        }
        val avgBright: Int = (totalBrightness / (width * height)).toInt()

        // Calculate the contrast factor using the formula you provided
        val alpha = (255 + contrast) / (255 - contrast)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = mutableBitmap.getPixel(x, y)

                // Extract original color components
                var red = Color.red(pixel)
                var green = Color.green(pixel)
                var blue = Color.blue(pixel)

                // Apply contrast formula
                red = (alpha * (red - avgBright) + avgBright).coerceIn(0.0F, 255.0F).toInt()
                green = (alpha * (green - avgBright) + avgBright).coerceIn(0.0F, 255.0F).toInt()
                blue = (alpha * (blue - avgBright) + avgBright).coerceIn(0.0F, 255.0F).toInt()

                // Set the new pixel color
                val newPixel = Color.rgb(red, green, blue)
                mutableBitmap.setPixel(x, y, newPixel)
            }
        }
        return mutableBitmap
    }

    fun addSaturationFilter(bitmap: Bitmap, saturation: Float): Bitmap {

        val width = bitmap.width
        val height = bitmap.height
        val mutableBitmap = bitmap.copy(Bitmap.Config.RGB_565, true)

        // Calculate the saturation alpha
        val alpha: Double = ((255.0 + saturation.toDouble()) / (255.0 - saturation.toDouble()))

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = mutableBitmap.getPixel(x, y)


                // Extract original color components
                var red = Color.red(pixel)
                var green = Color.green(pixel)
                var blue = Color.blue(pixel)

                // Calculate the rgbAvg for this pixel
                val rgbAvg: Int = (red + green + blue) / 3

                // Apply saturation formula
                red = ((alpha * (red - rgbAvg)) + rgbAvg).coerceIn(0.0, 255.0).toInt()
                green = ((alpha * (green - rgbAvg)) + rgbAvg).coerceIn(0.0, 255.0).toInt()
                blue = ((alpha * (blue - rgbAvg)) + rgbAvg).coerceIn(0.0, 255.0).toInt()


                // Set the new pixel color
                val newPixel = Color.rgb(red, green, blue)
                mutableBitmap.setPixel(x, y, newPixel)
            }
        }

        return mutableBitmap
    }

    fun addGammaFilter(bitmap: Bitmap, gamma: Float): Bitmap {

        val width = bitmap.width
        val height = bitmap.height
        val mutableBitmap = bitmap.copy(Bitmap.Config.RGB_565, true)

        // Calculate the gamma correction factor
        val gammaCorrection = gamma

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = mutableBitmap.getPixel(x, y)

                // Extract original color components
                var red = Color.red(pixel)
                var green = Color.green(pixel)
                var blue = Color.blue(pixel)

                // Apply gamma correction formula
                red = (255.0 * Math.pow(red / 255.0, gammaCorrection.toDouble())).coerceIn(0.0, 255.0).toInt()
                green = (255.0 * Math.pow(green / 255.0, gammaCorrection.toDouble())).coerceIn(0.0, 255.0).toInt()
                blue = (255.0 * Math.pow(blue / 255.0, gammaCorrection.toDouble())).coerceIn(0.0, 255.0).toInt()

                // Set the new pixel color
                val newPixel = Color.rgb(red, green, blue)
                mutableBitmap.setPixel(x, y, newPixel)
            }
        }

        return mutableBitmap
    }

    // do not change this function
    private fun createBitmap(): Bitmap {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height)
        // get pixel array from source

        var R: Int
        var G: Int
        var B: Int
        var index: Int

        for (y in 0 until height) {
            for (x in 0 until width) {
                // get current index in 2D-matrix
                index = y * width + x
                // get color
                R = x % 100 + 40
                G = y % 100 + 80
                B = (x+y) % 100 + 120

                pixels[index] = Color.rgb(R,G,B)

            }
        }
        // output bitmap
        val bitmapOut = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        bitmapOut.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmapOut
    }

    private fun getBitmapFromUri(uri: Uri, contentResolver: ContentResolver): Bitmap? {
        return try {
            // Open an InputStream from the Uri
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            // Decode the InputStream into a Bitmap
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}