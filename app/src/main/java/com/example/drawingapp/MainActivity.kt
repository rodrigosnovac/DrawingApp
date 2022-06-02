package com.example.drawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    // VARIABLES
    private var drawingView : DrawingView? = null
    private var myImageButtonCurrentPaint : ImageButton? = null
    private var customProgressDialog : Dialog? = null
    //// to set image as background
    private val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if (result.resultCode == RESULT_OK && result.data != null){
                val imageBackground : ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
            }
        }
    //// to verify if permissions are granted
    private val requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted){
                    Toast.makeText(this@MainActivity, "Permission granted", Toast.LENGTH_SHORT).show()

                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)

                }else{
                    if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this@MainActivity, "Permission not granted for storage", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // INIT VARIABLES
        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        val ibRedo : ImageButton = findViewById(R.id.ib_redo)
        val ibSave : ImageButton = findViewById(R.id.ib_save_button)
        val ibLoadImage : ImageButton = findViewById(R.id.ib_load_image)
        val brushSelector : ImageButton = findViewById(R.id.brush_selector)
        val linearLayoutPaintColors : LinearLayout = findViewById(R.id.ll_color_picker)

        // setup DrawingView.kt
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.onBrushSizeChange(20.toFloat())

        myImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton

        brushSelector.setOnClickListener{
            showBrushSizeDialog()
        }

        ibLoadImage.setOnClickListener{
            requestStoragePermission()
        }

        ibUndo.setOnClickListener{
            drawingView?.onClickUndo()
        }

        ibRedo.setOnClickListener{
            drawingView?.onClickRedo()
        }

        ibSave.setOnClickListener{
            if (isReadStorageAllowed()){
                showProgressDialog()

                // coroutine to save image
                lifecycleScope.launch{
                    val flDrawingView : FrameLayout = findViewById(R.id.fl_drawing_view_container)

                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
    }

    private fun showBrushSizeDialog() {
        // VARIABLES
        val brushDialog = Dialog(this)
        //// selectable buttons
        val smallBtn : ImageButton = brushDialog.findViewById(R.id.small_brush_icon)
        val mediumBtn : ImageButton = brushDialog.findViewById(R.id.medium_brush_icon)
        val largeBtn : ImageButton = brushDialog.findViewById(R.id.large_brush_icon)

        brushDialog.setContentView(R.layout.brush_size_dialog)
        brushDialog.setTitle("Brush Size: ")

        smallBtn.setOnClickListener {
            drawingView?.onBrushSizeChange(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            drawingView?.onBrushSizeChange(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            drawingView?.onBrushSizeChange(30.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view : View) {

        // view = current color
        if (view !== myImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            myImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            drawingView?.setColor(colorTag)

            myImageButtonCurrentPaint = view
        }
    }

    private fun isReadStorageAllowed() : Boolean{
        val result = ContextCompat.checkSelfPermission(this,
                                                        Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                                                                Manifest.permission.READ_EXTERNAL_STORAGE)){
            showRationaleDialog("Drawing App", "Drawing app needs to access your external storage")
        }else{
            requestPermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun showRationaleDialog(title: String, message: String){
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)

        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel"){
            dialog, _ -> dialog.dismiss()
        }

        builder.create().show()
    }

    // makes image of view layers
    private fun getBitmapFromView(view : View) : Bitmap?{
        val requestedBitmap = Bitmap.createBitmap(view.height, view.width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(requestedBitmap)
        val backgroundDrawable = view.background

        if (backgroundDrawable != null){
            backgroundDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)
        return requestedBitmap
    }

    private suspend fun saveBitmapFile(myBitmap : Bitmap?) : String{
        // this variable stores bitmap image as string
        var result = ""

        // coroutine
        withContext(Dispatchers.IO){
            if (myBitmap != null){
                try{
                    val bytes = ByteArrayOutputStream()
                    myBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    // output file directory
                    val fileFile = File(externalCacheDir?.absoluteFile.toString() +
                                        File.separator + "DrawingApp_" + System.currentTimeMillis() / 1000 + ".png")

                    // final file
                    val fileOut = FileOutputStream(fileFile)
                    fileOut.write(bytes.toByteArray())
                    fileOut.close()

                    result = fileFile.absolutePath

                    // loading screen
                    runOnUiThread {
                        // close progress bar dialog
                        cancelProgressDialog()
                        if (result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,
                                            "File saved at $result", Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }
                    }
                }catch(e : Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }

        return result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this@MainActivity)
        customProgressDialog?.setContentView(R.layout.custom_loading_dialog)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if (customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result : String){
        MediaScannerConnection.scanFile(this, arrayOf(result), null){
            path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"

            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }
}
