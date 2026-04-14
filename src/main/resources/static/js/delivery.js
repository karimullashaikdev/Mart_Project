/* ═══════════════════════════════════════════════
   KARIM DELIVERY DASHBOARD — delivery.js
   ═══════════════════════════════════════════════ */

'use strict';

// ── Auth ──
function getToken() {
  const token =
    localStorage.getItem('authToken') ||
    localStorage.getItem('accessToken') ||
    localStorage.getItem('token');

  if (!token || token === 'undefined' || token === 'null') {
    console.log('No token found in localStorage');
    return null;
  }

  return token.trim();
}

function getUserId() {
  const saved =
    localStorage.getItem('userId') ||
    localStorage.getItem('id') ||
    localStorage.getItem('user_id');

  if (saved && saved !== 'undefined' && saved !== 'null') {
    return saved.trim();
  }

  const token = getToken();
  if (!token) return null;

  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.userId || payload.uid || payload.sub || null;
  } catch (e) {
    return null;
  }
}

function authHeaders() {
  const token = getToken();
  const userId = getUserId();

  const headers = {
    'Content-Type': 'application/json'
  };

  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }

  if (userId) {
    headers['X-User-Id'] = userId;
  }

  return headers;
}

function authHeader() {
  const token = getToken();
  return token ? { 'Authorization': 'Bearer ' + token } : {};
}

const token = getToken();

(function guardDelivery() {
  const token = getToken();

  if (!token) {
    localStorage.clear();
    window.location.href = 'login.html';
    return;
  }

  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    const roleValue =
      payload.role ||
      payload.roles ||
      payload.authorities ||
      localStorage.getItem('role') ||
      '';

    const role = roleValue.toString().toUpperCase();

    if (role.includes('ADMIN')) {
      window.location.href = 'admin.html';
      return;
    }

    if (!role.includes('DELIVERY')) {
      window.location.href = 'products.html';
      return;
    }
  } catch (e) {
    const savedRole = (localStorage.getItem('role') || '').toUpperCase();

    if (savedRole === 'DELIVERY') {
      return;
    }

    localStorage.clear();
    window.location.href = 'login.html';
  }
})();

const username = localStorage.getItem('username') || 'Agent';
document.getElementById('agentName').textContent = username;

// ── State ──
let availableOrders = [];
let myOrders = [];
let stompClient = null;

const seenAvailableOrderIds = new Set();
const newlyArrivedOrderIds = new Set();

// ══════════════════════════════════════════════
// 📐 HAVERSINE DISTANCE CALCULATOR
// ══════════════════════════════════════════════
const EARTH_RADIUS_KM = 6371;

function calculateDistance(lat1, lon1, lat2, lon2) {
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const rLat1 = toRad(lat1);
  const rLat2 = toRad(lat2);
  const a = Math.pow(Math.sin(dLat / 2), 2)
    + Math.cos(rLat1) * Math.cos(rLat2) * Math.pow(Math.sin(dLon / 2), 2);
  return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
}
function toRad(deg) { return deg * Math.PI / 180; }

// ══════════════════════════════════════════════
// 💰 EARNINGS ENGINE
// ══════════════════════════════════════════════
let HUB_LAT = 17.3850;
let HUB_LNG = 78.4867;
let BASE_PAY = 20;
let RATE_PER_KM = 8;

function calcOrderEarning(order) {
  const dLat = order.latitude ?? order.addressLat ?? null;
  const dLng = order.longitude ?? order.addressLong ?? null;
  if (dLat == null || dLng == null) {
    return { earning: BASE_PAY, km: null, hasCoords: false };
  }
  const km = calculateDistance(HUB_LAT, HUB_LNG, dLat, dLng);
  const earning = BASE_PAY + (km * RATE_PER_KM);
  return { earning: parseFloat(earning.toFixed(2)), km: parseFloat(km.toFixed(2)), hasCoords: true };
}

function computeEarnings() {
  const delivered = myOrders.filter(o => o.status === 'DELIVERED');
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const weekStart = new Date(today); weekStart.setDate(today.getDate() - today.getDay());
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);

  let totalEarning = 0, totalKm = 0;
  let todayEarning = 0, weekEarning = 0, monthEarning = 0;

  const history = delivered.map(o => {
    const { earning, km, hasCoords } = calcOrderEarning(o);
    const oDate = new Date(o.createdAt || o.updatedAt || Date.now());
    totalEarning += earning;
    if (km != null) totalKm += km;
    if (oDate >= today) todayEarning += earning;
    if (oDate >= weekStart) weekEarning += earning;
    if (oDate >= monthStart) monthEarning += earning;
    return { order: o, earning, km, hasCoords, date: oDate };
  }).sort((a, b) => b.date - a.date);

  return { totalEarning, totalKm, todayEarning, weekEarning, monthEarning, history, count: delivered.length };
}

