package com.softbankrobotics.remotecontrol

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.Future
import com.aldebaran.qi.Promise
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.softbankrobotics.helpers.TAG
import com.softbankrobotics.telepresence.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.login.*


class MainActivity : RobotActivity() {


    private val loginDialog by lazy { Dialog(this).also {
            it.requestWindowFeature(Window.FEATURE_NO_TITLE)
            it.setCancelable(true)
            it.setCanceledOnTouchOutside(false)
            it.setContentView(R.layout.login)
            it.window?.setLayout(800, 600)
        }
    }

    /*********************
     * Android Lifecycle *
     *********************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
        setContentView(R.layout.activity_main)

        firebaseInit()

        requestPermissionForCameraAndMicrophone()
        initializeUI()
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()
    }

    private fun hideSystemBars() {
        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    /*********************
     *    GUI binding    *
     *********************/

    private fun initializeUI() {
        enterRoomButton.setOnClickListener { connectActionClickListener() }
        roomNameEt.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_GO -> {
                    connectActionClickListener()
                    true
                }
                else -> false
            }
        }
        logoutButton.setOnClickListener { logoutButtonPressed() }
    }

    override fun dispatchTouchEvent(ev : MotionEvent) : Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN)  {
            val outRect = Rect()
            roomNameEt.getGlobalVisibleRect(outRect)
            if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                // someone touched outside the text edit button: hide the keyboard.
                roomNameEt.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(roomNameEt.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }


    private fun connectActionClickListener() {
        if (roomNameEt.text.toString().length < 20) {
            roomNameError.setText(R.string.room_name_length)
            roomNameError.visibility = View.VISIBLE
        } else {
            roomNameError.visibility = View.GONE
            connectToRoom(roomNameEt.text.toString())
        }
    }

    private fun connectToRoom(roomName: String) {
        if (roomName.isNotEmpty()) {
            if (checkPermissionForCameraAndMicrophone()) {

                val intent = Intent(this, CallActivity::class.java)
                intent.putExtra("roomName", roomName)
                intent.putExtra("useFirebase", useFirebase)
                startActivity(intent)

            } else {
                Toast.makeText(this, R.string.permissions_needed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**********************
     * Android permissions
     **********************/

    private val CAMERA_MIC_PERMISSION_REQUEST_CODE = 1

    private fun requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
            ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                R.string.permissions_needed,
                Toast.LENGTH_LONG).show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                CAMERA_MIC_PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkPermissionForCameraAndMicrophone(): Boolean {
        val resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            var cameraAndMicPermissionGranted = true

            for (grantResult in grantResults) {
                cameraAndMicPermissionGranted = cameraAndMicPermissionGranted and
                        (grantResult == PackageManager.PERMISSION_GRANTED)
            }

            if (cameraAndMicPermissionGranted) {
                // Permission granted
            } else {
                Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /************************************
     * Log In (when firebase is enabled)
     ************************************/

    private fun isValidEmail(target: CharSequence?): Boolean {
        return !TextUtils.isEmpty(target) && Patterns.EMAIL_ADDRESS.matcher(target)
            .matches()
    }

    private fun isValidPassword(target: CharSequence?): Boolean {
        return !TextUtils.isEmpty(target)
    }

    private fun loginButtonPressed() {

        loginDialog.loginError.visibility = View.GONE

        if (!isValidEmail(loginDialog.email.text)) {
            loginDialog.loginError.text = getString(R.string.email_error)
            loginDialog.loginError.visibility = View.VISIBLE
        } else if (!isValidPassword(loginDialog.password.text)) {
            loginDialog.loginError.text = getString(R.string.password_error)
            loginDialog.loginError.visibility = View.VISIBLE
        } else {
            firebaseLogin().thenConsume { result ->
                if (result.hasError()) {
                    runOnUiThread {
                        loginDialog.loginError.text = result.errorMessage
                        loginDialog.loginError.visibility = View.VISIBLE
                    }
                } else {
                    runOnUiThread {
                        hideLoginFormAndShowMainInterface()
                    }
                }
            }
        }
    }

    private fun logoutButtonPressed() {
        firebaseLogOut()
        showLoginFormAndHideMainInterface()
        Log.d(TAG, "Firebase logged out")
    }

    private fun hideLoginFormAndShowMainInterface() {

        loginDialog.dismiss()
        hideSystemBars()
        loggedUser.text = auth.currentUser?.email

        userLayout.visibility = View.VISIBLE
    }

    private fun showLoginFormAndHideMainInterface() {
        loginDialog.show()
        loginDialog.loginButton.setOnClickListener { loginButtonPressed() }
        loginDialog.setOnCancelListener { finish() }

        userLayout.visibility = View.GONE
    }

    /************
     * Firebase
     ************/

    private var useFirebase = false
    private lateinit var auth: FirebaseAuth
    private lateinit var functions: FirebaseFunctions

    fun firebaseInit() {
        if (FirebaseApp.getApps(this).isNotEmpty()) {
            useFirebase = true
            auth = Firebase.auth
            functions = Firebase.functions
            if (auth.currentUser != null) {
                hideLoginFormAndShowMainInterface()
            } else {
                showLoginFormAndHideMainInterface()
            }
        }
    }

    private fun firebaseLogin(): Future<Boolean> {
        if (useFirebase) {
            if (auth.currentUser == null) {
                val promise = Promise<Boolean>()
                Log.d(TAG, "firebaseLogin: signing in")
                auth.signInWithEmailAndPassword(
                    loginDialog.email.text.toString(),
                    loginDialog.password.text.toString()
                )
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "firebaseLogin: Success")
                            promise.setValue(true)
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "firebaseLogin: Failure", task.exception)
                            promise.setError(task.exception.toString())
                        }
                    }
                return promise.future
            } else {
                Log.d(TAG, "firebaseLogin: User is already set")
                return Future.of(true)
            }
        } else {
            return Future.fromError("Firebase is not initialized")
        }
    }

    private fun firebaseLogOut() {
        if (useFirebase) {
            if (auth.currentUser != null) {
                auth.signOut()
            }
        }
    }

}
