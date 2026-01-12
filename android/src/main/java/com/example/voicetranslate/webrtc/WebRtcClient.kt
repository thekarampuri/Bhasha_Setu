package com.example.voicetranslate.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

/**
 * WebRTC Client for audio-only calls
 * 
 * Responsibilities:
 * - Create and manage PeerConnection
 * - Handle local audio track (microphone)
 * - Handle remote audio track (peer's audio)
 * - Create/answer SDP offers
 * - Handle ICE candidates
 * - Manage connection lifecycle
 */
class WebRtcClient(
    private val context: Context,
    private val listener: WebRtcListener
) {
    private val tag = "WebRtcClient"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    
    // ICE servers (STUN for NAT traversal)
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )
    
    interface WebRtcListener {
        fun onLocalSdpCreated(sdp: SessionDescription)
        fun onIceCandidateGenerated(candidate: IceCandidate)
        fun onConnectionStateChanged(state: PeerConnection.IceConnectionState)
        fun onAudioTrackReceived()
        fun onError(error: String)
    }
    
    fun initialize() {
        Log.d(tag, "Initializing WebRTC...")
        
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        
        Log.d(tag, "✅ WebRTC initialized")
    }
    
    fun createPeerConnection() {
        Log.d(tag, "Creating PeerConnection...")
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            peerConnectionObserver
        )
        
        if (peerConnection == null) {
            listener.onError("Failed to create PeerConnection")
            return
        }
        
        addLocalAudioTrack()
        Log.d(tag, "✅ PeerConnection created")
    }
    
    private fun addLocalAudioTrack() {
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", audioSource)
        localAudioTrack?.setEnabled(true)
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))
    }
    
    fun createOffer() {
        Log.d(tag, "Creating SDP offer...")
        
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    Log.e(tag, "❌ Created SDP is null")
                    listener.onError("Failed to create offer: SDP is null")
                    return
                }
                
                Log.d(tag, "✅ Offer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(tag, "✅ Local description set (offer)")
                        listener.onLocalSdpCreated(sdp)
                    }
                    
                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "❌ Failed to set local description: $error")
                        listener.onError("Failed to set local description: $error")
                    }
                    
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "❌ Failed to create offer: $error")
                listener.onError("Failed to create offer: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }
    
    fun createAnswer() {
        Log.d(tag, "Creating SDP answer...")
        
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    Log.e(tag, "❌ Created SDP is null")
                    listener.onError("Failed to create answer: SDP is null")
                    return
                }
                
                Log.d(tag, "✅ Answer created successfully")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(tag, "✅ Local description set (answer)")
                        listener.onLocalSdpCreated(sdp)
                    }
                    
                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "❌ Failed to set local description: $error")
                        listener.onError("Failed to set local description: $error")
                    }
                    
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "❌ Failed to create answer: $error")
                listener.onError("Failed to create answer: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }
    
    fun setRemoteDescription(sdp: SessionDescription) {
        Log.d(tag, "Setting remote description (${sdp.type})...")
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(tag, "✅ Remote description set (${sdp.type})")
            }
            
            override fun onSetFailure(error: String?) {
                Log.e(tag, "❌ Failed to set remote description: $error")
                listener.onError("Failed to set remote description: $error")
            }
            
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }
    
    fun addIceCandidate(candidate: IceCandidate) {
        Log.d(tag, "Adding ICE candidate: ${candidate.sdpMid}")
        val success = peerConnection?.addIceCandidate(candidate) ?: false
        if (success) {
            Log.d(tag, "✅ ICE candidate added")
        } else {
            Log.w(tag, "⚠️ Failed to add ICE candidate")
        }
    }
    
    fun setMuted(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }
    
    fun close() {
        peerConnection?.close()
        peerConnectionFactory?.dispose()
    }
    
    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let { listener.onIceCandidateGenerated(it) }
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            state?.let { listener.onConnectionStateChanged(it) }
        }
        override fun onTrack(transceiver: RtpTransceiver?) {
            listener.onAudioTrackReceived()
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
    }
}
