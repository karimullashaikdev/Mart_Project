// ════════════════════════════════════════════════════════════════════════════
//  admin.js  —  Full Admin Dashboard Logic
//  Depends on admin-api-fix.js being loaded first (token, ADMIN_API, helpers)
// ════════════════════════════════════════════════════════════════════════════

// ── Init ─────────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    setDateLabel();
    loadDashboard();
});

function setDateLabel() {
    const el = document.getElementById('dateLabel');
    if (el) {
        el.textContent = new Date().toLocaleDateString('en-IN', {
            weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
        });
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DASHBOARD
// ════════════════════════════════════════════════════════════════════════════
async function loadDashboard() {
    await Promise.all([
        loadDashboardUsers(),
        loadDashboardOrders(),
        loadDashboardProducts(),
        loadActivity(),
        loadSalesChart(),
    ]);
}

async function loadDashboardUsers() {
    try {
        const res = await fetch(`${ADMIN_API.users.list}?page=0&size=100`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) return;
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const users = Array.isArray(data) ? data : (data?.content || []);

        document.getElementById('statUserCount').textContent = (data?.totalElements ?? users.length).toLocaleString();
        document.getElementById('statUserChange').textContent = `${users.length} users loaded`;
        renderRecentUsers(users.slice(0, 5));

        const badge = document.getElementById('recentBadge');
        if (badge) badge.textContent = `${users.length} total`;
    } catch (e) {
        console.error('Dashboard users error:', e);
        document.getElementById('statUserChange').textContent = 'Failed to load';
    }
}

function renderRecentUsers(users) {
    const tbody = document.getElementById('recentUsersTable');
    if (!tbody) return;
    if (!users.length) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;padding:24px;color:var(--muted)">No users found</td></tr>`;
        return;
    }
    tbody.innerHTML = users.map(u => `
        <tr>
            <td>
                <div style="display:flex;align-items:center;gap:10px">
                    <div class="user-av">${(u.fullName || u.name || 'U')[0].toUpperCase()}</div>
                    <div style="font-weight:500">${escapeHtml(u.fullName || u.name || '—')}</div>
                </div>
            </td>
            <td style="color:var(--muted)">${escapeHtml(u.email || '—')}</td>
            <td><span class="badge ${u.role === 'ADMIN' ? 'badge-accent' : 'badge-green'}">${escapeHtml(u.role || '—')}</span></td>
            <td style="font-family:'Space Mono',monospace;font-size:11px;color:var(--muted)">${escapeHtml(String(u.id || '').slice(0, 8))}</td>
        </tr>
    `).join('');
}

async function loadDashboardOrders() {
    try {
        const res = await fetch(`${ADMIN_API.orders.list}?page=0&size=1`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) return;
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const total = data?.totalElements ?? (Array.isArray(data) ? data.length : 0);
        const revenue = Array.isArray(data?.content)
            ? data.content.reduce((s, o) => s + (o.totalAmount || o.total || 0), 0)
            : 0;

        document.getElementById('statOrderCount').textContent = total.toLocaleString();
        document.getElementById('statOrderChange').textContent = `${total} orders placed`;
        document.getElementById('statRevenue').textContent = `₹${revenue.toLocaleString('en-IN')}`;
        document.getElementById('statRevenueChange').textContent = 'From delivered orders';
    } catch (e) {
        console.error('Dashboard orders error:', e);
    }
}

async function loadDashboardProducts() {
    try {
        const res = await fetch(`${ADMIN_API.products.list}?page=0&size=1`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) return;
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const total = data?.totalElements ?? (Array.isArray(data) ? data.length : 0);

        document.getElementById('statProductCount').textContent = total.toLocaleString();
        document.getElementById('statProductChange').textContent = `${total} products listed`;

        const items = Array.isArray(data) ? data : (data?.content || []);
        const lowStock = items.filter(p => (p.availableQuantity ?? p.stock ?? 0) < 5);
        if (lowStock.length) renderLowStockAlerts(lowStock);
    } catch (e) {
        console.error('Dashboard products error:', e);
    }
}

async function loadActivity() {
    const list = document.getElementById('activityList');
    const badge = document.getElementById('activityBadge');
    try {
        const res = await fetch(`${ADMIN_API.orders.list}?page=0&size=8`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) throw new Error('Failed');
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const orders = Array.isArray(data) ? data : (data?.content || []);

        if (!orders.length) {
            list.innerHTML = `<div style="text-align:center;padding:32px;color:var(--muted);font-size:13px">No recent activity</div>`;
            if (badge) badge.textContent = '0 events';
            return;
        }
        if (badge) badge.textContent = `${orders.length} recent`;

        list.innerHTML = orders.map(o => {
            const statusColor = {
                PENDING: 'var(--yellow)', CONFIRMED: 'var(--accent)',
                PROCESSING: 'var(--accent)', DISPATCHED: 'var(--green)',
                OUT_FOR_DELIVERY: 'var(--green)', DELIVERED: 'var(--green)',
                CANCELLED: 'var(--red)'
            }[o.status] || 'var(--muted)';

            const time = o.createdAt || o.placedAt || o.orderDate || '';
            const timeStr = time ? new Date(time).toLocaleString('en-IN', {
                month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
            }) : '—';

            return `
                <div class="activity-item">
                    <div class="act-icon" style="background:rgba(59,130,246,0.1);color:var(--accent)">🛒</div>
                    <div class="act-content">
                        <div class="act-text">
                            Order <strong>${escapeHtml(o.orderNumber || String(o.id || '').slice(0, 8))}</strong>
                            — <span style="color:${statusColor};font-weight:600">${escapeHtml(o.status || '—')}</span>
                        </div>
                        <div class="act-time">${timeStr} · ₹${Number(o.totalAmount || o.total || 0).toLocaleString('en-IN')}</div>
                    </div>
                </div>
            `;
        }).join('');
    } catch (e) {
        if (list) list.innerHTML = `<div style="text-align:center;padding:32px;color:var(--muted);font-size:13px">Could not load activity</div>`;
        if (badge) badge.textContent = 'Error';
    }
}

function refreshActivity() { loadActivity(); }

async function loadSalesChart() {
    const chart = document.getElementById('salesChart');
    const badge = document.getElementById('salesBadge');
    try {
        const res = await fetch(`${ADMIN_API.products.list}?page=0&size=8`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) throw new Error('Failed');
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const products = Array.isArray(data) ? data : (data?.content || []);

        if (!products.length) {
            chart.innerHTML = `<div style="text-align:center;padding:24px;color:var(--muted);font-size:13px">No sales data</div>`;
            return;
        }
        if (badge) badge.textContent = `${products.length} products`;

        const max = Math.max(...products.map(p => p.availableQuantity ?? p.stock ?? 0), 1);
        chart.innerHTML = products.map(p => {
            const qty = p.availableQuantity ?? p.stock ?? 0;
            const pct = Math.max(4, Math.round((qty / max) * 100));
            return `
                <div class="bar-row">
                    <div class="bar-label" title="${escapeHtml(p.name || '—')}">${escapeHtml((p.name || '—').slice(0, 18))}</div>
                    <div class="bar-track"><div class="bar-fill" style="width:${pct}%"></div></div>
                    <div class="bar-val">${qty}</div>
                </div>
            `;
        }).join('');
    } catch (e) {
        if (chart) chart.innerHTML = `<div style="text-align:center;padding:24px;color:var(--muted);font-size:13px">Could not load sales</div>`;
    }
}

function renderLowStockAlerts(products) {
    const section = document.getElementById('lowStockSection');
    const list = document.getElementById('lowStockList');
    const badge = document.getElementById('lowStockBadge');
    if (!section || !list) return;
    section.style.display = 'block';
    if (badge) badge.textContent = `${products.length} products`;
    list.innerHTML = products.map(p => `
        <div style="display:flex;align-items:center;justify-content:space-between;padding:12px 20px;border-bottom:1px solid var(--border)">
            <div style="font-weight:500">${escapeHtml(p.name || '—')}</div>
            <div style="display:flex;align-items:center;gap:12px">
                <span style="font-size:12px;color:var(--muted)">${escapeHtml(p.sku || '—')}</span>
                <span class="badge badge-yellow">${p.availableQuantity ?? p.stock ?? 0} left</span>
            </div>
        </div>
    `).join('');
}

async function exportProductsCSV() {
    try {
        const res = await fetch(ADMIN_API.products.list, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) { alert('Failed to fetch products for export'); return; }
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const rows = Array.isArray(data) ? data : (data?.content || []);

        const header = ['ID', 'Name', 'SKU', 'Category', 'MRP', 'Selling Price', 'Stock', 'Active'];
        const lines = rows.map(p => [
            p.id, p.name, p.sku, p.categoryName || p.category || '',
            p.mrp ?? '', p.sellingPrice ?? '', p.availableQuantity ?? p.stock ?? 0, p.isActive
        ].map(v => `"${String(v).replace(/"/g, '""')}"`).join(','));

        const csv = [header.join(','), ...lines].join('\n');
        const a = document.createElement('a');
        a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
        a.download = `products_${Date.now()}.csv`;
        a.click();
    } catch (e) { alert('Export failed: ' + e.message); }
}

// ════════════════════════════════════════════════════════════════════════════
//  USERS
// ════════════════════════════════════════════════════════════════════════════
let allUsers = [];
let usersFetched = false;
let editingUserId = null;

async function openUsersModal() {
    document.getElementById('usersModal').classList.add('show');
    document.body.style.overflow = 'hidden';
    if (!usersFetched) {
        document.getElementById('allUsersTable').innerHTML =
            `<tr><td colspan="9" style="text-align:center;padding:32px;color:var(--muted)">Loading users...</td></tr>`;
        const users = await fetchUsers();
        if (users) { allUsers = users; usersFetched = true; }
    }
    renderUsersTable(allUsers);
}

function closeUsersModal() {
    document.getElementById('usersModal').classList.remove('show');
    document.body.style.overflow = '';
}

function closeModalOnBg(e) {
    if (e.target === document.getElementById('usersModal')) closeUsersModal();
}

async function fetchUsers() {
    try {
        const res = await fetch(`${ADMIN_API.users.list}?page=0&size=200`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.status === 401) { localStorage.clear(); window.location.href = 'login.html'; return null; }
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        return Array.isArray(data) ? data : (data?.content || []);
    } catch (e) {
        console.error('fetchUsers:', e);
        alert(e.message || 'Failed to fetch users');
        return null;
    }
}

function renderUsersTable(users) {
    const tbody = document.getElementById('allUsersTable');
    const countEl = document.getElementById('modalUserCount');
    if (countEl) countEl.textContent = `${users.length} user${users.length !== 1 ? 's' : ''} found`;
    if (!users.length) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:40px;color:var(--muted)">No users found</td></tr>`;
        return;
    }
    tbody.innerHTML = users.map(u => {
        const isDeleted = u.isDeleted || u.deleted || !!u.deletedAt;
        const isActive = u.isActive !== false;
        const created = u.createdAt ? new Date(u.createdAt).toLocaleDateString('en-IN') : '—';
        return `
            <tr>
                <td style="font-family:'Space Mono',monospace;font-size:11px;color:var(--muted)">${escapeHtml(String(u.id || '').slice(0, 8))}</td>
                <td>
                    <div style="display:flex;align-items:center;gap:10px">
                        <div class="user-av">${(u.fullName || u.name || 'U')[0].toUpperCase()}</div>
                        <div style="font-weight:500">${escapeHtml(u.fullName || u.name || '—')}</div>
                    </div>
                </td>
                <td style="color:var(--muted)">${escapeHtml(u.email || '—')}</td>
                <td style="color:var(--muted)">${escapeHtml(u.phone || '—')}</td>
                <td><span class="badge ${u.role === 'ADMIN' ? 'badge-accent' : 'badge-green'}">${escapeHtml(u.role || '—')}</span></td>
                <td><span class="badge ${isActive ? 'badge-green' : 'badge-yellow'}">${isActive ? 'Active' : 'Inactive'}</span></td>
                <td><span class="badge ${isDeleted ? 'badge-red' : 'badge-green'}">${isDeleted ? 'Deleted' : 'No'}</span></td>
                <td style="color:var(--muted);font-size:12px">${created}</td>
                <td style="text-align:center">
                    <div class="action-btns" style="justify-content:center;flex-wrap:wrap">
                        ${!isDeleted ? `<button class="act" onclick="openUserForm('${u.id}')">Edit</button>` : ''}
                        ${isDeleted ? `<button class="act" onclick="restoreUser('${u.id}')">Restore</button>` : ''}
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function openUserForm(userId) {
    const u = allUsers.find(x => String(x.id) === String(userId));
    if (!u) return;
    editingUserId = userId;
    document.getElementById('userFormTitle').textContent = 'Edit User_';
    document.getElementById('uf_name').value = u.fullName || u.name || '';
    document.getElementById('uf_email').value = u.email || '';
    document.getElementById('uf_phone').value = u.phone || '';
    document.getElementById('uf_isActive').value = String(u.isActive !== false);
    document.getElementById('userFormError').style.display = 'none';
    document.getElementById('userFormModal').classList.add('show');
}

function closeUserForm() {
    document.getElementById('userFormModal').classList.remove('show');
    editingUserId = null;
}

function closeUserFormOnBg(e) {
    if (e.target === document.getElementById('userFormModal')) closeUserForm();
}

async function submitUserForm() {
    const name = document.getElementById('uf_name').value.trim();
    const email = document.getElementById('uf_email').value.trim();
    const phone = document.getElementById('uf_phone').value.trim();
    const isActive = document.getElementById('uf_isActive').value === 'true';
    const errEl = document.getElementById('userFormError');
    const btn = document.getElementById('userSubmitBtn');

    errEl.style.display = 'none';
    if (!name) { errEl.textContent = 'Full name is required.'; errEl.style.display = 'block'; return; }

    btn.disabled = true;
    document.getElementById('userBtnText').textContent = 'Saving...';

    try {
        const res = await fetch(ADMIN_API.users.update(editingUserId), {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName: name, email, phone, isActive })
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchUsers();
        if (fresh) { allUsers = fresh; usersFetched = true; }
        closeUserForm();
        renderUsersTable(allUsers);
        alert('User updated successfully');
    } catch (e) {
        errEl.textContent = e.message || 'Failed to update user';
        errEl.style.display = 'block';
    } finally {
        btn.disabled = false;
        document.getElementById('userBtnText').textContent = 'Save Changes';
    }
}

async function restoreUser(userId) {
    if (!confirm('Restore this user?')) return;
    try {
        const res = await fetch(ADMIN_API.users.restore(userId), {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchUsers();
        if (fresh) { allUsers = fresh; usersFetched = true; }
        renderUsersTable(allUsers);
        alert('User restored successfully');
    } catch (e) { alert(e.message || 'Failed to restore user'); }
}

// ════════════════════════════════════════════════════════════════════════════
//  CATEGORIES
//
//  DTOs used:
//    CreateCategoryDto  → { name*, imageUrl, isActive, sortOrder, parentId }
//    UpdateCategoryDto  → { name, imageUrl, isActive, sortOrder, parentId }
//    CategoryFilterDto  → query params: name, isActive, isDeleted, parentId
//    ReorderCategoriesDto → { orderedIds: UUID[] }
//
//  Endpoints:
//    POST   /api/categories              createCategory
//    PATCH  /api/categories/{id}         updateCategory
//    DELETE /api/categories/{id}         softDelete  (blocked if active products/children exist)
//    PATCH  /api/categories/{id}/restore restore
//    PATCH  /api/categories/reorder      reorder
//    GET    /api/categories/admin/all    listAll (admin, includes deleted, supports filter params)
// ════════════════════════════════════════════════════════════════════════════
let allCategories = [];
let categoriesFetched = false;
let editingCategoryId = null;
let deletingCategoryId = null;

// ── Open modal ────────────────────────────────────────────────────────────────
async function openCategoriesModal() {
    document.getElementById('categoriesModal').classList.add('show');
    document.body.style.overflow = 'hidden';

    if (!categoriesFetched) {
        document.getElementById('allCategoriesTable').innerHTML =
            `<tr><td colspan="8" style="text-align:center;padding:32px;color:var(--muted)">Loading categories...</td></tr>`;
        const cats = await fetchCategoriesFromApi();
        if (cats) { allCategories = cats; categoriesFetched = true; }
    }
    renderCategoriesTable(allCategories);
    populateCategoryParentFilter(allCategories);
}

function closeCategoriesModal() {
    document.getElementById('categoriesModal').classList.remove('show');
    document.body.style.overflow = '';
}

function closeCategoriesModalOnBg(e) {
    if (e.target === document.getElementById('categoriesModal')) closeCategoriesModal();
}

// ── Fetch  GET /api/categories/admin/all  (CategoryFilterDto as query params) ─
// Supports: name, isActive, isDeleted, parentId
async function fetchCategoriesFromApi(filters = {}) {
    try {
        // Build query string from CategoryFilterDto fields
        const params = new URLSearchParams();
        if (filters.name)      params.set('name', filters.name);
        if (filters.isActive   !== undefined && filters.isActive   !== '') params.set('isActive', filters.isActive);
        if (filters.isDeleted  !== undefined && filters.isDeleted  !== '') params.set('isDeleted', filters.isDeleted);
        if (filters.parentId   && filters.parentId !== 'ROOT')             params.set('parentId', filters.parentId);

        const qs = params.toString() ? `?${params.toString()}` : '';
        const res = await fetch(`${ADMIN_API.categories.adminAll}${qs}`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.status === 401) { localStorage.clear(); window.location.href = 'login.html'; return null; }
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        return Array.isArray(data) ? data : (data?.content || []);
    } catch (e) {
        console.error('fetchCategories:', e);
        alert(e.message || 'Failed to fetch categories');
        return null;
    }
}

// ── Render table ──────────────────────────────────────────────────────────────
// CategoryResponseDto: { id, name, slug, imageUrl, isActive, sortOrder, parentId }
function renderCategoriesTable(cats) {
    const tbody = document.getElementById('allCategoriesTable');
    const countEl = document.getElementById('modalCategoryCount');
    if (countEl) countEl.textContent = `${cats.length} categor${cats.length !== 1 ? 'ies' : 'y'} found`;

    if (!cats.length) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center;padding:40px;color:var(--muted)">No categories found</td></tr>`;
        return;
    }

    tbody.innerHTML = cats.map((c, idx) => {
        const isDeleted = c.isDeleted || c.deleted || !!c.deletedAt;
        const isActive  = c.isActive !== false;
        // parentId is a UUID in CategoryResponseDto — look up name from local cache
        const parentName = allCategories.find(x => String(x.id) === String(c.parentId))?.name
                        || (c.parentId ? 'Has parent' : '—');

        return `
            <tr data-id="${c.id}">
                <td style="color:var(--muted);font-size:12px">⠿ ${c.sortOrder ?? idx + 1}</td>
                <td style="font-weight:600">${escapeHtml(c.name || '—')}</td>
                <td style="color:var(--muted);font-size:12px">${escapeHtml(c.slug || '—')}</td>
                <td>
                    ${c.imageUrl
                        ? `<img src="${escapeHtml(c.imageUrl)}" style="width:36px;height:36px;border-radius:8px;object-fit:cover;border:1px solid var(--border)">`
                        : `<div style="width:36px;height:36px;border-radius:8px;background:var(--surface2);border:1px solid var(--border);display:grid;place-items:center;font-size:16px">🗂️</div>`
                    }
                </td>
                <td><span class="badge ${isActive ? 'badge-green' : 'badge-yellow'}">${isActive ? 'Yes' : 'No'}</span></td>
                <td style="color:var(--muted);font-size:12px">${escapeHtml(String(parentName))}</td>
                <td style="font-family:'Space Mono',monospace;font-size:11px;color:var(--muted)">${escapeHtml(String(c.id || '').slice(0, 8))}</td>
                <td style="text-align:center">
                    <div class="action-btns" style="justify-content:center;flex-wrap:wrap">
                        ${!isDeleted
                            ? `<button class="act" onclick="openEditCategoryForm('${c.id}')">Edit</button>
                               <button class="act del" onclick="openDeleteCategoryConfirm('${c.id}','${escapeHtml(c.name || '')}')">🗑 Delete</button>`
                            : `<button class="act" onclick="restoreCategory('${c.id}')">Restore</button>`
                        }
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

// ── Client-side filtering using CategoryFilterDto fields ──────────────────────
// The server supports these same fields as query params (sent in fetchCategoriesFromApi).
// We do client-side filtering here for instant UX without extra API calls.
function filterCategories() {
    const name      = (document.getElementById('categorySearchInput').value || '').toLowerCase().trim();
    const isActive  = document.getElementById('categoryActiveFilter').value;   // '' | 'true' | 'false'
    const isDeleted = document.getElementById('categoryDeletedFilter').value;  // '' | 'true' | 'false'
    const parentVal = document.getElementById('categoryParentFilter').value;   // '' | 'ROOT' | UUID

    let results = [...allCategories];

    // CategoryFilterDto.name — substring match on name
    if (name) results = results.filter(c => (c.name || '').toLowerCase().includes(name));

    // CategoryFilterDto.isActive
    if (isActive !== '') results = results.filter(c => String(c.isActive !== false) === isActive);

    // CategoryFilterDto.isDeleted
    if (isDeleted !== '') {
        results = results.filter(c => {
            const deleted = !!(c.isDeleted || c.deleted || c.deletedAt);
            return String(deleted) === isDeleted;
        });
    }

    // CategoryFilterDto.parentId — 'ROOT' means parentId is null (root categories)
    if (parentVal === 'ROOT') {
        results = results.filter(c => !c.parentId);
    } else if (parentVal) {
        results = results.filter(c => String(c.parentId) === parentVal);
    }

    renderCategoriesTable(results);
}

function clearCategoryFilters() {
    document.getElementById('categorySearchInput').value = '';
    document.getElementById('categoryActiveFilter').value = '';
    document.getElementById('categoryDeletedFilter').value = '';
    document.getElementById('categoryParentFilter').value = '';
    renderCategoriesTable(allCategories);
}

function populateCategoryParentFilter(cats) {
    const sel = document.getElementById('categoryParentFilter');
    if (!sel) return;
    const rootCats = cats.filter(c => !c.parentId && !(c.isDeleted || c.deleted));
    sel.innerHTML = `<option value="">All Parents</option><option value="ROOT">Root Categories</option>` +
        rootCats.map(c => `<option value="${c.id}">${escapeHtml(c.name)}</option>`).join('');
}

// ── Open Add Category Form ────────────────────────────────────────────────────
// Maps to CreateCategoryDto: { name*, imageUrl, isActive, sortOrder, parentId }
function openCategoryForm() {
    editingCategoryId = null;
    document.getElementById('categoryFormTitle').textContent = 'Add Category_';
    document.getElementById('categoryBtnText').textContent = 'Add Category';

    // Reset all CreateCategoryDto fields
    document.getElementById('cf_name').value = '';
    document.getElementById('cf_isActive').value = 'true';
    document.getElementById('cf_sortOrder').value = '';
    document.getElementById('cf_parentId').value = '';
    document.getElementById('cf_imageUrl').value = '';

    document.getElementById('categoryImagePreviewWrap').style.display = 'none';
    document.getElementById('categorySelectedFileName').style.display = 'none';
    document.getElementById('categoryFormError').style.display = 'none';

    populateCategoryParentSelect();
    document.getElementById('categoryFormModal').classList.add('show');
}

// ── Open Edit Category Form ───────────────────────────────────────────────────
// Maps to UpdateCategoryDto: { name, imageUrl, isActive, sortOrder, parentId }
// CategoryResponseDto fields pre-filled: id, name, slug (read-only), imageUrl,
//   isActive, sortOrder, parentId
function openEditCategoryForm(categoryId) {
    const c = allCategories.find(x => String(x.id) === String(categoryId));
    if (!c) return;
    editingCategoryId = categoryId;
    document.getElementById('categoryFormTitle').textContent = 'Edit Category_';
    document.getElementById('categoryBtnText').textContent = 'Save Changes';

    // Pre-fill from CategoryResponseDto
    document.getElementById('cf_name').value      = c.name      || '';
    document.getElementById('cf_isActive').value  = String(c.isActive !== false);
    document.getElementById('cf_sortOrder').value = c.sortOrder  ?? '';
    document.getElementById('cf_parentId').value  = c.parentId  || '';
    document.getElementById('cf_imageUrl').value  = c.imageUrl  || '';

    document.getElementById('categoryFormError').style.display = 'none';
    document.getElementById('categorySelectedFileName').style.display = 'none';

    // Show existing image preview if available
    const previewWrap = document.getElementById('categoryImagePreviewWrap');
    const previewImg  = document.getElementById('categoryImagePreview');
    if (c.imageUrl) {
        previewWrap.style.display = 'block';
        previewImg.src = c.imageUrl;
    } else {
        previewWrap.style.display = 'none';
    }

    populateCategoryParentSelect(c.parentId);
    document.getElementById('categoryFormModal').classList.add('show');
}

function closeCategoryForm() {
    document.getElementById('categoryFormModal').classList.remove('show');
    editingCategoryId = null;
}

function closeCategoryFormOnBg(e) {
    if (e.target === document.getElementById('categoryFormModal')) closeCategoryForm();
}

// Populate parent select — exclude self to prevent circular dependency
function populateCategoryParentSelect(selectedId = '') {
    const sel = document.getElementById('cf_parentId');
    if (!sel) return;
    sel.innerHTML = `<option value="">No Parent (Root Category)</option>` +
        allCategories
            .filter(c => !(c.isDeleted || c.deleted) && String(c.id) !== String(editingCategoryId))
            .map(c => `<option value="${c.id}" ${String(c.id) === String(selectedId) ? 'selected' : ''}>${escapeHtml(c.name)}</option>`)
            .join('');
}

// ── Image upload for category ─────────────────────────────────────────────────
async function handleCategoryImageUpload(input) {
    const file = input.files?.[0];
    if (!file) return;

    const nameEl = document.getElementById('categorySelectedFileName');
    const nameText = document.getElementById('categoryFileNameText');
    const iconEl = document.getElementById('categoryUploadIcon');
    const textEl = document.getElementById('categoryUploadText');

    if (nameEl && nameText) { nameEl.style.display = 'block'; nameText.textContent = file.name; }
    if (iconEl) iconEl.textContent = '⏳';
    if (textEl) textEl.textContent = 'Uploading...';

    try {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch(ADMIN_API.images.upload, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token },
            body: formData
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const data = await res.json();
        const url = data?.imageUrl || data?.url || data?.data?.imageUrl;
        if (!url) throw new Error('No URL returned');

        // CreateCategoryDto / UpdateCategoryDto only has imageUrl — store it
        document.getElementById('cf_imageUrl').value = url;

        const previewWrap = document.getElementById('categoryImagePreviewWrap');
        const previewImg  = document.getElementById('categoryImagePreview');
        if (previewWrap && previewImg) { previewWrap.style.display = 'block'; previewImg.src = url; }
        if (iconEl) iconEl.textContent = '✓';
        if (textEl) textEl.textContent = 'Image uploaded!';
        setTimeout(() => {
            if (iconEl) iconEl.textContent = '🖼️';
            if (textEl) textEl.textContent = 'Click to upload category image';
        }, 2500);
    } catch (e) {
        alert('Image upload failed: ' + e.message);
        if (iconEl) iconEl.textContent = '🖼️';
        if (textEl) textEl.textContent = 'Click to upload category image';
    }
}

// ── Submit form ───────────────────────────────────────────────────────────────
// POST /api/categories       body: CreateCategoryDto  → { name*, imageUrl, isActive, sortOrder, parentId }
// PATCH /api/categories/{id} body: UpdateCategoryDto  → { name, imageUrl, isActive, sortOrder, parentId }
async function submitCategoryForm() {
    const name       = document.getElementById('cf_name').value.trim();
    const isActive   = document.getElementById('cf_isActive').value === 'true';
    const sortOrder  = document.getElementById('cf_sortOrder').value;
    const parentId   = document.getElementById('cf_parentId').value  || null;  // UUID or null
    const imageUrl   = document.getElementById('cf_imageUrl').value  || null;

    const errEl  = document.getElementById('categoryFormError');
    const btn    = document.getElementById('categorySubmitBtn');
    const isEdit = editingCategoryId !== null;

    errEl.style.display = 'none';

    // @NotBlank on CreateCategoryDto.name
    if (!name) { errEl.textContent = 'Category name is required.'; errEl.style.display = 'block'; return; }

    btn.disabled = true;
    document.getElementById('categoryBtnText').textContent = isEdit ? 'Saving...' : 'Adding...';

    // Build payload matching CreateCategoryDto / UpdateCategoryDto exactly
    const payload = {
        name,
        isActive,
        parentId,   // UUID (string) or null
        imageUrl,   // URL string or null
    };
    // sortOrder is Integer — include only if provided
    if (sortOrder !== '' && sortOrder !== null && sortOrder !== undefined) {
        payload.sortOrder = Number(sortOrder);
    }

    try {
        const url    = isEdit ? ADMIN_API.categories.update(editingCategoryId) : ADMIN_API.categories.create;
        const method = isEdit ? 'PATCH' : 'POST';
        const res = await fetch(url, {
            method,
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));

        // Refresh local cache from server
        const fresh = await fetchCategoriesFromApi();
        if (fresh) { allCategories = fresh; categoriesFetched = true; }
        closeCategoryForm();
        renderCategoriesTable(allCategories);
        populateCategoryParentFilter(allCategories);
        alert(isEdit ? 'Category updated successfully' : 'Category created successfully');
    } catch (e) {
        errEl.textContent = e.message || 'Failed to save category';
        errEl.style.display = 'block';
    } finally {
        btn.disabled = false;
        document.getElementById('categoryBtnText').textContent = isEdit ? 'Save Changes' : 'Add Category';
    }
}

// ── Delete ────────────────────────────────────────────────────────────────────
// DELETE /api/categories/{id}
// Blocked server-side if active products or child categories still exist
function openDeleteCategoryConfirm(categoryId, categoryName) {
    deletingCategoryId = String(categoryId);
    document.getElementById('deleteCategoryName').textContent = categoryName;
    document.getElementById('deleteCategoryModal').classList.add('show');
}

function closeDeleteCategoryModal() {
    document.getElementById('deleteCategoryModal').classList.remove('show');
    deletingCategoryId = null;
}

function closeDeleteCategoryModalOnBg(e) {
    if (e.target === document.getElementById('deleteCategoryModal')) closeDeleteCategoryModal();
}

async function confirmDeleteCategory() {
    if (!deletingCategoryId) return;
    const btn = document.getElementById('confirmDeleteCategoryBtn');
    btn.disabled = true; btn.textContent = 'Deleting...';
    try {
        const res = await fetch(ADMIN_API.categories.delete(deletingCategoryId), {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchCategoriesFromApi();
        if (fresh) { allCategories = fresh; categoriesFetched = true; }
        closeDeleteCategoryModal();
        renderCategoriesTable(allCategories);
        populateCategoryParentFilter(allCategories);
        alert('Category deleted successfully');
    } catch (e) {
        // Server blocks delete if active products/children exist — surface the message
        alert('Delete failed: ' + (e.message || 'Unknown error'));
    } finally {
        btn.disabled = false; btn.textContent = 'Delete';
    }
}

// ── Restore ───────────────────────────────────────────────────────────────────
// PATCH /api/categories/{id}/restore
async function restoreCategory(categoryId) {
    if (!confirm('Restore this category?')) return;
    try {
        const res = await fetch(ADMIN_API.categories.restore(categoryId), {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchCategoriesFromApi();
        if (fresh) { allCategories = fresh; categoriesFetched = true; }
        renderCategoriesTable(allCategories);
        populateCategoryParentFilter(allCategories);
        alert('Category restored successfully');
    } catch (e) { alert(e.message || 'Failed to restore category'); }
}

// ── Reorder ───────────────────────────────────────────────────────────────────
// PATCH /api/categories/reorder
// Body: ReorderCategoriesDto → { orderedIds: UUID[] }
// @NotNull @NotEmpty on orderedIds
async function saveCategoryReorder() {
    const rows = document.querySelectorAll('#allCategoriesTable tr[data-id]');
    const orderedIds = Array.from(rows).map(r => r.dataset.id).filter(Boolean);

    if (!orderedIds.length) { alert('No categories to reorder'); return; }

    try {
        const res = await fetch(ADMIN_API.categories.reorder, {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            // ReorderCategoriesDto shape exactly
            body: JSON.stringify({ orderedIds })
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));

        // Refresh to reflect server-assigned sortOrder values
        const fresh = await fetchCategoriesFromApi();
        if (fresh) { allCategories = fresh; categoriesFetched = true; }
        renderCategoriesTable(allCategories);
        alert('Category order saved successfully');
    } catch (e) { alert(e.message || 'Failed to save order'); }
}

// ── Populate category select inside product form ──────────────────────────────
async function populateProductCategorySelect(selectedId = '') {
    const sel = document.getElementById('pf_categoryId');
    if (!sel) return;
    if (!allCategories || !allCategories.length) {
        const cats = await fetchCategoriesFromApi();
        if (cats) { allCategories = cats; categoriesFetched = true; }
    }
    sel.innerHTML = '<option value="">Select Category</option>' +
        (allCategories || [])
            .filter(c => !(c.isDeleted || c.deleted || c.deletedAt))
            .map(c => `<option value="${c.id}" ${String(c.id) === String(selectedId) ? 'selected' : ''}>${escapeHtml(c.name || 'Category')}</option>`)
            .join('');
}

// ════════════════════════════════════════════════════════════════════════════
//  ORDERS
// ════════════════════════════════════════════════════════════════════════════
let allOrders = [];
let ordersFetched = false;

async function openOrdersModal() {
    document.getElementById('ordersModal').classList.add('show');
    document.body.style.overflow = 'hidden';
    if (!ordersFetched) {
        document.getElementById('allOrdersTable').innerHTML =
            `<tr><td colspan="7" style="text-align:center;padding:32px;color:var(--muted)">Loading orders...</td></tr>`;
        const orders = await fetchOrders();
        if (orders) { allOrders = orders; ordersFetched = true; }
    }
    renderOrdersTable(allOrders);
}

function closeOrdersModal() {
    document.getElementById('ordersModal').classList.remove('show');
    document.body.style.overflow = '';
}

function closeOrdersModalOnBg(e) {
    if (e.target === document.getElementById('ordersModal')) closeOrdersModal();
}

async function fetchOrders() {
    try {
        const res = await fetch(`${ADMIN_API.orders.list}?page=0&size=200`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.status === 401) { localStorage.clear(); window.location.href = 'login.html'; return null; }
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        return Array.isArray(data) ? data : (data?.content || []);
    } catch (e) {
        console.error('fetchOrders:', e);
        alert(e.message || 'Failed to fetch orders');
        return null;
    }
}

function renderOrdersTable(orders) {
    const tbody = document.getElementById('allOrdersTable');
    const countEl = document.getElementById('modalOrderCount');
    if (countEl) countEl.textContent = `${orders.length} order${orders.length !== 1 ? 's' : ''} found`;
    if (!orders.length) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--muted)">No orders found</td></tr>`;
        return;
    }
    tbody.innerHTML = orders.map(o => {
        const statusColors = {
            PENDING: 'badge-yellow', CONFIRMED: 'badge-accent', PROCESSING: 'badge-accent',
            DISPATCHED: 'badge-green', OUT_FOR_DELIVERY: 'badge-green',
            DELIVERED: 'badge-green', CANCELLED: 'badge-red'
        };
        const statusCls = statusColors[o.status] || 'badge-yellow';
        const placed = o.createdAt || o.placedAt ? new Date(o.createdAt || o.placedAt).toLocaleDateString('en-IN') : '—';
        const customerName = o.customerName || o.userName || o.user?.fullName || o.user?.name || '—';
        const total = Number(o.totalAmount || o.total || 0).toLocaleString('en-IN');
        return `
            <tr>
                <td style="font-family:'Space Mono',monospace;font-size:11px;color:var(--muted)">#${escapeHtml(String(o.id || '').slice(0, 8))}</td>
                <td style="font-weight:600">${escapeHtml(o.orderNumber || '—')}</td>
                <td>${escapeHtml(customerName)}</td>
                <td><span class="badge ${statusCls}">${escapeHtml(o.status || '—')}</span></td>
                <td>₹${total}</td>
                <td style="color:var(--muted);font-size:12px">${placed}</td>
                <td style="text-align:center">
                    <div class="action-btns" style="justify-content:center;flex-wrap:wrap">
                        <select onchange="updateOrderStatus('${o.id}', this.value)" style="background:var(--bg);border:1px solid var(--border);border-radius:6px;padding:4px 8px;color:var(--text);font-size:12px;cursor:pointer">
                            <option value="">Update Status</option>
                            <option value="confirm">Confirm</option>
                            <option value="processing">Processing</option>
                            <option value="dispatch">Dispatch</option>
                            <option value="out-for-delivery">Out For Delivery</option>
                            <option value="deliver">Deliver</option>
                        </select>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

async function updateOrderStatus(orderId, action) {
    if (!action) return;
    const actorId = getActorId();
    const urlMap = {
        'confirm':           ADMIN_API.orders.confirm(orderId),
        'processing':        ADMIN_API.orders.processing(orderId),
        'dispatch':          ADMIN_API.orders.dispatch(orderId),
        'out-for-delivery':  ADMIN_API.orders.outForDelivery(orderId),
        'deliver':           ADMIN_API.orders.deliver(orderId),
    };
    const url = urlMap[action];
    if (!url) return;
    try {
        const res = await fetch(url, {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token, 'X-Actor-Id': actorId }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchOrders();
        if (fresh) { allOrders = fresh; ordersFetched = true; }
        renderOrdersTable(allOrders);
        alert('Order status updated successfully');
    } catch (e) { alert(e.message || 'Failed to update order status'); }
}

function filterOrders() {
    const q = (document.getElementById('orderSearch').value || '').toLowerCase();
    const status = document.getElementById('orderStatusFilter').value;
    let results = [...allOrders];
    if (q) results = results.filter(o =>
        (o.orderNumber || '').toLowerCase().includes(q) ||
        (o.customerName || o.userName || '').toLowerCase().includes(q)
    );
    if (status) results = results.filter(o => o.status === status);
    renderOrdersTable(results);
    const countEl = document.getElementById('modalOrderCount');
    if (countEl) countEl.textContent = `${results.length} of ${allOrders.length} orders`;
}

function clearOrderFilters() {
    document.getElementById('orderSearch').value = '';
    document.getElementById('orderStatusFilter').value = '';
    renderOrdersTable(allOrders);
}

async function exportOrdersCSV() {
    const header = ['Order ID', 'Order Number', 'Customer', 'Status', 'Total', 'Placed At'];
    const lines = allOrders.map(o => [
        o.id, o.orderNumber || '',
        o.customerName || o.userName || '',
        o.status || '',
        o.totalAmount || o.total || 0,
        o.createdAt || o.placedAt || ''
    ].map(v => `"${String(v).replace(/"/g, '""')}"`).join(','));
    const csv = [header.join(','), ...lines].join('\n');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    a.download = `orders_${Date.now()}.csv`;
    a.click();
}

// ════════════════════════════════════════════════════════════════════════════
//  CART ADMIN
// ════════════════════════════════════════════════════════════════════════════
let cartAdminUsers = [];

async function openCartAdminModal() {
    document.getElementById('cartAdminModal').classList.add('show');
    document.body.style.overflow = 'hidden';
    await refreshCartAdminModal();
}

function closeCartAdminModal() {
    document.getElementById('cartAdminModal').classList.remove('show');
    document.body.style.overflow = '';
}

function closeCartAdminModalOnBg(e) {
    if (e.target === document.getElementById('cartAdminModal')) closeCartAdminModal();
}

async function refreshCartAdminModal() {
    document.getElementById('cartAdminTable').innerHTML =
        `<tr><td colspan="7" style="text-align:center;padding:32px;color:var(--muted)">Loading users...</td></tr>`;
    const users = await fetchUsers();
    if (users) {
        cartAdminUsers = users;
        renderCartAdminTable(cartAdminUsers);
        const countEl = document.getElementById('modalCartAdminCount');
        if (countEl) countEl.textContent = `${users.length} users — click Delete Cart to clear a user's cart`;
    }
}

function renderCartAdminTable(users) {
    const tbody = document.getElementById('cartAdminTable');
    if (!users.length) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;padding:40px;color:var(--muted)">No users found</td></tr>`;
        return;
    }
    tbody.innerHTML = users.map(u => {
        const isActive = u.isActive !== false;
        return `
            <tr>
                <td style="font-family:'Space Mono',monospace;font-size:11px;color:var(--muted)">${escapeHtml(String(u.id || '').slice(0, 8))}</td>
                <td style="font-weight:500">${escapeHtml(u.fullName || u.name || '—')}</td>
                <td style="color:var(--muted)">${escapeHtml(u.email || '—')}</td>
                <td style="color:var(--muted)">${escapeHtml(u.phone || '—')}</td>
                <td><span class="badge ${u.role === 'ADMIN' ? 'badge-accent' : 'badge-green'}">${escapeHtml(u.role || '—')}</span></td>
                <td><span class="badge ${isActive ? 'badge-green' : 'badge-yellow'}">${isActive ? 'Active' : 'Inactive'}</span></td>
                <td style="text-align:center">
                    <button class="act del" onclick="deleteUserCart('${u.id}','${escapeHtml(u.fullName || u.name || 'this user')}')">🗑 Delete Cart</button>
                </td>
            </tr>
        `;
    }).join('');
}

function filterCartAdminUsers() {
    const q = (document.getElementById('cartAdminSearch').value || '').toLowerCase();
    if (!q) { renderCartAdminTable(cartAdminUsers); return; }
    const filtered = cartAdminUsers.filter(u =>
        (u.fullName || u.name || '').toLowerCase().includes(q) ||
        (u.email || '').toLowerCase().includes(q) ||
        (u.phone || '').toLowerCase().includes(q) ||
        (u.id || '').toLowerCase().includes(q)
    );
    renderCartAdminTable(filtered);
}

async function deleteUserCart(userId, userName) {
    if (!confirm(`Delete cart for ${userName}? This cannot be undone.`)) return;
    try {
        const res = await fetch(ADMIN_API.cart.deleteByUser(userId), {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        alert(`Cart deleted for ${userName}`);
    } catch (e) { alert(e.message || 'Failed to delete cart'); }
}

// ════════════════════════════════════════════════════════════════════════════
//  STOCK MANAGER
// ════════════════════════════════════════════════════════════════════════════
function openStockInventoryModal() {
    alert('Stock Manager: Please share your StockController so the correct API routes can be wired up.');
}

// ════════════════════════════════════════════════════════════════════════════
//  PRODUCTS
// ════════════════════════════════════════════════════════════════════════════
let allProducts = [];
let productsFetched = false;
let filteredProducts = [];
let editingProductId = null;
let deletingProductId = null;

const UNIT_TYPES = ['PCS', 'KG', 'G', 'LTR', 'ML', 'DOZEN', 'PACK', 'BOX', 'PAIR', 'SET'];

function normalizeProduct(product) {
    if (!product) return product;
    return {
        ...product,
        id: product.id || '',
        name: product.name || '—',
        sku: product.sku || '—',
        barcode: product.barcode || '',
        description: product.description || '',
        categoryId: product.categoryId || '',
        category: product.categoryName || product.category || '—',
        mrp: product.mrp ?? null,
        sellingPrice: product.sellingPrice ?? product.price ?? null,
        taxPercent: product.taxPercent ?? null,
        unit: product.unit || '',
        unitValue: product.unitValue ?? null,
        images: Array.isArray(product.images) ? product.images : [],
        isActive: product.isActive !== false,
        isDeleted: product.isDeleted === true || product.deleted === true || !!product.deletedAt,
        availableQuantity: product.availableQuantity ?? product.stock ?? 0
    };
}

async function fetchProducts() {
    try {
        const res = await fetch(`${ADMIN_API.products.list}?page=0&size=200`, {
            headers: { 'Authorization': 'Bearer ' + token, 'X-Actor-Id': getActorId() }
        });
        if (res.status === 401) { localStorage.clear(); window.location.href = 'login.html'; return null; }
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const rows = Array.isArray(data) ? data : (data?.content || []);
        return rows.map(normalizeProduct);
    } catch (e) {
        console.error('fetchProducts:', e);
        alert(e.message || 'Failed to fetch products');
        return null;
    }
}

function categoryEmoji(cat) {
    if (!cat) return '📦';
    const c = String(cat).toLowerCase();
    if (c.includes('electron')) return '💻';
    if (c.includes('fashion') || c.includes('cloth')) return '👔';
    if (c.includes('home') || c.includes('furni')) return '🛋️';
    if (c.includes('sport')) return '⚽';
    if (c.includes('food') || c.includes('kitchen')) return '☕';
    if (c.includes('book')) return '📚';
    if (c.includes('phone') || c.includes('mobile')) return '📱';
    return '📦';
}

async function openProductsModal() {
    document.getElementById('productsModal').classList.add('show');
    document.body.style.overflow = 'hidden';
    if (!productsFetched) {
        document.getElementById('allProductsTable').innerHTML =
            `<tr><td colspan="9" style="text-align:center;padding:32px;color:var(--muted)">Loading products...</td></tr>`;
        const products = await fetchProducts();
        if (products) {
            allProducts = products;
            productsFetched = true;
            document.getElementById('statProductCount').textContent = products.length.toLocaleString();
            document.getElementById('statProductChange').textContent = `${products.length} products listed`;
        }
    }
    renderProductsModal(allProducts);
}

function closeProductsModal() {
    document.getElementById('productsModal').classList.remove('show');
    document.body.style.overflow = '';
}

function closeProductsModalOnBg(e) {
    if (e.target === document.getElementById('productsModal')) closeProductsModal();
}

function renderProductsModal(products) {
    filteredProducts = products || [];
    const countEl = document.getElementById('modalProductCount');
    if (countEl) countEl.textContent = `${filteredProducts.length} product${filteredProducts.length !== 1 ? 's' : ''} in catalogue`;
    populateCategoryFilter(filteredProducts);
    populateProductCategorySelect();
    renderProductsTable(filteredProducts);
}

function renderProductsTable(products) {
    const tbody = document.getElementById('allProductsTable');
    if (!products || !products.length) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:40px;color:var(--muted)">No products found</td></tr>`;
        return;
    }
    tbody.innerHTML = products.map(p => {
        const stock = p.availableQuantity ?? 0;
        const image = Array.isArray(p.images) && p.images.length ? p.images[0] : '';
        const isActive = p.isActive !== false;
        const isDeleted = p.isDeleted === true;
        let statusCls = 'badge-green', statusTxt = 'Active';
        if (isDeleted) { statusCls = 'badge-red'; statusTxt = 'Deleted'; }
        else if (!isActive) { statusCls = 'badge-yellow'; statusTxt = 'Inactive'; }
        else if (stock <= 0) { statusCls = 'badge-yellow'; statusTxt = 'Out of Stock'; }
        const actions = isDeleted
            ? `<div class="action-btns" style="justify-content:center">
                 <button class="act" onclick="restoreProduct('${p.id}')">Restore</button>
               </div>`
            : `<div class="action-btns" style="justify-content:center;flex-wrap:wrap">
                <button class="act" onclick="openEditForm('${p.id}')">Edit</button>
                <button class="act" onclick="toggleProductActive('${p.id}')" title="${isActive ? 'Deactivate' : 'Activate'}">⏻ ${isActive ? 'Deactivate' : 'Activate'}</button>
                <button class="act del" onclick="openDeleteConfirm('${p.id}','${escapeHtml(String(p.name))}')">🗑 Delete</button>
               </div>`;
        return `
            <tr>
                <td style="font-family:'Space Mono',monospace;font-size:12px;color:var(--muted)">#${escapeHtml(String(p.id).slice(0, 8))}</td>
                <td>${image
                    ? `<img src="${image}" alt="${escapeHtml(p.name)}" style="width:42px;height:42px;border-radius:10px;object-fit:cover;border:1px solid var(--border)">`
                    : `<div class="user-av" style="width:42px;height:42px;background:var(--accent-dim);color:var(--accent)">${categoryEmoji(p.category)}</div>`
                }</td>
                <td><div style="font-weight:600">${escapeHtml(p.name)}</div></td>
                <td style="color:var(--muted)">${escapeHtml(p.sku)}</td>
                <td style="color:var(--muted)">${escapeHtml(String(p.category || '—'))}</td>
                <td>₹${Number(p.sellingPrice ?? 0).toLocaleString('en-IN')}</td>
                <td>${stock}</td>
                <td><span class="badge ${statusCls}">${statusTxt}</span></td>
                <td style="text-align:center">${actions}</td>
            </tr>`;
    }).join('');
}

function populateCategoryFilter(products) {
    const sel = document.getElementById('categoryFilter');
    const current = sel.value;
    const localCats = [...new Set((products || []).map(p => p.categoryId || '').filter(Boolean))];
    sel.innerHTML = `<option value="">All Categories</option>` +
        localCats.map(cid => {
            const p = (products || []).find(x => String(x.categoryId) === String(cid));
            return `<option value="${cid}" ${String(cid) === String(current) ? 'selected' : ''}>${escapeHtml(p?.category || 'Category')}</option>`;
        }).join('');
}

function filterProducts() {
    const q = (document.getElementById('productSearch').value || '').trim().toLowerCase();
    const cat = document.getElementById('categoryFilter').value;
    let results = [...allProducts];
    if (q) results = results.filter(p => (p.name || '').toLowerCase().includes(q) || (p.sku || '').toLowerCase().includes(q));
    if (cat) results = results.filter(p => String(p.categoryId || '') === String(cat));
    renderProductsTable(results);
    document.getElementById('modalProductCount').textContent = `${results.length} of ${allProducts.length} products`;
}

function clearProductFilters() {
    document.getElementById('productSearch').value = '';
    document.getElementById('categoryFilter').value = '';
    renderProductsTable(allProducts);
    document.getElementById('modalProductCount').textContent = `${allProducts.length} products in catalogue`;
}

// ── Product image state ───────────────────────────────────────────────────────
let selectedProductFiles = [];
let productExistingImages = [];
let productUploadedImages = [];

function setProductUploadState(icon, textLabel, isBusy = false) {
    const iconEl = document.getElementById('productUploadIcon');
    const textEl = document.getElementById('productUploadText');
    const area = document.getElementById('productImgUploadArea');
    if (iconEl) iconEl.textContent = icon;
    if (textEl) textEl.textContent = textLabel;
    if (area) area.style.opacity = isBusy ? '0.7' : '1';
}

function syncProductUploadedImagesField() {
    const hidden = document.getElementById('pf_uploadedImages');
    if (hidden) hidden.value = JSON.stringify(productUploadedImages);
}

function getEffectiveProductImages() { return [...productExistingImages, ...productUploadedImages]; }

function resetProductImageState() {
    selectedProductFiles = []; productExistingImages = []; productUploadedImages = [];
    syncProductUploadedImagesField();
    const input = document.getElementById('pf_imageFiles');
    if (input) input.value = '';
    renderProductImagePreview();
    setProductUploadState('🖼️', 'Click to choose product images', false);
}

function removeProductImage(type, index) {
    if (type === 'existing') productExistingImages.splice(index, 1);
    else if (type === 'uploaded') { productUploadedImages.splice(index, 1); syncProductUploadedImagesField(); }
    else if (type === 'selected') { selectedProductFiles.splice(index, 1); const i = document.getElementById('pf_imageFiles'); if (i) i.value = ''; }
    renderProductImagePreview();
}

function handleProductFileSelect(input) {
    const files = Array.from(input.files || []);
    if (!files.length) return;
    selectedProductFiles = [...selectedProductFiles, ...files];
    renderProductImagePreview();
}

function renderProductImagePreview() {
    const wrap = document.getElementById('productImagePreviewWrap');
    const grid = document.getElementById('productImagePreviewGrid');
    const count = document.getElementById('productImageCount');
    const nameWrap = document.getElementById('productSelectedFileName');
    const nameText = document.getElementById('productFileNameText');
    if (!wrap || !grid || !count) return;

    const selectedNames = selectedProductFiles.map(f => f.name);
    if (nameWrap && nameText) {
        if (selectedNames.length) { nameWrap.style.display = 'block'; nameText.textContent = selectedNames.join(', '); }
        else { nameWrap.style.display = 'none'; nameText.textContent = ''; }
    }

    const existingHtml = productExistingImages.map((url, i) =>
        `<div style="position:relative">
            <img src="${url}" style="width:74px;height:74px;border-radius:10px;object-fit:cover;border:1px solid var(--border)">
            <button type="button" onclick="removeProductImage('existing',${i})" style="position:absolute;top:-6px;right:-6px;width:22px;height:22px;border:none;border-radius:50%;background:var(--red);color:white;cursor:pointer">×</button>
        </div>`).join('');

    const uploadedHtml = productUploadedImages.map((url, i) =>
        `<div style="position:relative">
            <img src="${url}" style="width:74px;height:74px;border-radius:10px;object-fit:cover;border:1px solid var(--border)">
            <button type="button" onclick="removeProductImage('uploaded',${i})" style="position:absolute;top:-6px;right:-6px;width:22px;height:22px;border:none;border-radius:50%;background:var(--red);color:white;cursor:pointer">×</button>
        </div>`).join('');

    const selectedHtml = selectedProductFiles.map((file, i) =>
        `<div style="position:relative;width:74px;height:74px;border-radius:10px;border:1px dashed var(--border);display:grid;place-items:center;color:var(--muted);font-size:11px;text-align:center;padding:6px;background:var(--surface2)">
            ${escapeHtml(file.name.slice(0, 18))}
            <button type="button" onclick="removeProductImage('selected',${i})" style="position:absolute;top:-6px;right:-6px;width:22px;height:22px;border:none;border-radius:50%;background:var(--red);color:white;cursor:pointer">×</button>
        </div>`).join('');

    const total = productExistingImages.length + productUploadedImages.length + selectedProductFiles.length;
    if (!total) { wrap.style.display = 'none'; grid.innerHTML = ''; count.textContent = '0 images'; return; }
    wrap.style.display = 'block';
    grid.innerHTML = existingHtml + uploadedHtml + selectedHtml;
    count.textContent = `${total} image${total !== 1 ? 's' : ''}`;
}

async function uploadProductImagesIfNeeded() {
    if (!selectedProductFiles.length) return;
    setProductUploadState('⏳', `Uploading ${selectedProductFiles.length} image(s)...`, true);
    for (const file of selectedProductFiles) {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch(ADMIN_API.images.upload, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token },
            body: formData
        });
        if (!res.ok) {
            let msg = 'Image upload failed';
            try { const err = await res.json(); msg = err?.message || err?.error || JSON.stringify(err); } catch { msg = await res.text() || msg; }
            throw new Error(msg);
        }
        const data = await res.json();
        const imageUrl = data?.imageUrl || data?.data?.imageUrl;
        if (!imageUrl) throw new Error('No image URL returned from upload API');
        productUploadedImages.push(imageUrl);
        syncProductUploadedImagesField();
        renderProductImagePreview();
    }
    selectedProductFiles = [];
    const input = document.getElementById('pf_imageFiles');
    if (input) input.value = '';
    renderProductImagePreview();
    setProductUploadState('✓', 'Images uploaded successfully', false);
    setTimeout(() => setProductUploadState('🖼️', 'Click to choose product images', false), 2500);
}

