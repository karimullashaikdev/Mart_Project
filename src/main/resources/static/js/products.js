// ── Auth guard ──
(function guardUser() {
    const token = localStorage.getItem("authToken");
    if (!token) { window.location.href = "login.html"; return; }
    const role = (localStorage.getItem("role") || "").toUpperCase();
    if (role === "ADMIN") { window.location.href = "admin.html"; return; }
    if (role === "DELIVERY") { window.location.href = "delivery-dashboard.html"; return; }
})();

// ── Auto token refresh ──
let _refreshing = null;

async function refreshAccessToken() {
    if (_refreshing) return _refreshing;
    _refreshing = (async () => {
        const refresh = localStorage.getItem("refreshToken");
        if (!refresh) throw new Error("No refresh token");
        const res = await fetch("/api/auth/refresh-token", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: refresh })
        });
        if (!res.ok) throw new Error("Refresh failed");
        const json = await res.json();
        const newAccess  = json.data?.accessToken  || json.accessToken;
        const newRefresh = json.data?.refreshToken || json.refreshToken;
        if (!newAccess) throw new Error("No access token");
        localStorage.setItem("authToken", newAccess);
        if (newRefresh) localStorage.setItem("refreshToken", newRefresh);
        return newAccess;
    })();
    try { return await _refreshing; } finally { _refreshing = null; }
}

// ── User APIs ──
const USER_API = {
    products: {
        list:   (params) => `/api/products?${params.toString()}`,
        byId:   (productId) => `/api/products/${encodeURIComponent(productId)}`,
        bySlug: (slug)      => `/api/products/slug/${encodeURIComponent(slug)}`
    },
    categories: {
        tree:       "/api/categories",
        byIdOrSlug: (idOrSlug) => `/api/categories/${encodeURIComponent(idOrSlug)}`
    },
    cart: {
        get:         "/api/v1/cart",
        addItem:     "/api/v1/cart/items",
        updateItem:  (itemId) => `/api/v1/cart/items/${encodeURIComponent(itemId)}`,
        removeItem:  (itemId) => `/api/v1/cart/items/${encodeURIComponent(itemId)}`,
        applyCoupon: "/api/v1/cart/coupon",
        removeCoupon:"/api/v1/cart/coupon"
    },
    users: {
        me:          "/api/users/me",
        profile:     "/api/users/me/profile",
        myAddresses: "/api/users/me/addresses",
        // FIX: these endpoints now exist after UserController.java update
        updateAddress: (id) => `/api/users/me/addresses/${encodeURIComponent(id)}`,
        deleteAddress: (id) => `/api/users/me/addresses/${encodeURIComponent(id)}`
    },
    orders: {
        place:    "/api/user/orders",
        list:     "/api/user/orders",
        byId:     (orderId)      => `/api/user/orders/${encodeURIComponent(orderId)}`,
        byNumber: (orderNumber)  => `/api/user/orders/number/${encodeURIComponent(orderNumber)}`,
        items:    (orderId)      => `/api/user/orders/${encodeURIComponent(orderId)}/items`,
        cancel:   (orderId)      => `/api/user/orders/${encodeURIComponent(orderId)}/cancel`
    },
    payments: {
        initiate:        "/api/payments/initiate",
        verifyRazorpay:  "/api/payments/verify-razorpay",
        byOrder:         (orderId) => `/api/payments/order/${encodeURIComponent(orderId)}`
    },
    stocks: {
        byProduct: (productId) => `/api/user/stocks/${encodeURIComponent(productId)}`
    }
};

// ── State ──
let currentPage       = 0;
let totalPages        = 0;
let totalElements     = 0;
let pageSize          = 20;
let currentCategoryId = "";
let searchQuery       = "";
let searchTimer       = null;
let cartItemCount     = 0;
let userData          = {};
let profileExists     = false;
let currentCart       = null;
let myOrders          = [];
let myOrdersFetched   = false;
let myOrderItemsCache = {};
let cartStockCache    = {};
let cartQtyLoading    = {};
let productStockCache = {};
let returnToCartAfterAddressSave = false;
let categoryMap       = {};
let allFlatCategories = []; // FIX: cache for searchable dropdown

// ── Map state ──
let addrMap            = null;
let addrMarker         = null;
let addrMapInitialized = false;

// ── API helper ──
function getUserId() {
    return localStorage.getItem("userId") || localStorage.getItem("id") ||
           localStorage.getItem("actorId") || "";
}

async function apiFetch(url, options = {}) {
    let token  = localStorage.getItem("authToken");
    const userId = getUserId();
    options.headers = { ...(options.headers || {}), Authorization: `Bearer ${token}` };
    if (userId) options.headers["X-User-Id"] = userId;

    let res = await fetch(url, options);
    if (res.status === 401) {
        try {
            token = await refreshAccessToken();
            options.headers.Authorization = `Bearer ${token}`;
            res = await fetch(url, options);
        } catch (err) { logout(); throw err; }
    }
    return res;
}

// ══════════════════════════════════════════════════════
// MAP PICKER
// ══════════════════════════════════════════════════════

function initAddressMap() {
    if (addrMapInitialized) return;
    addrMapInitialized = true;
    addrMap = L.map("addrMapContainer", { center: [17.3850, 78.4867], zoom: 13, zoomControl: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19
    }).addTo(addrMap);
    const greenIcon = makeGreenIcon();
    addrMap.on("click", function(e) {
        placeMapMarker(e.latlng.lat, e.latlng.lng, greenIcon);
        reverseGeocodeAndFill(e.latlng.lat, e.latlng.lng);
    });
    setTimeout(() => addrMap.invalidateSize(), 320);
}

function makeGreenIcon() {
    return L.divIcon({
        className: "",
        html: `<div style="width:28px;height:28px;background:#2d6a4f;border-radius:50% 50% 50% 0;transform:rotate(-45deg);border:3px solid white;box-shadow:0 4px 16px rgba(0,0,0,0.3);"></div>`,
        iconSize: [28, 28], iconAnchor: [14, 28], popupAnchor: [0, -30]
    });
}

function placeMapMarker(lat, lng, icon) {
    if (!icon) icon = makeGreenIcon();
    if (addrMarker) {
        addrMarker.setLatLng([lat, lng]);
    } else {
        addrMarker = L.marker([lat, lng], { icon, draggable: true }).addTo(addrMap);
        addrMarker.on("dragend", function(e) {
            const pos = e.target.getLatLng();
            setAddrCoords(pos.lat, pos.lng);
            reverseGeocodeAndFill(pos.lat, pos.lng);
        });
    }
    setAddrCoords(lat, lng);
    addrMap.setView([lat, lng], Math.max(addrMap.getZoom(), 15));
}

/**
 * Updates coordinate inputs and UI display chips
 */
function setAddrCoords(lat, lng, targetPrefix = "addr") {
    const latVal = parseFloat(lat).toFixed(6);
    const lngVal = parseFloat(lng).toFixed(6);
    
    const setEl = (id, v) => { 
        const el = document.getElementById(id); 
        if (el) {
            // Check if it's an input/select/textarea or a regular div/span
            const isInput = ["INPUT", "TEXTAREA", "SELECT"].includes(el.tagName);
            el[isInput ? "value" : "textContent"] = v; 
        }
    };

    // Update hidden inputs for form submission
    setEl(`${targetPrefix}-latitude`,  latVal);
    setEl(`${targetPrefix}-longitude`, lngVal);
    
    // Update visual display chips
    setEl(`${targetPrefix}LatDisplay`, latVal);
    setEl(`${targetPrefix}LngDisplay`, lngVal);

    // Add CSS classes for visual feedback
    document.getElementById("latChip")?.classList.add("has-value");
    document.getElementById("lngChip")?.classList.add("has-value");
}

/**
 * Performs reverse geocoding and fills address form fields
 */
