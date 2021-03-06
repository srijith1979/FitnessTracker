package sp.android.fitnesstracker.play.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_setup.*
import sp.android.fitnesstracker.play.R
import sp.android.fitnesstracker.play.util.Constants.KEY_FIRST_TIME_TOGGLE
import sp.android.fitnesstracker.play.util.Constants.KEY_NAME
import sp.android.fitnesstracker.play.util.Constants.KEY_WEIGHT
import javax.inject.Inject

@AndroidEntryPoint
class SetupFragment : Fragment(R.layout.fragment_setup) {
    @Inject
    lateinit var sharedPref: SharedPreferences

    @set:Inject
    var firstTimeAppOpen: Boolean = true

    @set:Inject
    var name: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!firstTimeAppOpen) {
            val toolbarText = String.format(getString(R.string.lets_go), name)
            requireActivity().tvToolbarTitle.text = toolbarText

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.setupFragment, true)
                .build()
            findNavController().navigate(
                R.id.action_setupFragment_to_runFragment,
                savedInstanceState,
                navOptions
            )
        }

        continueTextView.setOnClickListener {
            val success = writePersonalDataToSharedPref()
            if (success) {
                findNavController().navigate(R.id.action_setupFragment_to_runFragment)
            } else {
                Snackbar.make(
                    requireView(),
                    getString(R.string.please_enter_all_fields),
                    Snackbar.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    /**
     * Saves the name and the weight in shared preferences
     */
    private fun writePersonalDataToSharedPref(): Boolean {
        val name = ageInputEditText.text.toString()
        val weightText = weightInputEditText.text.toString()
        if (name.isEmpty() || weightText.isEmpty()) {
            return false
        }
        sharedPref.edit()
            .putString(KEY_NAME, name)
            .putFloat(KEY_WEIGHT, weightText.toFloat())
            .putBoolean(KEY_FIRST_TIME_TOGGLE, false)
            .apply()
        val toolbarText = String.format(getString(R.string.lets_go), name)
        requireActivity().tvToolbarTitle.text = toolbarText
        return true
    }
}