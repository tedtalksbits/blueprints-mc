package dev.whizzle.blueprints.client.net;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Byte-for-byte the plugin's Wire (FriendlyByteBuf-compatible VarInt / Int / Bool / String). See docs/PROTOCOL.md. */
public final class Wire {
	private Wire() {}

	public static final class Writer {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();
		public Writer varInt(int value) {
			while ((value & ~0x7F) != 0) { out.write((value & 0x7F) | 0x80); value >>>= 7; }
			out.write(value);
			return this;
		}
		public Writer int32(int v) { out.write(v >>> 24); out.write(v >>> 16); out.write(v >>> 8); out.write(v); return this; }
		public Writer bool(boolean v) { out.write(v ? 1 : 0); return this; }
		public Writer utf(String s) { byte[] b = s.getBytes(StandardCharsets.UTF_8); varInt(b.length); out.write(b, 0, b.length); return this; }
		public Writer bytes(byte[] b) { varInt(b.length); out.write(b, 0, b.length); return this; }
		public byte[] bytes() { return out.toByteArray(); }
	}

	public static final class Reader {
		private final byte[] data;
		private int position;
		public Reader(byte[] data) { this.data = data; }
		private int next() {
			if (position >= data.length) throw new IllegalArgumentException("truncated message");
			return data[position++] & 0xFF;
		}
		public int varInt() {
			int value = 0;
			for (int shift = 0; shift < 35; shift += 7) {
				int b = next();
				value |= (b & 0x7F) << shift;
				if ((b & 0x80) == 0) return value;
			}
			throw new IllegalArgumentException("VarInt too long");
		}
		public int int32() { return (next() << 24) | (next() << 16) | (next() << 8) | next(); }
		public boolean bool() { return next() != 0; }
		public String utf() {
			int length = varInt();
			if (length < 0 || position + length > data.length) throw new IllegalArgumentException("bad string");
			String s = new String(data, position, length, StandardCharsets.UTF_8);
			position += length;
			return s;
		}
		/** Guards list sizes against corrupt counts before allocating. */
		public int count(int max) {
			int n = varInt();
			if (n < 0 || n > max) throw new IllegalArgumentException("bad count " + n);
			return n;
		}
	}
}
