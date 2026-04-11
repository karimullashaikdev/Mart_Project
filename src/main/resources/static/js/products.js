// ── Auth guard ──
(function guardUser() {
    const token = localStorage.getItem("authToken");
    if (!token) {
        window.location.href = "login.html";
        return;
    }

    const role = (localStorage.getItem("role") || "").toUpperCase();
    if (role === "ADMIN") {
        window.location.href = "admin.html";
        return;
    }
    if (role === "DELIVERY") {
        window.location.href = "delivery-dashboard.html";
        return;
    }
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
        const newAccess = json.data?.accessToken || json.accessToken;
        const newRefresh = json.data?.refreshToken || json.refreshToken;

        if (!newAccess) throw new Error("No access token");

        localStorage.setItem("authToken", newAccess);
        if (newRefresh) {
            localStorage.setItem("refreshToken", newRefresh);
        }

        return newAccess;
    })();

    try {
        return await _refreshing;
    } finally {
        _refreshing = null;
    }
}

// ── User APIs ──
const USER_API = {
    products: {
        list: (params) => `/api/products?${params.toString()}`,
        byId: (productId) => `/api/products/${encodeURIComponent(productId)}`,
        bySlug: (slug) => `/api/products/slug/${encodeURIComponent(slug)}`
    },
    categories: {
        list: "/api/categories"
    },
    cart: {
        get: "/api/v1/cart",
        addItem: "/api/v1/cart/items"
    },
    users: {
        me: "/api/users/me",
        profile: "/api/users/me/profile",
        myAddresses: "/api/users/me/addresses"
    },
    stocks: {
        byProduct: (productId) => `/api/user/stocks/${encodeURIComponent(productId)}`
    }
};

// ── State ──
let currentPage = 0;
let totalPages = 0;
let totalElements = 0;
let pageSize = 20;
let currentCategoryId = "";
let searchQuery = "";
let searchTimer = null;
let cartItemCount = 0;
let userData = {};
let profileExists = false;

// ── API helper ──
async function apiFetch(url, options = {}) {
    let token = localStorage.getItem("authToken");

    options.headers = {
        ...(options.headers || {}),
        Authorization: `Bearer ${token}`
    };

    let res = await fetch(url, options);

    if (res.status === 401) {
        try {
            token = await refreshAccessToken();
            options.headers.Authorization = `Bearer ${token}`;
            res = await fetch(url, options);
        } catch (err) {
            logout();
            throw err;
        }
    }

    return res;
}

// ── Drawer helpers ──
function openDrawer() {
    document.getElementById("profileDrawer")?.classList.add("open");
    document.getElementById("drawerOverlay")?.classList.add("open");
    document.body.style.overflow = "hidden";
    loadAccountData();
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
    if (name === "addresses") loadAddresses();
}

// ── User/account ──
function getInitials(name) {
    if (!name) return "👤";
    return name.trim().split(/\s+/).map(p => p[0]).join("").toUpperCase().slice(0, 2);
}

function setAvatarUrl(url) {
    const initials = getInitials(userData.fullName || userData.name || "");
    const big = document.getElementById("bigAvatar");
    const small = document.getElementById("profileAvatarInitials");

    if (url) {
        if (big) big.innerHTML = `<img src="${url}" alt="avatar" />`;
        if (small) small.innerHTML = `<img src="${url}" alt="avatar" style="width:100%;height:100%;border-radius:50%;object-fit:cover;" />`;
    } else {
        if (big) big.textContent = initials;
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
        const email = userData.email || "";
        const phone = userData.phone || "";

        document.getElementById("acc-name").value = fullName;
        document.getElementById("acc-email").value = email;
        document.getElementById("acc-email-display").textContent = email || "—";
        document.getElementById("acc-phone").value = phone;
        document.getElementById("heroName").textContent = fullName || "My Account";
        document.getElementById("heroEmail").textContent = email || "";

        setAvatarUrl(userData.avatarUrl || "");
    } catch (err) {
        console.warn("Failed to load account data", err);
    }
}

