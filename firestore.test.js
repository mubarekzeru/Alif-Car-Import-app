const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const BOOTSTRAP_EMAIL = "alifcarimports@gmail.com";
const OTHER_ADMIN_EMAIL = "manager@alifcarimport.com";
const NON_ADMIN_EMAIL = "random@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function futureTimestamp(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
}

function validVehiclePayload(vehicleId, overrides = {}) {
  return {
    id: vehicleId,
    title: "2024 Lexus RX 350 F Sport",
    make: "Lexus",
    model: "RX 350",
    year: 2024,
    price: 68500,
    mileage: "12,400 km",
    transmission: "Automatic",
    fuelType: "Petrol",
    color: "Pearl White",
    origin: "Japan",
    imageUrl: "https://example.com/rx350.jpg",
    description: "Clean import Lexus RX 350 F Sport with full inspection.",
    tiktokUrl: "",
    status: "available",
    featured: false,
    createdAt: "2026-11-17T10:00:00.000Z",
    updatedAt: pastTimestamp(),
    ...overrides,
  };
}

function validAdminSessionPayload(sessionId, uid, overrides = {}) {
  const nowMs = Date.now();
  const ts = pastTimestamp();
  return {
    id: sessionId,
    uid,
    deviceId: "dev_abc123456789",
    deviceName: "Samsung SM-S928B (Android 15)",
    platform: "Mobile App",
    email: BOOTSTRAP_EMAIL,
    location: "Addis Ababa, Ethiopia",
    loginAtMillis: nowMs - 5000,
    lastActiveMillis: nowMs,
    status: "active",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validLoginAlertPayload(alertId, sessionId, uid, overrides = {}) {
  const ts = pastTimestamp();
  return {
    id: alertId,
    sessionId,
    uid,
    email: BOOTSTRAP_EMAIL,
    deviceName: "Chrome on Windows",
    platform: "Website",
    location: "Addis Ababa, Ethiopia",
    timestampMillis: Date.now() - 2000,
    decision: "pending",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Public can list and get vehicles, but cannot create/update/delete", async () => {
  const bootstrapDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    bootstrapDb
      .collection("vehicles")
      .doc("veh_lexus_rx350_2024")
      .set(validVehiclePayload("veh_lexus_rx350_2024"))
  );

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("vehicles").get());
  await assertSucceeds(
    unauthDb.collection("vehicles").doc("veh_lexus_rx350_2024").get()
  );
  await assertFails(
    unauthDb
      .collection("vehicles")
      .doc("veh_unauth")
      .set(validVehiclePayload("veh_unauth"))
  );
  await assertFails(
    unauthDb.collection("vehicles").doc("veh_lexus_rx350_2024").update({
      price: 1,
      updatedAt: pastTimestamp(1000),
    })
  );
  await assertFails(
    unauthDb.collection("vehicles").doc("veh_lexus_rx350_2024").delete()
  );
});

test("Bootstrap owner can create, quick price/status/featured update, full edit, and delete", async () => {
  const ownerDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();
  const col = ownerDb.collection("vehicles");

  await assertSucceeds(
    col.doc("veh_lc300").set(
      validVehiclePayload("veh_lc300", {
        make: "Toyota",
        model: "Land Cruiser",
        title: "2024 Toyota Land Cruiser",
        originalPrice: 95000,
        price: 89500,
        galleryImages: ["https://example.com/lc1.jpg"],
      })
    )
  );

  await assertSucceeds(
    col.doc("veh_lc300").update({
      price: 87900,
      updatedAt: pastTimestamp(1000),
    })
  );

  await assertSucceeds(
    col.doc("veh_lc300").update({
      status: "reserved",
      updatedAt: pastTimestamp(1000),
    })
  );

  await assertSucceeds(
    col.doc("veh_lc300").update({
      featured: true,
      updatedAt: pastTimestamp(1000),
    })
  );

  await assertSucceeds(col.doc("veh_lc300").delete());
});