function updateEarningsPanel() {
  const { totalEarning, totalKm, todayEarning, weekEarning, monthEarning, history, count } = computeEarnings();
  const avg = count > 0 ? totalEarning / count : 0;

  document.getElementById('bannerTotalEarnings').textContent = fmtAmount(totalEarning);
  document.getElementById('bannerTotalKm').textContent = totalKm.toFixed(1);
  document.getElementById('bannerTotalOrders').textContent = count;
  document.getElementById('bannerAvgEarning').textContent = fmtAmount(avg);
  document.getElementById('bannerEarningSub').textContent =
    count > 0 ? `${count} completed deliver${count === 1 ? 'y' : 'ies'} · ${totalKm.toFixed(1)} km total` : 'No completed deliveries yet';

  document.getElementById('statTodayEarnings').textContent = fmtAmount(todayEarning);
  document.getElementById('epTotalEarnings').textContent = fmtAmount(totalEarning);
  document.getElementById('epTodayLine').textContent = `Today: ${fmtAmount(todayEarning)}`;
  document.getElementById('epTotalDeliveries').textContent = count;
  document.getElementById('epTotalKm').textContent = totalKm.toFixed(2) + ' km';
  document.getElementById('epAvgPerDelivery').textContent = fmtAmount(avg);
  document.getElementById('epThisWeek').textContent = fmtAmount(weekEarning);
  document.getElementById('epThisMonth').textContent = fmtAmount(monthEarning);

  const histEl = document.getElementById('epHistoryList');
  if (history.length === 0) {
    histEl.innerHTML = `<div style="font-size:12px;color:var(--muted);text-align:center;padding:16px">No completed deliveries yet</div>`;
    return;
  }
  histEl.innerHTML = history.slice(0, 8).map((h, i) => {
    const kmText = h.hasCoords ? `📏 ${h.km} km` : '📏 GPS N/A';
    // FIX 1: Use orderId field (matches OrderNotificationDto) with fallback to id
    const displayId = h.order.orderId || h.order.id;
    return `
      <div class="ep-delivery-item" style="animation-delay:${i * 0.05}s">
        <div class="ep-di-top">
          <div class="ep-di-id">Order #${displayId}</div>
          <div class="ep-di-earn">${fmtAmount(h.earning)}</div>
        </div>
        <div class="ep-di-meta">
          <span>${kmText}</span>
          <span>🕐 ${fmtDate(h.order.createdAt || h.order.updatedAt || h.order.placedAt)}</span>
          ${h.hasCoords ? '' : '<span style="color:#6b7280;font-style:italic">Base pay only</span>'}
        </div>
      </div>`;
  }).join('');
}

// ── API helper ──
async function api(method, path, body = null) {
  const token = getToken();

  if (!token) {
    localStorage.clear();
    window.location.href = 'login.html';
    return null;
  }

  const opts = {
    method,
    cache: 'no-store',
    headers: authHeaders()
  };

  if (body) {
    opts.body = JSON.stringify(body);
  }

  console.log('API CALL =>', '/api/delivery' + path, opts.headers);

  const res = await fetch('/api/delivery' + path, opts);

  if (res.status === 401) {
    logout(false);
    return null;
  }

  return res;
}

const fmtAmount = v => '₹' + Number(v || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 });
const fmtDate = d => d ? new Date(d).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';

function statusBadge(status) {
  const map = {
    CONFIRMED:       ['assigned',    '📦 New'],
    PROCESSING:      ['assigned',    '🤝 Assigned'],
    OUT_FOR_DELIVERY:['out_delivery','🚀 On the Way'],
    DELIVERED:       ['delivered',   '✅ Delivered'],
  };
  const [cls, label] = map[status] || ['assigned', status];
  return `<span class="badge ${cls}">${label}</span>`;
}

// ══════════════════════════════════════════════
// 🔔 NEW ORDER NOTIFICATION SYSTEM
// ══════════════════════════════════════════════
let newOrderAlertTimer = null;

function triggerNewOrderAlert(newCount, newOrders) {
  newOrders.forEach(o => newlyArrivedOrderIds.add(o.orderId || o.id));

  const banner = document.getElementById('newOrderAlert');
  document.getElementById('newOrderAlertCount').textContent = newCount;
  // FIX 2: Use orderId field with fallback
  const names = newOrders.map(o => `#${o.orderId || o.id}`).slice(0, 3).join(', ');
  document.getElementById('newOrderAlertText').innerHTML =
    `<strong>${newCount} new order${newCount > 1 ? 's' : ''} just arrived!</strong> Order${newCount > 1 ? 's' : ''} ${names} ${newCount > 1 ? 'are' : 'is'} waiting to be accepted.`;

  banner.classList.add('visible');
  if (newOrderAlertTimer) clearTimeout(newOrderAlertTimer);
  newOrderAlertTimer = setTimeout(dismissNewOrderAlert, 12000);

  const statCard = document.getElementById('statAvailableCard');
  statCard.classList.remove('new-order-highlight');
  void statCard.offsetWidth;
  statCard.classList.add('new-order-highlight');

  showToast('neworder', `🆕 ${newCount} new order${newCount > 1 ? 's' : ''}! Tap to accept — ${names}`);

  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification('New Delivery Order!', {
      body: `${newCount} new order${newCount > 1 ? 's' : ''} available — ${names}`,
      icon: '🛵',
      tag: 'new-order',
    });
  }

  playNewOrderSound();
}

