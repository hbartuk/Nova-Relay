package com.radiantbyte.novarelay.listener

import com.radiantbyte.novarelay.NovaRelaySession
import com.radiantbyte.novarelay.util.AuthUtils
import com.radiantbyte.novarelay.util.refresh
import net.kyori.adventure.text.Component
import net.raphimc.minecraftauth.step.bedrock.session.StepFullBedrockSession
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils
import org.cloudburstmc.protocol.bedrock.util.JsonUtils
import org.jose4j.json.JsonUtil
import org.jose4j.json.internal.json_simple.JSONObject
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwx.HeaderParameterNames
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@Suppress("MemberVisibilityCanBePrivate")
class OnlineLoginPacketListener(
    val novaRelaySession: NovaRelaySession,
    private var fullBedrockSession: StepFullBedrockSession.FullBedrockSession
) : NovaRelayPacketListener {

    private var skinData: JSONObject? = null

    override fun beforeClientBound(packet: BedrockPacket): Boolean {
        if (packet is LoginPacket) {
            if (fullBedrockSession.isExpired) {
                println("Session expired, attempting to refresh tokens...")

                try {
                    fullBedrockSession = fullBedrockSession.refresh()
                    println("Successfully refreshed session for: ${fullBedrockSession.mcChain.displayName}")
                } catch (e: Exception) {
                    println("Failed to refresh session: ${e.message}")
                    novaRelaySession.server.disconnect("Your session has expired and could not be refreshed. Please re-login in the Nova Client.")
                    return true
                }
            }

            println("Handle online login data")

            val jws = JsonWebSignature()
            jws.compactSerialization = packet.clientJwt

            skinData = JSONObject(JsonUtil.parseJson(jws.unverifiedPayload))
            connectServer()
            return true
        }
        return false
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        if (packet is NetworkSettingsPacket) {
            val threshold = packet.compressionThreshold
            if (threshold > 0) {
                novaRelaySession.client!!.setCompression(packet.compressionAlgorithm)
                println("Compression threshold set to $threshold")
            } else {
                novaRelaySession.client!!.setCompression(PacketCompressionAlgorithm.NONE)
                println("Compression threshold set to 0")
            }

            try {
                val chain = AuthUtils.fetchOnlineChain(fullBedrockSession)
                val skinData =
                    AuthUtils.fetchOnlineSkinData(
                        fullBedrockSession,
                        skinData!!,
                        novaRelaySession.novaRelay.remoteAddress!!
                    )

                val loginPacket = LoginPacket()
                loginPacket.protocolVersion = novaRelaySession.server.codec.protocolVersion
                loginPacket.authPayload = CertificateChainPayload(chain, AuthType.FULL)
                loginPacket.clientJwt = skinData
                novaRelaySession.serverBoundImmediately(loginPacket)

                println("Login success")
            } catch (e: Throwable) {
                novaRelaySession.clientBound(DisconnectPacket().apply {
                    kickMessage = e.toString()
                })
                println("Login failed: $e")
            }

            return true
        }
        if (packet is ServerToClientHandshakePacket) {
            val jws = JsonWebSignature().apply {
                compactSerialization = packet.jwt
            }

            val saltJwt = JSONObject(JsonUtil.parseJson(jws.unverifiedPayload))
            val x5u = jws.getHeader(HeaderParameterNames.X509_URL)
            val serverKey = EncryptionUtils.parseKey(x5u)
            val key = EncryptionUtils.getSecretKey(
                fullBedrockSession.mcChain.privateKey, serverKey,
                Base64.decode(JsonUtils.childAsType(saltJwt, "salt", String::class.java))
            )
            novaRelaySession.client!!.enableEncryption(key)
            println("Encryption enabled")

            novaRelaySession.serverBoundImmediately(ClientToServerHandshakePacket())
            return true
        }
        return false
    }

    private fun connectServer() {
        novaRelaySession.novaRelay.connectToServer {
            println("Connected to server")

            val packet = RequestNetworkSettingsPacket()
            packet.protocolVersion = novaRelaySession.server.codec.protocolVersion
            novaRelaySession.serverBoundImmediately(packet)
        }
    }

}