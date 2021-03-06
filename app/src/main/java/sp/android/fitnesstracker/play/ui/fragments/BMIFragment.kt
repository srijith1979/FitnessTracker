package sp.android.fitnesstracker.play.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_bmr.*
import sp.android.fitnesstracker.play.R
import sp.android.fitnesstracker.play.util.Constants
import sp.android.fitnesstracker.play.util.TrackingUtility
import java.lang.Math.round
import javax.inject.Inject

/*
* Fragment responsible for calculating BMI based on the height and weight provided
* by the user
* */
@AndroidEntryPoint
class BMIFragment : Fragment(R.layout.fragment_bmi) {
    @set:Inject
    var name = ""

    @Inject
    lateinit var sharedPref: SharedPreferences
    var height: Float = 0f
    var weight: Float = 0f

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadFieldsFromSharedPref()

        calculateButtonId.setOnClickListener {
            if (!applyChangesToSharedPref()) {
                Snackbar.make(
                    requireView(),
                    getString(R.string.please_enter_all_fields),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadFieldsFromSharedPref() {
        weight = sharedPref.getFloat(Constants.KEY_WEIGHT, 0f)
        height = sharedPref.getFloat(Constants.KEY_HEIGHT, 0f)

        if (weight > 0f) weightInputEditText.setText(weight.toString())
        if (height > 0f) heightInputId.setText(height.toString())
    }

    private fun applyChangesToSharedPref(): Boolean {
        val weightText = weightInputEditText.text.toString()
        val heightText = heightInputId.text.toString()

        if (weightText.isEmpty() || heightText.isEmpty()) {
            return false
        }

        val weight = weightText.toFloat()
        val height = heightText.toFloat()
        sharedPref.edit()
            .putFloat(Constants.KEY_HEIGHT, height)
            .apply()

        var bmi = round(((weight / (height * height)) * 703) * 100) / 100f

        MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(String.format(getString(R.string.dear_user), name))
            .setMessage(
                String.format(
                    getString(R.string.your_bmi_result), bmi, TrackingUtility.getBMIMessage(
                        bmi,
                        requireContext()
                    )
                )
            )
            .setPositiveButton(getString(android.R.string.ok)) { dialogInterface, _ ->
                dialogInterface.cancel()
            }
            .create()
            .show()

        return true
    }
}