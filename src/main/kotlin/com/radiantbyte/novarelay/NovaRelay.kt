package com.radiantbyte.novarelay

import com.radiantbyte.novarelay.NovaRelaySession.ClientSession
import com.radiantbyte.novarelay.address.NovaAddress
import com.radiantbyte.novarelay.address.inetSocketAddress
import com.radiantbyte.novarelay.config.EnhancedServerConfig
import com.radiantbyte.novarelay.connection.ConnectionManager
import com.radiantbyte.novarelay.listener.OfflineLoginPacketListener
import com.radiantbyte.novarelay.listener.OnlineLoginPacketListener
import com.radiantbyte.novarelay.util.ServerCompatUtils
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.raphimc.minecraftauth.step.bedrock.session.StepFullBedrockSession
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter
import org.cloudburstmc.protocol.bedrock.BedrockPeer
import org.cloudburstmc.protocol.bedrock.BedrockPong
import org.cloudburstmc.protocol.bedrock.PacketDirection
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec
import org.cloudburstmc.protocol.bedrock.codec.v827.Bedrock_v827
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer
import kotlin.random.Random

/**
 * Определяет режим аутентификации для прокси.
 */
enum class LoginMode {
    /**
     * Использует реальную сессию Xbox Live для входа на защищенные серверы.
     */
    ONLINE,

    /**
     * Использует самоподписанные токены для входа на оффлайн-серверы.
     */
    OFFLINE
}

class NovaRelay(
    val localAddress: NovaAddress = NovaAddress("0.0.0.0", 19132),
    val advertisement: BedrockPong = DefaultAdvertisement,
    val serverConfig: EnhancedServerConfig = EnhancedServerConfig.DEFAULT
) {

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {

        val DefaultCodec: BedrockCodec = Bedrock_v827.CODEC

        val DefaultAdvertisement: BedrockPong = BedrockPong()
            .edition("MCPE")
            .gameType("Survival")
            .version(DefaultCodec.minecraftVersion)
            .protocolVersion(DefaultCodec.protocolVersion)
            .motd("NovaRelay")
            .playerCount(0)
            .maximumPlayerCount(20)
            .subMotd("Nova Relay")
            .nintendoLimited(false)

    }

    @Suppress("MemberVisibilityCanBePrivate")
    val isRunning: Boolean
        get() = channelFuture != null

    private var channelFuture: ChannelFuture? = null

    internal var novaRelaySession: NovaRelaySession? = null
    private var connectionManager: ConnectionManager? = null

    var remoteAddress: NovaAddress? = null
        internal set

    /**
     * Запускает прокси-сервер.
     * @param remoteAddress Адрес целевого сервера.
     * @param loginMode Режим аутентификации (ONLINE или OFFLINE).
     * @param fullBedrockSession Валидная сессия Xbox Live, обязательна для режима ONLINE.
     * @param onSessionCreated Лямбда, которая будет вызвана после создания сессии для добавления пользовательских слушателей.
     */
    fun capture(
        remoteAddress: NovaAddress = NovaAddress("geo.hivebedrock.network", 19132),
        loginMode: LoginMode,
        fullBedrockSession: StepFullBedrockSession.FullBedrockSession? = null,
        onSessionCreated: NovaRelaySession.() -> Unit
    ): NovaRelay {
        if (isRunning) {
            return this
        }

        this.remoteAddress = remoteAddress

        if (ServerCompatUtils.isProtectedServer(remoteAddress)) {
            println("Protected server detected: ${remoteAddress.hostName}")
            val tips = ServerCompatUtils.getConnectionTips(remoteAddress)
            tips.forEach { println("  - $it") }

            val serverInfo = ServerCompatUtils.extractServerInfo(remoteAddress.hostName)
            if (serverInfo != null) {
                println("  - Server ID: ${serverInfo.serverId}")
                println("  - Domain: ${serverInfo.domain}")
            }
        }

        advertisement
            .ipv4Port(localAddress.port)
            .ipv6Port(localAddress.port)

        ServerBootstrap()
            .group(NioEventLoopGroup())
            .channelFactory(RakChannelFactory.server(NioDatagramChannel::class.java))
            .option(RakChannelOption.RAK_ADVERTISEMENT, advertisement.toByteBuf())
            .option(RakChannelOption.RAK_GUID, Random.nextLong())
            .childHandler(object : BedrockChannelInitializer<NovaRelaySession.ServerSession>() {

                override fun createSession0(peer: BedrockPeer, subClientId: Int): NovaRelaySession.ServerSession {
                    return NovaRelaySession(peer, subClientId, this@NovaRelay)
                        .also { session ->
                            novaRelaySession = session
                            val config = if (ServerCompatUtils.isProtectedServer(remoteAddress)) {
                                ServerCompatUtils.getRecommendedConfig(remoteAddress)
                            } else {
                                serverConfig
                            }
                            connectionManager = ConnectionManager(session, config)

                            // В зависимости от режима, добавляем нужный слушатель
                            when (loginMode) {
                                LoginMode.ONLINE -> {
                                    require(fullBedrockSession != null) { "Online mode requires a valid Bedrock session!" }
                                    session.listeners.add(OnlineLoginPacketListener(session, fullBedrockSession))
                                }
                                LoginMode.OFFLINE -> {
                                    session.listeners.add(OfflineLoginPacketListener(session))
                                }
                            }

                            // Вызываем коллбэк, чтобы пользователь мог добавить свои слушатели
                            session.onSessionCreated()
                        }
                        .server
                }

                override fun initSession(session: NovaRelaySession.ServerSession) {}

                override fun preInitChannel(channel: Channel) {
                    channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.CLIENT_BOUND)
                    super.preInitChannel(channel)
                }

            })
            .localAddress(localAddress.inetSocketAddress)
            .bind()
            .awaitUninterruptibly()
            .also {
                it.channel().pipeline().remove(RakServerRateLimiter.NAME)
                channelFuture = it
            }

        return this
    }

    internal fun connectToServer(onSessionCreated: ClientSession.() -> Unit) {
        val manager = connectionManager ?: throw IllegalStateException("Connection manager not initialized")
        val address = remoteAddress ?: throw IllegalStateException("Remote address not set")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = manager.connectToServer(address, onSessionCreated)
                if (result.isFailure) {
                    println("Failed to connect to server: ${result.exceptionOrNull()?.message}")
                    novaRelaySession?.listeners?.forEach { listener ->
                        runCatching {
                            listener.onDisconnect("Connection failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error during connection: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun connectToServerAsync(onSessionCreated: ClientSession.() -> Unit): Result<ClientSession> {
        val manager = connectionManager ?: return Result.failure(IllegalStateException("Connection manager not initialized"))
        val address = remoteAddress ?: return Result.failure(IllegalStateException("Remote address not set"))

        return manager.connectToServer(address, onSessionCreated)
    }

    fun disconnect() {
        if (!isRunning) {
            return
        }

        channelFuture?.channel()?.also {
            it.close().awaitUninterruptibly()
            it.parent().close().awaitUninterruptibly()
        }
        channelFuture = null
        novaRelaySession = null
        connectionManager = null
    }
}