function dismissNewOrderAlert() {
  document.getElementById('newOrderAlert').classList.remove('visible');
  if (newOrderAlertTimer) { clearTimeout(newOrderAlertTimer); newOrderAlertTimer = null; }
}

function scrollToAvailableOrders() {
  dismissNewOrderAlert();
  document.getElementById('availableSection').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function playNewOrderSound() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const notes = [523.25, 659.25, 783.99];
    notes.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.type = 'sine';
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0, ctx.currentTime + i * 0.12);
      gain.gain.linearRampToValueAtTime(0.18, ctx.currentTime + i * 0.12 + 0.05);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.12 + 0.45);
      osc.start(ctx.currentTime + i * 0.12);
      osc.stop(ctx.currentTime + i * 0.12 + 0.5);
    });
  } catch (_) { }
}

function requestNotificationPermission() {
  if ('Notification' in window && Notification.permission === 'default') {
    Notification.requestPermission();
  }
}

// ── WebSocket ──
function connectWebSocket() {
  const liveToken = getToken();
  if (!liveToken) return;

  const socket = new SockJS('/ws');
  stompClient = Stomp.over(socket);
  stompClient.debug = null;
  stompClient.connect(
    { 'Authorization': 'Bearer ' + liveToken },
    onWsConnected,
    onWsError
  );
}

function onWsConnected() {
  setWsStatus(true);

  // FIX 3: Subscribe to /topic/delivery-orders — matches backend publish topic in OrderNotificationService
  stompClient.subscribe('/topic/delivery-orders', (message) => {
    const event = JSON.parse(message.body);
    handleOrderEvent(event);
  });
}

function onWsError() { setWsStatus(false); setTimeout(connectWebSocket, 5000); }

function setWsStatus(connected) {
  const el = document.getElementById('wsIndicator');
  el.className = 'ws-indicator ' + (connected ? 'connected' : 'disconnected');
  document.getElementById('wsLabel').textContent = connected ? 'Live' : 'Reconnecting…';
}

function handleOrderEvent(event) {
  // FIX 4: Backend sets status to CONFIRMED after payment (not PAID)
  // OrderNotificationDto has field 'status' and 'orderId'
  const isNewOrder =
    event.status === 'CONFIRMED' ||
    event.type === 'NEW_ORDER' ||
    event.type === 'ORDER_PLACED';

  if (isNewOrder) {
    loadAvailableOrders(true);
    loadMyOrders();
  } else {
    Promise.all([loadAvailableOrders(), loadMyOrders()]);
    const labels = {
      PROCESSING:       'accepted',
      OUT_FOR_DELIVERY: 'out for delivery',
      DELIVERED:        'delivered'
    };
    const label = labels[event.status];
    // FIX 5: Use orderId field from OrderNotificationDto
    const id = event.orderId || event.id;
    if (label) showToast('ws', `📡 Order #${id} is now ${label}`);
  }
}

// ── Load Available Orders ──
// Calls GET /api/delivery/orders/available → mapped to getActiveOrdersForDashboard()
async function loadAvailableOrders(fromWsEvent = false) {
  try {
    const res = await api('GET', '/orders/available');
    if (!res) return;
    const freshOrders = res.ok ? await res.json() : [];

    if (seenAvailableOrderIds.size > 0 || fromWsEvent) {
      // FIX 6: Use orderId field for deduplication (matches OrderNotificationDto)
      const newOrders = freshOrders.filter(o => !seenAvailableOrderIds.has(o.orderId || o.id));
      if (newOrders.length > 0) {
        triggerNewOrderAlert(newOrders.length, newOrders);
      }
    }

    freshOrders.forEach(o => seenAvailableOrderIds.add(o.orderId || o.id));
    const freshIds = new Set(freshOrders.map(o => o.orderId || o.id));
    for (const id of seenAvailableOrderIds) {
      if (!freshIds.has(id)) seenAvailableOrderIds.delete(id);
    }

    availableOrders = freshOrders;
  } catch (e) {
    console.error('loadAvailableOrders error:', e);
    availableOrders = [];
  }
  renderAvailable();
  updateStats();
}

