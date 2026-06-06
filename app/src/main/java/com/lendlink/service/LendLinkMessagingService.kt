package com.lendlink.service

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lendlink.worker.createChannel
import com.lendlink.worker.notify

class LendLinkMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        createChannel(this)
        val title = msg.notification?.title ?: msg.data["title"] ?: "LendLink"
        val body  = msg.notification?.body  ?: msg.data["body"]  ?: ""
        notify(this, System.currentTimeMillis().toInt(), title, body)
    }

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance().reference.child("users/$uid/fcmToken").setValue(token)
        }
    }
}
