# VoiceTranslate (Bhasha Setu)

## Project Description

**VoiceTranslate (Bhasha Setu)** is a real-time voice communication and translation project designed to bridge language barriers between users. The system is built using a **client–server architecture**, where an Android application communicates with a Python-based backend server.

At present, the project focuses on establishing a **real-time voice call between two mobile devices**. Advanced AI features such as Speech-to-Text, Language Translation, and Text-to-Speech are part of the project design but are currently **paused**.

---

## Project Architecture

The project follows a **Client–Server model**:

### 1. Android Application (Client)

* Developed using **Kotlin**
* Provides user interface for call interaction
* Captures microphone audio from the device
* Sends audio data to the backend server
* Receives audio data from another user and plays it in real time

### 2. Backend Server

* Developed using **Python** and **FastAPI**
* Uses **WebSockets** for real-time communication
* Acts as a relay server to forward audio data between two connected users
* Manages call sessions using a unique `call_id`

---

## Communication Mechanism

* Two users join the same call using a common `call_id`
* Each user establishes a WebSocket connection with the backend
* The backend forwards audio data received from one user to the other
* This enables real-time two-way voice communication

---

## Technologies Used

### Frontend (Android)

* Kotlin
* Android SDK
* AudioRecord & AudioTrack APIs
* OkHttp WebSocket
* Material UI Components

### Backend

* Python
* FastAPI
* Uvicorn
* WebSocket protocol

---

## Key Features

* Real-time voice call between two mobile devices
* Client–server based communication
* WebSocket-based low-latency audio streaming
* Modular design allowing future integration of AI models
* Supports dynamic backend IP configuration

---

## Future Enhancements

* Speech-to-Text using Whisper
* Language Translation using Helsinki-NLP models
* Text-to-Speech output
* Support for multiple languages
* Group calling support

---

## Project Status

* ✔ Real-time voice call implemented
* ✔ WebSocket communication established
* ❌ AI translation modules (paused)

---

## Author

**Akhil K**

---

## Note

This project is developed for academic and learning purposes.
