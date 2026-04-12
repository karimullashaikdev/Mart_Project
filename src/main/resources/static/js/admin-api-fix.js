// ── admin-api-fix.js ────────────────────────────────────────────────────────
// All routes verified directly from your Spring Boot controllers:
//   ProductController    → @RequestMapping("/api/products")
//   CategoryController   → @RequestMapping("/api/categories")
//   UserController       → @RequestMapping("/api/users")
//   OrderController      → /api/admin/orders  (inline mappings)
//   CartController       → @RequestMapping("/api/v1/cart")
//   CloudinaryController → @RequestMapping("/api/images")
//   AuthController       → @RequestMapping("/api/auth")
// ────────────────────────────────────────────────────────────────────────────

// ── 1. Base URL ───────────────────────────────────────────────────────────────
const BASE = '';

// ── 2. Auth token ─────────────────────────────────────────────────────────────
const token = localStorage.getItem('token') || localStorage.getItem('authToken') || '';

// ── 3. Actor-ID helper ────────────────────────────────────────────────────────
function getActorId() {
    return (
        localStorage.getItem('actorId') ||
        localStorage.getItem('userId')  ||
        localStorage.getItem('adminId') ||
        localStorage.getItem('id')      ||
        ''
    );
}

// ── 4. ADMIN_API route map ────────────────────────────────────────────────────
const ADMIN_API = {

    // ── Products ──────────────────────────────────────────────────────────────
    products: {
        list:                `${BASE}/api/products`,
        create:              `${BASE}/api/products`,
        getById:    (id)  => `${BASE}/api/products/${id}`,
        getBySlug:  (slug)=> `${BASE}/api/products/slug/${slug}`,
        update:     (id)  => `${BASE}/api/products/${id}`,
        delete:     (id)  => `${BASE}/api/products/${id}`,
        toggleActive:(id) => `${BASE}/api/products/${id}/toggle-active`,
        restore:    (id)  => `${BASE}/api/products/${id}/restore`,
        bulkPriceUpdate:     `${BASE}/api/products/bulk-price-update`,
    },

    // ── Categories ────────────────────────────────────────────────────────────
    // POST   /api/categories                → createCategory  (CreateCategoryDto)
    // PATCH  /api/categories/{id}           → updateCategory  (UpdateCategoryDto)
    // DELETE /api/categories/{id}           → softDelete
    // PATCH  /api/categories/{id}/restore   → restore
    // PATCH  /api/categories/reorder        → reorder         (ReorderCategoriesDto → { orderedIds: UUID[] })
    // GET    /api/categories/admin/all      → listAll (admin, includes deleted, supports CategoryFilterDto params)
    // GET    /api/categories                → tree (public, active only)
    categories: {
        adminAll:          `${BASE}/api/categories/admin/all`,
        tree:              `${BASE}/api/categories`,
        create:            `${BASE}/api/categories`,
        update:   (id)  => `${BASE}/api/categories/${id}`,
        delete:   (id)  => `${BASE}/api/categories/${id}`,
        restore:  (id)  => `${BASE}/api/categories/${id}/restore`,
        reorder:           `${BASE}/api/categories/reorder`,
    },

    // ── Users ─────────────────────────────────────────────────────────────────
    users: {
        list:              `${BASE}/api/users`,
        getById:  (id)  => `${BASE}/api/users/${id}`,
        update:   (id)  => `${BASE}/api/users/${id}`,
        restore:  (id)  => `${BASE}/api/users/${id}/restore`,
    },

    // ── Orders ────────────────────────────────────────────────────────────────
    orders: {
        list:                   `${BASE}/api/admin/orders`,
        items:         (id)  => `${BASE}/api/admin/orders/${id}/items`,
        updateStatus:  (id)  => `${BASE}/api/admin/orders/${id}/status`,
        confirm:       (id)  => `${BASE}/api/admin/orders/${id}/confirm`,
        processing:    (id)  => `${BASE}/api/admin/orders/${id}/processing`,
        dispatch:      (id)  => `${BASE}/api/admin/orders/${id}/dispatch`,
        outForDelivery:(id)  => `${BASE}/api/admin/orders/${id}/out-for-delivery`,
        deliver:       (id)  => `${BASE}/api/admin/orders/${id}/deliver`,
        delete:        (id)  => `${BASE}/api/admin/orders/${id}`,
    },

    // ── Cart (admin) ──────────────────────────────────────────────────────────
    cart: {
        deleteByUser: (uid) => `${BASE}/api/v1/cart?userId=${uid}`,
    },

    // ── Image upload ──────────────────────────────────────────────────────────
    images: {
        upload: `${BASE}/api/images/upload`,
        delete: `${BASE}/api/images/delete`,
    },

    // ── Auth ──────────────────────────────────────────────────────────────────
    auth: {
        login:          `${BASE}/api/auth/login`,
        register:       `${BASE}/api/auth/register`,
        logout:         `${BASE}/api/auth/logout`,
        refreshToken:   `${BASE}/api/auth/refresh-token`,
    },
};

// ── 5. Response unwrapper ─────────────────────────────────────────────────────
function unwrapApiResponse(raw) {
    if (raw === null || raw === undefined) return raw;
    if (typeof raw === 'object' && !Array.isArray(raw)) {
        if ('data'    in raw) return raw.data;
        if ('result'  in raw) return raw.result;
        if ('payload' in raw) return raw.payload;
    }
    return raw;
}

// ── 6. Error parser ───────────────────────────────────────────────────────────
function parseApiError(text) {
    if (!text) return 'Unknown error';
    try {
        const obj = JSON.parse(text);
        return obj?.message || obj?.error || obj?.data || JSON.stringify(obj);
    } catch {
        return String(text).slice(0, 200);
    }
}

// ── 7. HTML escape helper ─────────────────────────────────────────────────────
function escapeHtml(str) {
    return String(str ?? '')
        .replace(/&/g,  '&amp;')
        .replace(/</g,  '&lt;')
        .replace(/>/g,  '&gt;')
        .replace(/"/g,  '&quot;')
        .replace(/'/g,  '&#039;');
}

// ── 8. Auth guard ─────────────────────────────────────────────────────────────
(function guardAuth() {
    const publicPages = ['login.html', 'signin.html', 'index.html'];
    const onPublicPage = publicPages.some(p => window.location.pathname.endsWith(p));
    if (!token && !onPublicPage) {
        console.warn('[admin-api-fix] No auth token — redirecting to login.');
        window.location.href = 'login.html';
    }
})();

// ── 9. Logout ─────────────────────────────────────────────────────────────────
function logout() {
    localStorage.clear();
    window.location.href = 'login.html';
}

// ── 10. Debug log ─────────────────────────────────────────────────────────────
console.log('[admin-api-fix] All routes loaded. BASE:', BASE || '(same origin)');
console.table({
    'Products list':          ADMIN_API.products.list,
    'Products create':        ADMIN_API.products.create,
    'Products bulk-price':    ADMIN_API.products.bulkPriceUpdate,
    'Categories admin/all':   ADMIN_API.categories.adminAll,
    'Categories create':      ADMIN_API.categories.create,
    'Categories reorder':     ADMIN_API.categories.reorder,
    'Users list':             ADMIN_API.users.list,
    'Orders list':            ADMIN_API.orders.list,
    'Image upload':           ADMIN_API.images.upload,
    'Cart delete':            ADMIN_API.cart.deleteByUser('USER_ID'),
});