function renderAvailable() {
  const list = document.getElementById('availableList');
  const countEl = document.getElementById('availableCount');
  countEl.textContent = availableOrders.length;

  if (availableOrders.length === 0) {
    countEl.className = 'section-count';
    list.innerHTML = `<div class="empty-state"><div class="e-icon">📭</div><p>No orders available right now.<br><strong>Check back soon!</strong></p></div>`;
    return;
  }

  const hasNew = availableOrders.some(o => newlyArrivedOrderIds.has(o.orderId || o.id));
  countEl.className = 'section-count ' + (hasNew ? 'new-orders' : 'active');

  list.innerHTML = availableOrders.map((order, i) => {
    const { earning, km, hasCoords } = calcOrderEarning(order);
    const kmLabel = hasCoords ? `${km} km` : 'GPS N/A';
    const earnLabel = fmtAmount(earning);
    // FIX 7: Use orderId field (OrderNotificationDto) with fallback to id
    const displayId = order.orderId || order.id;
    const isNew = newlyArrivedOrderIds.has(displayId);
    const newPill = isNew ? `<span class="new-order-pill">🆕 New</span>` : '';

    // Build address from OrderNotificationDto fields
    const address = order.deliveryAddress
      ? `${order.deliveryAddress}${order.deliveryCity ? ', ' + order.deliveryCity : ''}`
      : 'Address not set';

    return `
    <div class="order-card${isNew ? ' new-arrival' : ''}" style="animation-delay:${i * 0.06}s" data-order-id="${displayId}">
      <div class="order-top">
        <div class="order-id">Order <span>#${displayId}</span>${newPill}</div>
        ${statusBadge(order.status)}
      </div>
      <div class="order-details">
        <div class="order-row"><span class="icon">📍</span><span><strong>Deliver to:</strong> ${address}</span></div>
        <div class="order-row"><span class="icon">💰</span><span><strong>Order Value:</strong> ${fmtAmount(order.totalAmount)}</span></div>
        <div class="order-row"><span class="icon">💳</span><span><strong>Payment:</strong> ${order.paymentType || '—'}</span></div>
      </div>
      <div class="order-earn-row">
        <div class="earn-chip"><span class="lbl">Distance</span><span class="val">📏 ${kmLabel}</span></div>
        <div class="earn-sep"></div>
        <div class="earn-chip"><span class="lbl">You earn</span><span class="val">💵 ${earnLabel}</span></div>
      </div>
      <div class="order-meta">
        <span>🕐 ${fmtDate(order.placedAt || order.createdAt)}</span>
        ${order.items ? `<span>🛍 ${order.items.length} item(s)</span>` : ''}
      </div>
      <div class="order-actions">
        <button class="btn btn-accept" onclick="acceptOrder('${displayId}', this)">🤝 Accept — ${earnLabel}</button>
      </div>
    </div>`;
  }).join('');

  setTimeout(() => {
    document.querySelectorAll('.order-card.new-arrival').forEach(card => {
      card.classList.add('settled');
    });
  }, 2000);
}

// ── Load My Orders ──
// Calls GET /api/delivery/orders/my → mapped to listAssignmentsByAgent()
async function loadMyOrders() {
  try {
    const res = await api('GET', '/orders/my');
    if (!res) return;
    myOrders = res.ok ? await res.json() : [];
  } catch (e) {
    console.error('loadMyOrders error:', e);
    myOrders = [];
  }
  renderMyOrders();
  updateStats();
  updateEarningsPanel();
}

