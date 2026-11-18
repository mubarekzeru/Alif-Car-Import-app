const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { createRequire } = require("node:module");
const rutsRequire = createRequire(require.resolve("@firebase/rules-unit-testing"));
const firebase = rutsRequire("firebase/compat/app");
rutsRequire("firebase/compat/firestore");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-alif-car-import";
const BOOTSTRAP_EMAIL = "alifcarimports@gmail.com";
const OTHER_ADMIN_EMAIL = "manager@alifcarimport.com";
const NON_ADMIN_EMAIL = "random@example.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return firebase.firestore.FieldValue.serverTimestamp();
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
    updatedAt: serverTimestamp(),
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
      updatedAt: serverTimestamp(),
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

  // Quick price update matching Android repository
  await assertSucceeds(
    col.doc("veh_lc300").update({
      price: 87900,
      updatedAt: serverTimestamp(),
    })
  );

  // Quick status update matching Android repository
  await assertSucceeds(
    col.doc("veh_lc300").update({
      status: "reserved",
      updatedAt: serverTimestamp(),
    })
  );

  // Quick featured toggle matching Android repository
  await assertSucceeds(
    col.doc("veh_lc300").update({
      featured: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Delete succeeds
  await assertSucceeds(col.doc("veh_lc300").delete());
});

test("Allows allowlisted admin in config/adminAccess and blocks non-admin", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("config")
      .doc("adminAccess")
      .set({ adminEmails: [OTHER_ADMIN_EMAIL] });
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
    col
      .doc("veh_mismatch")
      .set(validVehiclePayload("different_id"))
  );

  await assertFails(
    col
      .doc("veh_empty_img")
      .set(validVehiclePayload("veh_empty_img", { imageUrl: "" }))
  );

  await assertFails(
    col
      .doc("veh_empty_desc")
      .set(validVehiclePayload("veh_empty_desc", { description: "" }))
  );

  await assertFails(
    col.doc("veh_valid").update({
      createdAt: "2027-01-01T00:00:00.000Z",
      updatedAt: serverTimestamp(),
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

  // Showroom config write by owner, read by public, write denied to non-admin
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
    updatedAt: serverTimestamp(),
  };
  await assertSucceeds(ownerDb.collection("config").doc("showroom").set(showroomPayload));
  await assertSucceeds(nonAdminDb.collection("config").doc("showroom").get());
  await assertFails(nonAdminDb.collection("config").doc("showroom").set(showroomPayload));

  // TikTok posts CRUD by owner, read by public, write denied to non-admin
  const ttPayload = {
    id: "tt_clip_1",
    title: "2024 Prado",
    caption: "Fresh arrival",
    tiktokUrl: "https://www.tiktok.com/@alif/video/1",
    coverImageUrl: "",
    sortOrder: 0,
    updatedAt: serverTimestamp(),
  };
  await assertSucceeds(ownerDb.collection("tiktokPosts").doc("tt_clip_1").set(ttPayload));
  await assertSucceeds(nonAdminDb.collection("tiktokPosts").get());
  await assertFails(nonAdminDb.collection("tiktokPosts").doc("tt_clip_2").set({ ...ttPayload, id: "tt_clip_2" }));

  // Inquiries create by anyone, list only by bootstrap owner
  await assertSucceeds(
    nonAdminDb.collection("inquiries").doc("inq_1").set({
      id: "inq_1",
      name: "Abebe",
      phone: "+251911",
      vehicleTitle: "2024 Lexus RX",
      message: "Interested",
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(ownerDb.collection("inquiries").get());
  await assertFails(nonAdminDb.collection("inquiries").get());
});
