package dev.whizzle.blueprints.client;

import dev.whizzle.blueprints.client.net.Messages;
import dev.whizzle.blueprints.client.net.Net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sends a .litematic to the server: upload_begin, wait for upload_ready, then ordered chunks (each under the
 * game's 32 KB client-message limit), then upload_end. The server validates and imports; the client only transports.
 */
public final class Uploader {
	public static final int CHUNK = 24_000, CHUNKS_PER_TICK = 2;
	private static String id;
	private static byte[] data;
	private static int sent;
	private static boolean ready;

	private Uploader() {}

	public static boolean active() { return data != null; }
	public static String id() { return id; }
	public static int percent() { return data == null ? 0 : (int) (100L * sent / data.length); }

	public static void start(Path file, String name) {
		ClientState s = ClientState.INSTANCE;
		String target = Messages.toId(name);
		if (target.isEmpty()) { Actions.toast(false, "Give the blueprint a name with letters or digits."); return; }
		byte[] bytes;
		try { bytes = Files.readAllBytes(file); } catch (IOException e) { Actions.toast(false, "Couldn't read " + file.getFileName() + ": " + e.getMessage()); return; }
		if (s.welcome != null && bytes.length > s.welcome.maxUploadBytes()) {
			Actions.toast(false, file.getFileName() + " is " + (bytes.length >> 10) + " KB; this server accepts up to " + (s.welcome.maxUploadBytes() >> 10) + " KB.");
			return;
		}
		id = target; data = bytes; sent = 0; ready = false;
		Net.send(Messages.UPLOAD_BEGIN, Messages.uploadBegin(target, bytes.length));
	}

	/** Server's answer to upload_begin. */
	public static void onReady(boolean ok, String message) {
		if (data == null) return;
		if (!ok) { Actions.toast(false, message); cancel(); return; }
		ready = true;
	}

	/** Called every client tick: streams a few chunks, then finishes. */
	public static void tick() {
		if (data == null || !ready) return;
		for (int i = 0; i < CHUNKS_PER_TICK && sent < data.length; i++) {
			int length = Math.min(CHUNK, data.length - sent);
			byte[] part = java.util.Arrays.copyOfRange(data, sent, sent + length);
			Net.send(Messages.UPLOAD_CHUNK, Messages.uploadChunk(sent, part));
			sent += length;
		}
		if (sent >= data.length) { Net.send(Messages.UPLOAD_END, Messages.empty()); cancel(); }
	}

	public static void cancel() { data = null; id = null; sent = 0; ready = false; }
}
