package com.radiantbyte.novarelay.listener

import com.radiantbyte.novarelay.NovaRelaySession
import com.radiantbyte.novarelay.util.AuthUtils
import net.kyori.adventure.text.Component
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils
import org.cloudburstmc.protocol.bedrock.util.JsonUtils
import org.jose4j.json.JsonUtil
import org.jose4j.json.internal.json_simple.JSONObject
import org.jose4j.jws.JsonWebSignature
import java.security.KeyPair

class OfflineLoginPacketListener(
    private val novaRelaySession: NovaRelaySession,
    private val keyPair: KeyPair = AuthUtils.DefaultKeyPair // Используем ключ из AuthUtils
) : NovaRelayPacketListener {

    private var chain: List<String>? = null
    private var extraData: JSONObject? = null
    private var skinData: JSONObject? = null

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        if (packet is LoginPacket) {
            // В новых версиях протокола цепочка находится в authPayload
            val chainPayload = packet.authPayload as CertificateChainPayload
            chain = chainPayload.chain

            extraData = JSONObject(
                JsonUtils.childAsType(
                    EncryptionUtils.validateChain(chain).rawIdentityClaims(),
                    "extraData",
                    Map::class.java
                )
            )

            val jws = JsonWebSignature()
            jws.compactSerialization = packet.clientJwt
            skinData = JSONObject(JsonUtil.parseJson(jws.unverifiedPayload))

            println("Handle offline login data")
            connectServer()
            return true // Блокируем оригинальный пакет
        }
        return false
    }

    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        if (packet is NetworkSettingsPacket) {
            val threshold = packet.compressionThreshold
            if (threshold > 0) {
                novaRelaySession.client!!.setCompression(packet.compressionAlgorithm)
                println("Client compression enabled: ${packet.compressionAlgorithm}")
            } else {
                novaRelaySession.client!!.setCompression(PacketCompressionAlgorithm.NONE)
                println("Client compression disabled")
            }

            try {
                val newChain = AuthUtils.fetchOfflineChain(keyPair, extraData!!, chain!!)
                val newSkinData = AuthUtils.fetchOfflineSkinData(keyPair, skinData!!)

                val loginPacket = LoginPacket().apply {
                    protocolVersion = novaRelaySession.server.codec.protocolVersion
                    authPayload = CertificateChainPayload(newChain, AuthType.FULL)
                    clientJwt = newSkinData
                }
                novaRelaySession.serverBoundImmediately(loginPacket)
                println("Offline login success")
            } catch (e: Throwable) {
                novaRelaySession.clientBound(DisconnectPacket().apply {
                    kickMessage = Component.text("Offline login failed: ${e.message}")
                })
                println("Offline login failed: $e")
                e.printStackTrace()
            }
            return true
        }
        // Блокируем ServerToClientHandshake, так как при оффлайн-входе шифрование не нужно
        if (packet is ServerToClientHandshakePacket) {
            novaRelaySession.serverBoundImmediately(ClientToServerHandshakePacket())
            return true
        }
        return false
    }

    private fun connectServer() {
        novaRelaySession.novaRelay.connectToServer {
            println("Connected to server for offline login")
            val packet = RequestNetworkSettingsPacket().apply {
                protocolVersion = novaRelaySession.server.codec.protocolVersion
            }
            novaRelaySession.serverBoundImmediately(packet)
        }
    }
}