function renderMyOrders() {
  const list = document.getElementById('myOrdersList');
  document.getElementById('myOrdersCount').textContent = myOrders.length;

  if (myOrders.length === 0) {
    list.innerHTML = `<div class="empty-state"><div class="e-icon">🛵</div><p>No orders assigned yet.<br><strong>Accept an order</strong> from the left panel.</p></div>`;
    return;
  }

  list.innerHTML = myOrders.map((order, i) => {
    const isAssigned = order.status === 'PROCESSING';
    const isOut = order.status === 'OUT_FOR_DELIVERY';
    const isDelivered = order.status === 'DELIVERED';
    const { earning, km, hasCoords } = calcOrderEarning(order);
    const kmLabel = hasCoords ? `${km} km` : 'GPS N/A';
    const earnLabel = fmtAmount(earning);
    // FIX 8: Use orderId field with fallback
    const displayId = order.orderId || order.id;

    const address = order.deliveryAddress
      ? `${order.deliveryAddress}${order.deliveryCity ? ', ' + order.deliveryCity : ''}`
      : 'Not set';

    return `
    <div class="order-card" style="animation-delay:${i * 0.06}s">
      <div class="order-top"><div class="order-id">Order <span>#${displayId}</span></div>${statusBadge(order.status)}</div>
      <div class="order-details">
        <div class="order-row"><span class="icon">📍</span><span><strong>Address:</strong> ${address}</span></div>
        <div class="order-row"><span class="icon">💰</span><span><strong>Amount:</strong> ${fmtAmount(order.totalAmount)}</span></div>
        <div class="order-row"><span class="icon">💳</span><span><strong>Payment:</strong> ${order.paymentType || '—'}</span></div>
      </div>
      <div class="order-earn-row">
        <div class="earn-chip"><span class="lbl">Distance</span><span class="val">📏 ${kmLabel}</span></div>
        <div class="earn-sep"></div>
        <div class="earn-chip"><span class="lbl">${isDelivered ? 'Earned' : 'Will earn'}</span><span class="val">💵 ${earnLabel}</span></div>
      </div>
      ${address !== 'Not set' ? (isDelivered
        ? `<div class="maps-link-disabled">🔒 Map Unavailable — Customer Privacy Protected</div>`
        : `<button class="maps-link-btn" onclick="openMapsWithLocation('${address.replace(/'/g, "\\'").replace(/"/g, '&quot;')}',this,${order.latitude ?? 'null'},${order.longitude ?? 'null'})">📍 Open in Google Maps</button>`)
        : ''}
      <div class="order-meta"><span>🕐 ${fmtDate(order.placedAt || order.createdAt)}</span></div>
      <div class="order-actions">
        ${isAssigned ? `<button class="btn btn-start" onclick="startDelivery('${displayId}', this)">🚀 Start Delivery</button>` : ''}
        ${isOut      ? `<button class="btn btn-deliver" onclick="completeDelivery('${displayId}', this)">✅ Mark Delivered</button>` : ''}
        ${isDelivered ? `<button class="btn btn-done" disabled>✅ Delivered · ${earnLabel}</button>` : ''}
      </div>
    </div>`;
  }).join('');
}

function updateStats() {
  document.getElementById('statAvailable').textContent = availableOrders.length;
  document.getElementById('statAssigned').textContent = myOrders.filter(o => o.status === 'PROCESSING').length;
  document.getElementById('statOnRoute').textContent = myOrders.filter(o => o.status === 'OUT_FOR_DELIVERY').length;
  document.getElementById('statDelivered').textContent = myOrders.filter(o => o.status === 'DELIVERED').length;
}

// ── Accept Order ──
// Calls POST /api/delivery/orders/{orderId}/accept → mapped to acceptOrderByAgent()
async function acceptOrder(orderId, btn) {
  btn.classList.add('btn-loading'); btn.disabled = true;
  newlyArrivedOrderIds.delete(orderId);
  try {
    const res = await api('POST', `/orders/${orderId}/accept`);
    if (!res) return;
    if (res.ok) {
      showToast('success', '🤝 Order #' + orderId + ' accepted!');
      await Promise.all([loadAvailableOrders(), loadMyOrders()]);
    } else {
      const errText = await res.text();
      showToast('error', '❌ ' + (errText || 'Could not accept'));
      btn.classList.remove('btn-loading'); btn.disabled = false;
    }
  } catch {
    showToast('error', '❌ Network error.');
    btn.classList.remove('btn-loading'); btn.disabled = false;
  }
}

// ── Start Delivery ──
// Calls POST /api/delivery/orders/{orderId}/start → maps to markOutForDelivery via assignment
async function startDelivery(orderId, btn) {
  btn.classList.add('btn-loading'); btn.disabled = true;
  try {
    const res = await api('POST', `/orders/${orderId}/start`);
    if (!res) return;
    if (res.ok) {
      showToast('info', '🚀 Delivery started for Order #' + orderId);
      await loadMyOrders();
      startLocationSharing(orderId);
    } else {
      const errText = await res.text();
      showToast('error', '❌ ' + (errText || 'Could not start delivery'));
      btn.classList.remove('btn-loading'); btn.disabled = false;
    }
  } catch {
    showToast('error', '❌ Network error.');
    btn.classList.remove('btn-loading'); btn.disabled = false;
  }
}

// ── OTP Modal ──
let pendingDeliveryOrderId = null;
let pendingDeliveryBtn = null;

function completeDelivery(orderId, btn) {
  pendingDeliveryOrderId = orderId;
  pendingDeliveryBtn = btn;
  document.getElementById('otpOrderLabel').textContent = '#' + orderId;
  document.getElementById('otpInput').value = '';
  document.getElementById('otpModal').classList.remove('hidden');
  setTimeout(() => document.getElementById('otpInput').focus(), 100);
}
function closeOtpModal() {
  document.getElementById('otpModal').classList.add('hidden');
  pendingDeliveryOrderId = null;
  pendingDeliveryBtn = null;
}

async function submitOtp() {
  const otp = document.getElementById('otpInput').value.trim();

  if (!otp || otp.length < 4) {
    showToast('error', '❌ OTP must be 4 digits');
    return;
  }

  const confirmBtn = document.getElementById('otpConfirmBtn');
  confirmBtn.disabled = true;
  confirmBtn.textContent = 'Verifying…';

  if (pendingDeliveryBtn) {
    pendingDeliveryBtn.classList.add('btn-loading');
    pendingDeliveryBtn.disabled = true;
  }

  try {
    const liveToken = getToken();
    if (!liveToken) { logout(false); return; }

    // Calls POST /api/delivery/orders/{orderId}/complete
    const res = await fetch(`/api/delivery/orders/${pendingDeliveryOrderId}/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeader() },
      body: JSON.stringify({ otp })
    });

    if (res.status === 401) { logout(false); return; }

    if (res.ok) {
      const order = myOrders.find(o => (o.orderId || o.id) == pendingDeliveryOrderId);
      const { earning } = order ? calcOrderEarning(order) : { earning: BASE_PAY };
      closeOtpModal();
      showToast('success', `✅ Order #${pendingDeliveryOrderId} delivered! You earned ${fmtAmount(earning)}`);
      stopLocationSharing();
      await loadMyOrders();
    } else {
      let errMsg = 'Invalid OTP';
      try { const b = await res.text(); if (b) errMsg = b; } catch (_) { }
      showToast('error', '❌ ' + errMsg);
      document.getElementById('otpInput').value = '';
      document.getElementById('otpInput').focus();
      if (pendingDeliveryBtn) {
        pendingDeliveryBtn.classList.remove('btn-loading');
        pendingDeliveryBtn.disabled = false;
      }
    }
  } catch {
    showToast('error', '❌ Network error — check your connection.');
    if (pendingDeliveryBtn) {
      pendingDeliveryBtn.classList.remove('btn-loading');
      pendingDeliveryBtn.disabled = false;
    }
  }

  confirmBtn.disabled = false;
  confirmBtn.textContent = '✅ Confirm';
}

