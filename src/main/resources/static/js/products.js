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
		// GET /api/categories — returns CategoryTreeDto[] (nested tree, active only)
		tree: "/api/categories",
		// GET /api/categories/{idOrSlug} — returns CategoryResponseDto
		byIdOrSlug: (idOrSlug) => `/api/categories/${encodeURIComponent(idOrSlug)}`
	},
	cart: {
		get: "/api/v1/cart",
		addItem: "/api/v1/cart/items",
		updateItem: (itemId) => `/api/v1/cart/items/${encodeURIComponent(itemId)}`,
		removeItem: (itemId) => `/api/v1/cart/items/${encodeURIComponent(itemId)}`,
		applyCoupon: "/api/v1/cart/coupon",
		removeCoupon: "/api/v1/cart/coupon"
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
let currentCart = null;

// Flat map of id -> CategoryTreeDto, built after tree is loaded
// Used to quickly look up category name/slug by id
let categoryMap = {};

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

// ═══════════════════════════════════════════════
// CATEGORIES  –  GET /api/categories
//                GET /api/categories/{idOrSlug}
// ═══════════════════════════════════════════════

/**
 * Recursively flattens a CategoryTreeDto[] into a plain array.
 * Preserves depth so we can indent child categories in the filter bar.
 *
 * @param {CategoryTreeDto[]} nodes
 * @param {number} depth
 * @returns {{ id, name, slug, depth, children }[]}
 */
function flattenCategoryTree(nodes, depth = 0) {
	const result = [];
	for (const node of nodes) {
		result.push({ ...node, depth });
		if (Array.isArray(node.children) && node.children.length) {
			result.push(...flattenCategoryTree(node.children, depth + 1));
		}
	}
	return result;
}

/**
 * Builds a lookup map of id → node from the flat list.
 */
function buildCategoryMap(flatList) {
	const map = {};
	for (const cat of flatList) {
		map[cat.id] = cat;
	}
	return map;
}

/**
 * Fetches GET /api/categories — returns CategoryTreeDto[] (nested).
 * Flattens the tree for filter rendering and builds the id→category map.
 */
async function loadCategories() {
	try {
		const res = await apiFetch(USER_API.categories.tree);
		if (!res.ok) return;

		const json = await res.json();
		// The endpoint wraps in ApiResponse<List<CategoryTreeDto>>
		const tree = json.data || json || [];
		const flatList = flattenCategoryTree(Array.isArray(tree) ? tree : []);

		// Build global map for quick lookups
		categoryMap = buildCategoryMap(flatList);

		renderCategoryFilters(flatList);
	} catch (err) {
		console.warn("Failed to load categories", err);
	}
}

/**
 * Fetches GET /api/categories/{idOrSlug} — returns CategoryResponseDto.
 * Can be called with either a UUID or a slug string.
 *
 * @param {string} idOrSlug  UUID or slug
 * @returns {Promise<CategoryResponseDto>}
 */
async function fetchCategory(idOrSlug) {
	const res = await apiFetch(USER_API.categories.byIdOrSlug(idOrSlug));
	if (!res.ok) throw new Error(`Category not found: ${idOrSlug}`);
	const json = await res.json();
	return json.data || json;
}

/**
 * Renders category filter pills from a flat list (preserving hierarchy indent).
 * Root categories are shown normally; children get a subtle visual indent.
 */
function renderCategoryFilters(flatCategories) {
	const container = document.getElementById("filterControls");
	if (!container) return;

	let html = `
        <button class="filter-btn active" onclick="setFilter('all', '', this)">All</button>
    `;

	html += flatCategories.map(cat => {
		const indent = cat.depth > 0 ? `style="margin-left:${cat.depth * 8}px;opacity:${cat.depth > 0 ? 0.85 : 1}"` : "";
		const prefix = cat.depth > 0 ? "↳ " : "";
		return `
            <button class="filter-btn" ${indent}
                    data-category-id="${cat.id}"
                    data-category-slug="${cat.slug || ""}"
                    onclick="setFilter('${(cat.name || "category").replace(/'/g, "\\'")}', '${cat.id}', this)">
                ${prefix}${cat.name || "Category"}
            </button>
        `;
	}).join("");

	container.innerHTML = html;
}

/**
 * Called when the user clicks a category filter pill.
 * Also fetches the full CategoryResponseDto (by slug or id) and shows
 * an optional info banner so users can see the category description/image.
 */
function setFilter(_label, categoryId, btn) {
	currentCategoryId = categoryId || "";
	currentPage = 0;

	document.querySelectorAll(".filter-btn").forEach(b => b.classList.remove("active"));
	btn.classList.add("active");

	// Show/hide the category info banner
	if (categoryId) {
		const slug = btn.dataset.categorySlug || categoryId;
		loadCategoryBanner(slug);
	} else {
		hideCategoryBanner();
	}

	loadProducts(0);
}

/**
 * Fetches GET /api/categories/{idOrSlug} and renders a small banner
 * above the product grid showing the selected category's name + image.
 */
async function loadCategoryBanner(idOrSlug) {
	const banner = document.getElementById("categoryBanner");
	if (!banner) return;

	// Show loading state immediately
	banner.style.display = "flex";
	banner.innerHTML = `<span class="cat-banner-name">Loading…</span>`;

	try {
		const cat = await fetchCategory(idOrSlug);

		const imageStyle = cat.imageUrl
			? `background-image:url('${cat.imageUrl}');background-size:cover;background-position:center;`
			: "background:var(--border);";

		banner.innerHTML = `
            ${cat.imageUrl
				? `<div class="cat-banner-img" style="${imageStyle}"></div>`
				: ""
			}
            <div class="cat-banner-info">
                <span class="cat-banner-name">${cat.name || ""}</span>
                ${cat.slug ? `<span class="cat-banner-slug">/${cat.slug}</span>` : ""}
            </div>
            <button class="cat-banner-clear" onclick="clearCategoryFilter()" title="Clear filter">✕</button>
        `;
	} catch (err) {
		// If fetch fails, just hide the banner silently
		hideCategoryBanner();
	}
}

function hideCategoryBanner() {
	const banner = document.getElementById("categoryBanner");
	if (banner) banner.style.display = "none";
}

/**
 * Clears the active category filter and resets to "All".
 */
function clearCategoryFilter() {
	currentCategoryId = "";
	currentPage = 0;

	document.querySelectorAll(".filter-btn").forEach(b => b.classList.remove("active"));
	const allBtn = document.querySelector(".filter-btn");
	if (allBtn) allBtn.classList.add("active");

	hideCategoryBanner();
	loadProducts(0);
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

// ═══════════════════════════════════════════════
// PRODUCT DETAIL MODAL  –  GET /api/products/{id}
//                          GET /api/products/slug/{slug}
// ═══════════════════════════════════════════════

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
	openPdmModal();
	showPdmLoading();

	try {
		let product = null;

		if (slug) {
			product = await fetchProductBySlug(slug);
		} else {
			product = await fetchProductById(productId);
		}

		renderPdmProduct(product);
	} catch (err) {
		closePdmModal();
		showToast("❌ Failed to load product details", true);
	}
}

// ── Modal open / close ──
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

// ── Loading skeleton ──
function showPdmLoading() {
	const modal = document.getElementById("pdmModal");
	if (!modal) return;

	modal.innerHTML = `
        <div class="pdm-image-strip">
            <div class="pdm-image-placeholder">🛍️</div>
            <button class="pdm-close-btn" onclick="closePdmModal()">✕</button>
        </div>
        <div class="pdm-skel">
            <div class="pdm-skel-line" style="width:30%"></div>
            <div class="pdm-skel-line" style="width:60%"></div>
            <div class="pdm-skel-line" style="width:80%"></div>
            <div class="pdm-skel-line" style="width:50%"></div>
            <div class="pdm-skel-line" style="width:70%"></div>
        </div>
    `;
}

// ── Render full product detail ──
function renderPdmProduct(p) {
	const modal = document.getElementById("pdmModal");
	if (!modal) return;

	const images = Array.isArray(p.images) && p.images.length ? p.images : [];
	const primaryImage = images[0] || "";
	const sellingPrice = Number(p.sellingPrice ?? 0);
	const mrp = Number(p.mrp ?? 0);
	const availableQty = Number(p.availableQuantity ?? 0);
	const inStock = p.inStock ?? (availableQty > 0);
	const safeName = String(p.name || "Product").replace(/'/g, "\\'");
	const discount = mrp > sellingPrice && mrp > 0
		? Math.round(((mrp - sellingPrice) / mrp) * 100)
		: 0;

	const thumbsHtml = images.length > 1 ? `
        <div class="pdm-thumbs" id="pdmThumbs">
            ${images.map((img, i) => `
                <div class="pdm-thumb ${i === 0 ? "active" : ""}" onclick="pdmSelectImage('${img}', this)">
                    <img src="${img}" alt="img ${i + 1}" />
                </div>
            `).join("")}
        </div>
    ` : "";

	const unitPill = p.unitValue && p.unit
		? `<div class="pdm-meta-pill">${p.unitValue} ${p.unit}</div>`
		: "";

	// Resolve category name from our local map if not provided directly
	const categoryName = p.categoryName
		|| (p.categoryId && categoryMap[p.categoryId]?.name)
		|| "";

	// Category pill — clicking it filters products by this category
	const categoryPill = categoryName ? `
        <div class="pdm-category-pill"
             onclick="closePdmModal(); filterByCategoryFromModal('${p.categoryId || ""}', '${p.categorySlug || ""}', '${categoryName.replace(/'/g, "\\'")}')">
            ${categoryName} ↗
        </div>
    ` : "";

	modal.innerHTML = `
        <!-- Image strip -->
        <div class="pdm-image-strip" id="pdmImageStrip">
            ${primaryImage
			? `<img id="pdmMainImage" src="${primaryImage}" alt="${p.name || 'product'}" />`
			: `<div class="pdm-image-placeholder">🛒</div>`
		}
            <button class="pdm-close-btn" onclick="closePdmModal()">✕</button>
            <div class="pdm-stock-chip ${inStock ? "in" : "out"}">
                ${inStock ? "In Stock" : "Out of Stock"}
            </div>
        </div>

        ${thumbsHtml}

        <!-- Body -->
        <div class="pdm-body">
            <!-- Left: info -->
            <div class="pdm-info">
                ${categoryPill}
                <div class="pdm-name">${p.name || "Product"}</div>

                ${unitPill ? `<div class="pdm-meta-row">${unitPill}</div>` : ""}

                <div class="pdm-desc">${p.description || "No description available."}</div>
            </div>

            <!-- Right: pricing block -->
            <div class="pdm-price-block">
                <div>
                    <div class="pdm-price-label">Price</div>
                    <div class="pdm-price-value selling">₹${sellingPrice.toLocaleString("en-IN")}</div>
                </div>

                ${mrp > sellingPrice ? `
                    <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
                        <div class="pdm-price-value mrp">₹${mrp.toLocaleString("en-IN")}</div>
                        <div style="font-size:12px;background:rgba(45,106,79,0.12);color:var(--accent);font-weight:600;padding:3px 10px;border-radius:999px;">
                            ${discount}% off
                        </div>
                    </div>
                ` : ""}

                <div class="pdm-divider"></div>

                <div class="pdm-stock-row">
                    <span class="pdm-stock-label">Availability</span>
                    <span class="pdm-stock-value" style="color:${inStock ? "var(--accent)" : "#c0392b"}">
                        ${inStock ? `${availableQty} in stock` : "Out of stock"}
                    </span>
                </div>

                <button
                    class="pdm-add-btn"
                    ${!inStock ? "disabled" : ""}
                    onclick="addToCart('${p.id}', '${safeName}')"
                >
                    ${inStock ? "🛒 Add to Cart" : "Out of Stock"}
                </button>
            </div>
        </div>
    `;
}

/**
 * Called from the category pill inside the product detail modal.
 * Closes the modal, activates the matching filter pill, loads the
 * category banner via GET /api/categories/{idOrSlug}, then reloads products.
 */
function filterByCategoryFromModal(categoryId, categorySlug, categoryName) {
	if (!categoryId && !categorySlug) return;

	currentCategoryId = categoryId || "";
	currentPage = 0;

	// Activate the matching filter pill (by data-category-id)
	document.querySelectorAll(".filter-btn").forEach(b => b.classList.remove("active"));
	const matchingBtn = document.querySelector(`.filter-btn[data-category-id="${categoryId}"]`);
	if (matchingBtn) {
		matchingBtn.classList.add("active");
	}

	// Load banner using slug if available, otherwise id
	loadCategoryBanner(categorySlug || categoryId);
	loadProducts(0);
}

// ── Switch main image on thumbnail click ──
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

		if (!res.ok) {
			const errText = await res.text().catch(() => "");
			throw new Error(errText || "Failed to add to cart");
		}

		showToast(`✓ ${productName} added to cart`);
		await loadCartCount();

		const overlay = document.getElementById("cartModalOverlay");
		if (overlay?.classList.contains("show")) {
			await loadCart();
		}
	} catch (err) {
		showToast("❌ Failed to add to cart", true);
	}
}

