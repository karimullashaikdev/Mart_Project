// ── Products state ─────────────────────────────────────────────────────────
let allProducts = [];
let productsFetched = false;
let filteredProducts = [];
let editingProductId = null;
let deletingProductId = null;

// ── Product helpers ────────────────────────────────────────────────────────
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
        const res = await fetch(ADMIN_API.products.list, {
            headers: {
                'Authorization': 'Bearer ' + token,
                'X-Actor-Id': getActorId()
            }
        });

        if (res.status === 401) {
            localStorage.clear();
            window.location.href = 'login.html';
            return null;
        }

        if (!res.ok) throw new Error(parseApiError(await res.text()));

        const raw = await res.json();
        const data = unwrapApiResponse(raw);
        const rows = Array.isArray(data)
            ? data
            : (Array.isArray(data?.content) ? data.content : []);

        return rows.map(normalizeProduct);
    } catch (err) {
        console.error('Failed to fetch products:', err);
        alert(err.message || 'Failed to fetch products');
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

// ── Products modal ─────────────────────────────────────────────────────────
async function openProductsModal() {
    document.getElementById('productsModal').classList.add('show');
    document.body.style.overflow = 'hidden';

    if (!productsFetched) {
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
    countEl.textContent = `${filteredProducts.length} product${filteredProducts.length !== 1 ? 's' : ''} in catalogue`;

    populateCategoryFilter(filteredProducts);
    populateProductCategorySelect();
    renderProductsTable(filteredProducts);
}

function renderProductsTable(products) {
    const tbody = document.getElementById('allProductsTable');

    if (!products || products.length === 0) {
        tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;padding:40px;color:var(--muted)">No products found</td></tr>`;
        return;
    }

    tbody.innerHTML = products.map((p) => {
        const stock = p.availableQuantity ?? 0;
        const image = Array.isArray(p.images) && p.images.length ? p.images[0] : '';
        const isActive = p.isActive !== false;
        const isDeleted = p.isDeleted === true;

        let statusCls = 'badge-green';
        let statusTxt = 'Active';

        if (isDeleted) {
            statusCls = 'badge-red';
            statusTxt = 'Deleted';
        } else if (!isActive) {
            statusCls = 'badge-yellow';
            statusTxt = 'Inactive';
        } else if (stock <= 0) {
            statusCls = 'badge-yellow';
            statusTxt = 'Out of Stock';
        }

        const actions = isDeleted
            ? `
                <div class="action-btns" style="justify-content:center">
                    <button class="act" onclick="restoreProduct('${p.id}')">Restore</button>
                </div>
            `
            : `
                <div class="action-btns" style="justify-content:center;flex-wrap:wrap">
                    <button class="act" onclick="openEditForm('${p.id}')">Edit</button>
                    <button class="act" onclick="toggleProductActive('${p.id}')">⏻ Toggle</button>
                    <button class="act del" onclick="openDeleteConfirm('${p.id}', '${escapeHtml(String(p.name))}')">🗑 Delete</button>
                </div>
            `;

        return `
            <tr>
                <td style="font-family:'Space Mono',monospace;font-size:12px;color:var(--muted)">#${escapeHtml(String(p.id).slice(0, 8))}</td>
                <td>
                    ${image
                        ? `<img src="${image}" alt="${escapeHtml(p.name)}" style="width:42px;height:42px;border-radius:10px;object-fit:cover;border:1px solid var(--border)" />`
                        : `<div class="user-av" style="width:42px;height:42px;background:var(--accent-dim);color:var(--accent)">${categoryEmoji(p.category)}</div>`
                    }
                </td>
                <td><div style="font-weight:600">${escapeHtml(p.name)}</div></td>
                <td style="color:var(--muted)">${escapeHtml(p.sku)}</td>
                <td style="color:var(--muted)">${escapeHtml(String(p.category || '—'))}</td>
                <td>₹${Number(p.sellingPrice ?? 0).toLocaleString('en-IN')}</td>
                <td>${stock}</td>
                <td><span class="badge ${statusCls}">${statusTxt}</span></td>
                <td style="text-align:center">${actions}</td>
            </tr>
        `;
    }).join('');
}

function populateCategoryFilter(products) {
    const sel = document.getElementById('categoryFilter');
    const current = sel.value;

    const localCats = [...new Set(
        (products || [])
            .map(p => p.categoryId || '')
            .filter(Boolean)
    )];

    const options = localCats.map(categoryId => {
        const product = (products || []).find(p => String(p.categoryId) === String(categoryId));
        const label = product?.category || 'Category';
        return `<option value="${categoryId}" ${String(categoryId) === String(current) ? 'selected' : ''}>${escapeHtml(label)}</option>`;
    }).join('');

    sel.innerHTML = `<option value="">All Categories</option>${options}`;
}

function filterProducts() {
    const q = (document.getElementById('productSearch').value || '').trim().toLowerCase();
    const cat = document.getElementById('categoryFilter').value;

    let results = [...allProducts];

    if (q) {
        results = results.filter(p =>
            String(p.name || '').toLowerCase().includes(q) ||
            String(p.sku || '').toLowerCase().includes(q)
        );
    }

    if (cat) {
        results = results.filter(p => String(p.categoryId || '') === String(cat));
    }

    renderProductsTable(results);
    document.getElementById('modalProductCount').textContent = `${results.length} of ${allProducts.length} products`;
}

function clearProductFilters() {
    document.getElementById('productSearch').value = '';
    document.getElementById('categoryFilter').value = '';
    renderProductsTable(allProducts);
    document.getElementById('modalProductCount').textContent = `${allProducts.length} products in catalogue`;
}

// ── Product image upload state ────────────────────────────────────────────
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

function getEffectiveProductImages() {
    return [...productExistingImages, ...productUploadedImages];
}

function resetProductImageState() {
    selectedProductFiles = [];
    productExistingImages = [];
    productUploadedImages = [];
    syncProductUploadedImagesField();

    const input = document.getElementById('pf_imageFiles');
    if (input) input.value = '';

    renderProductImagePreview();
    setProductUploadState('🖼️', 'Click to choose product images', false);
}

function removeProductImage(type, index) {
    if (type === 'existing') {
        productExistingImages.splice(index, 1);
    } else if (type === 'uploaded') {
        productUploadedImages.splice(index, 1);
        syncProductUploadedImagesField();
    } else if (type === 'selected') {
        selectedProductFiles.splice(index, 1);
        const input = document.getElementById('pf_imageFiles');
        if (input) input.value = '';
    }
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
        if (selectedNames.length) {
            nameWrap.style.display = 'block';
            nameText.textContent = selectedNames.join(', ');
        } else {
            nameWrap.style.display = 'none';
            nameText.textContent = '';
        }
    }

    const existingHtml = productExistingImages.map((url, i) => `
        <div style="position:relative">
            <img src="${url}" style="width:74px;height:74px;border-radius:10px;object-fit:cover;border:1px solid var(--border)">
            <button type="button" onclick="removeProductImage('existing', ${i})" style="position:absolute;top:-6px;right:-6px;width:22px;height:22px;border:none;border-radius:50%;background:var(--red);color:white;cursor:pointer">×</button>
        </div>
    `).join('');

    const uploadedHtml = productUploadedImages.map((url, i) => `
        <div style="position:relative">
            <img src="${url}" style="width:74px;height:74px;border-radius:10px;object-fit:cover;border:1px solid var(--border)">
            <button type="button" onclick="removeProductImage('uploaded', ${i})" style="position:absolute;top:-6px;right:-6px;width:22px;height:22px;border:none;border-radius:50%;background:var(--red);color:white;cursor:pointer">×</button>
        </div>
    `).join('');

    const selectedHtml = selectedProductFiles.map((file, i) => `
        <div style="position:relative;width:74px;height:74px;border-radius:10px;border:1px dashed var(--border);display:grid;place-items:center;color:var(--muted);font-size:11px;text-align:center;padding:6px;background:var(--surface2)">
            ${escapeHtml(file.name.slice(0, 18))}
            <button type="button" onclick="removeProductImage('selected', ${i})" style="position:absolute;top:-6px;right:-6px;width:22px;height:22px;border:none;border-radius:50%;background:var(--red);color:white;cursor:pointer">×</button>
        </div>
    `).join('');

    const total = productExistingImages.length + productUploadedImages.length + selectedProductFiles.length;

    if (!total) {
        wrap.style.display = 'none';
        grid.innerHTML = '';
        count.textContent = '0 images';
        return;
    }

    wrap.style.display = 'block';
    grid.innerHTML = existingHtml + uploadedHtml + selectedHtml;
    count.textContent = `${total} image${total !== 1 ? 's' : ''}`;
}

async function uploadProductImagesIfNeeded() {
    if (!selectedProductFiles.length) return;

    setProductUploadState('⏳', `Uploading ${selectedProductFiles.length} image(s).`, true);

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
            try {
                const err = await res.json();
                msg = err?.message || err?.error || err?.data || JSON.stringify(err);
            } catch {
                msg = await res.text() || msg;
            }
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

// ── Add / Edit form ────────────────────────────────────────────────────────
async function populateProductCategorySelect(selectedId = '') {
    const sel = document.getElementById('pf_categoryId');
    if (!sel) return;

    if (!allCategories || !allCategories.length) {
        const categories = await fetchCategoriesFromApi();
        if (categories) {
            allCategories = categories;
            categoriesFetched = true;
        }
    }

    sel.innerHTML = '<option value="">Select Category</option>' +
        (allCategories || [])
            .filter(c => !(c.isDeleted || c.deleted || c.deletedAt))
            .map(c => `<option value="${c.id}" ${String(c.id) === String(selectedId) ? 'selected' : ''}>${escapeHtml(c.name || 'Category')}</option>`)
            .join('');
}

function openProductForm() {
    editingProductId = null;
    document.getElementById('formModalTitle').textContent = 'Add Product_';
    document.getElementById('formBtnText').textContent = 'Add Product';
    document.getElementById('pf_name').value = '';
    document.getElementById('pf_sku').value = '';
    document.getElementById('pf_barcode').value = '';
    document.getElementById('pf_description').value = '';
    document.getElementById('pf_categoryId').value = '';
    document.getElementById('pf_unit').value = '';
    document.getElementById('pf_mrp').value = '';
    document.getElementById('pf_sellingPrice').value = '';
    document.getElementById('pf_taxPercent').value = '';
    document.getElementById('pf_unitValue').value = '';
    document.getElementById('pf_isActive').value = 'true';
    document.getElementById('formError').style.display = 'none';
    resetProductImageState();
    populateProductCategorySelect();
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
    await populateProductCategorySelect(p.categoryId || '');
    document.getElementById('pf_unit').value = p.unit || '';
    document.getElementById('pf_mrp').value = p.mrp ?? '';
    document.getElementById('pf_sellingPrice').value = p.sellingPrice ?? '';
    document.getElementById('pf_taxPercent').value = p.taxPercent ?? '';
    document.getElementById('pf_unitValue').value = p.unitValue ?? '';
    document.getElementById('pf_isActive').value = String(p.isActive !== false);
    document.getElementById('formError').style.display = 'none';

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

    if (!name || !sku || !categoryId || !unit) {
        errEl.textContent = 'Name, SKU, Category and Unit are required.';
        errEl.style.display = 'block';
        return;
    }

    const actorId = getActorId();
    if (!actorId) {
        errEl.textContent = 'Actor ID is missing in localStorage.';
        errEl.style.display = 'block';
        return;
    }

    btn.disabled = true;
    document.getElementById('formBtnText').textContent = isEdit ? 'Saving...' : 'Adding...';

    try {
        await uploadProductImagesIfNeeded();

        const images = getEffectiveProductImages();
        if (images.length < 1) throw new Error('Please upload at least 1 product image.');
        if (images.length > 10) throw new Error('Maximum 10 images allowed.');

        const payload = {
            name,
            description: description || null,
            sku,
            barcode: barcode || null,
            mrp: mrpRaw === '' ? null : Number(mrpRaw),
            sellingPrice: sellingRaw === '' ? null : Number(sellingRaw),
            taxPercent: taxRaw === '' ? null : Number(taxRaw),
            unit,
            unitValue: unitValueRaw === '' ? null : Number(unitValueRaw),
            images,
            isActive,
            categoryId
        };

        const url = isEdit ? ADMIN_API.products.update(editingProductId) : ADMIN_API.products.create;
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
            allProducts = fresh;
            productsFetched = true;
        }

        closeProductForm();
        renderProductsModal(allProducts);
        document.getElementById('statProductCount').textContent = allProducts.length.toLocaleString();
        document.getElementById('statProductChange').textContent = `${allProducts.length} products listed`;

        alert(isEdit ? 'Product updated successfully' : 'Product created successfully');
    } catch (err) {
        errEl.textContent = err.message || 'Failed to save product';
        errEl.style.display = 'block';
    } finally {
        btn.disabled = false;
        document.getElementById('formBtnText').textContent = isEdit ? 'Save Changes' : 'Add Product';
    }
}

// ── Toggle / Restore / Bulk update ────────────────────────────────────────
async function toggleProductActive(productId) {
    const actorId = getActorId();
    if (!actorId) {
        alert('Missing actor id in localStorage');
        return;
    }

    try {
        const res = await fetch(ADMIN_API.products.toggleActive(productId), {
            method: 'PATCH',
            headers: {
                'Authorization': 'Bearer ' + token,
                'X-Actor-Id': actorId
            }
        });

        if (!res.ok) throw new Error(parseApiError(await res.text()));

        const fresh = await fetchProducts();
        if (fresh) {
            allProducts = fresh;
            productsFetched = true;
        }

        renderProductsModal(allProducts);
    } catch (err) {
        alert(err.message || 'Failed to toggle active status');
    }
}

async function restoreProduct(productId) {
    const actorId = getActorId();
    if (!actorId) {
        alert('Missing actor id in localStorage');
        return;
    }

    try {
        const res = await fetch(ADMIN_API.products.restore(productId), {
            method: 'PATCH',
            headers: {
                'Authorization': 'Bearer ' + token,
                'X-Actor-Id': actorId
            }
        });

        if (!res.ok) throw new Error(parseApiError(await res.text()));

        const fresh = await fetchProducts();
        if (fresh) {
            allProducts = fresh;
            productsFetched = true;
        }

        renderProductsModal(allProducts);
    } catch (err) {
        alert(err.message || 'Failed to restore product');
    }
}

async function openBulkPricePrompt() {
    const raw = prompt('Enter bulk price items as JSON array: [{"productId":"uuid","mrp":120,"sellingPrice":100}]');
    if (!raw) return;

    const actorId = getActorId();
    if (!actorId) {
        alert('Missing actor id in localStorage');
        return;
    }

    try {
        const items = JSON.parse(raw);

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
        if (fresh) {
            allProducts = fresh;
            productsFetched = true;
        }

        renderProductsModal(allProducts);
        alert('Bulk price update completed successfully');
    } catch (err) {
        alert(err.message || 'Bulk price update failed');
    }
}

// ── Delete ─────────────────────────────────────────────────────────────────
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
    btn.disabled = true;
    btn.textContent = 'Deleting...';

    try {
        const res = await fetch(ADMIN_API.products.delete(deletingProductId), {
            method: 'DELETE',
            headers: {
                'Authorization': 'Bearer ' + token,
                'X-Actor-Id': getActorId()
            }
        });

        if (!res.ok) throw new Error(parseApiError(await res.text()));

        const fresh = await fetchProducts();
        if (fresh) {
            allProducts = fresh;
            productsFetched = true;
        }

        closeDeleteModal();
        renderProductsModal(allProducts);
        alert('Product deleted successfully');
    } catch (err) {
        alert(err.message || 'Failed to delete product');
    } finally {
        btn.disabled = false;
        btn.textContent = 'Delete';
    }
}