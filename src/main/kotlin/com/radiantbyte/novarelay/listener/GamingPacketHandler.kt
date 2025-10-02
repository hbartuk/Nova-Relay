package com.radiantbyte.novarelay.listener

import com.radiantbyte.novarelay.NovaRelaySession
import com.radiantbyte.novarelay.definition.Definitions
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleNamedDefinition
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket
import org.cloudburstmc.protocol.common.NamedDefinition
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry

@Suppress("MemberVisibilityCanBePrivate")
class GamingPacketHandler(
    val novaRelaySession: NovaRelaySession
) : NovaRelayPacketListener {

    override fun beforeServerBound(packet: BedrockPacket): Boolean {
        if (packet is StartGamePacket) {
            println("Start game, setting definitions")
            Definitions.itemDefinitions = SimpleDefinitionRegistry.builder<ItemDefinition>()
                .addAll(packet.itemDefinitions)
                .build()

            novaRelaySession.client!!.peer.codecHelper.itemDefinitions = Definitions.itemDefinitions
            novaRelaySession.server.peer.codecHelper.itemDefinitions = Definitions.itemDefinitions

            if (packet.isBlockNetworkIdsHashed) {
                novaRelaySession.client!!.peer.codecHelper.blockDefinitions = Definitions.blockDefinitionsHashed
                novaRelaySession.server.peer.codecHelper.blockDefinitions = Definitions.blockDefinitionsHashed
            } else {
                novaRelaySession.client!!.peer.codecHelper.blockDefinitions = Definitions.blockDefinitions
                novaRelaySession.server.peer.codecHelper.blockDefinitions = Definitions.blockDefinitions
            }
        }
        if (packet is CameraPresetsPacket) {
            println("Camera presets")
            val cameraDefinitions =
                SimpleDefinitionRegistry.builder<NamedDefinition>()
                    .addAll(List(packet.presets.size) {
                        SimpleNamedDefinition(packet.presets[it].identifier, it)
                    })
                    .build()

            novaRelaySession.client!!.peer.codecHelper.cameraPresetDefinitions = cameraDefinitions
            novaRelaySession.server.peer.codecHelper.cameraPresetDefinitions = cameraDefinitions
        }
        return false
    }

}