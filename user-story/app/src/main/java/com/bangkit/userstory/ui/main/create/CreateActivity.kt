package com.bangkit.userstory.ui.main.create

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bangkit.userstory.R
import com.bangkit.userstory.ViewModelFactory
import com.bangkit.userstory.data.remote.response.GeneralResponse
import com.bangkit.userstory.databinding.ActivityCreateBinding
import com.bangkit.userstory.utils.getImageUri
import com.bangkit.userstory.utils.reduceFileImage
import com.bangkit.userstory.utils.uriToFile
import com.bangkit.userstory.ui.authentication.welcome.WelcomeActivity
import com.bangkit.userstory.ui.main.MainViewModel
import com.bangkit.userstory.ui.main.maps.MapsActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CreateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateBinding
    private var currentImageUri: Uri? = null
    private val viewModel by viewModels<MainViewModel> {
        ViewModelFactory.getInstance(this)
    }
    private lateinit var token: String;
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        playAnimation()
        setupView()

        viewModel.getSession().observe(this) { user ->
            if (!user.isLogin) {
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            } else {
                token = user.token
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.apply {
            galleryButton.setOnClickListener { startGallery() }
            cameraButton.setOnClickListener { startCamera() }
            submitButton.setOnClickListener {
                val uri = currentImageUri
                if (uri != null) {
                    val imageFile = uriToFile(uri, this@CreateActivity).reduceFileImage()
                    val description = binding.descEditText.text.toString()
                    if (binding.checkboxLocation.isChecked) {
                        getMyLocation { location ->
                            viewModel.createStory(token, imageFile, description, location.latitude, location.longitude)
                        }
                    } else {
                        viewModel.createStory(token, imageFile, description)
                    }
                } else {
                    handleResponse(
                        GeneralResponse(
                            error = true,
                            message = "You must pick a photo first before submit"
                        )
                    )
                }

                viewModel.newStory.observe(this@CreateActivity) { response ->
                    if (response.error != null) {
                        handleResponse(response)
                    }
                }

                viewModel.isLoading.observe(this@CreateActivity) { isLoading ->
                    showLoading(isLoading)
                }
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        )
        { isGranted: Boolean ->
            if (isGranted) {
                getMyLocation{}
            }
        }

    private fun getMyLocation(callback: (Location) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    callback(it)
                }
            }
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.logout -> {
                showLogoutConfirmationDialog()
            }
            R.id.change_language -> {
                startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
            }
            R.id.maps -> {
                val intent = Intent(this, MapsActivity::class.java)
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showLogoutConfirmationDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
    }


    private fun setupView() {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    private fun startGallery() {
        launcherGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private val launcherGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            currentImageUri = uri
            showImage()
        } else {
            Log.d("Photo Picker", "No media selected")
        }
    }

    private fun startCamera() {
        currentImageUri = getImageUri(this)
        launcherIntentCamera.launch(currentImageUri!!)
    }
    private val launcherIntentCamera = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { isSuccess ->
        if (isSuccess) {
            showImage()
        }
    }

    private fun showImage() {
        currentImageUri?.let {
            binding.previewImageView.setImageURI(it)
        }
    }

    private fun handleResponse(response: GeneralResponse?) {
        response?.let {
            if (it.error == false) {
                val dialog = MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.success))
                    .setMessage(getString(R.string.create_story_success))
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        finish()
                    }
                    .show()
            } else if (it.error == true) {
                val dialog = MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.failed))
                    .setMessage(it.message)
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
            }

        }
        viewModel.clearResponse()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
    }

    private fun playAnimation() {
        val title = ObjectAnimator.ofFloat(binding.titleTextView, View.ALPHA, 1f).setDuration(500)
        val preview = ObjectAnimator.ofFloat(binding.previewImageView, View.ALPHA, 1f).setDuration(500)
        val galleryButton = ObjectAnimator.ofFloat(binding.galleryButton, View.ALPHA, 1f).setDuration(500)
        val cameraButton = ObjectAnimator.ofFloat(binding.cameraButton, View.ALPHA, 1f).setDuration(500)
        val descTextView = ObjectAnimator.ofFloat(binding.descTextView, View.ALPHA, 1f).setDuration(500)
        val descEditTextLayout = ObjectAnimator.ofFloat(binding.descEditTextLayout, View.ALPHA, 1f).setDuration(500)
        val submitButton = ObjectAnimator.ofFloat(binding.submitButton, View.ALPHA, 1f).setDuration(500)
        val checkboxLocation = ObjectAnimator.ofFloat(binding.checkboxLocation, View.ALPHA, 1f).setDuration(500)

        val togetherButton = AnimatorSet().apply {
            playTogether(galleryButton, cameraButton)
        }

        val togetherDescription = AnimatorSet().apply {
            playTogether(descTextView, descEditTextLayout)
        }

        AnimatorSet().apply {
            playSequentially(
                title,
                preview,
                togetherButton,
                togetherDescription,
                checkboxLocation,
                submitButton
            )
            start()
        }
    }
}