function populateUnitSelect(selectedUnit = '') {
    const sel = document.getElementById('pf_unit');
    if (!sel) return;
    sel.innerHTML = '<option value="">Select Unit</option>' +
        UNIT_TYPES.map(u => `<option value="${u}" ${u === selectedUnit ? 'selected' : ''}>${u}</option>`).join('');
}

function openProductForm() {
    editingProductId = null;
    document.getElementById('formModalTitle').textContent = 'Add Product_';
    document.getElementById('formBtnText').textContent = 'Add Product';
    ['pf_name','pf_sku','pf_barcode','pf_description','pf_mrp','pf_sellingPrice','pf_taxPercent','pf_unitValue'].forEach(id => {
        const el = document.getElementById(id); if (el) el.value = '';
    });
    document.getElementById('pf_categoryId').value = '';
    document.getElementById('pf_isActive').value = 'true';
    document.getElementById('formError').style.display = 'none';
    resetProductImageState();
    populateProductCategorySelect();
    populateUnitSelect();
    document.getElementById('productFormModal').classList.add('show');
}

async function openEditForm(productId) {
    const p = allProducts.find(x => String(x.id) === String(productId));
    if (!p) return;
    editingProductId = productId;
    document.getElementById('formModalTitle').textContent = 'Edit Product_';
    document.getElementById('formBtnText').textContent = 'Save Changes';
    document.getElementById('pf_name').value = p.name || '';
    document.getElementById('pf_sku').value = p.sku || '';
    document.getElementById('pf_barcode').value = p.barcode || '';
    document.getElementById('pf_description').value = p.description || '';
    document.getElementById('pf_mrp').value = p.mrp ?? '';
    document.getElementById('pf_sellingPrice').value = p.sellingPrice ?? '';
    document.getElementById('pf_taxPercent').value = p.taxPercent ?? '';
    document.getElementById('pf_unitValue').value = p.unitValue ?? '';
    document.getElementById('pf_isActive').value = String(p.isActive !== false);
    document.getElementById('formError').style.display = 'none';
    await populateProductCategorySelect(p.categoryId || '');
    populateUnitSelect(p.unit || '');
    resetProductImageState();
    productExistingImages = Array.isArray(p.images) ? [...p.images] : [];
    renderProductImagePreview();
    document.getElementById('productFormModal').classList.add('show');
}

