package com.brijwel.googlecodescanner

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.*
import com.google.android.gms.tflite.java.TfLite
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val listener = ModuleInstallProgressListener()
    private val moduleInstallClient by lazy {
        ModuleInstall.getClient(this)
    }

    private val optionalModuleApi by lazy { TfLite.getClient(this) }
    private var progressBar: CircularProgressIndicator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressBar = findViewById(R.id.progress)

        deferredInstallModuleIfNotAvailable()
        findViewById<Button>(R.id.scan).setOnClickListener {
            if (checkPlayServices()) {
                launchGoogleCodeScanner()
            } else {
                toast("No Google Play Service Available!")
            }
        }
    }

    /**
     * Launch Google Code Scanner to scan bar code.
     */
    private fun launchGoogleCodeScanner() {
        checkForModuleAvailability {
            val options = GmsBarcodeScannerOptions
                .Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE
                )
                .build()

            val scanner = GmsBarcodeScanning.getClient(this, options)
            scanner
                .startScan()
                .addOnSuccessListener { barcode ->
                    // Task completed successfully
                    val rawValue: String? = barcode.rawValue
                    if (rawValue.isNullOrEmpty().not()) {
                        findViewById<TextView>(R.id.result).text = rawValue
                    }

                }.addOnFailureListener { e ->
                    // Task failed with an exception
                    toast(e.message ?: "Something went wrong!")
                }
        }
    }

    /**
     * Check if Play Services is available and if TfLite module is available.
     * if not install TfLite in deferral manner.
     */
    private fun deferredInstallModuleIfNotAvailable() {
        if (checkPlayServices()) {
            moduleInstallClient
                .areModulesAvailable(optionalModuleApi)
                .addOnSuccessListener {
                    if (it.areModulesAvailable().not())
                        moduleInstallClient.deferredInstall(optionalModuleApi)
                }
        }
    }

    /**
     * Check if TfLite is available and trigger availableCallback lambda function.
     * if TfLite is not available install TfLite immediately.
     */
    private fun checkForModuleAvailability(availableCallback: () -> Unit) {
        moduleInstallClient.areModulesAvailable(optionalModuleApi).addOnSuccessListener {
            if (it.areModulesAvailable()) {
                // Modules are present on the device...
                availableCallback()
            } else {
                // Modules are not present on the device...
                val moduleInstallRequest =
                    ModuleInstallRequest.newBuilder()
                        .addApi(optionalModuleApi)
                        .setListener(listener)
                        .build()

                moduleInstallClient.installModules(moduleInstallRequest)
                    .addOnSuccessListener { moduleInstallResponse ->
                        if (moduleInstallResponse.areModulesAlreadyInstalled()) {
                            // Modules are already installed when the request is sent.
                            availableCallback()
                        }
                    }.addOnFailureListener { e ->
                        // Handle failure…
                        toast(e.message ?: "Something went wrong!")
                    }
            }
        }.addOnFailureListener { e ->
            // Handle failure…
            toast(e.message ?: "Something went wrong!")
        }


    }

    /**
     * InstallStatusListener to check TfLite module install status.
     */
    inner class ModuleInstallProgressListener : InstallStatusListener {
        override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
            // Progress info is only set when modules are in the progress of downloading.
            update.progressInfo?.let {
                val progress = (it.bytesDownloaded * 100 / it.totalBytesToDownload).toInt()
                // Set the progress for the progress bar.
                progressBar?.progress = progress
                progressBar?.isVisible = progress > 100
                Log.d(TAG, "onInstallStatusUpdated: progress $progress")
            }

            when (update.installState) {
                STATE_PENDING,
                STATE_INSTALLING,
                STATE_DOWNLOADING -> {
                    progressBar?.isVisible = true
                }
                STATE_CANCELED,
                STATE_FAILED -> {
                    progressBar?.isVisible = false
                    moduleInstallClient.unregisterListener(this)
                }
                STATE_COMPLETED -> {
                    launchGoogleCodeScanner()
                    progressBar?.isVisible = false
                    moduleInstallClient.unregisterListener(this)
                }
                else -> progressBar?.isVisible = false
            }
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private fun checkPlayServices(canHaveUserResolvableError: Boolean = false): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            return apiAvailability.isUserResolvableError(resultCode) && canHaveUserResolvableError
        }
        return true
    }

    private fun Context.toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}