async function saveAccount() {
    const btn = document.getElementById("acc-save-btn");
    btn.disabled = true;

    try {
        const payload = {
            fullName: document.getElementById("acc-name").value.trim(),
            phone: document.getElementById("acc-phone").value.trim()
        };

        const res = await apiFetch(USER_API.users.me, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (!res.ok) throw new Error("Failed to update account");
        showToast("✓ Account updated successfully");
        loadAccountData();
    } catch (err) {
        showToast("❌ Failed to update account", true);
    } finally {
        btn.disabled = false;
    }
}

async function loadProfile() {
    try {
        const res = await apiFetch(USER_API.users.profile);

        if (res.status === 404) {
            profileExists = false;
            return;
        }

        if (!res.ok) return;

        const json = await res.json();
        const profile = json.data || json || {};
        profileExists = true;

        document.getElementById("prf-dob").value = profile.dateOfBirth
            ? profile.dateOfBirth.split("T")[0]
            : "";

        document.getElementById("prf-gender").value = profile.gender || "";
        document.getElementById("prf-avatar").value = profile.avatarUrl || "";

        if (profile.avatarUrl) {
            setAvatarUrl(profile.avatarUrl);
        }
    } catch (err) {
        console.warn("Failed to load profile", err);
    }
}

async function saveProfile() {
    try {
        const payload = {
            dateOfBirth: document.getElementById("prf-dob").value || null,
            gender: document.getElementById("prf-gender").value || null,
            avatarUrl: document.getElementById("prf-avatar").value || null
        };

        const res = await apiFetch(USER_API.users.profile, {
            method: profileExists ? "PATCH" : "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (!res.ok) throw new Error("Failed to save profile");

        profileExists = true;
        showToast("✓ Profile saved");
    } catch (err) {
        showToast("❌ Failed to save profile", true);
    }
}

async function loadAddresses() {
    const list = document.getElementById("addressList");
    if (!list) return;

    try {
        const res = await apiFetch(USER_API.users.myAddresses);
        if (!res.ok) throw new Error("Failed to load addresses");

        const json = await res.json();
        const addresses = json.data || [];

        if (!addresses.length) {
            list.innerHTML = `<div class="address-card">No saved addresses found.</div>`;
            return;
        }

        list.innerHTML = addresses.map(a => `
            <div class="address-card">
                <div class="address-card-label">${a.label || "Address"}</div>
                <div class="address-card-text">
                    ${a.line1 || ""}${a.line2 ? ", " + a.line2 : ""}, ${a.city || ""}, ${a.state || ""} - ${a.pincode || ""}
                </div>
                <div class="address-card-meta">${a.isDefault ? "Default address" : ""}</div>
            </div>
        `).join("");
    } catch (err) {
        list.innerHTML = `<div class="address-card">Failed to load addresses.</div>`;
    }
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

        if (currentCategoryId) {
            params.set("categoryId", currentCategoryId);
        }

        if (searchQuery.trim()) {
            params.set("search", searchQuery.trim());
        }

        const res = await apiFetch(USER_API.products.list(params));
        if (!res.ok) throw new Error("Failed to fetch products");

        const data = await res.json();
        const pageData = data.data || data;

        let products = pageData.content || [];
        totalPages = pageData.totalPages || 0;
        totalElements = pageData.totalElements || 0;

        const stockResults = await Promise.all(
            products.map(async (product) => {
                try {
                    const stockRes = await apiFetch(USER_API.stocks.byProduct(product.id));
                    if (!stockRes.ok) return null;
                    const stockJson = await stockRes.json();
                    return stockJson.data || stockJson;
                } catch {
                    return null;
                }
            })
        );

        products = products.map((product, index) => {
            const stock = stockResults[index] || {};
            return {
                ...product,
                availableQuantity: stock.quantityAvailable ?? product.availableQuantity ?? product.stock ?? 0,
                quantityReserved: stock.quantityReserved ?? 0
            };
        });

        renderProducts(products);
        renderPagination();
    } catch (err) {
        document.getElementById("productGrid").innerHTML = `
            <div class="address-card">Failed to load products: ${err.message}</div>
        `;
        document.getElementById("paginationWrapper").style.display = "none";
    }
}

function renderProducts(products) {
    const grid = document.getElementById("productGrid");

    if (!products.length) {
        grid.innerHTML = `<div class="address-card">No products found.</div>`;
        return;
    }

    grid.innerHTML = products.map((p, index) => {
        const price = Number(p.sellingPrice ?? p.price ?? 0);
        const stock = Number(p.availableQuantity ?? 0);
        const imageUrl = p.imageUrl || (Array.isArray(p.images) && p.images.length ? p.images[0] : "");
        const safeName = String(p.name || "Product").replace(/'/g, "\\'");
        const safeSlug = p.slug || "";

        return `
            <div class="product-card" style="animation-delay:${index * 0.05}s" onclick="openProductDetails('${p.id}', '${safeSlug}')">
                <div class="product-img" ${imageUrl ? `style="background-image:url('${imageUrl}');background-size:cover;background-position:center;"` : ""}>
                    ${stock <= 0 ? '<div class="product-badge" style="background:#c0392b;">Out of Stock</div>' : '<div class="product-badge">Available</div>'}
                </div>

                <div class="product-body">
                    <div class="product-category">${p.categoryName || "Product"}</div>
                    <div class="product-name">${p.name || "-"}</div>
                    <div class="product-desc">${p.description || "No description available."}</div>

                    <div class="product-footer">
                        <div>
                            <div class="product-price">₹${price.toLocaleString("en-IN")}</div>
                            <div style="font-size:11px;color:gray;margin-top:4px;">Stock: ${stock}</div>
                        </div>
                        <button class="add-btn" ${stock <= 0 ? "disabled" : ""} onclick="event.stopPropagation(); addToCart('${p.id}', '${safeName}')">
                            ${stock <= 0 ? "Sold Out" : "Add to Cart"}
                        </button>
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

// ── Product detail APIs usage ──
async function fetchProductById(productId) {
    const res = await apiFetch(USER_API.products.byId(productId));
    if (!res.ok) throw new Error("Failed to fetch product by id");
    const json = await res.json();
    return json.data || json;
}

async function fetchProductBySlug(slug) {
    const res = await apiFetch(USER_API.products.bySlug(slug));
    if (!res.ok) throw new Error("Failed to fetch product by slug");
    const json = await res.json();
    return json.data || json;
}

async function openProductDetails(productId, slug) {
    try {
        let product = null;

        if (slug) {
            product = await fetchProductBySlug(slug);
        } else {
            product = await fetchProductById(productId);
        }

        showProductDetails(product);
    } catch (err) {
        showToast("❌ Failed to load product details", true);
    }
}

function showProductDetails(product) {
    const name = product.name || "Product";
    const sku = product.sku || "-";
    const slug = product.slug || "-";
    const price = Number(product.sellingPrice ?? 0).toLocaleString("en-IN");
    const description = product.description || "No description available.";

    alert(
        `Product Details

Name: ${name}
SKU: ${sku}
Slug: ${slug}
Price: ₹${price}

Description:
${description}`
    );
}

// ── Categories ──
async function loadCategories() {
    try {
        const res = await apiFetch(USER_API.categories.list);
        if (!res.ok) return;

        const json = await res.json();
        const categories = json.data || json.content || json || [];
        renderCategoryFilters(Array.isArray(categories) ? categories : []);
    } catch (err) {
        console.warn("Failed to load categories", err);
    }
}

function renderCategoryFilters(categories) {
    const container = document.getElementById("filterControls");
    if (!container) return;

    let html = `
        <button class="filter-btn active" onclick="setFilter('all', '', this)">All</button>
    `;

    html += categories.map(category => `
        <button class="filter-btn" onclick="setFilter('${category.name || "category"}', '${category.id}', this)">
            ${category.name || "Category"}
        </button>
    `).join("");

    container.innerHTML = html;
}

function setFilter(_label, categoryId, btn) {
    currentCategoryId = categoryId || "";
    currentPage = 0;

    document.querySelectorAll(".filter-btn").forEach(b => b.classList.remove("active"));
    btn.classList.add("active");

    loadProducts(0);
}

function onSearch() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
        searchQuery = document.getElementById("searchInput").value || "";
        loadProducts(0);
    }, 400);
}

function onPageSizeChange() {
    const value = Number(document.getElementById("pageSizeSelect").value || 20);
    pageSize = value;
    loadProducts(0);
}

// ── Pagination ──
function renderPagination() {
    const wrapper = document.getElementById("paginationWrapper");
    const info = document.getElementById("paginationInfo");
    const controls = document.getElementById("paginationControls");

    if (!wrapper || !info || !controls) return;

    if (totalPages <= 0) {
        wrapper.style.display = "none";
        return;
    }

    wrapper.style.display = "flex";

    const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
    const end = Math.min((currentPage + 1) * pageSize, totalElements);

    info.innerHTML = `Showing <strong>${start}</strong> - <strong>${end}</strong> of <strong>${totalElements}</strong> products`;

    let buttons = `
        <button class="page-btn" ${currentPage === 0 ? "disabled" : ""} onclick="loadProducts(${currentPage - 1})">Prev</button>
    `;

    for (let i = 0; i < totalPages; i++) {
        buttons += `
            <button class="page-btn ${i === currentPage ? "active" : ""}" onclick="loadProducts(${i})">
                ${i + 1}
            </button>
        `;
    }

    buttons += `
        <button class="page-btn" ${currentPage >= totalPages - 1 ? "disabled" : ""} onclick="loadProducts(${currentPage + 1})">Next</button>
    `;

    controls.innerHTML = buttons;
}

function showSkeleton() {
    const grid = document.getElementById("productGrid");
    grid.innerHTML = Array.from({ length: 8 }).map(() => `
        <div class="product-card">
            <div class="product-img"></div>
            <div class="product-body">
                <div class="skel-line" style="width:45%"></div>
                <div class="skel-line" style="width:80%"></div>
                <div class="skel-line" style="width:65%"></div>
                <div class="skel-line" style="width:35%"></div>
            </div>
        </div>
    `).join("");
}

// ── Cart ──
async function addToCart(productId, productName) {
    try {
        const res = await apiFetch(USER_API.cart.addItem, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                productId,
                quantity: 1
            })
        });

        if (!res.ok) throw new Error("Failed to add to cart");

        showToast(`✓ ${productName} added to cart`);
        loadCartCount();
    } catch (err) {
        showToast("❌ Failed to add to cart", true);
    }
}

async function loadCartCount() {
    try {
        const res = await apiFetch(USER_API.cart.get);
        if (!res.ok) return;

        const json = await res.json();
        const cart = json.data || json || {};
        const items = cart.items || [];

        cartItemCount = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);

        const countEl = document.getElementById("cartCount");
        const cartBtn = document.getElementById("cartBtn");

        if (countEl) countEl.textContent = cartItemCount;
        if (cartBtn) cartBtn.style.display = cartItemCount > 0 ? "flex" : "none";
    } catch (err) {
        console.warn("Failed to load cart count", err);
    }
}

// ── Utility ──
function logout() {
    localStorage.clear();
    window.location.href = "login.html";
}

function showToast(message, isError = false) {
    const toast = document.getElementById("toast");
    if (!toast) return;

    toast.textContent = message;
    toast.style.background = isError ? "#e06c6c" : "#1f1f1f";
    toast.classList.add("show");

    setTimeout(() => {
        toast.classList.remove("show");
    }, 2500);
}

// Placeholder methods so page does not break
function openCartModal() {
    showToast("Cart modal logic already exists in your page.");
}

// ── Init ──
document.addEventListener("DOMContentLoaded", () => {
    loadAccountData();
    loadProfile();
    loadProducts(0);
    loadCategories();
    loadCartCount();
});