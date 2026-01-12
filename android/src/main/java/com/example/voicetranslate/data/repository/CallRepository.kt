package com.example.voicetranslate.data.repository

import android.content.Context
import android.util.Log
import com.example.voicetranslate.data.model.*
import com.example.voicetranslate.webrtc.SignalingClient
import com.example.voicetranslate.webrtc.WebRtcClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * Repository coordinating SignalingClient and WebRtcClient
 * 
 * Responsibilities:
 * - Manage call lifecycle
 * - Coordinate signaling and WebRTC
 * - Handle offer/answer exchange
 * - Handle ICE candidate exchange
 * - Manage call state
 */
class CallRepository(
    private val context: Context,
    private val userRepository: UserRepository
) {
    private val tag = "CallRepository"
    
    private var webRtcClient: WebRtcClient? = null
    private var signalingClient: SignalingClient? = null
    
    private val _callState = MutableStateFlow<CallState>(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState
    
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted
    
    private var currentCallId: String = ""
    private var currentUserId: String = ""
    private var isInitiator: Boolean = false
    
    /**
     * Start outgoing call
     */
    suspend fun startCall(serverUrl: String, callId: String) {
        Log.d(tag, "Starting call: $callId")
        
        currentCallId = callId
        isInitiator = true
        
        val user = userRepository.getUser()
        currentUserId = user.userId
        
        // Initialize WebRTC
        webRtcClient = WebRtcClient(context, webRtcListener)
        webRtcClient?.initialize()
        webRtcClient?.createPeerConnection()
        
        // Connect to signaling server
        signalingClient = SignalingClient(serverUrl, signalingListener)
        signalingClient?.connect(callId, user.userId)
        
        _callState.value = CallState.CALLING
    }
    
    /**
     * SignalingClient listener
     */
    private val signalingListener = object : SignalingClient.SignalingListener {
        override fun onConnected() {
            Log.d(tag, "✅ Connected to signaling server")
        }
        
        override fun onPeerJoined(peerId: String?) {
            Log.d(tag, "👤 Peer joined: $peerId")
            
            if (peerId != null && isInitiator) {
                // We're the caller - create offer
                Log.d(tag, "Creating offer (we're the caller)")
                _callState.value = CallState.CONNECTING
                webRtcClient?.createOffer()
            } else if (peerId != null) {
                // We're the callee - wait for offer
                Log.d(tag, "Waiting for offer (we're the callee)")
                _callState.value = CallState.RINGING
            }
        }
        
        override fun onOfferReceived(sdp: String) {
            Log.d(tag, "📞 Offer received")
            
            // Set remote description (offer)
            val sessionDescription = SessionDescription(
                SessionDescription.Type.OFFER,
                sdp
            )
            webRtcClient?.setRemoteDescription(sessionDescription)
            
            // Create answer
            _callState.value = CallState.CONNECTING
            webRtcClient?.createAnswer()
        }
        
        override fun onAnswerReceived(sdp: String) {
            Log.d(tag, "✅ Answer received")
            
            // Set remote description (answer)
            val sessionDescription = SessionDescription(
                SessionDescription.Type.ANSWER,
                sdp
            )
            webRtcClient?.setRemoteDescription(sessionDescription)
        }
        
        override fun onIceCandidateReceived(candidate: com.example.voicetranslate.data.model.IceCandidate) {
            Log.d(tag, "🧊 ICE candidate received")
            
            // Add ICE candidate to peer connection
            val iceCandidate = IceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.candidate
            )
            webRtcClient?.addIceCandidate(iceCandidate)
        }
        
        override fun onPeerLeft(peerId: String) {
            Log.d(tag, "👋 Peer left: $peerId")
            endCall()
        }
        
        override fun onDisconnected() {
            Log.d(tag, "❌ Disconnected from signaling server")
            if (_callState.value != CallState.ENDED) {
                endCall()
            }
        }
        
        override fun onError(error: String) {
            Log.e(tag, "⚠️ Signaling error: $error")
            endCall()
        }
    }
    
    /**
     * WebRtcClient listener
     */
    private val webRtcListener = object : WebRtcClient.WebRtcListener {
        override fun onLocalSdpCreated(sdp: SessionDescription) {
            val sdpString = sdp.description
            val type = sdp.type.canonicalForm()
            
            Log.d(tag, "📤 Local SDP created: $type")
            
            // Send SDP to peer via signaling
            if (type == "offer") {
                signalingClient?.sendOffer(currentCallId, sdpString)
            } else if (type == "answer") {
                signalingClient?.sendAnswer(currentCallId, sdpString)
            }
        }
        
        override fun onIceCandidateGenerated(candidate: IceCandidate) {
            Log.d(tag, "📤 ICE candidate generated")
            
            // Send ICE candidate to peer via signaling
            val iceCandidate = com.example.voicetranslate.data.model.IceCandidate(
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
            signalingClient?.sendIceCandidate(currentCallId, iceCandidate)
        }
        
        override fun onConnectionStateChanged(state: PeerConnection.IceConnectionState) {
            Log.d(tag, "🔌 Connection state: $state")
            
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    Log.d(tag, "✅ Call connected!")
                    _callState.value = CallState.CONNECTED
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.d(tag, "❌ Call disconnected")
                    endCall()
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.e(tag, "❌ Connection failed")
                    endCall()
                }
                PeerConnection.IceConnectionState.CHECKING -> {
                    Log.d(tag, "🔍 Checking connectivity...")
                }
                else -> {
                    Log.d(tag, "State: $state")
                }
            }
        }
        
        override fun onAudioTrackReceived() {
            Log.d(tag, "🎵 Remote audio track received")
            // Audio will play automatically
        }
        
        override fun onError(error: String) {
            Log.e(tag, "⚠️ WebRTC error: $error")
            endCall()
        }
    }
    
    /**
     * Toggle mute
     */
    fun toggleMute() {
        val newMutedState = !_isMuted.value
        _isMuted.value = newMutedState
        webRtcClient?.setMuted(newMutedState)
        Log.d(tag, if (newMutedState) "🔇 Muted" else "🔊 Unmuted")
    }
    
    /**
     * End call
     */
    fun endCall() {
        Log.d(tag, "Ending call...")
        
        webRtcClient?.close()
        webRtcClient = null
        
        signalingClient?.disconnect()
        signalingClient = null
        
        _callState.value = CallState.ENDED
        _isMuted.value = false
        
        Log.d(tag, "✅ Call ended")
    }
}
