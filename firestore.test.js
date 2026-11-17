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
