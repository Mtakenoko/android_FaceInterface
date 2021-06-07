package com.example.myapplication

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object UDPController {
    var ip = InetAddress.getByName("192.168.43.12")
    var port = 8000

    fun receive() : String {
        // udpを受信して、文字列にして返す
        val socket = DatagramSocket(port)

        val buffer = ByteArray(8192)
        val packet = DatagramPacket(buffer, buffer.size)

        socket.receive(packet)
        socket.close()

        return String(buffer)
    }

    fun send(msg: String) {
        // udpで文字列を送信する
        val socket = DatagramSocket(port)

        val byte = msg.toByteArray();
        val packet = DatagramPacket(byte, byte.size, ip, port);

        socket.send(packet);
        socket.close()
    }

    fun setip(address: String){
        ip = InetAddress.getByName(address)
    }
}