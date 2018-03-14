/*
 * Copyright 2018 Nicholas Harris (ScarabCoder)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 *  to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *  and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 *  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 *   OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 *   OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.scarabcoder.input

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.ListeningWhitelist
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.events.PacketListener
import net.minecraft.server.v1_12_R1.*
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.*

object InputManager: PacketListener {

    var thisPlugin: Plugin? = null

    private val pData = HashMap<UUID, PlayerData>()

    fun attach(player: Player) {
        val ent = EntityArmorStand((player.world as CraftWorld).handle)
        val l = player.location
        ent.setPosition(l.x, l.y, l.z)
        ent.isInvisible = true
        ent.isMarker = true
        (player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutSpawnEntityLiving(ent))
        ent.passengers.add(player.handle)
        player.handle.playerConnection.sendPacket(PacketPlayOutMount(ent))
        pData.put(player.uniqueId, PlayerData(player.uniqueId, ent, InputType.getEmptyMap().toMutableMap()))
    }

    fun detach(player: Player) {
        if(!pData.contains(player.uniqueId)) return
        (player as CraftPlayer).handle.playerConnection.sendPacket(PacketPlayOutEntityDestroy(pData[player.uniqueId]!!.mount.id))
        pData.remove(player.uniqueId)
    }



    override fun onPacketSending(p0: PacketEvent?) {}

    override fun getSendingWhitelist(): ListeningWhitelist = ListeningWhitelist.EMPTY_WHITELIST

    override fun onPacketReceiving(packet: PacketEvent) {
        if(!pData.contains(packet.player.uniqueId)) return
        val pk = packet.packet.handle as PacketPlayInSteerVehicle
        val newMap = HashMap<InputType, Boolean>()
        val pd = pData[packet.player.uniqueId]!!
        newMap.put(InputType.A, pk.a() > 0)
        newMap.put(InputType.D, pk.a() < 0)
        newMap.put(InputType.W, pk.b() > 0)
        newMap.put(InputType.S, pk.b() < 0)
        newMap.put(InputType.SPACE, pk.c())
        newMap.put(InputType.SHIFT, pk.d())
        newMap.filter{ (key, isDown) -> pd.previousVals[key]!! != isDown}.forEach { key, isDown -> Bukkit.getPluginManager().callEvent(PlayerInputEvent(key, isDown, packet.player)) }
    }

    override fun getPlugin(): Plugin = thisPlugin!!

    override fun getReceivingWhitelist(): ListeningWhitelist {
        return ListeningWhitelist.newBuilder().types(PacketType.Play.Client.STEER_VEHICLE).build()
    }

    private data class PlayerData(val uuid: UUID, val mount: EntityArmorStand, val previousVals: MutableMap<InputType, Boolean>)

}