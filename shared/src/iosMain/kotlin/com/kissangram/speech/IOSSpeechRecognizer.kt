package com.kissangram.speech

// This is a stub implementation - actual implementation is in Swift
// The Swift wrapper handles AVAudioEngine setup natively
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
public class IOSSpeechRecognizer : com.kissangram.speech.SpeechRecognizer {
    
    public constructor() {
        // Default constructor for Swift
        // Actual implementation is in IOSSpeechRecognizerWrapper.swift
    }
    
    override fun isAvailable(): Boolean {
        // This will be called from Swift wrapper
        return false
    }
    
    override fun hasPermission(): Boolean {
        // This will be called from Swift wrapper
        return false
    }
    
    override suspend fun requestPermission(): Boolean {
        // This will be called from Swift wrapper
        return false
    }
    
    override suspend fun startListening(): String {
        // This will be called from Swift wrapper
        throw Exception("Use IOSSpeechRecognizerWrapper in Swift instead")
    }
    
    override suspend fun stopListening() {
        // This will be called from Swift wrapper
    }
}