function closeProductForm() {
    document.getElementById('productFormModal').classList.remove('show');
    resetProductImageState();
}

function closeFormModalOnBg(e) {
    if (e.target === document.getElementById('productFormModal')) closeProductForm();
}

async function submitProductForm() {
    const name = document.getElementById('pf_name').value.trim();
    const sku = document.getElementById('pf_sku').value.trim();
    const barcode = document.getElementById('pf_barcode').value.trim();
    const description = document.getElementById('pf_description').value.trim();
    const categoryId = document.getElementById('pf_categoryId').value;
    const unit = document.getElementById('pf_unit').value;
    const mrpRaw = document.getElementById('pf_mrp').value;
    const sellingRaw = document.getElementById('pf_sellingPrice').value;
    const taxRaw = document.getElementById('pf_taxPercent').value;
    const unitValueRaw = document.getElementById('pf_unitValue').value;
    const isActive = document.getElementById('pf_isActive').value === 'true';

    const errEl = document.getElementById('formError');
    const btn = document.getElementById('formSubmitBtn');
    const isEdit = editingProductId !== null;

    errEl.style.display = 'none';

    if (!name)       { errEl.textContent = 'Product name is required.'; errEl.style.display = 'block'; return; }
    if (!sku)        { errEl.textContent = 'SKU is required.';          errEl.style.display = 'block'; return; }
    if (!categoryId) { errEl.textContent = 'Category is required.';     errEl.style.display = 'block'; return; }
    if (!unit)       { errEl.textContent = 'Unit is required.';         errEl.style.display = 'block'; return; }

    const actorId = getActorId();
    if (!actorId) { errEl.textContent = 'Actor ID missing — please log in again.'; errEl.style.display = 'block'; return; }

    btn.disabled = true;
    document.getElementById('formBtnText').textContent = isEdit ? 'Saving...' : 'Adding...';

    try {
        await uploadProductImagesIfNeeded();
        const images = getEffectiveProductImages();
        if (!isEdit && images.length < 1) throw new Error('Please upload at least 1 product image.');
        if (images.length > 10) throw new Error('Maximum 10 images allowed.');

        const payload = {
            name,
            description: description || null,
            sku,
            barcode: barcode || null,
            mrp:          mrpRaw       === '' ? null : Number(mrpRaw),
            sellingPrice: sellingRaw   === '' ? null : Number(sellingRaw),
            taxPercent:   taxRaw       === '' ? null : Number(taxRaw),
            unit,
            unitValue:    unitValueRaw === '' ? null : Number(unitValueRaw),
            images,
            isActive,
            categoryId
        };

        const url    = isEdit ? ADMIN_API.products.update(editingProductId) : ADMIN_API.products.create;
        const method = isEdit ? 'PUT' : 'POST';

        const res = await fetch(url, {
            method,
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json',
                'X-Actor-Id': actorId
            },
            body: JSON.stringify(payload)
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));

        const fresh = await fetchProducts();
        if (fresh) {
            allProducts = fresh; productsFetched = true;
            document.getElementById('statProductCount').textContent = fresh.length.toLocaleString();
            document.getElementById('statProductChange').textContent = `${fresh.length} products listed`;
        }
        closeProductForm();
        renderProductsModal(allProducts);
        alert(isEdit ? 'Product updated successfully' : 'Product created successfully');
    } catch (e) {
        errEl.textContent = e.message || 'Failed to save product';
        errEl.style.display = 'block';
    } finally {
        btn.disabled = false;
        document.getElementById('formBtnText').textContent = isEdit ? 'Save Changes' : 'Add Product';
    }
}

