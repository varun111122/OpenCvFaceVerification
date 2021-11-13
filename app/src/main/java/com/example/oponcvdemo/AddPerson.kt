package com.example.oponcvdemo

import android.Manifest
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tzutalin.dlib.Constants
import com.tzutalin.dlib.FaceRec
import com.tzutalin.dlib.VisionDetRet
import java.io.*
import java.lang.Exception

class AddPerson :AppCompatActivity() {
    var et_name: EditText? = null
    var et_image:EditText? = null
    var btn_select_image: Button? = null
    var btn_add:android.widget.Button? = null
    var BITMAP_QUALITY = 100
    var MAX_IMAGE_SIZE = 500
    var TAG = "AddPerson"
    private var bitmap: Bitmap? = null
    private var destination: File? = null
    private var imgPath: String? = null
    private val PICK_IMAGE_CAMERA = 1
    private val PICK_IMAGE_GALLERY = 2
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_person)
        btn_select_image = findViewById<View>(R.id.btn_select_image) as Button
        btn_add = findViewById<View>(R.id.btn_add) as Button
        et_name = findViewById<View>(R.id.et_name) as EditText
        et_image = findViewById<View>(R.id.et_image) as EditText
        btn_select_image!!.setOnClickListener(mOnClickListener)
        btn_add!!.setOnClickListener(mOnClickListener)
        btn_add!!.setEnabled(false)
        et_name!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(arg0: Editable) {
                imgPath = null
                et_image!!.setText("")
                enableSubmitIfReady()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
        destination = File(Constants.getDLibDirectoryPath() + "/temp.jpg")
    }

    fun enableSubmitIfReady() {
        val isReady = et_name!!.text.toString().length > 0 && imgPath != null
        btn_add!!.setEnabled(isReady)
    }


    private val mOnClickListener =
        View.OnClickListener { v ->
            when (v.id) {
                R.id.btn_select_image -> selectImage()
                R.id.btn_add -> {
                    val targetPath =
                        Constants.getDLibImageDirectoryPath() + "/" + et_name!!.text.toString() + ".jpg"
                    FileUtils.copyFile(imgPath, targetPath)
                    val i = Intent(this@AddPerson, MainActivity::class.java)
                    startActivity(i)
                    finish()
                }
            }
        }

    // Select image from camera and gallery
    private fun selectImage() {
        try {
            val pm = packageManager
            val hasPerm = pm.checkPermission(Manifest.permission.CAMERA, packageName)
            if (hasPerm == PackageManager.PERMISSION_GRANTED) {
                val options = arrayOf<CharSequence>("Take Photo", "Choose From Gallery", "Cancel")
                val builder = AlertDialog.Builder(this@AddPerson)
                builder.setTitle("Select Option")
                builder.setItems(options) { dialog, item ->
                    if (options[item] == "Take Photo") {
                        dialog.dismiss()
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        startActivityForResult(intent, PICK_IMAGE_CAMERA)
                    } else if (options[item] == "Choose From Gallery") {
                        dialog.dismiss()
                        val pickPhoto =
                            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        startActivityForResult(pickPhoto, PICK_IMAGE_GALLERY)
                    } else if (options[item] == "Cancel") {
                        dialog.dismiss()
                    }
                }
                builder.show()
            } else Toast.makeText(this, "Camera Permission error", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Camera Permission error", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_CAMERA) {
            try {
                val selectedImage = data!!.data
                bitmap = data.extras!!["data"] as Bitmap?
                val scaledBitmap = scaleDown(bitmap!!, MAX_IMAGE_SIZE.toFloat(), true)
                et_image!!.setText(destination!!.absolutePath)
                detectAsync().execute(scaledBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (requestCode == PICK_IMAGE_GALLERY) {
            val selectedImage = data!!.data
            try {
                bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImage)
                val scaledBitmap = scaleDown(bitmap!!, MAX_IMAGE_SIZE.toFloat(), true)
                et_image!!.setText(getRealPathFromURI(selectedImage))
                detectAsync().execute(scaledBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getRealPathFromURI(contentUri: Uri?): String? {
        val proj = arrayOf(MediaStore.Audio.Media.DATA)
        val cursor = managedQuery(contentUri, proj, null, null, null)
        val column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        cursor.moveToFirst()
        return cursor.getString(column_index)
    }

    fun scaleDown(realImage: Bitmap, maxImageSize: Float, filter: Boolean): Bitmap {
        val ratio = Math.min(
            maxImageSize / realImage.width,
            maxImageSize / realImage.height
        )
        val width = Math.round(ratio * realImage.width)
        val height = Math.round(ratio * realImage.height)
        return Bitmap.createScaledBitmap(
            realImage, width,
            height, filter
        )
    }

    private var mFaceRec: FaceRec? = null

   inner private class detectAsync : AsyncTask<Bitmap?, Void?, String?>() {
        var dialog: ProgressDialog? = ProgressDialog(this@AddPerson)
        override fun onPreExecute() {
            dialog!!.setMessage("Detecting face...")
            dialog!!.setCancelable(false)
            dialog!!.show()
            super.onPreExecute()
        }

        protected override fun doInBackground(vararg p0: Bitmap?): String? {
            mFaceRec = FaceRec(Constants.getDLibDirectoryPath())
            val results: List<VisionDetRet>
            results = mFaceRec!!.detect(p0[0]!!) as List<VisionDetRet>
            var msg: String? = null
            if (results.size == 0) {
                msg = "No face was detected or face was too small. Please select a different image"
            } else if (results.size > 1) {
                msg = "More than one face was detected. Please select a different image"
            } else {
                val bytes = ByteArrayOutputStream()
                p0[0]!!.compress(Bitmap.CompressFormat.JPEG, BITMAP_QUALITY, bytes)
                val fo: FileOutputStream
                try {
                    destination!!.createNewFile()
                    fo = FileOutputStream(destination)
                    fo.write(bytes.toByteArray())
                    fo.close()
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                imgPath = destination!!.getAbsolutePath()
            }
            return msg
        }

        override fun onPostExecute(result: String?) {
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
                if (result != null) {
                    val builder1 = AlertDialog.Builder(this@AddPerson)
                    builder1.setMessage(result)
                    builder1.setCancelable(true)
                    val alert11 = builder1.create()
                    alert11.show()
                    imgPath = null
                    et_image!!.setText("")
                }
                enableSubmitIfReady()
            }
        }
    }
}