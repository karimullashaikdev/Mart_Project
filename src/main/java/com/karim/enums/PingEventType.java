package com.karim.enums;

public enum PingEventType {

	// 🚀 Initial connection established
	CONNECT,

	// 📍 Regular location update from client
	LOCATION_UPDATE,

	// ❤️ Heartbeat to keep session alive
	HEARTBEAT,

	// ⛔ Client disconnected intentionally
	DISCONNECT,

	// ⚠️ Connection lost / timeout
	TIMEOUT,

	// 🔄 Session reconnected
	RECONNECT
}
