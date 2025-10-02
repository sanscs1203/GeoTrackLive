// Global variables
let map;
let marker = null;
let isFirstUpdate = true;
let locations = [];
let polyline = null;
let locationMarkers = [];
let liveInterval = null;
let currentMode = 'live';

// Initialize map
function initMap() {
    map = L.map('map').setView([0, 0], 2);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    }).addTo(map);
}

// Set default date
function setDefaultDates() {
    const now = new Date();
    const oneHourAgo = new Date(now.getTime() - (60 * 60 * 1000));
    
    // Adjust to local time
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    oneHourAgo.setMinutes(oneHourAgo.getMinutes() - oneHourAgo.getTimezoneOffset());

    // Set default values
    document.getElementById('start-datetime').value = oneHourAgo.toISOString().slice(0, 16);
    document.getElementById('end-datetime').value = now.toISOString().slice(0, 16);

    // Set max values
    const maxDateTime = now.toISOString().slice(0, 16);
    document.getElementById('start-datetime').max = maxDateTime;
    document.getElementById('end-datetime').max = maxDateTime;
}

// Update date/hour limits
function updateDateTimeLimits(){
    const now = new Date ();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    const maxDateTime = now.toISOString().slice(0, 16);

    document.getElementById('start-datetime').max = maxDateTime;
    document.getElementById('end-datetime').max = maxDateTime;
}

// Set date validations
function setupDateValidations() {
    // Validation when the start date changes
    document.getElementById('start-datetime').addEventListener('change', function() {
        const startDate = this.value;
        const endDateInput = document.getElementById('end-datetime');
        
        // End date can not be before start date
        endDateInput.min = startDate;
        
        // If the end date is before the start date, adjust it
        if (endDateInput.value && endDateInput.value < startDate) {
            endDateInput.value = startDate;
        }
    });
    
    // Validation when the end time changes
    document.getElementById('end-datetime').addEventListener('change', function() {
        const endDate = this.value;
        const startDateInput = document.getElementById('start-datetime');
        
        // If the end date is before the start date, show alert
        if (startDateInput.value && endDate < startDateInput.value) {
            alert('La fecha final no puede ser anterior a la fecha inicial');
            this.value = startDateInput.value;
        }
    });
}

// Clear map
function clearMap() {
    locationMarkers.forEach(m => map.removeLayer(m));
    locationMarkers = [];
    
    if (polyline) {
        map.removeLayer(polyline);
        polyline = null;
    }
    
    if (marker) {
        map.removeLayer(marker);
        marker = null;
    }
    
    locations = [];
}

