package com.radiantbyte.novarelay.examples

import com.radiantbyte.novarelay.LoginMode // Импортируем LoginMode
import com.radiantbyte.novarelay.NovaRelay
import com.radiantbyte.novarelay.address.NovaAddress
import com.radiantbyte.novarelay.config.EnhancedServerConfig
import com.radiantbyte.novarelay.listener.NovaRelayPacketListener
import com.radiantbyte.novarelay.util.ServerCompatUtils
import com.radiantbyte.novarelay.util.authorize // Импортируем authorize
import kotlinx.coroutines.runBlocking
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket

object EnhancedServerExample {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("Nova Relay - Enhanced Server Support Example")
        println("============================================")

        val protectedServer = NovaAddress("play.lbsg.net", 19132)

        if (ServerCompatUtils.isProtectedServer(protectedServer)) {
            println("✓ Protected server detected: ${protectedServer.hostName}")

            val tips = ServerCompatUtils.getConnectionTips(protectedServer)
            tips.forEach { tip ->
                println("  💡 $tip")
            }

            val serverInfo = ServerCompatUtils.extractServerInfo(protectedServer.hostName)
            if (serverInfo != null) {
                println("  📋 Server ID: ${serverInfo.serverId}")
                println("  🌐 Domain: ${serverInfo.domain}")
                println("  🔢 Numeric ID: ${serverInfo.isNumericId}")
            }
        }
        
        println()

        val relay = NovaRelay(
            localAddress = NovaAddress("0.0.0.0", 19132),
            serverConfig = EnhancedServerConfig.DEFAULT
        )
        
        println("🚀 Starting Nova Relay...")

        // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
        // Получаем сессию и передаем ее вместе с LoginMode.ONLINE
        val session = authorize()
        relay.capture(
            remoteAddress = protectedServer,
            loginMode = LoginMode.ONLINE,
            fullBedrockSession = session
        ) {
            println("📡 Nova Relay session created")

            listeners.add(object : NovaRelayPacketListener {
                override fun onDisconnect(reason: String) {
                    println("❌ Disconnected: $reason")
                }
                
                override fun beforeClientBound(packet: BedrockPacket): Boolean {
                    return false
                }
                
                override fun beforeServerBound(packet: BedrockPacket): Boolean {
                    return false
                }
            })
            
            println("🔗 Attempting to connect to protected server...")

            runBlocking {
                try {
                    val result = novaRelay.connectToServerAsync {
                        println("✅ Successfully connected to protected server!")
                        println("🎮 You can now connect your Minecraft client to localhost:19132")
                        println("📊 All traffic will be proxied through Nova Relay")
                    }

                    if (result.isFailure) {
                        println("❌ Failed to connect: ${result.exceptionOrNull()?.message}")
                        println("💡 Try the following:")
                        println("   - Make sure the server is online")
                        println("   - Check the server address and port")
                        println("   - Wait a few minutes and try again (DDoS protection)")
                    }
                } catch (e: Exception) {
                    println("❌ Connection error: ${e.message}")
                }
            }
        }
        
        println("🎯 Nova Relay is running on localhost:19132")
        println("📱 Connect your Minecraft Bedrock client to localhost:19132")
        println("🔄 Traffic will be relayed to ${protectedServer.hostName}:${protectedServer.port}")
        println()
        println("Press Ctrl+C to stop the relay")

        try {
            Thread.currentThread().join()
        } catch (e: InterruptedException) {
            println("🛑 Nova Relay stopped")
        }
    }

    fun demonstrateConfigurations() {
        println("Configuration Examples")
        println("=============================")

        val fastRelay = NovaRelay(serverConfig = EnhancedServerConfig.FAST)
        println("⚡ Fast config - for stable servers")
        println("   Max retries: ${EnhancedServerConfig.FAST.maxRetryAttempts}")
        println("   Initial delay: ${EnhancedServerConfig.FAST.initialRetryDelay}ms")

        val defaultRelay = NovaRelay(serverConfig = EnhancedServerConfig.DEFAULT)
        println("🔧 Default config - for most servers")
        println("   Max retries: ${EnhancedServerConfig.DEFAULT.maxRetryAttempts}")
        println("   Initial delay: ${EnhancedServerConfig.DEFAULT.initialRetryDelay}ms")

        val aggressiveRelay = NovaRelay(serverConfig = EnhancedServerConfig.AGGRESSIVE)
        println("🔥 Aggressive config - for problematic servers")
        println("   Max retries: ${EnhancedServerConfig.AGGRESSIVE.maxRetryAttempts}")
        println("   Initial delay: ${EnhancedServerConfig.AGGRESSIVE.initialRetryDelay}ms")
    }

    fun testServerConnectivity(hostname: String, port: Int) = runBlocking {
        val server = NovaAddress(hostname, port)
        
        println("Testing connectivity to $hostname:$port")
        
        if (ServerCompatUtils.isProtectedServer(server)) {
            println("✓ Protected server detected")
            val config = ServerCompatUtils.getRecommendedConfig(server)
            println("📋 Recommended config: ${config.maxRetryAttempts} retries, ${config.initialRetryDelay}ms delay")
        } else {
            println("ℹ️ Regular Minecraft server")
        }
        
        val relay = NovaRelay()
        // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
        // Используем OFFLINE, так как для простого теста подключения не нужна аутентификация
        relay.capture(
            remoteAddress = server,
            loginMode = LoginMode.OFFLINE
        ) {
            runBlocking {
                try {
                    val result = relay.connectToServerAsync {
                        println("✅ Connection successful!")
                    }

                    if (result.isSuccess) {
                        println("🎉 Server is reachable")
                    } else {
                        println("❌ Connection failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    println("❌ Test failed: ${e.message}")
                }
            }
        }
    }
}