async function reverseGeocodeAndFill(lat, lng, targetPrefix = "addr") {
    // 1. Immediately update the coordinate values and UI chips
    setAddrCoords(lat, lng, targetPrefix);

    const preview = document.getElementById("mapAddressPreview");
    if (preview) preview.textContent = "Fetching address…";

    try {
        const res = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&addressdetails=1`, 
            { headers: { "Accept-Language": "en" } }
        );
        
        if (!res.ok) throw new Error("Geocode failed");
        
        const data = await res.json();
        const addr = data.address || {};
        const displayName = data.display_name || "";

        // Update preview text
        if (preview) {
            preview.textContent = displayName 
                ? displayName.split(",").slice(0, 3).join(", ") 
                : `${parseFloat(lat).toFixed(5)}, ${parseFloat(lng).toFixed(5)}`;
        }

        // Logical field mapping
        const line1 = [addr.house_number, addr.road || addr.pedestrian || addr.footway].filter(Boolean).join(", ");
        const line2 = addr.suburb || addr.neighbourhood || addr.quarter || "";
        const city  = addr.city || addr.town || addr.village || addr.county || addr.municipality || "";
        const state = addr.state || "";
        const postcode = addr.postcode || "";

        const setField = (id, value) => { 
            const el = document.getElementById(`${targetPrefix}-${id}`); 
            // Only update if element exists and value is present to avoid overwriting manual edits with nulls
            if (el && value) el.value = value; 
        };

        // Populate fields
        setField("line1",   line1 || addr.road || addr.suburb);
        setField("line2",   line2);
        setField("city",    city);
        setField("state",   state);
        setField("pincode", postcode);

    } catch (err) {
        console.error("Reverse Geocode Error:", err);
        if (preview) {
            preview.textContent = `${parseFloat(lat).toFixed(5)}, ${parseFloat(lng).toFixed(5)}`;
        }
    }
}

function useMyLocation() {
    const btn = document.getElementById("useMyLocationBtn");
    if (!btn) return;
    if (!navigator.geolocation) { showToast("❌ Geolocation not supported", true); return; }
    btn.disabled = true;
    btn.innerHTML = `<span class="map-loc-spinner"></span> Locating…`;
    navigator.geolocation.getCurrentPosition(
        function(pos) {
            initAddressMap();
            placeMapMarker(pos.coords.latitude, pos.coords.longitude);
            reverseGeocodeAndFill(pos.coords.latitude, pos.coords.longitude);
            btn.disabled = false;
            btn.innerHTML = `🎯 Use My Location`;
            showToast("📍 Location set");
        },
        function(err) {
            btn.disabled = false;
            btn.innerHTML = `🎯 Use My Location`;
            const msgs = { 1: "Location access denied.", 2: "Location unavailable.", 3: "Request timed out." };
            showToast(`❌ ${msgs[err.code] || "Failed to get location"}`, true);
        },
        { timeout: 10000, maximumAge: 30000, enableHighAccuracy: true }
    );
}

// ── Label Preset Helpers ──
function selectLabelPreset(value, btn) {
    document.querySelectorAll(".label-presets .label-preset-btn").forEach(b => { if (b.closest("#tab-addresses")) b.classList.remove("selected"); });
    btn.classList.add("selected");
    const input = document.getElementById("addr-label");
    if (input) input.value = value;
}

function selectEditLabelPreset(value, btn) {
    document.querySelectorAll("#editLabelPresets .label-preset-btn").forEach(b => b.classList.remove("selected"));
    btn.classList.add("selected");
    const input = document.getElementById("edit-addr-label");
    if (input) input.value = value;
}

// ── Drawer helpers ──
function openDrawer() {
    document.getElementById("profileDrawer")?.classList.add("open");
    document.getElementById("drawerOverlay")?.classList.add("open");
    document.body.style.overflow = "hidden";
    loadAccountData();
}

function openDrawerToTab(name = "account", fromCart = false) {
    openDrawer();
    const tabBtn = [...document.querySelectorAll(".dtab")].find(btn => (btn.textContent || "").trim().toLowerCase() === name.toLowerCase());
    if (tabBtn) switchTab(name, tabBtn);
    returnToCartAfterAddressSave = !!fromCart;
}

function closeDrawer() {
    document.getElementById("profileDrawer")?.classList.remove("open");
    document.getElementById("drawerOverlay")?.classList.remove("open");
    document.body.style.overflow = "";
}

function switchTab(name, btn) {
    document.querySelectorAll(".dtab").forEach(tab => tab.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach(panel => panel.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(`tab-${name}`)?.classList.add("active");
    if (name === "profile") loadProfile();
    if (name === "addresses") {
        loadAddresses();
        setTimeout(() => { initAddressMap(); if (addrMap) addrMap.invalidateSize(); }, 100);
    }
}

// ── User/account ──
function getInitials(name) {
    if (!name) return "👤";
    return name.trim().split(/\s+/).map(p => p[0]).join("").toUpperCase().slice(0, 2);
}

function setAvatarUrl(url) {
    const initials = getInitials(userData.fullName || userData.name || "");
    const big   = document.getElementById("bigAvatar");
    const small = document.getElementById("profileAvatarInitials");
    if (url) {
        if (big)   big.innerHTML   = `<img src="${url}" alt="avatar" />`;
        if (small) small.innerHTML = `<img src="${url}" alt="avatar" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />`;
    } else {
        if (big)   big.textContent   = initials;
        if (small) small.textContent = initials;
    }
}

async function loadAccountData() {
    try {
        const res = await apiFetch(USER_API.users.me);
        if (!res.ok) return;
        const json = await res.json();
        userData = json.data || json || {};
        const fullName = userData.fullName || userData.name || "";
        const email    = userData.email || "";
        const phone    = userData.phone || "";
        document.getElementById("acc-name").value             = fullName;
        document.getElementById("acc-email").value            = email;
        document.getElementById("acc-email-display").textContent = email || "—";
        document.getElementById("acc-phone").value            = phone;
        document.getElementById("heroName").textContent       = fullName || "My Account";
        document.getElementById("heroEmail").textContent      = email || "";
        setAvatarUrl(userData.avatarUrl || "");
    } catch (err) { console.warn("Failed to load account data", err); }
}

async function saveAccount() {
    const btn = document.getElementById("acc-save-btn");
    btn.disabled = true;
    try {
        const payload = {
            fullName: document.getElementById("acc-name").value.trim(),
            phone:    document.getElementById("acc-phone").value.trim()
        };
        const res = await apiFetch(USER_API.users.me, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
        if (!res.ok) throw new Error("Failed to update account");
        showToast("✓ Account updated successfully");
        loadAccountData();
    } catch (err) { showToast("❌ Failed to update account", true); }
    finally { btn.disabled = false; }
}

async function loadProfile() {
    try {
        const res = await apiFetch(USER_API.users.profile);
        if (res.status === 404) { profileExists = false; return; }
        if (!res.ok) return;
        const json    = await res.json();
        const profile = json.data || json || {};
        profileExists = true;
        document.getElementById("prf-dob").value    = profile.dateOfBirth ? profile.dateOfBirth.split("T")[0] : "";
        document.getElementById("prf-gender").value = profile.gender || "";
        document.getElementById("prf-avatar").value = profile.avatarUrl || "";
        if (profile.avatarUrl) setAvatarUrl(profile.avatarUrl);
    } catch (err) { console.warn("Failed to load profile", err); }
}

async function saveProfile() {
    try {
        const payload = {
            dateOfBirth: document.getElementById("prf-dob").value || null,
            gender:      document.getElementById("prf-gender").value || null,
            avatarUrl:   document.getElementById("prf-avatar").value || null
        };
        const res = await apiFetch(USER_API.users.profile, {
            method: profileExists ? "PATCH" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error("Failed to save profile");
        profileExists = true;
        showToast("✓ Profile saved");
    } catch (err) { showToast("❌ Failed to save profile", true); }
}

// ══════════════════════════════════════════════════════
// ADDRESSES — Fixed: edit & delete now use correct endpoints
// ══════════════════════════════════════════════════════

async function loadAddresses() {
    const list            = document.getElementById("addressList");
    const checkoutSelect  = document.getElementById("checkoutAddressSelect");
    if (checkoutSelect) checkoutSelect.innerHTML = `<option value="">Loading addresses...</option>`;
    if (list) list.innerHTML = `<div class="address-card">Loading addresses...</div>`;
    try {
        const res = await apiFetch(USER_API.users.myAddresses);
        if (!res.ok) throw new Error("Failed to load addresses");
        const json      = await res.json();
        const addresses = json.data || [];
        if (list) {
            list.innerHTML = addresses.length
                ? addresses.map(a => renderSavedAddressCard(a)).join("")
                : `<div class="address-card" style="text-align:center;color:var(--muted);padding:24px;">No saved addresses yet.<br>Add one above!</div>`;
        }
        if (checkoutSelect) {
            const options = addresses.map(a => `<option value="${a.id}" ${a.isDefault ? "selected" : ""}>${a.label || "Address"} — ${a.line1 || ""}, ${a.city || ""}</option>`).join("");
            checkoutSelect.innerHTML = `<option value="">Select delivery address</option>${options}<option value="__ADD_NEW_ADDRESS__">+ Add New Address</option>`;
        }
    } catch (err) {
        if (list) list.innerHTML = `<div class="address-card">Failed to load addresses.</div>`;
        if (checkoutSelect) checkoutSelect.innerHTML = `<option value="">Failed to load addresses</option><option value="__ADD_NEW_ADDRESS__">+ Add New Address</option>`;
    }
}

function renderSavedAddressCard(a) {
    const labelEmoji = getLabelEmoji(a.label || "");
    const hasCoords  = a.latitude != null && a.longitude != null;
    const coordsHtml = hasCoords
        ? `<span class="saved-addr-coords">📍 ${parseFloat(a.latitude).toFixed(5)}, ${parseFloat(a.longitude).toFixed(5)}</span>`
        : `<span class="saved-addr-no-coords">⚠ No coordinates</span>`;
    return `
        <div class="saved-address-card ${a.isDefault ? "is-default" : ""}" id="addr-card-${a.id}">
            <div class="saved-addr-top">
                <div class="saved-addr-label-row">
                    <div class="saved-addr-label-badge">${labelEmoji} ${a.label || "Address"}</div>
                    ${a.isDefault ? `<div class="saved-addr-default-badge">✓ Default</div>` : ""}
                </div>
                <div class="saved-addr-actions">
                    <button class="addr-action-btn" onclick="openEditAddrModal('${a.id}')">✏️ Edit</button>
                    <button class="addr-action-btn delete" onclick="deleteAddress('${a.id}')">🗑 Delete</button>
                </div>
            </div>
            <div class="saved-addr-text">
                ${[a.line1, a.line2, a.city, a.state].filter(Boolean).join(", ")}${a.pincode ? " — " + a.pincode : ""}
            </div>
            <div class="saved-addr-meta">
                ${a.phone    ? `<span class="saved-addr-meta-item">📞 ${a.phone}</span>` : ""}
                ${a.landmark ? `<span class="saved-addr-meta-item">🏛 ${a.landmark}</span>` : ""}
                ${coordsHtml}
            </div>
        </div>`;
}

function getLabelEmoji(label) {
    const lower = label.toLowerCase();
    if (lower.includes("home")) return "🏠";
    if (lower.includes("work") || lower.includes("office")) return "💼";
    return "📦";
}

function buildAddressPayload() {
    const latRaw = document.getElementById("addr-latitude")?.value || "";
    const lngRaw = document.getElementById("addr-longitude")?.value || "";
    return {
        label:     document.getElementById("addr-label")?.value?.trim()   || "",
        phone:     document.getElementById("addr-phone")?.value?.trim()   || "",
        line1:     document.getElementById("addr-line1")?.value?.trim()   || "",
        line2:     document.getElementById("addr-line2")?.value?.trim()   || "",
        city:      document.getElementById("addr-city")?.value?.trim()    || "",
        state:     document.getElementById("addr-state")?.value?.trim()   || "",
        pincode:   document.getElementById("addr-pincode")?.value?.trim() || "",
        landmark:  document.getElementById("addr-landmark")?.value?.trim()|| "",
        isDefault: !!document.getElementById("addr-isDefault")?.checked,
        latitude:  latRaw ? parseFloat(latRaw) : null,
        longitude: lngRaw ? parseFloat(lngRaw) : null
    };
}

function resetAddressForm() {
    ["addr-label","addr-phone","addr-line1","addr-line2","addr-city","addr-state","addr-pincode","addr-landmark","addr-latitude","addr-longitude"].forEach(id => {
        const el = document.getElementById(id); if (el) el.value = "";
    });
    const defaultEl = document.getElementById("addr-isDefault");
    if (defaultEl) defaultEl.checked = false;
    document.querySelectorAll("#tab-addresses .label-preset-btn").forEach(b => b.classList.remove("selected"));
    const msg = document.getElementById("addr-msg");
    if (msg) { msg.className = "inline-msg"; msg.textContent = ""; }
    const setEl = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
    setEl("addrLatDisplay", "—"); setEl("addrLngDisplay", "—");
    document.getElementById("latChip")?.classList.remove("has-value");
    document.getElementById("lngChip")?.classList.remove("has-value");
    const preview = document.getElementById("mapAddressPreview");
    if (preview) preview.textContent = `Click on map or use "My Location" to pick coordinates`;
    if (addrMarker && addrMap) { addrMap.removeLayer(addrMarker); addrMarker = null; }
}

function showAddressMessage(message, isError = false) {
    const msg = document.getElementById("addr-msg");
    if (!msg) return;
    msg.textContent = message;
    msg.className   = `inline-msg ${isError ? "error" : "success"}`;
}

async function saveNewAddress() {
    const btn     = document.getElementById("addr-save-btn");
    const payload = buildAddressPayload();
    if (!payload.label || !payload.line1 || !payload.city || !payload.state || !payload.pincode) {
        showAddressMessage("Please fill required fields: Label, Address Line 1, City, State, Pincode", true);
        return;
    }
    if (payload.latitude == null || payload.longitude == null) {
        if (!confirm("No map location selected.\n\nSave without coordinates?")) return;
    }
    if (btn) { btn.disabled = true; btn.textContent = "Saving…"; }
    try {
        const res = await apiFetch(USER_API.users.myAddresses, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
        const rawText = await res.text();
        if (!res.ok) throw new Error(rawText || "Failed to save address");
        showAddressMessage("✓ Address saved successfully" + (payload.latitude ? " with location" : ""));
        resetAddressForm();
        await loadAddresses();
        if (returnToCartAfterAddressSave) { closeDrawer(); openCartModal(); returnToCartAfterAddressSave = false; }
    } catch (err) { showAddressMessage(err.message || "❌ Failed to save address", true); }
    finally { if (btn) { btn.disabled = false; btn.textContent = "Save Address"; } }
}

// ── Edit Address Modal ──
// FIX: now calls PATCH /api/users/me/addresses/{id} which exists after UserController update

function openEditAddrModal(addressId) {
    _loadAddressIntoEditModal(addressId);
    const overlay = document.getElementById("editAddrOverlay");
    if (overlay) overlay.classList.add("show");
    document.body.style.overflow = "hidden";
}

async function _loadAddressIntoEditModal(addressId) {
    try {
        const res = await apiFetch(USER_API.users.myAddresses);
        if (!res.ok) throw new Error("Failed to load addresses");
        const json      = await res.json();
        const addresses = json.data || [];
        const addr      = addresses.find(a => String(a.id) === String(addressId));
        if (!addr) { showToast("❌ Address not found", true); return; }

        document.getElementById("edit-addr-id").value      = addr.id;
        document.getElementById("edit-addr-label").value   = addr.label    || "";
        document.getElementById("edit-addr-phone").value   = addr.phone    || "";
        document.getElementById("edit-addr-line1").value   = addr.line1    || "";
        document.getElementById("edit-addr-line2").value   = addr.line2    || "";
        document.getElementById("edit-addr-city").value    = addr.city     || "";
        document.getElementById("edit-addr-state").value   = addr.state    || "";
        document.getElementById("edit-addr-pincode").value = addr.pincode  || "";
        document.getElementById("edit-addr-landmark").value= addr.landmark || "";
        const defaultEl = document.getElementById("edit-addr-isDefault");
        if (defaultEl) defaultEl.checked = !!addr.isDefault;

        const labelVal = (addr.label || "").toLowerCase();
        document.querySelectorAll("#editLabelPresets .label-preset-btn").forEach(b => {
            b.classList.remove("selected");
            if (labelVal.includes("home")  && b.textContent.includes("Home")) b.classList.add("selected");
            if ((labelVal.includes("work") || labelVal.includes("office")) && b.textContent.includes("Work")) b.classList.add("selected");
            if (labelVal.includes("other") && b.textContent.includes("Other")) b.classList.add("selected");
        });

        const msg = document.getElementById("edit-addr-msg");
        if (msg) { msg.className = "inline-msg"; msg.textContent = ""; }
    } catch (err) { showToast("❌ Failed to load address details", true); }
}

function closeEditAddrModal() {
    const overlay = document.getElementById("editAddrOverlay");
    if (overlay) overlay.classList.remove("show");
    document.body.style.overflow = "";
}

function handleEditAddrOverlayClick(e) {
    if (e.target === document.getElementById("editAddrOverlay")) closeEditAddrModal();
}

// FIX: Now calls PATCH /api/users/me/addresses/{id}
async function saveEditedAddress() {
    const btn       = document.getElementById("edit-addr-save-btn");
    const addressId = document.getElementById("edit-addr-id")?.value || "";
    if (!addressId) { showEditAddrMessage("Address ID missing", true); return; }

    const payload = {
        label:     document.getElementById("edit-addr-label")?.value?.trim()   || "",
        phone:     document.getElementById("edit-addr-phone")?.value?.trim()   || "",
        line1:     document.getElementById("edit-addr-line1")?.value?.trim()   || "",
        line2:     document.getElementById("edit-addr-line2")?.value?.trim()   || "",
        city:      document.getElementById("edit-addr-city")?.value?.trim()    || "",
        state:     document.getElementById("edit-addr-state")?.value?.trim()   || "",
        pincode:   document.getElementById("edit-addr-pincode")?.value?.trim() || "",
        landmark:  document.getElementById("edit-addr-landmark")?.value?.trim()|| "",
        isDefault: !!document.getElementById("edit-addr-isDefault")?.checked
    };

    if (!payload.label || !payload.line1 || !payload.city || !payload.state || !payload.pincode) {
        showEditAddrMessage("Please fill required fields: Label, Line 1, City, State, Pincode", true);
        return;
    }

    if (btn) { btn.disabled = true; btn.textContent = "Saving…"; }
    try {
        // FIX: Use PATCH /api/users/me/addresses/{id} — now implemented in backend
        const res = await apiFetch(USER_API.users.updateAddress(addressId), {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        const rawText = await res.text();
        if (!res.ok) throw new Error(rawText || "Failed to update address");
        showToast("✓ Address updated successfully");
        closeEditAddrModal();
        await loadAddresses();
    } catch (err) { showEditAddrMessage(err.message || "❌ Failed to update address", true); }
    finally { if (btn) { btn.disabled = false; btn.textContent = "Save Changes"; } }
}

function showEditAddrMessage(message, isError = false) {
    const msg = document.getElementById("edit-addr-msg");
    if (!msg) return;
    msg.textContent = message;
    msg.className   = `inline-msg ${isError ? "error" : "success"}`;
}

// FIX: Now calls DELETE /api/users/me/addresses/{id} — now implemented in backend
async function deleteAddress(addressId) {
    if (!confirm("Delete this address? This action cannot be undone.")) return;
    try {
        const res = await apiFetch(USER_API.users.deleteAddress(addressId), { method: "DELETE" });
        if (!res.ok) {
            const errText = await res.text().catch(() => "");
            throw new Error(errText || "Failed to delete address");
        }
        showToast("✓ Address deleted");
        await loadAddresses();
    } catch (err) { showToast(err.message || "❌ Failed to delete address", true); }
}

function handleCheckoutAddressChange(value) {
    if (value === "__ADD_NEW_ADDRESS__") openDrawerToTab("addresses", true);
}

// ══════════════════════════════════════════════════════
// CATEGORIES — Fixed: searchable dropdown, only categories with products
// ══════════════════════════════════════════════════════

function flattenCategoryTree(nodes, depth = 0) {
    const result = [];
    for (const node of nodes) {
        result.push({ ...node, depth });
        if (Array.isArray(node.children) && node.children.length)
            result.push(...flattenCategoryTree(node.children, depth + 1));
    }
    return result;
}

function buildCategoryMap(flatList) {
    const map = {};
    for (const cat of flatList) map[cat.id] = cat;
    return map;
}

async function loadCategories() {
    try {
        const res = await apiFetch(USER_API.categories.tree);
        if (!res.ok) return;
        const json     = await res.json();
        const tree     = json.data || json || [];
        const flatList = flattenCategoryTree(Array.isArray(tree) ? tree : []);
        categoryMap       = buildCategoryMap(flatList);
        allFlatCategories = flatList;

        // Render the searchable category dropdown
        renderCategoryDropdown(flatList);
    } catch (err) { console.warn("Failed to load categories", err); }
}

// FIX: Replace filter-button row with a searchable <select> + search input
function renderCategoryDropdown(flatCategories) {
    const container = document.getElementById("filterControls");
    if (!container) return;

    container.innerHTML = `
        <div style="position:relative;display:flex;flex-direction:column;gap:6px;min-width:260px;">
            <div style="position:relative;">
                <input
                    type="text"
                    id="categorySearchInput"
                    placeholder="🔍 Search category..."
                    oninput="filterCategoryDropdown()"
                    onfocus="showCategoryDropdown()"
                    autocomplete="off"
                    style="width:100%;padding:11px 16px;border:1px solid var(--border);border-radius:10px;background:var(--surface);font-family:'DM Sans',sans-serif;font-size:14px;color:var(--text);outline:none;cursor:pointer;"
                />
                <div id="categoryDropdownList"
                    style="display:none;position:absolute;top:calc(100% + 4px);left:0;right:0;background:var(--surface);border:1px solid var(--border);border-radius:10px;box-shadow:0 8px 32px rgba(0,0,0,0.10);z-index:99;max-height:260px;overflow-y:auto;">
                    <!-- options injected by filterCategoryDropdown() -->
                </div>
            </div>
            <div id="activeCategoryChip" style="display:none;align-items:center;gap:8px;padding:7px 14px;background:var(--accent);color:white;border-radius:999px;font-size:12px;font-weight:600;">
                <span id="activeCategoryName"></span>
                <button onclick="clearCategoryFilter()" style="background:none;border:none;color:white;cursor:pointer;font-size:14px;line-height:1;padding:0;margin-left:4px;">✕</button>
            </div>
        </div>
    `;

    // Build initial full list
    buildCategoryDropdownOptions(flatCategories);

    // Close dropdown on outside click
    document.addEventListener("click", function closeCatDropdown(e) {
        const wrapper = document.getElementById("categorySearchInput")?.closest("div");
        if (wrapper && !wrapper.contains(e.target)) hideCategoryDropdown();
    });
}

function buildCategoryDropdownOptions(categories) {
    const list = document.getElementById("categoryDropdownList");
    if (!list) return;

    let html = `<div style="padding:8px 14px;font-size:12px;color:var(--muted);border-bottom:1px solid var(--border);cursor:pointer;font-weight:600;" onclick="selectCategory('', 'All Categories', '')">All Categories</div>`;

    html += categories.map(cat => {
        const indent = cat.depth > 0 ? `padding-left:${14 + cat.depth * 14}px;` : "padding-left:14px;";
        const prefix = cat.depth > 0 ? "↳ " : "";
        return `<div
            style="${indent}padding-top:9px;padding-bottom:9px;padding-right:14px;font-size:13px;color:var(--text);cursor:pointer;transition:background 0.15s;border-bottom:1px solid rgba(0,0,0,0.04);"
            onmouseover="this.style.background='var(--accent-light)'"
            onmouseout="this.style.background=''"
            onclick="selectCategory('${cat.id}', '${(cat.name || '').replace(/'/g, "\\'")}', '${cat.slug || ''}')">
            ${prefix}${cat.name || "Category"}
        </div>`;
    }).join("");

    list.innerHTML = html;
}

function showCategoryDropdown() {
    const list = document.getElementById("categoryDropdownList");
    if (list) list.style.display = "block";
}

function hideCategoryDropdown() {
    const list = document.getElementById("categoryDropdownList");
    if (list) list.style.display = "none";
}

function filterCategoryDropdown() {
    const query    = (document.getElementById("categorySearchInput")?.value || "").toLowerCase().trim();
    const filtered = query
        ? allFlatCategories.filter(c => (c.name || "").toLowerCase().includes(query))
        : allFlatCategories;
    buildCategoryDropdownOptions(filtered);
    showCategoryDropdown();
}

function selectCategory(categoryId, categoryName, categorySlug) {
    currentCategoryId = categoryId || "";
    currentPage       = 0;

    // Update search input to show selected category
    const input = document.getElementById("categorySearchInput");
    if (input) input.value = categoryId ? categoryName : "";

    // Show/hide active chip
    const chip     = document.getElementById("activeCategoryChip");
    const chipName = document.getElementById("activeCategoryName");
    if (chip && chipName) {
        if (categoryId) {
            chipName.textContent = categoryName;
            chip.style.display   = "flex";
        } else {
            chip.style.display = "none";
        }
    }

    hideCategoryDropdown();

    if (categoryId) {
        loadCategoryBanner(categorySlug || categoryId);
    } else {
        hideCategoryBanner();
    }
    loadProducts(0);
}

function clearCategoryFilter() {
    selectCategory("", "All Categories", "");
}

async function fetchCategory(idOrSlug) {
    const res = await apiFetch(USER_API.categories.byIdOrSlug(idOrSlug));
    if (!res.ok) throw new Error(`Category not found: ${idOrSlug}`);
    const json = await res.json();
    return json.data || json;
}

async function loadCategoryBanner(idOrSlug) {
    const banner = document.getElementById("categoryBanner");
    if (!banner) return;
    banner.style.display = "flex";
    banner.innerHTML = `<span class="cat-banner-name">Loading…</span>`;
    try {
        const cat = await fetchCategory(idOrSlug);
        const imageStyle = cat.imageUrl ? `background-image:url('${cat.imageUrl}');background-size:cover;background-position:center;` : "background:var(--border);";
        banner.innerHTML = `
            ${cat.imageUrl ? `<div class="cat-banner-img" style="${imageStyle}"></div>` : ""}
            <div class="cat-banner-info">
                <span class="cat-banner-name">${cat.name || ""}</span>
                ${cat.slug ? `<span class="cat-banner-slug">/${cat.slug}</span>` : ""}
            </div>
            <button class="cat-banner-clear" onclick="clearCategoryFilter()" title="Clear filter">✕</button>`;
    } catch (err) { hideCategoryBanner(); }
}

function hideCategoryBanner() {
    const banner = document.getElementById("categoryBanner");
    if (banner) banner.style.display = "none";
}

// ── Products ──
async function loadProducts(page = 0) {
    currentPage = page;
    showSkeleton();
    try {
        const params = new URLSearchParams();
        params.set("page", page);
        params.set("size", pageSize);
        params.set("isActive", "true");
        if (currentCategoryId) params.set("categoryId", currentCategoryId);
        if (searchQuery.trim()) params.set("search", searchQuery.trim());

        const res = await apiFetch(USER_API.products.list(params));
        if (!res.ok) throw new Error("Failed to fetch products");
        const data     = await res.json();
        const pageData = data.data || data;
        let products   = pageData.content || [];
        totalPages     = pageData.totalPages   || 0;
        totalElements  = pageData.totalElements || 0;

        const stockResults = await Promise.all(products.map(p => fetchStockByProductId(p.id, true)));
        products = products.map((product, i) => {
            const stock            = stockResults[i] || {};
            const availableQuantity = normalizeAvailableStock(stock || product);
            return { ...product, availableQuantity, quantityReserved: stock?.quantityReserved ?? 0, inStock: availableQuantity > 0 };
        });

        renderProducts(products);
        renderPagination();
    } catch (err) {
        document.getElementById("productGrid").innerHTML = `<div class="address-card">Failed to load products: ${err.message}</div>`;
        document.getElementById("paginationWrapper").style.display = "none";
    }
}

function renderProducts(products) {
    const grid = document.getElementById("productGrid");
    if (!products.length) { grid.innerHTML = `<div class="address-card">No products found.</div>`; return; }
    grid.innerHTML = products.map((p, index) => {
        const price      = Number(p.sellingPrice ?? p.price ?? 0);
        const stock      = Number(p.availableQuantity ?? 0);
        const imageUrl   = p.imageUrl || (Array.isArray(p.images) && p.images.length ? p.images[0] : "");
        const safeName   = String(p.name || "Product").replace(/'/g, "\\'");
        const safeSlug   = p.slug || "";
        const isOutOfStock = stock <= 0;
        return `
            <div class="product-card" style="animation-delay:${index * 0.05}s" onclick="openProductDetails('${p.id}', '${safeSlug}')">
                <div class="product-img" ${imageUrl ? `style="background-image:url('${imageUrl}');background-size:cover;background-position:center;"` : ""}>
                    ${isOutOfStock ? '<div class="product-badge" style="background:#c0392b;">Out of Stock</div>' : `<div class="product-badge">Available: ${stock}</div>`}
                </div>
                <div class="product-body">
                    <div class="product-category">${p.categoryName || "Product"}</div>
                    <div class="product-name">${p.name || "-"}</div>
                    <div class="product-desc">${p.description || "No description available."}</div>
                    <div class="product-footer">
                        <div>
                            <div class="product-price">₹${price.toLocaleString("en-IN")}</div>
                            <div style="font-size:11px;color:gray;margin-top:4px;">${isOutOfStock ? "Stock unavailable" : `Stock: ${stock}`}</div>
                        </div>
                        <button class="add-btn" ${isOutOfStock ? "disabled" : ""} onclick="event.stopPropagation(); addToCart('${p.id}', '${safeName}')">
                            ${isOutOfStock ? "Out of Stock" : "Add to Cart"}
                        </button>
                    </div>
                </div>
            </div>`;
    }).join("");
}

// ── Product Detail Modal ──
async function fetchProductById(productId) {
    const res  = await apiFetch(USER_API.products.byId(productId));
    if (!res.ok) throw new Error("Failed to fetch product by id");
    const json = await res.json();
    return json.data || json;
}

async function fetchProductBySlug(slug) {
    const res  = await apiFetch(USER_API.products.bySlug(slug));
    if (!res.ok) throw new Error("Failed to fetch product by slug");
    const json = await res.json();
    return json.data || json;
}

async function openProductDetails(productId, slug) {
    openPdmModal();
    showPdmLoading();
    try {
        const product = slug ? await fetchProductBySlug(slug) : await fetchProductById(productId);
        renderPdmProduct(product);
    } catch (err) { closePdmModal(); showToast("❌ Failed to load product details", true); }
}

function openPdmModal() {
    const overlay = document.getElementById("pdmOverlay");
    if (!overlay) return;
    overlay.classList.add("show");
    document.body.style.overflow = "hidden";
}

function closePdmModal() {
    const overlay = document.getElementById("pdmOverlay");
    if (!overlay) return;
    overlay.classList.remove("show");
    document.body.style.overflow = "";
}

function showPdmLoading() {
    const modal = document.getElementById("pdmModal");
    if (!modal) return;
    modal.innerHTML = `
        <div class="pdm-image-strip"><div class="pdm-image-placeholder">🛍️</div><button class="pdm-close-btn" onclick="closePdmModal()">✕</button></div>
        <div class="pdm-skel">${Array.from({length:5}).map(() => `<div class="pdm-skel-line"></div>`).join("")}</div>`;
}

function renderPdmProduct(p) {
    const modal = document.getElementById("pdmModal");
    if (!modal) return;
    const images        = Array.isArray(p.images) && p.images.length ? p.images : [];
    const primaryImage  = images[0] || "";
    const sellingPrice  = Number(p.sellingPrice ?? 0);
    const mrp           = Number(p.mrp ?? 0);
    const availableQty  = Number(p.availableQuantity ?? 0);
    const inStock       = p.inStock ?? (availableQty > 0);
    const safeName      = String(p.name || "Product").replace(/'/g, "\\'");
    const discount      = mrp > sellingPrice && mrp > 0 ? Math.round(((mrp - sellingPrice) / mrp) * 100) : 0;
    const thumbsHtml    = images.length > 1 ? `<div class="pdm-thumbs" id="pdmThumbs">${images.map((img, i) => `<div class="pdm-thumb ${i === 0 ? "active" : ""}" onclick="pdmSelectImage('${img}', this)"><img src="${img}" alt="img ${i+1}" /></div>`).join("")}</div>` : "";
    const unitPill      = p.unitValue && p.unit ? `<div class="pdm-meta-pill">${p.unitValue} ${p.unit}</div>` : "";
    const categoryName  = p.categoryName || (p.categoryId && categoryMap[p.categoryId]?.name) || "";
    const categoryPill  = categoryName ? `<div class="pdm-category-pill" onclick="closePdmModal(); filterByCategoryFromModal('${p.categoryId || ""}', '${p.categorySlug || ""}', '${categoryName.replace(/'/g, "\\'")}')"> ${categoryName} ↗</div>` : "";

    modal.innerHTML = `
        <div class="pdm-image-strip" id="pdmImageStrip">
            ${primaryImage ? `<img id="pdmMainImage" src="${primaryImage}" alt="${p.name || 'product'}" />` : `<div class="pdm-image-placeholder">🛒</div>`}
            <button class="pdm-close-btn" onclick="closePdmModal()">✕</button>
            <div class="pdm-stock-chip ${inStock ? "in" : "out"}">${inStock ? "In Stock" : "Out of Stock"}</div>
        </div>
        ${thumbsHtml}
        <div class="pdm-body">
            <div class="pdm-info">
                ${categoryPill}
                <div class="pdm-name">${p.name || "Product"}</div>
                ${unitPill ? `<div class="pdm-meta-row">${unitPill}</div>` : ""}
                <div class="pdm-desc">${p.description || "No description available."}</div>
            </div>
            <div class="pdm-price-block">
                <div><div class="pdm-price-label">Price</div><div class="pdm-price-value selling">₹${sellingPrice.toLocaleString("en-IN")}</div></div>
                ${mrp > sellingPrice ? `<div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;"><div class="pdm-price-value mrp">₹${mrp.toLocaleString("en-IN")}</div><div style="font-size:12px;background:rgba(45,106,79,0.12);color:var(--accent);font-weight:600;padding:3px 10px;border-radius:999px;">${discount}% off</div></div>` : ""}
                <div class="pdm-divider"></div>
                <div class="pdm-stock-row"><span class="pdm-stock-label">Availability</span><span class="pdm-stock-value" style="color:${inStock ? "var(--accent)" : "#c0392b"}">${inStock ? `${availableQty} in stock` : "Out of stock"}</span></div>
                <button class="pdm-add-btn" ${!inStock ? "disabled" : ""} onclick="addToCart('${p.id}', '${safeName}')">${inStock ? "🛒 Add to Cart" : "Out of Stock"}</button>
            </div>
        </div>`;
}

function filterByCategoryFromModal(categoryId, categorySlug, categoryName) {
    if (!categoryId && !categorySlug) return;
    selectCategory(categoryId, categoryName, categorySlug);
}

function pdmSelectImage(url, thumbEl) {
    const mainImg = document.getElementById("pdmMainImage");
    if (mainImg) mainImg.src = url;
    document.querySelectorAll(".pdm-thumb").forEach(t => t.classList.remove("active"));
    thumbEl.classList.add("active");
}

function onSearch() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
        searchQuery = document.getElementById("searchInput").value || "";
        loadProducts(0);
    }, 400);
}

function onPageSizeChange() {
    pageSize = Number(document.getElementById("pageSizeSelect").value || 20);
    loadProducts(0);
}

// ── Pagination ──
function renderPagination() {
    const wrapper  = document.getElementById("paginationWrapper");
    const info     = document.getElementById("paginationInfo");
    const controls = document.getElementById("paginationControls");
    if (!wrapper || !info || !controls) return;
    if (totalPages <= 0) { wrapper.style.display = "none"; return; }
    wrapper.style.display = "flex";
    const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
    const end   = Math.min((currentPage + 1) * pageSize, totalElements);
    info.innerHTML = `Showing <strong>${start}</strong> - <strong>${end}</strong> of <strong>${totalElements}</strong> products`;
    let buttons = `<button class="page-btn" ${currentPage === 0 ? "disabled" : ""} onclick="loadProducts(${currentPage - 1})">Prev</button>`;
    for (let i = 0; i < totalPages; i++) buttons += `<button class="page-btn ${i === currentPage ? "active" : ""}" onclick="loadProducts(${i})">${i + 1}</button>`;
    buttons += `<button class="page-btn" ${currentPage >= totalPages - 1 ? "disabled" : ""} onclick="loadProducts(${currentPage + 1})">Next</button>`;
    controls.innerHTML = buttons;
}

function showSkeleton() {
    document.getElementById("productGrid").innerHTML = Array.from({length: 8}).map(() => `
        <div class="product-card"><div class="product-img"></div><div class="product-body">
            <div class="skel-line" style="width:45%"></div><div class="skel-line" style="width:80%"></div>
            <div class="skel-line" style="width:65%"></div><div class="skel-line" style="width:35%"></div>
        </div></div>`).join("");
}

// ── Cart ──
async function addToCart(productId, productName) {
    try {
        const stock     = await fetchStockByProductId(productId, true);
        const available = normalizeAvailableStock(stock);
        if (available <= 0) { showToast(`❌ ${productName} is out of stock`, true); await loadProducts(currentPage); return; }
        const existingQty = Array.isArray(currentCart?.items)
            ? currentCart.items.filter(item => String(item.productId || item.product?.id || "") === String(productId)).reduce((sum, item) => sum + Number(item.quantity || 0), 0)
            : 0;
        if (existingQty >= available) { showToast(`❌ Only ${available} item(s) available`, true); return; }
        const res = await apiFetch(USER_API.cart.addItem, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ productId, quantity: 1 }) });
        if (!res.ok) { const errText = await res.text().catch(() => ""); throw new Error(errText || "Failed to add to cart"); }
        showToast(`✓ ${productName} added to cart`);
        await loadCartCount();
        const overlay = document.getElementById("cartModalOverlay");
        if (overlay?.classList.contains("show")) await loadCart();
        await loadProducts(currentPage);
    } catch (err) {
        const msg = String(err?.message || "");
        showToast(msg.toLowerCase().includes("out of stock") ? "❌ Product is out of stock" : (msg || "❌ Failed to add to cart"), true);
    }
}

async function loadCartCount() {
    try {
        const cart  = await fetchCart();
        const items = Array.isArray(cart.items) ? cart.items : [];
        cartItemCount = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
        const countEl = document.getElementById("cartCount");
        const cartBtn = document.getElementById("cartBtn");
        if (countEl) countEl.textContent  = cartItemCount;
        if (cartBtn) cartBtn.style.display = cartItemCount > 0 ? "flex" : "none";
    } catch (err) { console.warn("Failed to load cart count", err); }
}

// ── Utility ──
function logout() { localStorage.clear(); window.location.href = "login.html"; }

function showToast(message, isError = false) {
    const toast = document.getElementById("toast");
    if (!toast) return;
    toast.textContent = message;
    toast.style.background = isError ? "#e06c6c" : "#1f1f1f";
    toast.classList.add("show");
    setTimeout(() => { toast.classList.remove("show"); }, 2500);
}

function openCartModal() {
    const overlay = document.getElementById("cartModalOverlay");
    if (!overlay) return;
    overlay.classList.add("show");
    document.body.style.overflow = "hidden";
    loadCart();
    loadAddresses();
}

function formatMoney(value) {
    const amount = Number(value || 0);
    return `₹${amount.toLocaleString("en-IN", { minimumFractionDigits: amount % 1 === 0 ? 0 : 2, maximumFractionDigits: 2 })}`;
}

function showCartMessage(message, isError = false) {
    const msg = document.getElementById("cartInlineMsg");
    if (!msg) return;
    msg.textContent = message;
    msg.className   = `cart-inline-msg show ${isError ? "error" : "success"}`;
}

function clearCartMessage() {
    const msg = document.getElementById("cartInlineMsg");
    if (!msg) return;
    msg.textContent = ""; msg.className = "cart-inline-msg";
}

async function fetchStockByProductId(productId, forceRefresh = false) {
    if (!productId) return null;
    if (!forceRefresh && productStockCache[productId]) return productStockCache[productId];
    try {
        const res  = await apiFetch(USER_API.stocks.byProduct(productId));
        if (!res.ok) return null;
        const json = await res.json();
        const stock = json.data || json || null;
        if (stock) productStockCache[productId] = stock;
        return stock;
    } catch (err) { return null; }
}

function normalizeAvailableStock(stockLike) {
    return Number(stockLike?.quantityAvailable ?? stockLike?.availableQuantity ?? stockLike?.stock ?? 0);
}

async function hydrateCartStock(cart, forceRefresh = false) {
    const items           = Array.isArray(cart?.items) ? cart.items : [];
    const uniqueProductIds = [...new Set(items.map(item => item.productId || item.product?.id || "").filter(Boolean))];
    const stockEntries    = await Promise.all(uniqueProductIds.map(async (productId) => [productId, await fetchStockByProductId(productId, forceRefresh)]));
    cartStockCache = Object.fromEntries(stockEntries);
    return {
        ...cart,
        items: items.map(item => {
            const productId = item.productId || item.product?.id || "";
            const stock     = cartStockCache[productId] || null;
            return { ...item, availableQuantity: normalizeAvailableStock(stock), quantityReserved: stock?.quantityReserved ?? item.quantityReserved ?? 0 };
        })
    };
}

function getCartItemAvailableStock(item) {
    const productId = item.productId || item.product?.id || "";
    return normalizeAvailableStock(cartStockCache[productId] || productStockCache[productId] || item);
}

function updateCurrentCartItemQuantity(itemId, quantity) {
    if (!currentCart || !Array.isArray(currentCart.items)) return;
    currentCart.items = currentCart.items.map(item => {
        if (String(item.id || item.cartItemId) !== String(itemId)) return item;
        const unitPrice = Number(item.unitPrice || item.price || item.sellingPrice || 0);
        return { ...item, quantity, lineTotal: unitPrice * quantity };
    });
    currentCart.itemCount = currentCart.items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
    currentCart.subtotal  = currentCart.items.reduce((sum, item) => {
        const unitPrice = Number(item.unitPrice || item.price || item.sellingPrice || 0);
        return sum + (unitPrice * Number(item.quantity || 0));
    }, 0);
    if (!currentCart.totalAmount || Number(currentCart.totalAmount) === 0) currentCart.totalAmount = currentCart.subtotal;
}

function setCartItemLoading(itemId, isLoading) {
    if (isLoading) cartQtyLoading[itemId] = true; else delete cartQtyLoading[itemId];
}

async function fetchCart() {
    const res = await apiFetch(USER_API.cart.get);
    if (!res.ok) throw new Error("Failed to fetch cart");
    const json = await res.json();
    return json.data || json || {};
}

function closeCartModal() {
    const overlay = document.getElementById("cartModalOverlay");
    if (!overlay) return;
    overlay.classList.remove("show");
    document.body.style.overflow = "";
    clearCartMessage();
}

async function loadCart() {
    const itemsPanel = document.getElementById("cartItemsPanel");
    if (itemsPanel) itemsPanel.innerHTML = `<div class="cart-empty-state">Loading cart...</div>`;
    clearCartMessage();
    try {
        const rawCart = await fetchCart();
        currentCart   = await hydrateCartStock(rawCart, true);
        renderCart(currentCart);
        await loadCartCount();
    } catch (err) {
        console.error("loadCart error:", err);
        if (itemsPanel) itemsPanel.innerHTML = `<div class="cart-empty-state">Failed to load cart.</div>`;
        showCartMessage("Failed to load cart", true);
    }
}

function renderCart(cart) {
    const itemsPanel  = document.getElementById("cartItemsPanel");
    const subEl       = document.getElementById("cartModalSub");
    const itemsEl     = document.getElementById("cartSummaryItems");
    const subtotalEl  = document.getElementById("cartSummarySubtotal");
    const couponEl    = document.getElementById("cartSummaryCoupon");
    const totalEl     = document.getElementById("cartSummaryTotal");
    const couponInput = document.getElementById("cartCouponInput");
    const items       = Array.isArray(cart?.items) ? cart.items : [];
    const itemCount   = Number(cart?.itemCount ?? items.reduce((sum, item) => sum + Number(item.quantity || 0), 0));
    const subtotal    = Number(cart?.subtotal || 0);
    const totalAmount = Number(cart?.totalAmount ?? cart?.total ?? subtotal);
    const couponCode  = cart?.couponCode || "";
    if (subEl)       subEl.textContent      = itemCount > 0 ? `${itemCount} item${itemCount > 1 ? "s" : ""} in your cart` : "Your cart is empty";
    if (itemsEl)     itemsEl.textContent     = itemCount;
    if (subtotalEl)  subtotalEl.textContent  = formatMoney(subtotal);
    if (couponEl)    couponEl.textContent     = couponCode || "—";
    if (totalEl)     totalEl.textContent      = formatMoney(totalAmount);
    if (couponInput) couponInput.value        = couponCode;
    if (!itemsPanel) return;
    if (!items.length) { itemsPanel.innerHTML = `<div class="cart-empty-state">Your cart is empty.<br />Add products to see them here.</div>`; return; }
    itemsPanel.innerHTML = items.map(item => {
        const itemId      = item.id || item.cartItemId;
        const productId   = item.productId || item.product?.id || "";
        const productName = item.productName || item.name || item.product?.name || "Product";
        const quantity    = Number(item.quantity || 0);
        const unitPrice   = Number(item.unitPrice || item.price || item.sellingPrice || 0);
        const lineTotal   = Number(item.lineTotal || (unitPrice * quantity));
        const stock       = getCartItemAvailableStock(item);
        const isUpdating  = !!cartQtyLoading[itemId];
        return `
            <div class="cart-item-card">
                <div class="cart-item-top">
                    <div>
                        <div class="cart-item-name">${productName}</div>
                        <div class="cart-item-meta">Product ID: ${productId || "-"}<br />Unit Price: ${formatMoney(unitPrice)}</div>
                    </div>
                    <div class="cart-stock-pill ${stock > 0 ? "" : "out"}">${stock > 0 ? `Available: ${stock}` : "Out of stock"}</div>
                </div>
                <div class="cart-item-controls">
                    <div class="cart-qty-box">
                        <label style="font-size:12px;color:var(--muted);">Qty</label>
                        <div class="cart-qty-stepper">
                            <button type="button" class="cart-qty-btn" onclick="changeCartItemQuantity('${itemId}', -1)" ${isUpdating || quantity <= 1 ? "disabled" : ""}>−</button>
                            <span class="cart-qty-value" id="qty-label-${itemId}">${quantity}</span>
                            <button type="button" class="cart-qty-btn" onclick="changeCartItemQuantity('${itemId}', 1)" ${isUpdating || stock <= 0 || quantity >= stock ? "disabled" : ""}>+</button>
                        </div>
                    </div>
                    <div class="cart-actions">
                        <div class="cart-line-total">${formatMoney(lineTotal)}</div>
                        <button class="filter-btn" onclick="removeCartItem('${itemId}')" ${isUpdating ? "disabled" : ""}>Remove</button>
                    </div>
                </div>
            </div>`;
    }).join("");
}

async function updateCartItem(itemId, quantity) {
    try {
        const safeQty = Number(quantity || 0);
        if (!safeQty || safeQty < 1 || safeQty > 100) { showCartMessage("Quantity must be between 1 and 100", true); return false; }
        setCartItemLoading(itemId, true);
        const res = await apiFetch(USER_API.cart.updateItem(itemId), { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ quantity: safeQty }) });
        if (!res.ok) { const errText = await res.text().catch(() => ""); throw new Error(errText || "Failed to update cart item"); }
        updateCurrentCartItemQuantity(itemId, safeQty);
        renderCart(currentCart);
        const changedItem = Array.isArray(currentCart?.items) ? currentCart.items.find(x => String(x.id || x.cartItemId) === String(itemId)) : null;
        if (changedItem) {
            const productId = changedItem.productId || changedItem.product?.id || "";
            if (productId) { const stock = await fetchStockByProductId(productId, true); cartStockCache[productId] = stock; }
        }
        currentCart = await hydrateCartStock(currentCart, true);
        renderCart(currentCart);
        await loadCartCount();
        await loadProducts(currentPage);
        showCartMessage("Cart item updated");
        return true;
    } catch (err) {
        const msg = String(err?.message || "");
        showCartMessage(msg.toLowerCase().includes("out of stock") ? "Requested quantity is more than available stock" : (msg || "Failed to update cart item"), true);
        await loadCart();
        return false;
    } finally { setCartItemLoading(itemId, false); renderCart(currentCart); }
}

async function changeCartItemQuantity(itemId, delta) {
    const items = Array.isArray(currentCart?.items) ? currentCart.items : [];
    const item  = items.find(x => String(x.id || x.cartItemId) === String(itemId));
    if (!item) { showCartMessage("Cart item not found", true); return; }
    const currentQty = Number(item.quantity || 0);
    const nextQty    = currentQty + Number(delta || 0);
    const productId  = item.productId || item.product?.id || "";
    if (nextQty < 1) { showCartMessage("Quantity must be at least 1", true); return; }
    const latestStock = await fetchStockByProductId(productId, true);
    if (latestStock) { productStockCache[productId] = latestStock; cartStockCache[productId] = latestStock; }
    const stock = normalizeAvailableStock(latestStock || item);
    if (stock <= 0) { showCartMessage("This product is out of stock", true); await loadCart(); await loadProducts(currentPage); return; }
    if (nextQty > stock) { showCartMessage(`Only ${stock} item(s) available`, true); return; }
    await updateCartItem(itemId, nextQty);
}

async function removeCartItem(itemId) {
    try {
        const res = await apiFetch(USER_API.cart.removeItem(itemId), { method: "DELETE" });
        if (!res.ok) { const errText = await res.text().catch(() => ""); throw new Error(errText || "Failed to remove cart item"); }
        showCartMessage("Item removed from cart");
        await loadCart();
        await loadProducts(currentPage);
    } catch (err) { showCartMessage("Failed to remove cart item", true); }
}

async function applyCoupon() {
    try {
        const input      = document.getElementById("cartCouponInput");
        const couponCode = (input?.value || "").trim();
        if (!couponCode) { showCartMessage("Please enter a coupon code", true); return; }
        const res = await apiFetch(USER_API.cart.applyCoupon, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ couponCode }) });
        if (!res.ok) { const errText = await res.text().catch(() => ""); throw new Error(errText || "Failed to apply coupon"); }
        showCartMessage("Coupon applied");
        await loadCart();
    } catch (err) { showCartMessage("Failed to apply coupon", true); }
}

async function removeCoupon() {
    try {
        const res = await apiFetch(USER_API.cart.removeCoupon, { method: "DELETE" });
        if (!res.ok) { const errText = await res.text().catch(() => ""); throw new Error(errText || "Failed to remove coupon"); }
        showCartMessage("Coupon removed");
        await loadCart();
    } catch (err) { showCartMessage("Failed to remove coupon", true); }
}

function handleCartOverlayClick(event) { if (event.target === document.getElementById("cartModalOverlay")) closeCartModal(); }
function handleMyOrdersOverlayClick(event) { if (event.target === document.getElementById("myOrdersOverlay")) closeMyOrdersModal(); }

function formatOrderDate(value) {
    if (!value) return "—";
    try { return new Date(value).toLocaleString("en-IN", { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }); }
    catch (e) { return value; }
}

function getOrderBadgeStyle(status) {
    const map = {
        PENDING: "background:#fff7db;color:#a16207;border:1px solid #f4e3a3",
        CONFIRMED: "background:#eef6ff;color:#2563eb;border:1px solid #bfd6ff",
        PROCESSING: "background:#eef6ff;color:#2563eb;border:1px solid #bfd6ff",
        DISPATCHED: "background:#eefbf3;color:#15803d;border:1px solid #bfe8cb",
        OUT_FOR_DELIVERY: "background:#eefbf3;color:#15803d;border:1px solid #bfe8cb",
        DELIVERED: "background:#eefbf3;color:#15803d;border:1px solid #bfe8cb",
        CANCELLED: "background:#fff0f0;color:#c0392b;border:1px solid #f1bcbc"
    };
    return map[status] || "background:#f5f5f5;color:#666;border:1px solid #ddd";
}

async function fetchMyOrders() {
    try {
        const params   = new URLSearchParams({ page: "0", size: "100" });
        const status   = document.getElementById("myOrderStatusFilter")?.value || "";
        const fromDate = document.getElementById("myOrderFromDate")?.value || "";
        const toDate   = document.getElementById("myOrderToDate")?.value || "";
        if (status)   params.set("status",   status);
        if (fromDate) params.set("fromDate", new Date(fromDate).toISOString());
        if (toDate)   params.set("toDate",   new Date(toDate).toISOString());
        const res  = await apiFetch(`${USER_API.orders.list}?${params.toString()}`);
        if (!res.ok) throw new Error("Failed to fetch my orders");
        const json = await res.json();
        const data = json.data || json || {};
        return Array.isArray(data) ? data : (data.content || []);
    } catch (err) { console.warn("fetchMyOrders error", err); return []; }
}

async function fetchMyOrderById(orderId) {
    const res  = await apiFetch(USER_API.orders.byId(orderId));
    if (!res.ok) throw new Error("Failed to fetch order");
    const json = await res.json();
    return json.data || json;
}

async function fetchMyOrderItems(orderId) {
    const res  = await apiFetch(USER_API.orders.items(orderId));
    if (!res.ok) throw new Error("Failed to fetch order items");
    const json = await res.json();
    return json.data || json || [];
}

async function openMyOrdersModal() {
    const overlay = document.getElementById("myOrdersOverlay");
    if (!overlay) return;
    overlay.style.display = "block";
    document.body.style.overflow = "hidden";
    document.getElementById("myOrdersList").innerHTML = `<div class="address-card">Loading orders...</div>`;
    if (!myOrdersFetched) { myOrders = await fetchMyOrders(); myOrdersFetched = true; }
    renderMyOrders(myOrders);
}

function closeMyOrdersModal() {
    const overlay = document.getElementById("myOrdersOverlay");
    if (!overlay) return;
    overlay.style.display = "none";
    document.body.style.overflow = "";
}

function renderMyOrders(orders) {
    const list    = document.getElementById("myOrdersList");
    const countEl = document.getElementById("myOrdersCount");
    if (!list) return;
    if (countEl) countEl.textContent = `${orders.length} order${orders.length !== 1 ? "s" : ""} found`;
    if (!orders.length) { list.innerHTML = `<div class="address-card">No orders found.</div>`; return; }
    list.innerHTML = orders.map(order => {
        const orderId    = order.orderId || order.id;
        const itemWrapId = `order-items-${orderId}`;
        return `
            <div class="address-card" style="margin-bottom:16px;padding:18px;border-radius:16px">
                <div style="display:flex;justify-content:space-between;gap:14px;flex-wrap:wrap;align-items:flex-start">
                    <div>
                        <div style="font-size:11px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;color:var(--accent);margin-bottom:6px">Order</div>
                        <div style="font-size:18px;font-weight:600">${order.orderNumber || "—"}</div>
                        <div style="font-size:12px;color:var(--muted);margin-top:6px">Placed: ${formatOrderDate(order.placedAt)}</div>
                        <div style="font-size:12px;color:var(--muted);margin-top:4px">Order ID: ${orderId || "—"}</div>
                    </div>
                    <div style="text-align:right">
                        <div style="display:inline-block;padding:5px 12px;border-radius:999px;font-size:12px;font-weight:600;${getOrderBadgeStyle(order.status)}">${order.status || "—"}</div>
                        <div style="font-size:20px;font-weight:700;margin-top:10px">${formatMoney(order.totalAmount || 0)}</div>
                    </div>
                </div>
                <div style="display:flex;gap:10px;flex-wrap:wrap;margin-top:16px">
                    <button class="filter-btn" onclick="viewMyOrderDetails('${orderId}')">Details</button>
                    <button class="filter-btn" onclick="loadMyOrderItems('${orderId}')">Items</button>
                    ${["PENDING","CONFIRMED","PROCESSING"].includes(order.status) ? `<button class="filter-btn" style="color:#c0392b;border-color:#efc2bb" onclick="cancelMyOrder('${orderId}')">Cancel Order</button>` : ""}
                </div>
                <div id="${itemWrapId}" style="margin-top:14px"></div>
            </div>`;
    }).join("");
}

async function viewMyOrderDetails(orderId) {
    try {
        const order = await fetchMyOrderById(orderId);
        alert(
            `Order Number: ${order.orderNumber || "—"}\n` +
            `Status: ${order.status || "—"}\n` +
            `Subtotal: ${formatMoney(order.subtotal || 0)}\n` +
            `Tax: ${formatMoney(order.taxAmount || 0)}\n` +
            `Delivery Fee: ${formatMoney(order.deliveryFee || 0)}\n` +
            `Total: ${formatMoney(order.totalAmount || 0)}\n` +
            `Placed At: ${formatOrderDate(order.placedAt)}`
        );
    } catch (err) { showToast("❌ Failed to load order details", true); }
}

async function loadMyOrderItems(orderId) {
    const wrap = document.getElementById(`order-items-${orderId}`);
    if (!wrap) return;
    wrap.innerHTML = `<div class="address-card">Loading order items...</div>`;
    try {
        const items = await fetchMyOrderItems(orderId);
        myOrderItemsCache[orderId] = items;
        if (!items.length) { wrap.innerHTML = `<div class="address-card">No items found for this order.</div>`; return; }
        wrap.innerHTML = `
            <div style="border:1px solid var(--border);border-radius:14px;overflow:hidden">
                <table style="width:100%;border-collapse:collapse">
                    <thead><tr style="background:var(--bg)">
                        <th style="padding:12px 14px;text-align:left;font-size:12px">Product</th>
                        <th style="padding:12px 14px;text-align:left;font-size:12px">Qty</th>
                        <th style="padding:12px 14px;text-align:left;font-size:12px">Unit Price</th>
                        <th style="padding:12px 14px;text-align:left;font-size:12px">Line Total</th>
                        <th style="padding:12px 14px;text-align:left;font-size:12px">Status</th>
                    </tr></thead>
                    <tbody>
                        ${items.map(item => `
                            <tr style="border-top:1px solid var(--border)">
                                <td style="padding:12px 14px">${item.productName || "—"}</td>
                                <td style="padding:12px 14px">${item.quantity ?? 0}</td>
                                <td style="padding:12px 14px">${formatMoney(item.unitPrice || 0)}</td>
                                <td style="padding:12px 14px">${formatMoney(item.lineTotal || 0)}</td>
                                <td style="padding:12px 14px">${item.status || "—"}</td>
                            </tr>`).join("")}
                    </tbody>
                </table>
            </div>`;
    } catch (err) { wrap.innerHTML = `<div class="address-card">Failed to load order items.</div>`; }
}

async function cancelMyOrder(orderId) {
    const reason = prompt("Enter cancel reason (optional):") || "";
    try {
        const res = await apiFetch(USER_API.orders.cancel(orderId), { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason }) });
        if (!res.ok) { const errText = await res.text().catch(() => ""); throw new Error(errText || "Failed to cancel order"); }
        showToast("✓ Order cancelled");
        myOrders = await fetchMyOrders();
        myOrdersFetched = true;
        renderMyOrders(myOrders);
    } catch (err) { showToast("❌ Failed to cancel order", true); }
}

async function startRazorpayPayment(order) {
    const orderId = order.orderId || order.id;
    if (!orderId) throw new Error("Order ID missing");
    const initiateRes  = await apiFetch(USER_API.payments.initiate, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ orderId, method: "UPI" }) });
    const initiateText = await initiateRes.text();
    if (!initiateRes.ok) throw new Error(initiateText || "Failed to initiate payment");
    const payment = (JSON.parse(initiateText).data || JSON.parse(initiateText));
    return new Promise((resolve, reject) => {
        const options = {
            key: payment.gatewayKeyId, amount: Math.round(Number(payment.amount || 0) * 100), currency: "INR",
            name: "Karim Store", description: `Order ${order.orderNumber || ""}`, order_id: payment.gatewayOrderId,
            handler: async function(response) {
                try {
                    const verifyRes = await apiFetch(USER_API.payments.verifyRazorpay, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ paymentId: payment.paymentId, razorpayOrderId: response.razorpay_order_id, razorpayPaymentId: response.razorpay_payment_id, razorpaySignature: response.razorpay_signature }) });
                    const verifyText = await verifyRes.text();
                    if (!verifyRes.ok) throw new Error(verifyText || "Payment verification failed");
                    resolve(true);
                } catch (err) { reject(err); }
            },
            modal: { ondismiss: function() { reject(new Error("Payment cancelled by user")); } },
            theme: { color: "#2d6a4f" }
        };
        const razorpay = new Razorpay(options);
        razorpay.open();
    });
}

async function placeMyOrder() {
    const addressId     = document.getElementById("checkoutAddressSelect")?.value || "";
    const customerNotes = document.getElementById("checkoutCustomerNotes")?.value?.trim() || "";
    const btn           = document.getElementById("checkoutBtn");
    if (!addressId || addressId === "__ADD_NEW_ADDRESS__") { showCartMessage("Please select delivery address", true); return; }
    if (!currentCart || !Array.isArray(currentCart.items) || !currentCart.items.length) { showCartMessage("Your cart is empty", true); return; }
    if (btn) { btn.disabled = true; btn.textContent = "Creating Order..."; }
    try {
        const orderRes  = await apiFetch(USER_API.orders.place, { method: "POST", headers: { "Content-Type": "application/json", "X-Actor-Id": getUserId() }, body: JSON.stringify({ addressId, customerNotes }) });
        const orderText = await orderRes.text();
        if (!orderRes.ok) throw new Error(orderText || "Failed to place order");
        const order = (JSON.parse(orderText).data || JSON.parse(orderText));
        renderCartSummary({ items: Array.isArray(order.items) && order.items.length ? order.items : (currentCart?.items || []), subtotal: Number(order.subtotal ?? currentCart?.subtotal ?? 0), taxAmount: Number(order.taxAmount ?? currentCart?.taxAmount ?? 0), deliveryFee: Number(order.deliveryFee ?? currentCart?.deliveryFee ?? 0), totalAmount: Number(order.totalAmount ?? currentCart?.totalAmount ?? 0), couponCode: order.couponCode || currentCart?.couponCode || "" });
        if (btn) btn.textContent = "Opening Payment...";
        await startRazorpayPayment(order);
        showCartMessage("✓ Payment successful and order placed");
        document.getElementById("checkoutCustomerNotes").value = "";
        closeCartModal();
        await loadCartCount();
        myOrders = await fetchMyOrders();
        myOrdersFetched = true;
    } catch (err) { console.error("Place order error:", err); showCartMessage(err.message || "Failed to place order/payment", true); }
    finally { if (btn) { btn.disabled = false; btn.textContent = "✅ Place Order & Pay"; } }
}

function filterMyOrders() {
    const q        = (document.getElementById("myOrderSearch")?.value || "").toLowerCase().trim();
    const status   = document.getElementById("myOrderStatusFilter")?.value || "";
    const fromDate = document.getElementById("myOrderFromDate")?.value || "";
    const toDate   = document.getElementById("myOrderToDate")?.value || "";
    let results    = [...myOrders];
    if (q)        results = results.filter(o => String(o.orderNumber || "").toLowerCase().includes(q) || String(o.orderId || o.id || "").toLowerCase().includes(q));
    if (status)   results = results.filter(o => o.status === status);
    if (fromDate) { const from = new Date(fromDate).getTime(); results = results.filter(o => o.placedAt ? new Date(o.placedAt).getTime() >= from : false); }
    if (toDate)   { const to   = new Date(toDate).getTime();   results = results.filter(o => o.placedAt ? new Date(o.placedAt).getTime() <= to   : false); }
    renderMyOrders(results);
    const countEl = document.getElementById("myOrdersCount");
    if (countEl) countEl.textContent = `${results.length} of ${myOrders.length} orders`;
}

function clearMyOrderFilters() {
    ["myOrderSearch","myOrderStatusFilter","myOrderFromDate","myOrderToDate"].forEach(id => { const el = document.getElementById(id); if (el) el.value = ""; });
    renderMyOrders(myOrders);
}

function exportMyOrdersCSV() {
    const header = ["Order ID","Order Number","Status","Total Amount","Placed At"];
    const lines  = myOrders.map(o => [o.orderId || o.id || "", o.orderNumber || "", o.status || "", o.totalAmount || 0, o.placedAt || ""].map(v => `"${String(v).replace(/"/g, '""')}"`).join(","));
    const csv    = [header.join(","), ...lines].join("\n");
    const a      = document.createElement("a");
    a.href       = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
    a.download   = `my_orders_${Date.now()}.csv`;
    a.click();
}