test("Allows allowlisted admin in config/adminAccess and blocks non-admin", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("config")
      .doc("adminAccess")
      .set({ adminEmails: [OTHER_ADMIN_EMAIL], updatedAt: pastTimestamp() });
  });

  const adminDb = testEnv
    .authenticatedContext("mgr_uid", {
      email: OTHER_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb
      .collection("vehicles")
      .doc("veh_mgr_1")
      .set(validVehiclePayload("veh_mgr_1"))
  );

  const nonAdminDb = testEnv
    .authenticatedContext("user_uid", {
      email: NON_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertFails(
    nonAdminDb
      .collection("vehicles")
      .doc("veh_user_1")
      .set(validVehiclePayload("veh_user_1"))
  );
});

test("Rejects mismatched id, empty imageUrl/description, or mutating createdAt", async () => {
  const ownerDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();
  const col = ownerDb.collection("vehicles");

  await assertSucceeds(
    col.doc("veh_valid").set(validVehiclePayload("veh_valid"))
  );

  await assertFails(
    col.doc("veh_mismatch").set(validVehiclePayload("different_id"))
  );

  await assertFails(
    col.doc("veh_empty_img").set(validVehiclePayload("veh_empty_img", { imageUrl: "" }))
  );

  await assertFails(
    col.doc("veh_empty_desc").set(validVehiclePayload("veh_empty_desc", { description: "" }))
  );

  await assertFails(
    col.doc("veh_valid").update({
      createdAt: "2027-01-01T00:00:00.000Z",
      updatedAt: pastTimestamp(1000),
    })
  );
});

test("Showroom config, TikTok posts, inquiries, and adminAccess enforce website rules", async () => {
  const ownerDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();
  const nonAdminDb = testEnv
    .authenticatedContext("user_uid", {
      email: NON_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  const showroomPayload = {
    branding: { companyName: "Alif Car Import", logoUrl: "", tagline: "Direct imports" },
    hero: { kicker: "NEW", title: "Direct Imports", description: "Clean cars", imageUrl: "", ctaLabel: "Browse" },
    tiktok: { title: "TikTok", description: "Clips", profileUrl: "", handle: "@alif" },
    contact: {
      addressLine: "Bole",
      cityCountry: "Addis Ababa",
      hours: "9-6",
      phone: "+251900",
      whatsapp: "+251900",
      email: "info@alif.com",
      mapsUrl: "",
      landmarkNote: "",
    },
    updatedAt: pastTimestamp(),
  };
  await assertSucceeds(ownerDb.collection("config").doc("showroom").set(showroomPayload));
  await assertSucceeds(nonAdminDb.collection("config").doc("showroom").get());
  await assertFails(nonAdminDb.collection("config").doc("showroom").set(showroomPayload));

  const ttPayload = {
    id: "tt_clip_1",
    title: "2024 Prado",
    caption: "Fresh arrival",
    tiktokUrl: "https://www.tiktok.com/@alif/video/1",
    coverImageUrl: "",
    sortOrder: 0,
    updatedAt: pastTimestamp(),
  };
  await assertSucceeds(ownerDb.collection("tiktokPosts").doc("tt_clip_1").set(ttPayload));
  await assertSucceeds(nonAdminDb.collection("tiktokPosts").get());
  await assertFails(
    nonAdminDb.collection("tiktokPosts").doc("tt_clip_2").set({ ...ttPayload, id: "tt_clip_2" })
  );

  await assertSucceeds(
    nonAdminDb.collection("inquiries").doc("inq_1").set({
      id: "inq_1",
      name: "Abebe",
      phone: "+251911",
      vehicleTitle: "2024 Lexus RX",
      message: "Interested",
      createdAt: pastTimestamp(),
    })
  );
  await assertSucceeds(ownerDb.collection("inquiries").get());
  await assertFails(nonAdminDb.collection("inquiries").get());
});

// --- MULTI-DEVICE ADMIN SESSIONS & LOGIN ALERTS TESTS ---

test("adminSessions: blocks unauthenticated, non-admin, and unverified email spoof", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const nonAdminDb = testEnv
    .authenticatedContext("user_uid", {
      email: NON_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: false,
    })
    .firestore();

  await assertFails(unauthDb.collection("adminSessions").get());
  await assertFails(
    unauthDb
      .collection("adminSessions")
      .doc("sess_unauth_1")
      .set(validAdminSessionPayload("sess_unauth_1", "unauth_uid"))
  );

  await assertFails(nonAdminDb.collection("adminSessions").get());
  await assertFails(
    nonAdminDb
      .collection("adminSessions")
      .doc("sess_user_1")
      .set(validAdminSessionPayload("sess_user_1", "user_uid", { email: NON_ADMIN_EMAIL }))
  );

  await assertFails(spoofDb.collection("adminSessions").get());
  await assertFails(
    spoofDb
      .collection("adminSessions")
      .doc("sess_spoof_1")
      .set(validAdminSessionPayload("sess_spoof_1", "spoof_uid"))
  );
});

test("adminSessions: multiple admins (mobile + website) sync sessions, refresh heartbeat, and revoke remotely", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("config")
      .doc("adminAccess")
      .set({ adminEmails: [OTHER_ADMIN_EMAIL], updatedAt: pastTimestamp() });
  });

  const ownerMobileDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();

  const otherAdminWebDb = testEnv
    .authenticatedContext("mgr_uid", {
      email: OTHER_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Owner signs in on Mobile App
  await assertSucceeds(
    ownerMobileDb
      .collection("adminSessions")
      .doc("sess_owner_phone1")
      .set(validAdminSessionPayload("sess_owner_phone1", "owner_uid"))
  );

  // Another admin signs in on Website by email
  await assertSucceeds(
    otherAdminWebDb
      .collection("adminSessions")
      .doc("sess_mgr_web1")
      .set(
        validAdminSessionPayload("sess_mgr_web1", "mgr_uid", {
          deviceId: "web_browser_99",
          deviceName: "Chrome on Windows",
          platform: "Website",
          email: OTHER_ADMIN_EMAIL,
          userAgent: "Mozilla/5.0 Chrome/128",
        })
      )
  );

  // Both admins can list all active sessions (mirrors SecuritySessionRepository snapshot listener)
  const ownerListSnap = await assertSucceeds(ownerMobileDb.collection("adminSessions").get());
  if (ownerListSnap.size !== 2) {
    throw new Error(`Expected 2 active sessions, found ${ownerListSnap.size}`);
  }

  // Owner updates own heartbeat & enriched IP location (Action 2)
  await assertSucceeds(
    ownerMobileDb.collection("adminSessions").doc("sess_owner_phone1").update({
      location: "Bole, Addis Ababa",
      lastActiveMillis: Date.now(),
      updatedAt: pastTimestamp(500),
    })
  );

  // Non-owner admin cannot spoof heartbeat/location of someone else's session
  await assertFails(
    otherAdminWebDb.collection("adminSessions").doc("sess_owner_phone1").update({
      location: "Fake City",
      updatedAt: pastTimestamp(500),
    })
  );

  // Owner can immediately revoke the other device's session (Action 1)
  await assertSucceeds(
    ownerMobileDb.collection("adminSessions").doc("sess_mgr_web1").update({
      status: "revoked",
      updatedAt: pastTimestamp(500),
    })
  );

  // Owner can re-activate own device session after signing back in
  await assertSucceeds(
    ownerMobileDb.collection("adminSessions").doc("sess_owner_phone1").update({
      status: "revoked",
      updatedAt: pastTimestamp(500),
    })
  );
  await assertSucceeds(
    ownerMobileDb.collection("adminSessions").doc("sess_owner_phone1").update({
      status: "active",
      loginAtMillis: Date.now(),
      lastActiveMillis: Date.now(),
      updatedAt: pastTimestamp(200),
    })
  );

  // Rejects delete, uid spoof, ghost field, future timestamp, and immutable field mutation
  await assertFails(ownerMobileDb.collection("adminSessions").doc("sess_mgr_web1").delete());
  await assertFails(
    ownerMobileDb
      .collection("adminSessions")
      .doc("sess_spoof_uid")
      .set(validAdminSessionPayload("sess_spoof_uid", "someone_else"))
  );
  await assertFails(
    ownerMobileDb
      .collection("adminSessions")
      .doc("sess_ghost")
      .set(validAdminSessionPayload("sess_ghost", "owner_uid", { isSuperAdmin: true }))
  );
  await assertFails(
    ownerMobileDb
      .collection("adminSessions")
      .doc("sess_future")
      .set(validAdminSessionPayload("sess_future", "owner_uid", { updatedAt: futureTimestamp() }))
  );
  await assertFails(
    ownerMobileDb.collection("adminSessions").doc("sess_owner_phone1").update({
      deviceId: "hijacked_device",
      updatedAt: pastTimestamp(200),
    })
  );
});

test("loginAlerts: admins receive and verify cross-device login alerts with strict state locking", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("config")
      .doc("adminAccess")
      .set({ adminEmails: [OTHER_ADMIN_EMAIL], updatedAt: pastTimestamp() });
  });

  const ownerDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();

  const mgrDb = testEnv
    .authenticatedContext("mgr_uid", {
      email: OTHER_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  const nonAdminDb = testEnv
    .authenticatedContext("user_uid", {
      email: NON_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  // Non-admin blocked from reading or writing loginAlerts
  await assertFails(nonAdminDb.collection("loginAlerts").get());
  await assertFails(
    nonAdminDb
      .collection("loginAlerts")
      .doc("alert_unauth")
      .set(validLoginAlertPayload("alert_unauth", "sess_1", "user_uid"))
  );

  // Manager logs in on website -> writes pending loginAlert
  await assertSucceeds(
    mgrDb
      .collection("loginAlerts")
      .doc("alert_web_001")
      .set(
        validLoginAlertPayload("alert_web_001", "sess_mgr_web1", "mgr_uid", {
          email: OTHER_ADMIN_EMAIL,
        })
      )
  );

  // Creator can enrich location on their own pending alert (Action 2)
  await assertSucceeds(
    mgrDb.collection("loginAlerts").doc("alert_web_001").update({
      location: "Addis Ababa, Ethiopia",
      updatedAt: pastTimestamp(500),
    })
  );

  // Owner lists loginAlerts on mobile app
  const alertsSnap = await assertSucceeds(ownerDb.collection("loginAlerts").get());
  if (alertsSnap.size !== 1) {
    throw new Error(`Expected 1 login alert, found ${alertsSnap.size}`);
  }

  // Owner rejects alert ("It wasn't me") -> transitions pending -> rejected_not_me (Action 1)
  await assertSucceeds(
    ownerDb.collection("loginAlerts").doc("alert_web_001").update({
      decision: "rejected_not_me",
      updatedAt: pastTimestamp(300),
    })
  );

  // Terminal state locking: once decided, alert cannot be flipped back or modified again
  await assertFails(
    mgrDb.collection("loginAlerts").doc("alert_web_001").update({
      decision: "confirmed_me",
      updatedAt: pastTimestamp(200),
    })
  );

  // Rejects creating pre-confirmed alert, spoofing uid, mutating email, or deleting alert
  await assertFails(
    ownerDb
      .collection("loginAlerts")
      .doc("alert_bad_state")
      .set(validLoginAlertPayload("alert_bad_state", "sess_1", "owner_uid", { decision: "confirmed_me" }))
  );
  await assertFails(
    ownerDb
      .collection("loginAlerts")
      .doc("alert_bad_uid")
      .set(validLoginAlertPayload("alert_bad_uid", "sess_1", "mgr_uid"))
  );
  await assertFails(ownerDb.collection("loginAlerts").doc("alert_web_001").delete());
});

test("config/security forceSignOutBefore immediately blocks pre-update/stale admin tokens across all collections", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("config")
      .doc("adminAccess")
      .set({ adminEmails: [OTHER_ADMIN_EMAIL], updatedAt: pastTimestamp(120000) });
  });

  const nowMs = Date.now();
  const staleAuthTimeSec = Math.floor((nowMs - 60000) / 1000); // Logged in 60s ago (old app/browser)
  const cutoffDate = new Date(nowMs - 10000); // Global Sign Out All triggered 10s ago
  const freshAuthTimeSec = Math.floor((nowMs - 2000) / 1000); // Freshly logged in 2s ago

  const ownerBeforeResetDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
      auth_time: staleAuthTimeSec,
    })
    .firestore();

  const staleOtherAdminDb = testEnv
    .authenticatedContext("mgr_uid", {
      email: OTHER_ADMIN_EMAIL,
      email_verified: true,
      auth_time: staleAuthTimeSec,
    })
    .firestore();

  const freshOwnerDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
      auth_time: freshAuthTimeSec,
    })
    .firestore();

  const nonAdminDb = testEnv
    .authenticatedContext("user_uid", {
      email: NON_ADMIN_EMAIL,
      email_verified: true,
      auth_time: freshAuthTimeSec,
    })
    .firestore();

  // Trigger global Sign Out All Devices (writes config/security.forceSignOutBefore)
  await assertSucceeds(
    ownerBeforeResetDb.collection("config").doc("security").set({
      forceSignOutBefore: cutoffDate,
      triggeredByEmail: BOOTSTRAP_EMAIL,
      updatedAt: cutoffDate,
    })
  );

  // Non-admin cannot read or write config/security
  await assertFails(nonAdminDb.collection("config").doc("security").get());
  await assertFails(
    nonAdminDb.collection("config").doc("security").set({
      forceSignOutBefore: pastTimestamp(1000),
      triggeredByEmail: NON_ADMIN_EMAIL,
      updatedAt: pastTimestamp(1000),
    })
  );

  // Stale/pre-update owner & other admin can read config/security (to auto-logout),
  // but are IMMEDIATELY BLOCKED from all admin reads & writes!
  await assertSucceeds(ownerBeforeResetDb.collection("config").doc("security").get());
  await assertSucceeds(staleOtherAdminDb.collection("config").doc("security").get());

  await assertFails(
    ownerBeforeResetDb
      .collection("vehicles")
      .doc("veh_stale_owner")
      .set(validVehiclePayload("veh_stale_owner"))
  );
  await assertFails(
    staleOtherAdminDb
      .collection("vehicles")
      .doc("veh_stale_mgr")
      .set(validVehiclePayload("veh_stale_mgr"))
  );
  await assertFails(ownerBeforeResetDb.collection("inquiries").get());
  await assertFails(ownerBeforeResetDb.collection("adminSessions").get());
  await assertFails(ownerBeforeResetDb.collection("loginAlerts").get());
  await assertFails(ownerBeforeResetDb.collection("config").doc("adminAccess").get());
  await assertFails(
    staleOtherAdminDb
      .collection("adminSessions")
      .doc("sess_stale_1")
      .set(validAdminSessionPayload("sess_stale_1", "mgr_uid", { email: OTHER_ADMIN_EMAIL }))
  );

  // Freshly signed-in admin (auth_time >= forceSignOutBefore) regains full access!
  await assertSucceeds(
    freshOwnerDb
      .collection("vehicles")
      .doc("veh_fresh_owner")
      .set(validVehiclePayload("veh_fresh_owner"))
  );
  await assertSucceeds(freshOwnerDb.collection("inquiries").get());
  await assertSucceeds(freshOwnerDb.collection("adminSessions").get());
  await assertSucceeds(
    freshOwnerDb
      .collection("adminSessions")
      .doc("sess_fresh_owner")
      .set(validAdminSessionPayload("sess_fresh_owner", "owner_uid"))
  );

  // Public customer showroom still works normally
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("vehicles").get());
});