document.getElementById('otpInput').addEventListener('keydown', e => {
  if (e.key === 'Enter') submitOtp();
  if (e.key === 'Escape') closeOtpModal();
});

// ── Refresh buttons ──
async function refreshAvailable() {
  const btn = document.getElementById('refreshAvailableBtn');
  btn.classList.add('spinning');
  await loadAvailableOrders();
  setTimeout(() => btn.classList.remove('spinning'), 600);
  showToast('info', '🔄 Refreshed');
}
async function refreshMyOrders() {
  const btn = document.getElementById('refreshMyBtn');
  btn.classList.add('spinning');
  await loadMyOrders();
  setTimeout(() => btn.classList.remove('spinning'), 600);
}

// ── Toast ──
function showToast(type, msg) {
  const el = document.createElement('div');
  el.className = 'toast ' + type;
  el.textContent = msg;
  document.getElementById('toastContainer').appendChild(el);
  setTimeout(() => el.remove(), type === 'neworder' ? 5500 : 3800);
}

// ── Logout ──
function logout(redirect = true) {
  try { if (stompClient) stompClient.disconnect(); } catch (_) { }
  _clearLocationSession();
  localStorage.removeItem('authToken');
  localStorage.removeItem('accessToken');
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('username');
  localStorage.removeItem('role');
  localStorage.removeItem('activeDeliveryOrderId');
  if (redirect) window.location.href = 'login.html';
}

// ════════════════════════════════════════════════════
// 🛵 LIVE LOCATION TRACKING
// ════════════════════════════════════════════════════
let activeTrackOrder = null;
let geoWatchId = null;
let heartbeatInterval = null;
let lastKnownLat = null;
let lastKnownLng = null;
let visibilityHandler = null;

function _persistActiveOrder(orderId) {
  if (orderId) localStorage.setItem('activeDeliveryOrderId', String(orderId));
  else localStorage.removeItem('activeDeliveryOrderId');
}

async function _resumeLocationSharingIfNeeded() {
  const savedOrderId = localStorage.getItem('activeDeliveryOrderId');
  if (!savedOrderId) return;
  try {
    const res = await api('GET', '/orders/my');
    if (!res) return;
    const orders = res.ok ? await res.json() : [];
    const activeOrder = orders.find(o =>
      String(o.orderId || o.id) === String(savedOrderId) && o.status === 'OUT_FOR_DELIVERY'
    );
    if (activeOrder) {
      showToast('info', '📡 Resuming location sharing for Order #' + savedOrderId);
      startLocationSharing(savedOrderId);
    } else {
      _persistActiveOrder(null);
    }
  } catch (_) {
    startLocationSharing(savedOrderId);
  }
}

