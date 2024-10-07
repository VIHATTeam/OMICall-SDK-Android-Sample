package vn.vihat.omisample

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.vihat.omicall.R
import vn.vihat.omicall.databinding.FragmentSecondBinding
import vn.vihat.omicall.omisdk.OmiClient
import vn.vihat.omicall.omisdk.utils.OmiSipTransport
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
            if (event.action == KeyEvent.ACTION_UP) handleMakeCall()
            true
        }

        binding.btnCall.setOnClickListener {
            handleMakeCall()
        }

        binding.btnLogout.setOnClickListener {
            mainScope.launch {
                withContext(Dispatchers.Default) {
                    AppUtils.setSession(appContext, false)
                    OmiClient.getInstance(appContext).logout()
                }
            }
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
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


    private fun handleMakeCall() {
        val isVideo = binding.switchIsVideo.isChecked
        val intent = Intent(context, CallingActivity::class.java)
        intent.putExtra(SipServiceConstants.PARAM_NUMBER, "${binding.txtPhone.text}")
        intent.putExtra(SipServiceConstants.PARAM_IS_VIDEO, isVideo)
        startActivity(intent)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}