function renderCartSummary(cart = {}) {
    const items       = Array.isArray(cart.items) ? cart.items : [];
    const itemCount   = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
    const subtotal    = Number(cart.subtotal    || cart.subTotal    || 0);
    const taxAmount   = Number(cart.taxAmount   || cart.tax         || 0);
    const deliveryFee = Number(cart.deliveryFee || cart.shippingFee || 0);
    const totalAmount = Number(cart.totalAmount || cart.total       || 0);
    const couponCode  = cart.couponCode || cart.appliedCouponCode   || "";
    const setEl = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val; };
    setEl("cartSummaryItems",    itemCount);
    setEl("cartSummarySubtotal", formatMoney(subtotal));
    setEl("cartSummaryTax",      formatMoney(taxAmount));
    setEl("cartSummaryDelivery", formatMoney(deliveryFee));
    setEl("cartSummaryCoupon",   couponCode || "—");
    setEl("cartSummaryTotal",    formatMoney(totalAmount));
}

function handlePostPaymentReturn() {
    const raw = localStorage.getItem("paymentResult");
    if (!raw) return;
    try {
        const paymentResult = JSON.parse(raw);
        if (paymentResult.status === "success") { showToast(`✓ Payment successful for ${paymentResult.orderNumber || "order"}`); setTimeout(() => openMyOrdersModal(), 350); }
        else if (paymentResult.status === "failed") { showToast("❌ Payment failed or was cancelled", true); }
    } catch (err) { console.warn("Failed to parse payment return", err); }
    finally { localStorage.removeItem("paymentResult"); }
}

// ── Init ──
document.addEventListener("DOMContentLoaded", () => {
    loadAccountData();
    loadProfile();
    loadProducts(0);
    loadCategories();
    loadCartCount();
    loadAddresses();
    handlePostPaymentReturn();
});