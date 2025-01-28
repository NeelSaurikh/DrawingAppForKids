package com.example.kidsdrawingapp

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.get
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream




class MainActivity : AppCompatActivity() {

    private var drawingView : DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? =null

    var customProgressDialog : Dialog? = null


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(10.toFloat())



        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)

        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val id_brush: ImageButton = findViewById(R.id.ib_brush)
        id_brush.setOnClickListener{
            showBrushSIzeChooserDialog()
        }

        val id_undo: ImageButton = findViewById(R.id.ib_undo)
        id_undo.setOnClickListener{
            drawingView?.onClickUndo()
        }

        val ibGallery : ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener{
            requestStoragePermission()
        }

        val ibSave: ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {
            if (isReadStorageAllowed()) {
                lifecycleScope.launch {
                    val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    showProgressDialog()
                    Log.d("SaveFeature", "Attempting to capture bitmap")
                    val bitmap = getBitmapFromView(flDrawingView)
                    if (bitmap != null) {
                        val filePath = saveBitmapFile(bitmap)
                        if (filePath.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "File saved at: $filePath", Toast.LENGTH_SHORT).show()
                            cancelProgressDialog()
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to save file.", Toast.LENGTH_SHORT).show()
                            cancelProgressDialog()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to generate bitmap.", Toast.LENGTH_SHORT).show()
                        cancelProgressDialog()
                    }
                }
            } else {
                requestStoragePermission()
                Toast.makeText(this, "Storage permission not granted.", Toast.LENGTH_SHORT).show()
                cancelProgressDialog()
            }
        }
    }

    val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result ->
            if(result.resultCode == RESULT_OK && result.data!=null){
                val imageBackGround : ImageView = findViewById(R.id.iv_background)
                imageBackGround.setImageURI(result.data?.data)
            }
        }
    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach { entry ->
                val permissionName = entry.key
                val isGranted = entry.value

                when {
                    isGranted -> {
                        Toast.makeText(
                            this,
                            "$permissionName Permission Granted. You can access storage.",
                            Toast.LENGTH_SHORT
                        ).show()

                        val pickIntent = Intent(Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        openGalleryLauncher.launch(pickIntent)
                    }
                    permissionName == Manifest.permission.READ_EXTERNAL_STORAGE ||
                            permissionName == Manifest.permission.READ_MEDIA_IMAGES -> {
                        Toast.makeText(
                            this,
                            "$permissionName Permission Denied. Cannot access storage.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    private fun isReadStorageAllowed(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }


    private fun requestStoragePermission() {
        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        // Check if rationale should be shown
        if (permissionsToRequest.any {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }) {
            showRationaleDialog(
                "Permission Required",
                "The app needs access to your storage to allow you to load and save images."
            )
        } else {
            // Request the permissions
            requestPermission.launch(permissionsToRequest)
        }
    }






    private fun showBrushSIzeChooserDialog(){
        var brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size:")
        val smallBtn:ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn:ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn:ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View){
        if(view !==mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view
        }
    }

    private fun getBitmapFromView(view:View): Bitmap{
            val returnedBitmap = Bitmap.createBitmap(view.width,
                view.height,
                Bitmap.Config.ARGB_8888)

            val canvas = Canvas(returnedBitmap)
            val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // For Android 10 and above
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "KidsDrawing_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KidsDrawingApp")
                        }

                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        uri?.let { nonNullUri ->
                            contentResolver.openOutputStream(nonNullUri)?.use { outputStream ->
                                mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                                result = nonNullUri.toString()
                            }
                        }
                    } else {
                        // For Android 9 and below
                        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        val file = File(picturesDir, "KidsDrawingApp_${System.currentTimeMillis()}.jpeg")
                        val fos = FileOutputStream(file)
                        mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        fos.close()
                        result = file.absolutePath

                        // Add to gallery
                        addToGallery(result)
                    }

                    runOnUiThread {
                        if (result.isNotEmpty()) {
                            Toast.makeText(this@MainActivity, "File Saved Successfully: $result", Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to save file.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("SaveFeature", "Error saving bitmap: ${e.message}")
                }
            }
        }
        return result
    }

    private fun addToGallery(filePath: String) {
        MediaScannerConnection.scanFile(this, arrayOf(filePath), null) { _, uri ->
            runOnUiThread {
                Toast.makeText(this, "Saved to Gallery: $uri", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRationaleDialog(
        title:String,
        message: String,
    ){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){ dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)

        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        customProgressDialog!!.show()
    }

    private fun cancelProgressDialog(){
        customProgressDialog!!.dismiss()
    }

    private fun shareImage(result:String){

        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path,uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/jpeg"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }

}