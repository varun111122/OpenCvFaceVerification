package com.example.oponcvdemo

import android.Manifest
import android.app.Dialog
import android.app.ProgressDialog
import android.content.ContentValues.TAG
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.DialogFragment
import com.google.android.cameraview.CameraView
import com.tzutalin.dlib.Constants
import com.tzutalin.dlib.FaceRec
import com.tzutalin.dlib.VisionDetRet
import java.io.File
import java.lang.IllegalArgumentException
import java.util.ArrayList
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {

    private val TAG = "MainActivity"
    private val INPUT_SIZE = 500

    private val FLASH_OPTIONS = intArrayOf(
        CameraView.FLASH_AUTO,
        CameraView.FLASH_OFF,
        CameraView.FLASH_ON
    )

    private val FLASH_ICONS = intArrayOf(
        R.drawable.ic_flash_auto,
        R.drawable.ic_flash_off,
        R.drawable.ic_flash_on
    )

    private val FLASH_TITLES = intArrayOf(
        R.string.flash_auto,
        R.string.flash_off,
        R.string.flash_on
    )

    private var mCurrentFlash = 0

    private var mCameraView: CameraView? = null

    private var mBackgroundHandler: Handler? = null

    private val mOnClickListener =
        View.OnClickListener { v ->
            when (v.id) {
                R.id.take_picture -> if (mCameraView != null) {
                    mCameraView!!.takePicture()
                }
                R.id.add_person -> {
                    val i = Intent(this@MainActivity, AddPerson::class.java)
                    startActivity(i)
                    finish()
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        checkPermissions()

        mCameraView = findViewById<View>(R.id.camera) as CameraView
        if (mCameraView != null) {
            mCameraView!!.addCallback(mCallback)
        }

        val fab = findViewById<View>(R.id.take_picture) as Button
        fab?.setOnClickListener(mOnClickListener)
        val add_person = findViewById<View>(R.id.add_person) as Button
        add_person?.setOnClickListener(mOnClickListener)
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.setDisplayShowTitleEnabled(false)
    }

    private var mFaceRec: FaceRec? = null
    private fun changeProgressDialogMessage(pd: ProgressDialog, msg: String) {
        val changeMessage = Runnable { pd.setMessage(msg) }
        runOnUiThread(changeMessage)
    }

   inner private class initRecAsync :
        AsyncTask<Void?, Void?, Void?>() {
        var dialog: ProgressDialog? = ProgressDialog(this@MainActivity)
        override fun onPreExecute() {
            Log.d(TAG, "initRecAsync onPreExecute called")
            dialog!!.setMessage("Initializing...")
            dialog!!.setCancelable(false)
            dialog!!.show()
            super.onPreExecute()
        }

        protected override fun doInBackground(vararg p0: Void?): Void? {
            // create dlib_rec_example directory in sd card and copy model files
            val folder = File(Constants.getDLibDirectoryPath())
            var success = false
            if (!folder.exists()) {
                success = folder.mkdirs()
            }
            if (success) {
                val image_folder = File(Constants.getDLibImageDirectoryPath())
                image_folder.mkdirs()
                if (!File(Constants.getFaceShapeModelPath()).exists()) {
                    FileUtils.copyFileFromRawToOthers(
                        this@MainActivity,
                        R.raw.shape_predictor_5_face_landmarks,
                        Constants.getFaceShapeModelPath()
                    )
                }
                if (!File(Constants.getFaceDescriptorModelPath()).exists()) {
                    FileUtils.copyFileFromRawToOthers(
                        this@MainActivity,
                        R.raw.dlib_face_recognition_resnet_model_v1,
                        Constants.getFaceDescriptorModelPath()
                    )
                }
            } else {
                //Log.d(TAG, "error in setting dlib_rec_example directory");
            }
            mFaceRec = FaceRec(Constants.getDLibDirectoryPath())
            changeProgressDialogMessage(dialog!!, "Adding people...")
            mFaceRec!!.train()
            return null
        }

        override fun onPostExecute(result: Void?) {
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
            }
        }
    }

    var permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private fun checkPermissions(): Boolean {
        var result: Int
        val listPermissionsNeeded: MutableList<String> = ArrayList()
        for (p in permissions) {
            result = ContextCompat.checkSelfPermission(this, p)
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p)
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), 100)
            return false
        }
        return true
    }


    override fun onResume() {
        Log.d(TAG, "onResume called")
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            mCameraView!!.start()
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            initRecAsync().execute()
        }
    }

    override fun onPause() {
        Log.d(TAG, "onPause called")
        mCameraView!!.stop()
        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        mFaceRec?.release()
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler!!.getLooper().quitSafely()
            } else {
                mBackgroundHandler!!.getLooper().quit()
            }
            mBackgroundHandler = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.size > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                // do something
            }
            return
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.switch_flash -> {
                if (mCameraView != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.size
                    item.setTitle(FLASH_TITLES.get(mCurrentFlash))
                    item.setIcon(FLASH_ICONS.get(mCurrentFlash))
                    mCameraView!!.flash = FLASH_OPTIONS.get(mCurrentFlash)
                }
                return true
            }
            R.id.switch_camera -> {
                if (mCameraView != null) {
                    val facing = mCameraView!!.facing
                    mCameraView!!.facing =
                        if (facing == CameraView.FACING_FRONT) CameraView.FACING_BACK else CameraView.FACING_FRONT
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getBackgroundHandler(): Handler? {
        if (mBackgroundHandler == null) {
            val thread = HandlerThread("background")
            thread.start()
            mBackgroundHandler = Handler(thread.looper)
        }
        return mBackgroundHandler
    }

    private fun getResultMessage(names: ArrayList<String>): String? {
        var msg = String()
        if (names.isEmpty()) {
            msg = "No face detected or Unknown person"
        } else {
            for (i in names.indices) {
                msg += names[i].split(Pattern.quote(".")).toTypedArray()[0]
                if (i != names.size - 1) msg += ", "
            }
            msg += " found!"
        }
        return msg
    }

   inner private class recognizeAsync :
        AsyncTask<Bitmap?, Void?, ArrayList<String>>() {
        var dialog: ProgressDialog? = ProgressDialog(this@MainActivity)
        private var mScreenRotation = 0
        private val mIsComputing = false
        private val mCroppedBitmap = Bitmap.createBitmap(
           INPUT_SIZE,
            INPUT_SIZE,
            Bitmap.Config.ARGB_8888
        )

        override fun onPreExecute() {
            dialog!!.setMessage("Recognizing...")
            dialog!!.setCancelable(false)
            dialog!!.show()
            super.onPreExecute()
        }

        protected override fun doInBackground(vararg p0: Bitmap?): ArrayList<String>? {
            drawResizedBitmap(p0[0]!!, mCroppedBitmap)
            Log.d(TAG, "byte to bitmap")
            val startTime = System.currentTimeMillis()
            val results: List<VisionDetRet>
            results = mFaceRec!!.recognize(mCroppedBitmap) as List<VisionDetRet>
            val endTime = System.currentTimeMillis()
            Log.d(
               TAG,
                "Time cost: " + ((endTime - startTime) / 1000f).toString() + " sec"
            )
            val names = ArrayList<String>()
            for (n in results) {
                names.add(n.label)
            }
            return names
        }

        override fun onPostExecute(names: ArrayList<String>) {
            if (dialog != null && dialog!!.isShowing) {
                dialog!!.dismiss()
                val builder1 = AlertDialog.Builder(this@MainActivity)
                builder1.setMessage(getResultMessage(names))
                builder1.setCancelable(true)
                val alert11 = builder1.create()
                alert11.show()
            }
        }

        private fun drawResizedBitmap(src: Bitmap, dst: Bitmap) {
            val getOrient = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
            var orientation = Configuration.ORIENTATION_UNDEFINED
            val point = Point()
            getOrient.getSize(point)
            val screen_width = point.x
            val screen_height = point.y
            Log.d(
                TAG,
                String.format("screen size (%d,%d)", screen_width, screen_height)
            )
            if (screen_width < screen_height) {
                orientation = Configuration.ORIENTATION_PORTRAIT
                mScreenRotation = 0
            } else {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                mScreenRotation = 0
            }
//           Assert.assertEquals(dst.width, dst.height)
            val minDim = Math.min(src.width, src.height).toFloat()
            val matrix = Matrix()

            // We only want the center square out of the original rectangle.
            val translateX = -Math.max(0f, (src.width - minDim) / 2)
            val translateY = -Math.max(0f, (src.height - minDim) / 2)
            matrix.preTranslate(translateX, translateY)
            val scaleFactor = dst.height / minDim
            matrix.postScale(scaleFactor, scaleFactor)

            // Rotate around the center if necessary.
            if (mScreenRotation != 0) {
                matrix.postTranslate(-dst.width / 2.0f, -dst.height / 2.0f)
                matrix.postRotate(mScreenRotation.toFloat())
                matrix.postTranslate(dst.width / 2.0f, dst.height / 2.0f)
            }
            val canvas = Canvas(dst)
            canvas.drawBitmap(src, matrix, null)
        }
    }


    private val mCallback: CameraView.Callback = object : CameraView.Callback() {
        override fun onCameraOpened(cameraView: CameraView) {
            Log.d(TAG, "onCameraOpened")
        }

        override fun onCameraClosed(cameraView: CameraView) {
            Log.d(TAG, "onCameraClosed")
        }

        override fun onPictureTaken(cameraView: CameraView, data: ByteArray) {
            Log.d(TAG, "onPictureTaken " + data.size)
            Toast.makeText(cameraView.context, R.string.picture_taken, Toast.LENGTH_SHORT)
                .show()
            val bp = BitmapFactory.decodeByteArray(data, 0, data.size)
            recognizeAsync().execute(bp)
        }
    }

    class ConfirmationDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val args = arguments
            return AlertDialog.Builder(requireActivity())
                .setMessage(args!!.getInt(ARG_MESSAGE))
                .setPositiveButton(
                    android.R.string.ok
                ) { dialog, which ->
                    val permissions =
                        args.getStringArray(ARG_PERMISSIONS)
                            ?: throw IllegalArgumentException()
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        permissions, args.getInt(ARG_REQUEST_CODE)
                    )
                }
                .setNegativeButton(
                    android.R.string.cancel
                ) { dialog, which ->
                    Toast.makeText(
                        activity,
                        args.getInt(ARG_NOT_GRANTED_MESSAGE),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .create()
        }

        companion object {
            private const val ARG_MESSAGE = "message"
            private const val ARG_PERMISSIONS = "permissions"
            private const val ARG_REQUEST_CODE = "request_code"
            private const val ARG_NOT_GRANTED_MESSAGE = "not_granted_message"
            fun newInstance(
                @StringRes message: Int,
                permissions: Array<String?>?, requestCode: Int, @StringRes notGrantedMessage: Int
            ): ConfirmationDialogFragment {
                val fragment = ConfirmationDialogFragment()
                val args = Bundle()
                args.putInt(ARG_MESSAGE, message)
                args.putStringArray(ARG_PERMISSIONS, permissions)
                args.putInt(ARG_REQUEST_CODE, requestCode)
                args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage)
                fragment.arguments = args
                return fragment
            }
        }
    }
}