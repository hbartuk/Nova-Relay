package com.radiantbyte.novarelay

import com.radiantbyte.novarelay.NovaRelaySession.ClientSession
import com.radiantbyte.novarelay.address.NovaAddress
import com.radiantbyte.novarelay.address.inetSocketAddress
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
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

class NovaRelay(
    val localAddress: NovaAddress = NovaAddress("0.0.0.0", 19132),
    val advertisement: BedrockPong = DefaultAdvertisement
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
            .subMotd("hello world")
            .nintendoLimited(false)

    }

    @Suppress("MemberVisibilityCanBePrivate")
    val isRunning: Boolean
        get() = channelFuture != null

    private var channelFuture: ChannelFuture? = null

    internal var novaRelaySession: NovaRelaySession? = null

    var remoteAddress: NovaAddress? = null
        internal set

    fun capture(
        remoteAddress: NovaAddress = NovaAddress("geo.hivebedrock.network", 19132),
        onSessionCreated: NovaRelaySession.() -> Unit
    ): NovaRelay {
        if (isRunning) {
            return this
        }

        this.remoteAddress = remoteAddress

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
                        .also {
                            novaRelaySession = it
                            it.onSessionCreated()
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
        val clientGUID = Random.nextLong()

        Bootstrap()
            .group(NioEventLoopGroup())
            .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
            .option(RakChannelOption.RAK_PROTOCOL_VERSION, 11)
            .option(RakChannelOption.RAK_GUID, clientGUID)
            .option(RakChannelOption.RAK_REMOTE_GUID, clientGUID)
            .option(RakChannelOption.RAK_CONNECT_TIMEOUT, Long.MAX_VALUE)
            .handler(object : BedrockChannelInitializer<ClientSession>() {

                override fun createSession0(peer: BedrockPeer, subClientId: Int): ClientSession {
                    return novaRelaySession!!.ClientSession(peer, subClientId)
                }

                override fun initSession(clientSession: ClientSession) {
                    novaRelaySession!!.client = clientSession
                    onSessionCreated(clientSession)
                }

                override fun preInitChannel(channel: Channel) {
                    channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.SERVER_BOUND)
                    super.preInitChannel(channel)
                }

            })
            .remoteAddress(remoteAddress!!.inetSocketAddress)
            .connect()
            .awaitUninterruptibly()
    }

}
