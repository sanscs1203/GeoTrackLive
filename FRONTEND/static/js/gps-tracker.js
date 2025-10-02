// mapa-completo.js
// Versión completa y estable: selección 2 puntos (color), popup con distancia/tiempo,
// y cierre de popup + limpieza al cambiar entre En Vivo / Histórico.

// Globales
let map;
let marker = null;
let isFirstUpdate = true;
let locations = [];
let polyline = null;
let locationMarkers = [];
let liveInterval = null;
let currentMode = 'live';
let selectedPoints = []; // {lat, lon, datetime, markerRef}
let currentPopup = null;
let tempLine = null;

// --- Utilidades ---
function haversineDistance(lat1, lon1, lat2, lon2) {
    const R = 6371e3;
    const toRad = Math.PI / 180;
    const φ1 = lat1 * toRad, φ2 = lat2 * toRad;
    const Δφ = (lat2 - lat1) * toRad;
    const Δλ = (lon2 - lon1) * toRad;
    const a = Math.sin(Δφ/2)**2 + Math.cos(φ1)*Math.cos(φ2)*Math.sin(Δλ/2)**2;
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
}

function formatDuration(ms) {
    const sec = Math.floor(ms / 1000);
    const hours = Math.floor(sec / 3600);
    const mins = Math.floor((sec % 3600) / 60);
    const seconds = sec % 60;
    const parts = [];
    if (hours) parts.push(`${hours}h`);
    if (mins) parts.push(`${mins}m`);
    if (seconds || parts.length === 0) parts.push(`${seconds}s`);
    return parts.join(' ');
}

function defaultColorForMode(mode) {
    return mode === 'live' ? '#3388ff' : '#ff7800';
}

function safeClosePopup() {
    try {
        if (map) map.closePopup();
        currentPopup = null;
    } catch (e) {
        // ignore
    }
}

function restoreSelectedMarkers(prevMode) {
    if (!selectedPoints || selectedPoints.length === 0) return;
    const color = defaultColorForMode(prevMode);
    selectedPoints.forEach(p => {
        if (p.markerRef && p.markerRef.setStyle) {
            try { p.markerRef.setStyle({ fillColor: color }); } catch(e){}
        }
    });
    selectedPoints = [];
    if (tempLine) { try { map.removeLayer(tempLine); } catch(e){} tempLine = null; }
}

// --- Manejo de selección y popup ---
function handleMarkerClick(lat, lon, datetime, markerRef) {
    // Si ya hay dos seleccionados (no deberían), limpiar
    if (selectedPoints.length >= 2) {
        selectedPoints.forEach(s => { try { s.markerRef.setStyle({ fillColor: defaultColorForMode(currentMode) }); } catch(e){} });
        selectedPoints = [];
    }

    // marcar visualmente
    try { markerRef.setStyle({ fillColor: 'limegreen' }); } catch(e){}

    // push
    selectedPoints.push({ lat, lon, datetime, markerRef });

    if (selectedPoints.length === 2) {
        const p1 = selectedPoints[0];
        const p2 = selectedPoints[1];

        // distancia
        const dist = haversineDistance(p1.lat, p1.lon, p2.lat, p2.lon);
        const distText = dist >= 1000 ? `${(dist/1000).toFixed(3)} km` : `${dist.toFixed(2)} m`;

        // tiempo
        let t1 = new Date(p1.datetime);
        let t2 = new Date(p2.datetime);
        if (isNaN(t1.getTime())) t1 = new Date();
        if (isNaN(t2.getTime())) t2 = new Date();
        const diffMs = Math.abs(t2 - t1);
        const timeText = formatDuration(diffMs);

        const popupHtml = `
            <div style="font-size:14px">
                <b>Distancia:</b> ${distText}<br>
                <b>Tiempo entre puntos:</b> ${timeText}<br>
                <small>Punto A: ${t1.toLocaleString()}<br>Punto B: ${t2.toLocaleString()}</small>
            </div>
        `;

        // popup centrado
        const midLat = (p1.lat + p2.lat) / 2;
        const midLon = (p1.lon + p2.lon) / 2;

        // cerrar popup anterior si existe
        safeClosePopup();

        currentPopup = L.popup({ maxWidth: 350 })
            .setLatLng([midLat, midLon])
            .setContent(popupHtml)
            .openOn(map);

        // dibujar linea temporal temporal (opcional)
        try {
            if (tempLine) { map.removeLayer(tempLine); tempLine = null; }
            tempLine = L.polyline([[p1.lat, p1.lon], [p2.lat, p2.lon]], {
                color: 'limegreen',
                weight: 2,
                dashArray: '6'
            }).addTo(map);
        } catch(e){ tempLine = null; }

        // Restaurar colores y limpiar después de 2s
        const restoreColor = defaultColorForMode(currentMode);
        setTimeout(() => {
            try { p1.markerRef.setStyle({ fillColor: restoreColor }); } catch(e){}
            try { p2.markerRef.setStyle({ fillColor: restoreColor }); } catch(e){}
            // quitar linea temporal
            if (tempLine) { try { map.removeLayer(tempLine); } catch(e){} tempLine = null; }
            selectedPoints = [];
            // dejamos el popup visible (el usuario pidió cerrar al cambiar de modo)
        }, 2000);
    }
}

