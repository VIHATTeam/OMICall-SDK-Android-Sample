package vn.vihat.omisample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.vihat.omicall.R
import vn.vihat.omicall.databinding.FragmentSecondBinding
import vn.vihat.omicall.omisdk.OmiClient
import vn.vihat.omicall.omisdk.utils.OmiSipTransport
import vn.vihat.omicall.omisdk.utils.OmiStartCallStatus
import vn.vihat.omicall.omisdk.utils.SipServiceConstants
import vn.vihat.omisample.utils.AppUtils


class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    private val binding get() = _binding!!
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val appContext by lazy { requireContext().applicationContext }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val curSipRealm = "Current sip_realm: " + OmiClient.getInstance(appContext).getSipRealm()
        val curSipUser = "Current sip_user: " + OmiClient.getInstance(appContext).getSipUser()

        binding.sipRealm.text = curSipRealm
        binding.sipUser.text = curSipUser

        binding.txtPhone.setOnEditorActionListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_UP) {
                lifecycleScope.launch {
                    handleMakeCall()
                }
            }
            true
        }

        binding.btnCall.setOnClickListener {
            lifecycleScope.launch {
                handleMakeCall()
            }
        }

        binding.btnLogout.setOnClickListener {
            handleLogout()
        }

        binding.auto.isChecked =
            OmiClient.getInstance(appContext).getSipTransport() == OmiSipTransport.AUTO
        binding.tcp.isChecked =
            OmiClient.getInstance(appContext).getSipTransport() == OmiSipTransport.TCP
        binding.udp.isChecked =
            OmiClient.getInstance(appContext).getSipTransport() == OmiSipTransport.UDP
        binding.auto.setOnCheckedChangeListener(listenerRadio)
        binding.tcp.setOnCheckedChangeListener(listenerRadio)
        binding.udp.setOnCheckedChangeListener(listenerRadio)
    }

    private var listenerRadio =
        CompoundButton.OnCheckedChangeListener { compoundButton, isChecked ->
            if (isChecked) {

                val transport = when (compoundButton.text) {
                    "Auto select" -> OmiSipTransport.AUTO
                    "Use TCP" -> OmiSipTransport.TCP
                    "Use UDP" -> OmiSipTransport.UDP
                    else -> null
                }
                OmiClient.getInstance(appContext).updateSipTransport(
                    transport
                )
            }
        }


    private fun handleLogout() {
        binding.btnLogout.isEnabled = false
        binding.btnLogout.text = "Logging out..."

        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                AppUtils.setSession(appContext, false)
                OmiClient.getInstance(appContext).logout(onCompleted = {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d("SecondFragment", "logout -> onCompleted fired after ${elapsed}ms")

                    activity?.runOnUiThread {
                        binding.btnLogout.isEnabled = true
                        binding.btnLogout.text = "Đăng xuất"
                        Toast.makeText(context, "Logout completed in ${elapsed}ms", Toast.LENGTH_LONG).show()
                        findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
                    }
                })
            } catch (e: Exception) {
                Log.e("SecondFragment", "logout -> Error: ${e.message}", e)
                activity?.runOnUiThread {
                    binding.btnLogout.isEnabled = true
                    binding.btnLogout.text = "Đăng xuất"
                    Toast.makeText(context, "Logout error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun handleMakeCall() {
        val isVideo = binding.switchIsVideo.isChecked
        val result = OmiClient.getInstance(appContext).startCall(
            "${binding.txtPhone.text}",
            isVideo = isVideo,
            name = "",
            avatar = ""
        )
        when (result) {
            OmiStartCallStatus.SUCCESS, OmiStartCallStatus.SWITCHBOARD_REGISTERING -> {
                val intent = Intent(context, CallingActivity::class.java)
                intent.putExtra(SipServiceConstants.PARAM_NUMBER, "${binding.txtPhone.text}")
                intent.putExtra(SipServiceConstants.PARAM_IS_VIDEO, isVideo)
                startActivity(intent)
            }
            OmiStartCallStatus.NO_NETWORK -> {
                Toast.makeText(context, "Không có kết nối mạng. Vui lòng kiểm tra lại.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(context, "Start call error: ${result.name} (${result.value})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