// Update live data
function updateLiveData() {
    if (currentMode !== 'live') return;
    
    fetch('/api/location')
        .then(r => r.json())
        .then(data => {
            document.getElementById('latitude').textContent = data.latitude || 'N/A';
            document.getElementById('longitude').textContent = data.longitude || 'N/A';
            document.getElementById('datetime').textContent = data.datetime || 'N/A';

            if (data.latitude && data.longitude) {
                const lat = parseFloat(data.latitude);
                const lon = parseFloat(data.longitude);

                const isNewLocation = locations.length === 0 || 
                    locations[locations.length - 1][0] !== lat || 
                    locations[locations.length - 1][1] !== lon;

                if (isNewLocation) {
                    locations.push([lat, lon]);

                    const locationMarker = L.circleMarker([lat, lon], {
                        radius: 8,
                        fillColor: "#3388ff",
                        color: "#ffffff",
                        weight: 2,
                        opacity: 1,
                        fillOpacity: 0.8
                    }).addTo(map).bindPopup(`Ubicación ${locations.length}<br>Hora: ${data.datetime}`);

                    locationMarkers.push(locationMarker);

                    if (locations.length > 20) {
                        locations.shift();
                        const oldMarker = locationMarkers.shift();
                        map.removeLayer(oldMarker);
                    }

                    if (locations.length >= 2) {
                        if (polyline) {
                            polyline.setLatLngs(locations);
                        } else {
                            polyline = L.polyline(locations, {
                                color: 'red',
                                weight: 4,
                                opacity: 0.9,
                                smoothFactor: 1,
                                lineCap: 'round',
                                lineJoin: 'round'
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
            document.getElementById('latitude').textContent = 'Error';
            document.getElementById('longitude').textContent = 'Error';
            document.getElementById('datetime').textContent = 'Error';
        });
}

// Show historical data
function showHistoricalData() {
    const startDate = document.getElementById('start-datetime').value;
    const endDate = document.getElementById('end-datetime').value;
    
    if (!startDate || !endDate) {
        alert('Por favor selecciona un rango de fecha y hora');
        return;
    }

    // Validate no future dates
    const now = new Date();
    const selectedStart = new Date(startDate);
    const selectedEnd = new Date(endDate);
    
    if (selectedStart > now || selectedEnd > now) {
        alert('No puedes seleccionar fechas futuras');
        return;
    }
    
    if (selectedEnd < selectedStart) {
        alert('La fecha final no puede ser anterior a la fecha inicial');
        return;
    }
    
    fetch(`/api/location/history?start=${startDate}&end=${endDate}`)
        .then(r => r.json())
        .then(data => {
            clearMap();
            
            if (data.locations && data.locations.length > 0) {
                data.locations.forEach((loc, index) => {
                    const lat = parseFloat(loc.latitude);
                    const lon = parseFloat(loc.longitude);
                    
                    locations.push([lat, lon]);
                    
                    const locationMarker = L.circleMarker([lat, lon], {
                        radius: 6,
                        fillColor: "#ff7800",
                        color: "#ffffff",
                        weight: 2,
                        opacity: 1,
                        fillOpacity: 0.8
                    }).addTo(map).bindPopup(`Ubicación ${index + 1}<br>Hora: ${loc.datetime}`);
                    
                    locationMarkers.push(locationMarker);
                });
                
                if (locations.length >= 2) {
                    polyline = L.polyline(locations, {
                        color: 'orange',
                        weight: 4,
                        opacity: 0.9,
                        smoothFactor: 1,
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);
                }
                
                const bounds = L.latLngBounds(locations);
                map.fitBounds(bounds, { padding: [50, 50] });
                
                const lastLoc = data.locations[data.locations.length - 1];
                document.getElementById('latitude').textContent = lastLoc.latitude || 'N/A';
                document.getElementById('longitude').textContent = lastLoc.longitude || 'N/A';
                document.getElementById('datetime').textContent = lastLoc.datetime || 'N/A';
            } else {
                alert('No se encontraron ubicaciones en el rango seleccionado');
            }
        })
        .catch(e => {
            console.error(e);
            alert('Error al cargar los datos históricos');
        });
}

// Switch to history mode
function switchToHistoryMode() {
    currentMode = 'history';
    clearInterval(liveInterval);
    document.getElementById('current-mode').textContent = 'Histórico';
    document.getElementById('current-mode').className = 'text-orange-400';
    document.getElementById('live-indicator').style.display = 'none';
    showHistoricalData();
}

// Switch to live mode
function switchToLiveMode() {
    currentMode = 'live';
    clearMap();
    isFirstUpdate = true;
    document.getElementById('current-mode').textContent = 'En Vivo';
    document.getElementById('current-mode').className = 'text-green-400';
    document.getElementById('live-indicator').style.display = 'flex';
    liveInterval = setInterval(updateLiveData, 1000);
    updateLiveData();
}

// Toggle dark mode
function toggleDarkMode() {
    const htmlElement = document.documentElement;
    const toggleBtn = document.getElementById('toggle-dark');
    
    if (htmlElement.classList.contains('dark')) {
        // Switch to light mode
        htmlElement.classList.remove('dark');
        htmlElement.classList.add('light');
        toggleBtn.textContent = 'Modo Oscuro';
        localStorage.setItem('theme', 'light');
    } else {
        // Switch to dark mode
        htmlElement.classList.remove('light');
        htmlElement.classList.add('dark');
        toggleBtn.textContent = 'Modo Claro';
        localStorage.setItem('theme', 'dark');
    }
}

// Initialize theme
function initializeTheme() {
    const htmlElement = document.documentElement;
    const toggleBtn = document.getElementById('toggle-dark');
    const savedTheme = localStorage.getItem('theme') || 'dark';
    
    // Remove any existing theme classes first
    htmlElement.classList.remove('light', 'dark');
    
    if (savedTheme === 'light') {
        htmlElement.classList.add('light');
        toggleBtn.textContent = 'Modo Oscuro';
    } else {
        htmlElement.classList.add('dark');
        toggleBtn.textContent = 'Modo Claro';
    }
}

// Initialize when the DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    // Initialize theme FIRST
    initializeTheme();
    
    // Initialize the map
    initMap();
    
    // Set default dates
    setDefaultDates();
    
    // Set up date validations
    setupDateValidations();
    
    // Event listeners
    document.getElementById('show-history').addEventListener('click', switchToHistoryMode);
    document.getElementById('show-live').addEventListener('click', switchToLiveMode);
    document.getElementById('toggle-dark').addEventListener('click', toggleDarkMode);
    
    // Initialize live mode
    switchToLiveMode();
    
    // Update date limits every minute
    setInterval(updateDateTimeLimits, 60000);
});