function startLocationSharing(orderId) {
  if (!navigator.geolocation) { showToast('error', '❌ GPS not supported'); return; }
  _clearLocationSession();
  activeTrackOrder = orderId;
  _persistActiveOrder(orderId);
  document.getElementById('liveTrackBar').classList.add('visible');
  setTrackState('requesting', '⏳ Starting location sharing…', 'Order #' + orderId);

  geoWatchId = navigator.geolocation.watchPosition(
    (pos) => {
      lastKnownLat = pos.coords.latitude;
      lastKnownLng = pos.coords.longitude;
      _sendLocation(lastKnownLat, lastKnownLng);
    },
    (err) => {
      const msgs = { 1: 'Location permission denied', 2: 'Location unavailable', 3: 'GPS timed out' };
      setTrackState('error', '❌ GPS unavailable', msgs[err.code] || 'Check permissions');
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 3000 }
  );

  heartbeatInterval = setInterval(() => {
    if (lastKnownLat !== null && lastKnownLng !== null) _sendLocation(lastKnownLat, lastKnownLng);
  }, 8000);

  visibilityHandler = () => {
    if (document.visibilityState === 'visible' && activeTrackOrder) {
      setTrackState('requesting', '🔄 Resuming location…', 'Order #' + activeTrackOrder);
      if (lastKnownLat !== null && lastKnownLng !== null) _sendLocation(lastKnownLat, lastKnownLng);
      if (geoWatchId === null) {
        geoWatchId = navigator.geolocation.watchPosition(
          (pos) => { lastKnownLat = pos.coords.latitude; lastKnownLng = pos.coords.longitude; _sendLocation(lastKnownLat, lastKnownLng); },
          (err) => { console.warn('[GPS resume]', err.message); },
          { enableHighAccuracy: true, timeout: 10000, maximumAge: 3000 }
        );
      }
    }
  };
  document.addEventListener('visibilitychange', visibilityHandler);
}

async function stopLocationSharing() {
  const orderIdToStop = activeTrackOrder;
  _clearLocationSession();
  _persistActiveOrder(null);

  if (orderIdToStop) {
    try {
      if (getToken()) {
        // Calls POST /api/delivery/orders/{orderId}/location/stop
        await fetch(`/api/delivery/orders/${orderIdToStop}/location/stop`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeader() }
        });
      }
    } catch (_) { }
  }

  activeTrackOrder = null;
  setTrackState('off', '📡 Location sharing stopped', 'Delivery completed');
  setTimeout(() => document.getElementById('liveTrackBar').classList.remove('visible'), 3000);
}

function _clearLocationSession() {
  if (geoWatchId !== null) { navigator.geolocation.clearWatch(geoWatchId); geoWatchId = null; }
  if (heartbeatInterval !== null) { clearInterval(heartbeatInterval); heartbeatInterval = null; }
  if (visibilityHandler !== null) { document.removeEventListener('visibilitychange', visibilityHandler); visibilityHandler = null; }
  lastKnownLat = null; lastKnownLng = null;
}

async function _sendLocation(lat, lng) {
  if (!activeTrackOrder) return;
  try {
    const liveToken = getToken();
    if (!liveToken) return;

    // Calls POST /api/delivery/orders/{orderId}/location
    const res = await fetch(`/api/delivery/orders/${activeTrackOrder}/location`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeader() },
      body: JSON.stringify({ orderId: activeTrackOrder, latitude: lat, longitude: lng })
    });

    if (res.ok) {
      setTrackState('live', '🟢 Sharing live location',
        'Last update: ' + new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    } else if (res.status === 401) {
      stopLocationSharing(); logout(false);
    }
  } catch (_) {
    setTrackState('error', '⚠️ Network error', 'Retrying…');
  }
}

function setTrackState(state, title, sub) {
  const dot = document.getElementById('trackDot');
  dot.className = state === 'live' ? 'live' : state === 'error' ? 'error' : '';
  document.getElementById('trackStatusTitle').textContent = title;
  document.getElementById('trackStatusSub').textContent = sub;
}

function openMapsWithLocation(address, btn, destLat, destLng) {
  const originalHTML = btn.innerHTML;
  btn.style.pointerEvents = 'none';
  const destination = (destLat != null && destLng != null)
    ? encodeURIComponent(destLat + ',' + destLng)
    : encodeURIComponent(address);

  function openMap(originLat, originLng) {
    const origin = (originLat != null && originLng != null) ? encodeURIComponent(originLat + ',' + originLng) : null;
    const url = 'https://www.google.com/maps/dir/?api=1' + (origin ? '&origin=' + origin : '') + '&destination=' + destination + '&travelmode=driving';
    window.open(url, '_blank');
    btn.innerHTML = originalHTML;
    btn.style.pointerEvents = '';
  }

  if (lastKnownLat !== null && lastKnownLng !== null) { openMap(lastKnownLat, lastKnownLng); return; }
  if (!navigator.geolocation) { openMap(null, null); return; }
  btn.innerHTML = '⏳ Getting your location…';
  navigator.geolocation.getCurrentPosition(
    (pos) => openMap(pos.coords.latitude, pos.coords.longitude),
    (_err) => { showToast('info', '📍 Opening Maps — enable location for turn-by-turn'); openMap(null, null); },
    { enableHighAccuracy: false, timeout: 5000, maximumAge: 30000 }
  );
}