test("config/authenticator: enforces admin-only access and strict Base32 TOTP schema", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const nonAdminDb = testEnv
    .authenticatedContext("user_uid", {
      email: NON_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
  const ownerDb = testEnv
    .authenticatedContext("owner_uid", {
      email: BOOTSTRAP_EMAIL,
      email_verified: true,
    })
    .firestore();

  const validEnablePayload = {
    enabled: true,
    secretBase32: "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP",
    accountLabel: BOOTSTRAP_EMAIL,
    issuer: "Alif Car Import",
    updatedByEmail: BOOTSTRAP_EMAIL,
    updatedAt: pastTimestamp(1000),
  };

  // Unauthenticated and non-admin cannot read or write config/authenticator
  await assertFails(unauthDb.collection("config").doc("authenticator").get());
  await assertFails(unauthDb.collection("config").doc("authenticator").set(validEnablePayload));
  await assertFails(nonAdminDb.collection("config").doc("authenticator").get());
  await assertFails(nonAdminDb.collection("config").doc("authenticator").set(validEnablePayload));

  // Invalid payloads rejected even for admin (enabled with bad secret, disabled with non-empty secret)
  await assertFails(
    ownerDb.collection("config").doc("authenticator").set({
      ...validEnablePayload,
      secretBase32: "invalid-lowercase-189",
    })
  );
  await assertFails(
    ownerDb.collection("config").doc("authenticator").set({
      ...validEnablePayload,
      enabled: false,
      secretBase32: "JBSWY3DPEHPK3PXP",
    })
  );

  // Verified admin can enable with valid Base32 secret, read it, and disable by clearing secret
  await assertSucceeds(ownerDb.collection("config").doc("authenticator").set(validEnablePayload));
  await assertSucceeds(ownerDb.collection("config").doc("authenticator").get());
  await assertSucceeds(
    ownerDb.collection("config").doc("authenticator").set({
      enabled: false,
      secretBase32: "",
      accountLabel: BOOTSTRAP_EMAIL,
      issuer: "Alif Car Import",
      updatedByEmail: BOOTSTRAP_EMAIL,
      updatedAt: pastTimestamp(500),
    })
  );
  await assertFails(ownerDb.collection("config").doc("authenticator").delete());
});

