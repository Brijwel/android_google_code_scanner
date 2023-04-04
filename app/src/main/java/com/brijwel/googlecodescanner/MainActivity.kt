package com.brijwel.googlecodescanner

import android.os.Bundle
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

        if (checkPlayServices()) {
            moduleInstallClient.deferredInstall(optionalModuleApi)
        }
        findViewById<Button>(R.id.scan).setOnClickListener {
            if (checkPlayServices(true)) {
                checkForAvailability {
                    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC
                    ).allowManualInput().build()

                    val scanner = GmsBarcodeScanning.getClient(this, options)
                    scanner
                        .startScan()
                        .addOnSuccessListener { barcode ->
                        // Task completed successfully
                        val rawValue: String? = barcode.rawValue
                        findViewById<TextView>(R.id.result).text = rawValue
                    }.addOnCanceledListener {
                        // Task canceled
                    }.addOnFailureListener { e ->
                        // Task failed with an exception
                        Toast.makeText(
                            this, e.message ?: "Something went wrong!", Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    this, "No Google Play Service Available!", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun checkForAvailability(availableCallback: () -> Unit) {
        moduleInstallClient.areModulesAvailable(optionalModuleApi).addOnSuccessListener {
            if (it.areModulesAvailable()) {
                // Modules are present on the device...
                availableCallback()
            } else {
                // Modules are not present on the device...
                val moduleInstallRequest =
                    ModuleInstallRequest.newBuilder().addApi(optionalModuleApi)
                        .setListener(listener).build()

                moduleInstallClient.installModules(moduleInstallRequest)
                    .addOnSuccessListener { moduleInstallResponse ->
                        if (moduleInstallResponse.areModulesAlreadyInstalled()) {
                            // Modules are already installed when the request is sent.
                            availableCallback()
                        }
                    }.addOnFailureListener { e ->
                        // Handle failure…
                        Toast.makeText(
                            this, e.message ?: "Something went wrong!", Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }.addOnFailureListener { e ->
            // Handle failure…
            Toast.makeText(
                this, e.message ?: "Something went wrong!", Toast.LENGTH_SHORT
            ).show()
        }


    }

    inner class ModuleInstallProgressListener : InstallStatusListener {
        override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
            // Progress info is only set when modules are in the progress of downloading.
            update.progressInfo?.let {
                val progress = (it.bytesDownloaded * 100 / it.totalBytesToDownload).toInt()
                // Set the progress for the progress bar.
                progressBar?.progress = progress
                progressBar?.isVisible = progress > 100
            }

            if (isTerminateState(update.installState)) {
                moduleInstallClient.unregisterListener(this)
            }
        }

        private fun isTerminateState(@ModuleInstallStatusUpdate.InstallState state: Int): Boolean {
            return state == STATE_CANCELED || state == STATE_COMPLETED || state == STATE_FAILED
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


}