package net.azureaaron.hmapi.network;

import java.util.Map;
import java.util.stream.Collectors;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.Event;
import org.jetbrains.annotations.ApiStatus;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.c2s.HypixelC2SPacket;
import net.azureaaron.hmapi.network.packet.c2s.RegisterC2SPacket;
import net.azureaaron.hmapi.network.packet.s2c.HelloS2CPacket;
import net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.azureaaron.hmapi.network.packet.v1.s2c.PlayerInfoS2CPacket;
import net.azureaaron.hmapi.network.packet.v2.s2c.PartyInfoS2CPacket;
import net.azureaaron.hmapi.utils.PacketSendResult;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

@ApiStatus.Internal
public class HypixelNetworkingImpl {
	private static final long COOLDOWN = 1000L;
	private static final Object2LongMap<CustomPayload.Id<?>> COOLDOWNS = Object2LongMaps.synchronize(new Object2LongOpenHashMap<>());

	static <T extends HypixelC2SPacket> PacketSendResult sendPacket(T payload, boolean bypassCooldown) {
		if ((System.currentTimeMillis() + COOLDOWN > COOLDOWNS.computeIfAbsent(payload.getId(), _id -> 0L)) || bypassCooldown) {
			ClientPlayNetworking.send(payload);
			COOLDOWNS.put(payload.getId(), System.currentTimeMillis());

			return PacketSendResult.success();
		}

		return PacketSendResult.onCooldown(COOLDOWNS.getLong(payload.getId()) - System.currentTimeMillis());
	}

	private static void sendEventRegistrations() {
		if (!HypixelNetworking.REGISTERED_EVENTS.isEmpty()) {
			Object2IntMap<Identifier> packetsToRegisterFor = HypixelNetworking.REGISTERED_EVENTS.object2IntEntrySet().stream()
					.collect(Collectors.toMap(e -> e.getKey().id(), Object2IntMap.Entry::getIntValue, (a, b) -> a > b ? a : b, Object2IntOpenHashMap::new));

			sendPacket(new RegisterC2SPacket(1, packetsToRegisterFor), true);
		}
	}

	public static void bootstrap() {
		Map<CustomPayload.Id<HypixelS2CPacket>, Event<HypixelPacketEvents. PacketCallback>> packets = Map.of(
				PartyInfoS2CPacket.ID, HypixelPacketEvents.PARTY_INFO,
				PlayerInfoS2CPacket.ID, HypixelPacketEvents.PLAYER_INFO,
				HelloS2CPacket.ID, HypixelPacketEvents.HELLO,
				LocationUpdateS2CPacket.ID, HypixelPacketEvents.LOCATION_UPDATE
		);
		for (var entry : packets.entrySet()) {
			ClientPlayNetworking.registerGlobalReceiver(entry.getKey(), (payload, context) ->
					context.client().execute(() ->
							entry.getValue().invoker().onPacket(payload)));
		}

		// Send initial event registration
		HypixelPacketEvents.HELLO.register(p -> sendEventRegistrations());
	}
}