async function toggleProductActive(productId) {
    const actorId = getActorId();
    if (!actorId) { alert('Missing actor id — please log in again.'); return; }
    try {
        const res = await fetch(ADMIN_API.products.toggleActive(productId), {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token, 'X-Actor-Id': actorId }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchProducts();
        if (fresh) { allProducts = fresh; productsFetched = true; }
        renderProductsModal(allProducts);
    } catch (e) { alert(e.message || 'Failed to toggle product status'); }
}

async function restoreProduct(productId) {
    const actorId = getActorId();
    if (!actorId) { alert('Missing actor id — please log in again.'); return; }
    if (!confirm('Restore this product?')) return;
    try {
        const res = await fetch(ADMIN_API.products.restore(productId), {
            method: 'PATCH',
            headers: { 'Authorization': 'Bearer ' + token, 'X-Actor-Id': actorId }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchProducts();
        if (fresh) { allProducts = fresh; productsFetched = true; }
        renderProductsModal(allProducts);
        alert('Product restored successfully');
    } catch (e) { alert(e.message || 'Failed to restore product'); }
}

async function openBulkPriceModal() {
    document.getElementById('bulkPriceModal').classList.add('show');
    document.getElementById('bulkPriceRows').innerHTML = '';
    document.getElementById('bulkPriceError').style.display = 'none';
    addBulkPriceRow();
}

function closeBulkPriceModal() { document.getElementById('bulkPriceModal').classList.remove('show'); }

function closeBulkPriceModalOnBg(e) {
    if (e.target === document.getElementById('bulkPriceModal')) closeBulkPriceModal();
}

function addBulkPriceRow(productId = '', mrp = '', sellingPrice = '') {
    const container = document.getElementById('bulkPriceRows');
    const rowId = `bpr_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
    const div = document.createElement('div');
    div.id = rowId;
    div.style.cssText = 'display:grid;grid-template-columns:2fr 1fr 1fr auto;gap:10px;align-items:center;margin-bottom:10px';
    div.innerHTML = `
        <select class="bpr-product" style="background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:9px 12px;color:var(--text);font-family:Inter,sans-serif;font-size:13px;outline:none">
            <option value="">Select Product</option>
            ${allProducts.filter(p => !p.isDeleted).map(p =>
                `<option value="${p.id}" ${p.id === productId ? 'selected' : ''}>${escapeHtml(p.name)} (${escapeHtml(p.sku)})</option>`
            ).join('')}
        </select>
        <input type="number" class="bpr-mrp" min="0" step="0.01" placeholder="MRP" value="${mrp}"
            style="background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:9px 12px;color:var(--text);font-family:Inter,sans-serif;font-size:13px;outline:none" />
        <input type="number" class="bpr-selling" min="0" step="0.01" placeholder="Selling Price" value="${sellingPrice}"
            style="background:var(--bg);border:1px solid var(--border);border-radius:8px;padding:9px 12px;color:var(--text);font-family:Inter,sans-serif;font-size:13px;outline:none" />
        <button type="button" onclick="document.getElementById('${rowId}').remove()"
            style="background:var(--red-dim);border:1px solid rgba(239,68,68,0.3);border-radius:8px;padding:9px 12px;color:var(--red);cursor:pointer;font-size:14px">✕</button>
    `;
    container.appendChild(div);
}

async function submitBulkPrices() {
    const errEl = document.getElementById('bulkPriceError');
    const btn = document.getElementById('bulkPriceSubmitBtn');
    const actorId = getActorId();
    errEl.style.display = 'none';

    if (!actorId) { errEl.textContent = 'Actor ID missing — please log in again.'; errEl.style.display = 'block'; return; }

    const rows = document.querySelectorAll('#bulkPriceRows > div');
    if (!rows.length) { errEl.textContent = 'Add at least one row.'; errEl.style.display = 'block'; return; }

    const items = [];
    let hasError = false;
    rows.forEach(row => {
        const productId  = row.querySelector('.bpr-product')?.value;
        const mrpVal     = row.querySelector('.bpr-mrp')?.value;
        const sellingVal = row.querySelector('.bpr-selling')?.value;
        if (!productId) { hasError = true; return; }
        const item = { productId };
        if (mrpVal !== '')     item.mrp          = Number(mrpVal);
        if (sellingVal !== '') item.sellingPrice  = Number(sellingVal);
        items.push(item);
    });

    if (hasError)   { errEl.textContent = 'Please select a product for every row.'; errEl.style.display = 'block'; return; }
    if (!items.length) { errEl.textContent = 'No valid rows to submit.'; errEl.style.display = 'block'; return; }

    btn.disabled = true; btn.textContent = 'Updating...';

    try {
        const res = await fetch(ADMIN_API.products.bulkPriceUpdate, {
            method: 'PUT',
            headers: {
                'Authorization': 'Bearer ' + token,
                'Content-Type': 'application/json',
                'X-Actor-Id': actorId
            },
            body: JSON.stringify(items)
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));

        const fresh = await fetchProducts();
        if (fresh) { allProducts = fresh; productsFetched = true; }
        closeBulkPriceModal();
        renderProductsModal(allProducts);
        alert(`Bulk price update completed for ${items.length} product(s)`);
    } catch (e) {
        errEl.textContent = e.message || 'Bulk price update failed';
        errEl.style.display = 'block';
    } finally {
        btn.disabled = false; btn.textContent = 'Apply Price Updates';
    }
}

function openDeleteConfirm(productId, productName) {
    deletingProductId = String(productId);
    document.getElementById('deleteProductName').textContent = productName;
    document.getElementById('deleteModal').classList.add('show');
}

function closeDeleteModal() {
    document.getElementById('deleteModal').classList.remove('show');
    deletingProductId = null;
}

function closeDeleteModalOnBg(e) {
    if (e.target === document.getElementById('deleteModal')) closeDeleteModal();
}

async function confirmDelete() {
    if (!deletingProductId) return;
    const btn = document.getElementById('confirmDeleteBtn');
    const actorId = getActorId();
    if (!actorId) { alert('Missing actor id — please log in again.'); return; }
    btn.disabled = true; btn.textContent = 'Deleting...';
    try {
        const res = await fetch(ADMIN_API.products.delete(deletingProductId), {
            method: 'DELETE',
            headers: { 'Authorization': 'Bearer ' + token, 'X-Actor-Id': actorId }
        });
        if (!res.ok) throw new Error(parseApiError(await res.text()));
        const fresh = await fetchProducts();
        if (fresh) { allProducts = fresh; productsFetched = true; }
        closeDeleteModal();
        renderProductsModal(allProducts);
        alert('Product soft deleted successfully');
    } catch (e) {
        alert(e.message || 'Failed to delete product');
    } finally {
        btn.disabled = false; btn.textContent = 'Delete';
    }
}