// --- Map & datos ---
function initMap() {
    map = L.map('map').setView([0, 0], 2);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    }).addTo(map);
}

function setDefaultDates() {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - 60*60*1000);
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    oneHourAgo.setMinutes(oneHourAgo.getMinutes() - oneHourAgo.getTimezoneOffset());
    const startEl = document.getElementById('start-datetime');
    const endEl = document.getElementById('end-datetime');
    if (startEl && endEl) {
        startEl.value = oneHourAgo.toISOString().slice(0,16);
        endEl.value = now.toISOString().slice(0,16);
        startEl.max = now.toISOString().slice(0,16);
        endEl.max = now.toISOString().slice(0,16);
    }
}

function updateDateTimeLimits() {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    const max = now.toISOString().slice(0,16);
    const s = document.getElementById('start-datetime');
    const e = document.getElementById('end-datetime');
    if (s) s.max = max;
    if (e) e.max = max;
}

function setupDateValidations() {
    const s = document.getElementById('start-datetime');
    const e = document.getElementById('end-datetime');
    if (s) s.addEventListener('change', function() {
        if (e) {
            e.min = this.value;
            if (e.value && e.value < this.value) e.value = this.value;
        }
    });
    if (e) e.addEventListener('change', function(){
        if (s && this.value < s.value) {
            alert('La fecha final no puede ser anterior a la fecha inicial');
            this.value = s.value;
        }
    });
}

function clearMap() {
    // remover marcadores y polilinea
    locationMarkers.forEach(m => {
        try { map.removeLayer(m); } catch(e){}
    });
    locationMarkers = [];
    if (polyline) {
        try { map.removeLayer(polyline); } catch(e){}
        polyline = null;
    }
    if (marker) {
        try { map.removeLayer(marker); } catch(e){}
        marker = null;
    }
    locations = [];
    // limpiar popup y selección temporal
    safeClosePopup();
    if (tempLine) { try { map.removeLayer(tempLine); } catch(e){} tempLine = null; }
    selectedPoints = [];
}

// update live
function updateLiveData() {
    if (currentMode !== 'live') return;
    fetch('/api/location')
        .then(r => r.json())
        .then(data => {
            try {
                document.getElementById('latitude').textContent = data.latitude || 'N/A';
                document.getElementById('longitude').textContent = data.longitude || 'N/A';
                document.getElementById('datetime').textContent = data.datetime || 'N/A';
            } catch(e){}

            if (data.latitude && data.longitude) {
                const lat = parseFloat(data.latitude);
                const lon = parseFloat(data.longitude);

                const isNewLocation = locations.length === 0 ||
                    locations[locations.length - 1][0] !== lat ||
                    locations[locations.length - 1][1] !== lon;

                if (isNewLocation) {
                    locations.push([lat, lon]);

                    const locationMarker = L.circleMarker([lat, lon], {
                        radius: 12,
                        fillColor: defaultColorForMode('live'),
                        color: "#ffffff",
                        weight: 2,
                        opacity: 1,
                        fillOpacity: 0.8
                    }).addTo(map);

                    // bind click pasando la referencia del marcador
                    locationMarker.on('click', () => handleMarkerClick(lat, lon, data.datetime, locationMarker));

                    locationMarkers.push(locationMarker);

                    if (locations.length > 20) {
                        locations.shift();
                        const oldMarker = locationMarkers.shift();
                        try { map.removeLayer(oldMarker); } catch(e){}
                    }

                    if (locations.length >= 2) {
                        if (polyline) {
                            polyline.setLatLngs(locations);
                        } else {
                            polyline = L.polyline(locations, {
                                color: 'red',
                                weight: 4,
                                opacity: 0.9,
                                smoothFactor: 1
                            }).addTo(map);
                        }
                    }
                }

                if (!marker) {
                    marker = L.marker([lat, lon]).addTo(map);
                    if (isFirstUpdate) {
                        map.setView([lat, lon], 16);
                        isFirstUpdate = false;
                    }
                } else {
                    marker.setLatLng([lat, lon]);
                    map.setView([lat, lon], map.getZoom());
                }
            }
        })
        .catch(e => {
            console.error(e);
            try {
                document.getElementById('latitude').textContent = 'Error';
                document.getElementById('longitude').textContent = 'Error';
                document.getElementById('datetime').textContent = 'Error';
            } catch(err){}
        });
}

