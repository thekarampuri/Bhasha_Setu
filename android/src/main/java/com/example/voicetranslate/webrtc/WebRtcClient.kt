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
 * 
 * Features:
 * - Audio only (no video)
 * - Opus codec
 * - Echo cancellation
 * - Noise suppression
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
    
    /**
     * Listener interface for WebRTC events
     */
    interface WebRtcListener {
        fun onLocalSdpCreated(sdp: SessionDescription)
        fun onIceCandidateGenerated(candidate: IceCandidate)
        fun onConnectionStateChanged(state: PeerConnection.IceConnectionState)
        fun onAudioTrackReceived()
        fun onError(error: String)
    }
    
    /**
     * Initialize WebRTC
     * Must be called before any other methods
     */
    fun initialize() {
        Log.d(tag, "Initializing WebRTC...")
        
        // Initialize PeerConnectionFactory
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        // Create PeerConnectionFactory
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
        
        Log.d(tag, "✅ WebRTC initialized")
    }
    
    /**
     * Create PeerConnection and add local audio track
     */
    fun createPeerConnection() {
        Log.d(tag, "Creating PeerConnection...")
        
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            // Use unified plan (modern SDP format)
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Enable DTLS for encryption
            enableDtlsSrtp = true
            // Continuous gathering for better connectivity
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
        
        // Add local audio track
        addLocalAudioTrack()
        
        Log.d(tag, "✅ PeerConnection created")
    }
    
    /**
     * Add local audio track (microphone)
     */
    private fun addLocalAudioTrack() {
        Log.d(tag, "Adding local audio track...")
        
        // Audio constraints with echo cancellation and noise suppression
        val audioConstraints = MediaConstraints().apply {
            // Echo cancellation
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            
            // Noise suppression
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            
            // Auto gain control
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            
            // High-pass filter
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            
            // Audio mirroring (disable for calls)
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        }
        
        // Create audio source
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        
        // Create audio track
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", audioSource)
        localAudioTrack?.setEnabled(true)
        
        // Add track to peer connection
        peerConnection?.addTrack(localAudioTrack, listOf("local_stream"))
        
        Log.d(tag, "✅ Local audio track added")
    }
    
    /**
     * Create SDP offer
     * Call this when initiating a call
     */
    fun createOffer() {
        Log.d(tag, "Creating SDP offer...")
        
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    listener.onError("Failed to create offer: SDP is null")
                    return
                }
                
                Log.d(tag, "✅ Offer created")
                
                // Set local description
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(tag, "✅ Local description set")
                        listener.onLocalSdpCreated(sdp)
                    }
                    
                    override fun onSetFailure(error: String?) {
                        listener.onError("Failed to set local description: $error")
                    }
                    
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String?) {
                listener.onError("Failed to create offer: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }
    
    /**
     * Create SDP answer
     * Call this when receiving an offer
     */
    fun createAnswer() {
        Log.d(tag, "Creating SDP answer...")
        
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp == null) {
                    listener.onError("Failed to create answer: SDP is null")
                    return
                }
                
                Log.d(tag, "✅ Answer created")
                
                // Set local description
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(tag, "✅ Local description set")
                        listener.onLocalSdpCreated(sdp)
                    }
                    
                    override fun onSetFailure(error: String?) {
                        listener.onError("Failed to set local description: $error")
                    }
                    
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }
            
            override fun onCreateFailure(error: String?) {
                listener.onError("Failed to create answer: $error")
            }
            
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, sdpConstraints)
    }
    
    /**
     * Set remote SDP (offer or answer from peer)
     */
    fun setRemoteDescription(sdp: SessionDescription) {
        Log.d(tag, "Setting remote description: ${sdp.type}")
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(tag, "✅ Remote description set")
            }
            
            override fun onSetFailure(error: String?) {
                listener.onError("Failed to set remote description: $error")
            }
            
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }
    
    /**
     * Add ICE candidate received from peer
     */
    fun addIceCandidate(candidate: IceCandidate) {
        Log.d(tag, "Adding ICE candidate")
        peerConnection?.addIceCandidate(candidate)
    }
    
    /**
     * Mute/unmute local audio
     */
    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        Log.d(tag, if (muted) "🔇 Muted" else "🔊 Unmuted")
    }
    
    /**
     * Close connection and clean up resources
     */
    fun close() {
        Log.d(tag, "Closing WebRTC...")
        
        localAudioTrack?.dispose()
        localAudioTrack = null
        
        audioSource?.dispose()
        audioSource = null
        
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        
        Log.d(tag, "✅ WebRTC closed")
    }
    
    /**
     * PeerConnection observer for handling events
     */
    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) {
                Log.d(tag, "🧊 ICE candidate generated")
                listener.onIceCandidateGenerated(candidate)
            }
        }
        
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            state?.let {
                Log.d(tag, "🔌 ICE connection state: $it")
                listener.onConnectionStateChanged(it)
            }
        }
        
        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            if (track is AudioTrack) {
                Log.d(tag, "🎵 Remote audio track received")
                track.setEnabled(true)
                listener.onAudioTrackReceived()
            }
        }
        
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(tag, "Signaling state: $state")
        }
        
        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(tag, "ICE receiving: $receiving")
        }
        
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(tag, "ICE gathering state: $state")
        }
        
        override fun onAddStream(stream: MediaStream?) {
            // Not used in unified plan
        }
        
        override fun onRemoveStream(stream: MediaStream?) {
            // Not used in unified plan
        }
        
        override fun onDataChannel(channel: DataChannel?) {
            // Not used for audio-only
        }
        
        override fun onRenegotiationNeeded() {
            Log.d(tag, "Renegotiation needed")
        }
        
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            // Handled in onTrack
        }
    }
}