// ════════════════════════════════════════════════════
// 🤖 AGENT HELP BOT
// ════════════════════════════════════════════════════
let agentChatOpen = false;
let agentChatLoading = false;
let agentChatHistory = [];
let agentGreeted = false;

function toggleAgentChat() {
  agentChatOpen = !agentChatOpen;
  document.getElementById('agentChatWindow').classList.toggle('hidden', !agentChatOpen);
  if (agentChatOpen && !agentGreeted) {
    agentGreeted = true;
    const { totalEarning, count } = computeEarnings();
    appendAgentBotMessage(`Hi ${username}! 👋 I'm your delivery assistant. You've completed ${count} deliver${count === 1 ? 'y' : 'ies'} and earned ${fmtAmount(totalEarning)} total. Ask me anything!`);
  }
  if (agentChatOpen) setTimeout(() => document.getElementById('agentChatInput').focus(), 150);
}

function sendAgentChip(el) {
  document.getElementById('agentChatInput').value = el.textContent.trim();
  sendAgentChatMessage();
}

function appendAgentBotMessage(text) {
  const msgs = document.getElementById('agentChatMessages');
  const div = document.createElement('div');
  div.className = 'amsg bot';
  div.innerHTML = `<div class="amsg-bubble">${text}</div>`;
  msgs.appendChild(div); msgs.scrollTop = msgs.scrollHeight;
}

function appendAgentUserMessage(text) {
  const msgs = document.getElementById('agentChatMessages');
  const div = document.createElement('div');
  div.className = 'amsg user';
  div.innerHTML = `<div class="amsg-bubble">${text}</div>`;
  msgs.appendChild(div); msgs.scrollTop = msgs.scrollHeight;
}

function showAgentTyping() {
  const msgs = document.getElementById('agentChatMessages');
  const div = document.createElement('div');
  div.className = 'amsg bot'; div.id = 'agentTyping';
  div.innerHTML = `<div class="amsg-bubble" style="display:flex;gap:4px;align-items:center">
    <div style="width:6px;height:6px;border-radius:50%;background:#6b7280;animation:typingBounce 1.2s infinite"></div>
    <div style="width:6px;height:6px;border-radius:50%;background:#6b7280;animation:typingBounce 1.2s infinite;animation-delay:.2s"></div>
    <div style="width:6px;height:6px;border-radius:50%;background:#6b7280;animation:typingBounce 1.2s infinite;animation-delay:.4s"></div>
  </div>`;
  msgs.appendChild(div); msgs.scrollTop = msgs.scrollHeight;
}

function hideAgentTyping() { const el = document.getElementById('agentTyping'); if (el) el.remove(); }

async function sendAgentChatMessage() {
  const input = document.getElementById('agentChatInput');
  const text = input.value.trim();
  if (!text || agentChatLoading) return;
  input.value = '';
  appendAgentUserMessage(text);
  agentChatHistory.push({ role: 'user', content: text });
  agentChatLoading = true;
  document.getElementById('agentChatSendBtn').disabled = true;
  showAgentTyping();

  const { totalEarning, totalKm, count } = computeEarnings();
  const orderSummary = myOrders.length
    ? myOrders.map(o => `Order #${o.orderId || o.id} - ${o.status} - ${o.deliveryAddress || 'no address'}`).join('\n')
    : 'No active orders.';
  const earningsSummary = `Agent has completed ${count} deliveries. Total km: ${totalKm.toFixed(1)}. Total earned: ${fmtAmount(totalEarning)}. Rate: ₹${BASE_PAY} base + ₹${RATE_PER_KM}/km.`;

  try {
    const liveToken = getToken();
    if (!liveToken) { hideAgentTyping(); logout(false); return; }

    const res = await fetch('/api/chat/agent-help', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeader() },
      body: JSON.stringify({
        agentName: username,
        orderSummary,
        earningsSummary,
        message: text,
        history: agentChatHistory.slice(-8)
      })
    });

    hideAgentTyping();
    if (res.ok) {
      const data = await res.json();
      const reply = data.reply || "Sorry, I couldn't get a response.";
      appendAgentBotMessage(reply);
      agentChatHistory.push({ role: 'assistant', content: reply });
    } else {
      appendAgentBotMessage("⚠️ Something went wrong. Try again.");
    }
  } catch (_) {
    hideAgentTyping();
    appendAgentBotMessage("⚠️ Network error. Check your connection.");
  }

  agentChatLoading = false;
  document.getElementById('agentChatSendBtn').disabled = false;
  document.getElementById('agentChatInput').focus();
}

document.getElementById('agentChatInput').addEventListener('keydown', e => {
  if (e.key === 'Enter') sendAgentChatMessage();
});

// ── Init ──
(async () => {
  requestNotificationPermission();
  await Promise.all([loadAvailableOrders(), loadMyOrders()]);
  connectWebSocket();
  await _resumeLocationSharingIfNeeded();
})();