async function loadCartCount() {
	try {
		const cart = await fetchCart();
		const items = Array.isArray(cart.items) ? cart.items : [];

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

function openCartModal() {
	const overlay = document.getElementById("cartModalOverlay");
	if (!overlay) return;

	overlay.classList.add("show");
	document.body.style.overflow = "hidden";
	loadCart();
}

function formatMoney(value) {
	return `₹${Number(value || 0).toLocaleString("en-IN")}`;
}
function showCartMessage(message, isError = false) {
	const msg = document.getElementById("cartInlineMsg");
	if (!msg) return;

	msg.textContent = message;
	msg.className = `cart-inline-msg show ${isError ? "error" : "success"}`;
}
function clearCartMessage() {
	const msg = document.getElementById("cartInlineMsg");
	if (!msg) return;

	msg.textContent = "";
	msg.className = "cart-inline-msg";
} async function fetchCart() {
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
} async function loadCart() {
	const itemsPanel = document.getElementById("cartItemsPanel");
	if (itemsPanel) {
		itemsPanel.innerHTML = `<div class="cart-empty-state">Loading cart...</div>`;
	}

	clearCartMessage();

	try {
		currentCart = await fetchCart();
		renderCart(currentCart);
		await loadCartCount();
	} catch (err) {
		if (itemsPanel) {
			itemsPanel.innerHTML = `<div class="cart-empty-state">Failed to load cart.</div>`;
		}
		showCartMessage("Failed to load cart", true);
	}
} function renderCart(cart) {
	const itemsPanel = document.getElementById("cartItemsPanel");
	const subEl = document.getElementById("cartModalSub");
	const itemsEl = document.getElementById("cartSummaryItems");
	const subtotalEl = document.getElementById("cartSummarySubtotal");
	const couponEl = document.getElementById("cartSummaryCoupon");
	const totalEl = document.getElementById("cartSummaryTotal");
	const couponInput = document.getElementById("cartCouponInput");

	const items = Array.isArray(cart?.items) ? cart.items : [];
	const itemCount = Number(
		cart?.itemCount ?? items.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
	);
	const subtotal = Number(cart?.subtotal || 0);
	const couponCode = cart?.couponCode || "";

	if (subEl) {
		subEl.textContent = itemCount > 0
			? `${itemCount} item${itemCount > 1 ? "s" : ""} in your cart`
			: "Your cart is empty";
	}

	if (itemsEl) itemsEl.textContent = itemCount;
	if (subtotalEl) subtotalEl.textContent = formatMoney(subtotal);
	if (couponEl) couponEl.textContent = couponCode || "—";
	if (totalEl) totalEl.textContent = formatMoney(subtotal);
	if (couponInput) couponInput.value = couponCode;

	if (!itemsPanel) return;

	if (!items.length) {
		itemsPanel.innerHTML = `
	            <div class="cart-empty-state">
	                Your cart is empty.<br />
	                Add products to see them here.
	            </div>
	        `;
		return;
	}

	itemsPanel.innerHTML = items.map(item => {
		const itemId = item.id || item.cartItemId;
		const productId = item.productId || item.product?.id || "";
		const productName = item.productName || item.name || item.product?.name || "Product";
		const quantity = Number(item.quantity || 0);
		const unitPrice = Number(item.unitPrice || item.price || item.sellingPrice || 0);
		const lineTotal = Number(item.lineTotal || (unitPrice * quantity));
		const stock = Number(item.availableQuantity ?? item.stock ?? item.product?.availableQuantity ?? 0);
		const inStock = stock > 0;

		return `
	            <div class="cart-item-card">
	                <div class="cart-item-top">
	                    <div>
	                        <div class="cart-item-name">${productName}</div>
	                        <div class="cart-item-meta">
	                            Product ID: ${productId || "-"}<br />
	                            Unit Price: ${formatMoney(unitPrice)}
	                        </div>
	                    </div>
	                    <div class="cart-stock-pill ${inStock ? "" : "out"}">
	                        ${inStock ? `Stock: ${stock}` : "Out of stock"}
	                    </div>
	                </div>

	                <div class="cart-item-controls">
	                    <div class="cart-qty-box">
	                        <label for="qty-${itemId}" style="font-size:12px;color:var(--muted);">Qty</label>
	                        <input
	                            id="qty-${itemId}"
	                            type="number"
	                            min="1"
	                            max="100"
	                            value="${quantity}"
	                            class="cart-qty-input"
	                        />
	                        <button class="add-btn" onclick="updateCartItem('${itemId}')">Update</button>
	                    </div>

	                    <div class="cart-actions">
	                        <div class="cart-line-total">${formatMoney(lineTotal)}</div>
	                        <button class="filter-btn" onclick="removeCartItem('${itemId}')">Remove</button>
	                    </div>
	                </div>
	            </div>
	        `;
	}).join("");
} async function updateCartItem(itemId) {
	try {
		const qtyInput = document.getElementById(`qty-${itemId}`);
		const quantity = Number(qtyInput?.value || 0);

		if (!quantity || quantity < 1 || quantity > 100) {
			showCartMessage("Quantity must be between 1 and 100", true);
			return;
		}

		const res = await apiFetch(USER_API.cart.updateItem(itemId), {
			method: "PATCH",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ quantity })
		});

		if (!res.ok) {
			const errText = await res.text().catch(() => "");
			throw new Error(errText || "Failed to update cart item");
		}

		showCartMessage("Cart item updated");
		await loadCart();
	} catch (err) {
		showCartMessage("Failed to update cart item", true);
	}
} async function removeCartItem(itemId) {
	try {
		const res = await apiFetch(USER_API.cart.removeItem(itemId), {
			method: "DELETE"
		});

		if (!res.ok) {
			const errText = await res.text().catch(() => "");
			throw new Error(errText || "Failed to remove cart item");
		}

		showCartMessage("Item removed from cart");
		await loadCart();
	} catch (err) {
		showCartMessage("Failed to remove cart item", true);
	}
} async function applyCoupon() {
	try {
		const input = document.getElementById("cartCouponInput");
		const couponCode = (input?.value || "").trim();

		if (!couponCode) {
			showCartMessage("Please enter a coupon code", true);
			return;
		}

		const res = await apiFetch(USER_API.cart.applyCoupon, {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ couponCode })
		});

		if (!res.ok) {
			const errText = await res.text().catch(() => "");
			throw new Error(errText || "Failed to apply coupon");
		}

		showCartMessage("Coupon applied");
		await loadCart();
	} catch (err) {
		showCartMessage("Failed to apply coupon", true);
	}
} async function removeCoupon() {
	try {
		const res = await apiFetch(USER_API.cart.removeCoupon, {
			method: "DELETE"
		});

		if (!res.ok) {
			const errText = await res.text().catch(() => "");
			throw new Error(errText || "Failed to remove coupon");
		}

		showCartMessage("Coupon removed");
		await loadCart();
	} catch (err) {
		showCartMessage("Failed to remove coupon", true);
	}
} function handleCartOverlayClick(event) {
	if (event.target === document.getElementById("cartModalOverlay")) {
		closeCartModal();
	}
}

// ── Init ──
document.addEventListener("DOMContentLoaded", () => {
	loadAccountData();
	loadProfile();
	loadProducts(0);
	loadCategories(); // loads tree + builds categoryMap
	loadCartCount();
});