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
// '' = same origin. Change to 'http://localhost:8080' for local dev if
// your frontend is served from a different port than Spring Boot.
const BASE = '';

// ── 2. Auth token ─────────────────────────────────────────────────────────────
const token = localStorage.getItem('token') || localStorage.getItem('authToken') || '';

// ── 3. Actor-ID helper ────────────────────────────────────────────────────────
// Controllers read: @RequestHeader("X-Actor-Id") UUID actorId
// Make sure your login page saves the logged-in user's UUID under one of
// these keys after a successful POST /api/auth/login response.
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
    // ProductController  →  /api/products
    products: {
        list:               `${BASE}/api/products`,                          // GET
        create:             `${BASE}/api/products`,                          // POST
        update:    (id)  => `${BASE}/api/products/${id}`,                    // PUT
        delete:    (id)  => `${BASE}/api/products/${id}`,                    // DELETE
        toggleActive:(id)=> `${BASE}/api/products/${id}/toggle-active`,      // PATCH
        restore:   (id)  => `${BASE}/api/products/${id}/restore`,            // PATCH
        bulkPriceUpdate:    `${BASE}/api/products/bulk-price-update`,        // PUT
    },

    // ── Categories ────────────────────────────────────────────────────────────
    // CategoryController  →  /api/categories
    // Admin flat list     →  GET /api/categories/admin/all
    categories: {
        list:              `${BASE}/api/categories/admin/all`,       // GET  admin list
        tree:              `${BASE}/api/categories`,                 // GET  public tree
        create:            `${BASE}/api/categories`,                 // POST
        update:   (id)  => `${BASE}/api/categories/${id}`,           // PATCH
        delete:   (id)  => `${BASE}/api/categories/${id}`,           // DELETE soft
        restore:  (id)  => `${BASE}/api/categories/${id}/restore`,   // PATCH
        reorder:           `${BASE}/api/categories/reorder`,         // PATCH
    },

    // ── Users ─────────────────────────────────────────────────────────────────
    // UserController  →  /api/users
    users: {
        list:              `${BASE}/api/users`,                      // GET  ?page=&size=
        getById:  (id)  => `${BASE}/api/users/${id}`,                // GET
        update:   (id)  => `${BASE}/api/users/${id}`,                // PATCH
        restore:  (id)  => `${BASE}/api/users/${id}/restore`,        // PATCH
    },

    // ── Orders ────────────────────────────────────────────────────────────────
    // OrderController  →  /api/admin/orders
    orders: {
        list:                   `${BASE}/api/admin/orders`,                        // GET
        items:         (id)  => `${BASE}/api/admin/orders/${id}/items`,            // GET
        updateStatus:  (id)  => `${BASE}/api/admin/orders/${id}/status`,           // PATCH
        confirm:       (id)  => `${BASE}/api/admin/orders/${id}/confirm`,          // PATCH
        processing:    (id)  => `${BASE}/api/admin/orders/${id}/processing`,       // PATCH
        dispatch:      (id)  => `${BASE}/api/admin/orders/${id}/dispatch`,         // PATCH
        outForDelivery:(id)  => `${BASE}/api/admin/orders/${id}/out-for-delivery`, // PATCH
        deliver:       (id)  => `${BASE}/api/admin/orders/${id}/deliver`,          // PATCH
        delete:        (id)  => `${BASE}/api/admin/orders/${id}`,                  // DELETE
    },

    // ── Cart (admin) ──────────────────────────────────────────────────────────
    // CartController  →  /api/v1/cart
    // Admin delete:   DELETE /api/v1/cart?userId={uid}  (@PreAuthorize ADMIN)
    cart: {
        deleteByUser: (uid) => `${BASE}/api/v1/cart?userId=${uid}`,  // DELETE
    },

    // ── Image upload ──────────────────────────────────────────────────────────
    // CloudinaryController  →  /api/images
    images: {
        upload: `${BASE}/api/images/upload`,   // POST multipart/form-data  key="file"
        delete: `${BASE}/api/images/delete`,   // DELETE ?publicId=...
    },

    // ── Auth ──────────────────────────────────────────────────────────────────
    // AuthController  →  /api/auth
    auth: {
        login:          `${BASE}/api/auth/login`,
        register:       `${BASE}/api/auth/register`,
        logout:         `${BASE}/api/auth/logout`,
        refreshToken:   `${BASE}/api/auth/refresh-token`,
    },
};

// ── 5. Response unwrapper ─────────────────────────────────────────────────────
// CategoryController / UserController / OrderController return:
//   ApiResponse<T>  →  { success: true, data: T, message: "..." }
// ProductController write endpoints return plain ResponseEntity<String>.
// This handles both shapes transparently.
function unwrapApiResponse(raw) {
    if (raw === null || raw === undefined) return raw;
    if (typeof raw === 'object' && !Array.isArray(raw)) {
        if ('data'    in raw) return raw.data;
        if ('result'  in raw) return raw.result;
        if ('payload' in raw) return raw.payload;
    }
    return raw; // plain array or string — return as-is
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

// ── 10. Debug log (safe to remove in production) ──────────────────────────────
console.log('[admin-api-fix] All routes loaded. BASE:', BASE || '(same origin)');
console.table({
    'Products list':    ADMIN_API.products.list,
    'Categories list':  ADMIN_API.categories.list,
    'Users list':       ADMIN_API.users.list,
    'Orders list':      ADMIN_API.orders.list,
    'Image upload':     ADMIN_API.images.upload,
    'Cart delete':      ADMIN_API.cart.deleteByUser('USER_ID'),
});