// historial
function showHistoricalData() {
    const startDate = document.getElementById('start-datetime').value;
    const endDate = document.getElementById('end-datetime').value;
    if (!startDate || !endDate) { alert('Por favor selecciona un rango de fecha y hora'); return; }

    const now = new Date();
    const s = new Date(startDate);
    const e = new Date(endDate);
    if (s > now || e > now) { alert('No puedes seleccionar fechas futuras'); return; }
    if (e < s) { alert('La fecha final no puede ser anterior a la fecha inicial'); return; }

    fetch(`/api/location/history?start=${startDate}&end=${endDate}`)
        .then(r => r.json())
        .then(data => {
            clearMap();
            if (data.locations && data.locations.length > 0) {
                data.locations.forEach((loc) => {
                    const lat = parseFloat(loc.latitude);
                    const lon = parseFloat(loc.longitude);
                    locations.push([lat, lon]);

                    const locationMarker = L.circleMarker([lat, lon], {
                        radius: 12,
                        fillColor: defaultColorForMode('history'),
                        color: "#ffffff",
                        weight: 2,
                        opacity: 1,
                        fillOpacity: 0.8
                    }).addTo(map);

                    locationMarker.on('click', () => handleMarkerClick(lat, lon, loc.datetime, locationMarker));
                    locationMarkers.push(locationMarker);
                });

                if (locations.length >= 2) {
                    polyline = L.polyline(locations, {
                        color: 'orange',
                        weight: 4,
                        opacity: 0.9,
                        smoothFactor: 1
                    }).addTo(map);
                }

                try {
                    const bounds = L.latLngBounds(locations);
                    map.fitBounds(bounds, { padding: [50, 50] });
                } catch(e){}

                const lastLoc = data.locations[data.locations.length - 1];
                try {
                    document.getElementById('latitude').textContent = lastLoc.latitude || 'N/A';
                    document.getElementById('longitude').textContent = lastLoc.longitude || 'N/A';
                    document.getElementById('datetime').textContent = lastLoc.datetime || 'N/A';
                } catch(e){}
            } else {
                alert('No se encontraron ubicaciones en el rango seleccionado');
            }
        })
        .catch(e => {
            console.error(e);
            alert('Error al cargar los datos históricos');
        });
}

// --- Modos ---
function switchToHistoryMode() {
    const prevMode = currentMode;
    // Cerrar popup y restaurar marcadores seleccionados del modo anterior
    safeClosePopup();
    restoreSelectedMarkers(prevMode);

    currentMode = 'history';
    if (liveInterval) { clearInterval(liveInterval); liveInterval = null; }
    document.getElementById('current-mode').textContent = 'Histórico';
    document.getElementById('current-mode').className = 'text-orange-400';
    const liveInd = document.getElementById('live-indicator');
    if (liveInd) liveInd.style.display = 'none';

    showHistoricalData();
}

function switchToLiveMode() {
    const prevMode = currentMode;
    safeClosePopup();
    restoreSelectedMarkers(prevMode);

    currentMode = 'live';
    clearMap();
    isFirstUpdate = true;
    document.getElementById('current-mode').textContent = 'En Vivo';
    document.getElementById('current-mode').className = 'text-green-400';
    const liveInd = document.getElementById('live-indicator');
    if (liveInd) liveInd.style.display = 'flex';

    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(updateLiveData, 1000);
    updateLiveData();
}

// Tema y UI
function toggleDarkMode() {
    const html = document.documentElement;
    const btn = document.getElementById('toggle-dark');
    if (html.classList.contains('dark')) {
        html.classList.replace('dark','light');
        if (btn) btn.textContent = 'Modo Oscuro';
        localStorage.setItem('theme','light');
    } else {
        html.classList.replace('light','dark');
        if (btn) btn.textContent = 'Modo Claro';
        localStorage.setItem('theme','dark');
    }
}

function initializeTheme() {
    const html = document.documentElement;
    const btn = document.getElementById('toggle-dark');
    const saved = localStorage.getItem('theme') || 'dark';
    html.classList.remove('light','dark');
    if (saved === 'light') {
        html.classList.add('light');
        if (btn) btn.textContent = 'Modo Oscuro';
    } else {
        html.classList.add('dark');
        if (btn) btn.textContent = 'Modo Claro';
    }
}

// --- Inicialización DOM ---
document.addEventListener('DOMContentLoaded', function() {
    initializeTheme();
    initMap();
    setDefaultDates();
    setupDateValidations();

    const btnHist = document.getElementById('show-history');
    const btnLive = document.getElementById('show-live');
    const btnToggle = document.getElementById('toggle-dark');

    if (btnHist) btnHist.addEventListener('click', switchToHistoryMode);
    if (btnLive) btnLive.addEventListener('click', switchToLiveMode);
    if (btnToggle) btnToggle.addEventListener('click', toggleDarkMode);

    // Empezar en vivo
    switchToLiveMode();

    // Actualizar limites de fecha cada minuto
    setInterval(updateDateTimeLimits, 60000);
});
