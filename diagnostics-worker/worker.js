// PABI Flip Companion development diagnostics. No caller names or notification text are accepted.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EVENTS = new Set([
  "diagnostics_enabled", "listener_connected", "listener_disconnected", "hinge_sensor",
  "notification_seen", "call_candidate", "call_cleared", "hinge_monitoring",
  "hinge_closed", "hinge_opened", "answer_trigger", "hangup_trigger",
  "answer_action", "answer_retry", "hangup_action", "hangup_accessibility",
]);
const SERVICES = new Set(["whatsapp", "snapchat", "instagram", "messenger", "google_meet", "discord"]);
const PHASES = new Set(["incoming", "ongoing", "outgoing"]);
const KINDS = new Set(["audio", "video", "unknown"]);
const RESULTS = new Set(["available", "missing", "started", "failed", "classified", "unclassified", "sent", "canceled", "clicked"]);
const ENVELOPE_KEYS = new Set(["installationId", "appVersion", "androidApi", "deviceModel", "events"]);
const EVENT_KEYS = new Set(["eventId", "clientTime", "event", "service", "phase", "kind", "angle", "result", "locked", "interactive"]);

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

async function limitedJson(request) {
  if (!request.headers.get("content-type")?.toLowerCase().startsWith("application/json")) throw new Error("content_type");
  const reader = request.body?.getReader();
  if (!reader) throw new Error("empty_body");
  const parts = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > 32768) { await reader.cancel(); throw new Error("body_too_large"); }
    parts.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const part of parts) { bytes.set(part, offset); offset += part.byteLength; }
  try { return JSON.parse(new TextDecoder().decode(bytes)); }
  catch { throw new Error("invalid_json"); }
}

function exactKeys(value, allowed) {
  return value && typeof value === "object" && !Array.isArray(value) &&
    Object.keys(value).length === allowed.size && Object.keys(value).every(key => allowed.has(key));
}

function optional(value, set) { return value === null || set.has(value); }
function validEvent(value) {
  return exactKeys(value, EVENT_KEYS) && UUID.test(value.eventId) &&
    Number.isSafeInteger(value.clientTime) &&
    value.clientTime > Date.now() - 31 * 86400000 && value.clientTime < Date.now() + 86400000 &&
    EVENTS.has(value.event) && optional(value.service, SERVICES) &&
    optional(value.phase, PHASES) && optional(value.kind, KINDS) &&
    (value.angle === null || Number.isInteger(value.angle) && value.angle >= 0 && value.angle <= 180) &&
    optional(value.result, RESULTS) && typeof value.locked === "boolean" &&
    typeof value.interactive === "boolean";
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    if (request.method === "GET" && pathname === "/health") return json({ ok: true });
    if (request.method === "POST" && pathname === "/v1/events") {
      let payload;
      try { payload = await limitedJson(request); }
      catch (error) { return json({ error: error.message }, 400); }
      if (!exactKeys(payload, ENVELOPE_KEYS) || !UUID.test(payload.installationId) ||
          typeof payload.appVersion !== "string" || payload.appVersion.length < 1 || payload.appVersion.length > 40 ||
          !Number.isInteger(payload.androidApi) || payload.androidApi < 30 || payload.androidApi > 99 ||
          typeof payload.deviceModel !== "string" || payload.deviceModel.length < 1 || payload.deviceModel.length > 80 ||
          !Array.isArray(payload.events) || payload.events.length < 1 || payload.events.length > 20 ||
          !payload.events.every(validEvent)) return json({ error: "invalid_payload" }, 400);

      const recent = await env.DB.prepare(
        "SELECT COUNT(*) AS count FROM diagnostic_events WHERE installation_id = ? AND received_at >= datetime('now', '-1 hour')"
      ).bind(payload.installationId).first();
      if ((recent?.count ?? 0) + payload.events.length > 300) return json({ error: "rate_limited" }, 429);

      await env.DB.batch(payload.events.map(item => env.DB.prepare(
        `INSERT OR IGNORE INTO diagnostic_events
         (event_id, installation_id, client_time_ms, app_version, android_api, device_model,
          service, event, phase, kind, angle, result, locked, interactive)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(item.eventId, payload.installationId, item.clientTime, payload.appVersion,
        payload.androidApi, payload.deviceModel, item.service, item.event, item.phase,
        item.kind, item.angle, item.result, Number(item.locked), Number(item.interactive))));
      return json({ accepted: payload.events.length }, 202);
    }
    if (request.method === "DELETE" && pathname === "/v1/installation") {
      let payload;
      try { payload = await limitedJson(request); }
      catch (error) { return json({ error: error.message }, 400); }
      if (!payload || Object.keys(payload).length !== 1 || !UUID.test(payload.installationId))
        return json({ error: "invalid_payload" }, 400);
      await env.DB.prepare("DELETE FROM diagnostic_events WHERE installation_id = ?")
        .bind(payload.installationId).run();
      return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
    }
    return json({ error: "not_found" }, 404);
  },

  async scheduled(_event, env, ctx) {
    ctx.waitUntil(env.DB.prepare(
      "DELETE FROM diagnostic_events WHERE received_at < datetime('now', '-30 days')"
    ).run());
  },
};
