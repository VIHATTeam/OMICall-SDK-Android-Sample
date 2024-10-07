package vn.vihat.omisample

import PrefManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vn.vihat.omicall.R
import vn.vihat.omicall.databinding.FragmentFirstBinding
import vn.vihat.omicall.omisdk.OmiClient
import vn.vihat.omicall.omisdk.const.PrefConstants
import vn.vihat.omicall.omisdk.utils.OmiSDKUtils
import vn.vihat.omisample.utils.AppUtils

class FirstFragment : Fragment() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private var isRegisteringWithUuid = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        val defaultSipRealm = ""
//        val defaultSipUser = ""
//        val defaultSipPassword = ""

        val defaultSipRealm = "quidn"
        val defaultSipUser = "100"
        val defaultSipPassword = "Duongngocqui@98"

//        val defaultSipRealm = "testtuanla2k1"
//        val defaultSipUser = "100"
//        val defaultSipPassword = "TestTuanLa2001"

        val defaultApiKey = ""
        val defaultUserName = ""
        val defaultUserPhone = ""
        val defaultSipUuid = ""

        binding.sipRealm.setText(defaultSipRealm)
        binding.sipUser.setText(defaultSipUser)
        binding.sipPassword.setText(defaultSipPassword)

        binding.apiKey.setText(defaultApiKey)
        binding.userName.setText(defaultUserName)
        binding.userPhone.setText(defaultUserPhone)
        binding.sipUuid.setText(defaultSipUuid)

        binding.btnRegister.setOnClickListener {

            if (isRegisteringWithUuid) {
                Toast.makeText(
                    requireContext(),
                    "Connecting to with UUID, please wait...",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val isVideoCall = true

            // sip user
            val sipRealm = binding.sipRealm.text.toString()
            val sipUser = binding.sipUser.text.toString()
            val sipPassword = binding.sipPassword.text.toString()
            val isEmptySipUser = arrayOf(
                sipRealm,
                sipUser,
                sipPassword,
            ).any { it.isEmpty() }

            // sip uuid
            val apiKey = binding.apiKey.text.toString()
            val userName = binding.userName.text.toString()
            val userPhone = binding.userPhone.text.toString()
            val sipUuid = binding.sipUuid.text.toString()
            val isEmptySipUuid = arrayOf(
                apiKey,
                userName,
                userPhone,
                sipUuid,
            ).any { it.isEmpty() }

            if (!isEmptySipUser || !isEmptySipUuid) {

                FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        return@OnCompleteListener
                    }
                    val firebaseToken = task.result ?: ""
                    coroutineScope.launch {
                        if (!isEmptySipUuid) {
                            isRegisteringWithUuid = true
                            val result = OmiClient.registerWithApiKey(
                                apiKey,
                                userName,
                                userPhone,
                                sipUuid,
                                isVideoCall,
                                firebaseToken,
                            )
                            isRegisteringWithUuid = false
                            if (result) {
                                AppUtils.setSession(requireContext(), true)
                                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)

                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Register failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                        } else {
                            val result = OmiClient.register(
                                sipUser,
                                sipPassword,
                                sipRealm,
                                isVideoCall,
                                firebaseToken,
                                projectId = "omi-test-39522"
                            )
                            if (result) {
                                AppUtils.setSession(requireContext(), true)
                                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
                            } else {
                                Toast.makeText(requireContext(), "Register failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                })

            } else {
                Toast.makeText(requireContext(), R.string.omi_sdk_empty, Toast.LENGTH_LONG).show()
            }

            val editor = PrefManager.getInstance(requireContext()).editor()
            editor.putBoolean(PrefConstants.CAN_SEE_PHONE_NUMBER, false)
            editor.apply()

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        OmiSDKUtils.handlePermissionRequest(
            requestCode,
            permissions,
            grantResults,
            requireActivity()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}