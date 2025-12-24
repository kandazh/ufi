// Hue
let currentHue = 0;
// Opacity
let currentOpacity = 1;
// Brightness
let currentValue = 1;
// Saturation
let currentSaturation = 1;
// Text color
let currentTextColor = 0;

// Home blur toggle
let homeBlurSwitch = true

// Background overlay toggle
let overlaySwitch = true;

// Color palette
function getColorByPercent(e) {
    const HValue = document.querySelector("#HValue")
    if (HValue) HValue.innerText = (+e.target.value / 100 * 255).toFixed(0)
    const value = e.target.value; // 0 ~ 100
    const h = (value / 100) * 300;
    currentHue = h;
    updateColor();
    // Save slider value to localStorage
    localStorage.setItem('colorPer', value);
}

// Brightness
function getValueByPercent(e) {
    const LValue = document.querySelector("#LValue")
    if (LValue) LValue.innerText = (+e.target.value / 100 * 255).toFixed(0)
    const value = e.target.value;
    currentValue = value / 100;
    updateColor();
    localStorage.setItem('brightPer', value);
}

// Opacity
function getOpacityByPercent(e) {
    const opacityValue = document.querySelector("#opacityValue")
    if (opacityValue) opacityValue.innerText = (+e.target.value / 100 * 255).toFixed(0)
    const value = e.target.value; // 0 ~ 100
    currentOpacity = value / 100;
    updateColor();
    // Save slider value to localStorage
    localStorage.setItem('opacityPer', value);
}

// Saturation
function getSaturationByPercent(e) {
    const SValue = document.querySelector("#SValue")
    if (SValue) SValue.innerText = (+e.target.value / 100 * 255).toFixed(0)
    const value = e.target.value; // 0 ~ 100
    currentSaturation = value / 100;
    updateColor();
    // Save slider value to localStorage
    localStorage.setItem('saturationPer', value);
}

// Text color
function updateTextColor(e) {
    const fontColorValue = document.querySelector("#fontColorValue")
    if (fontColorValue) fontColorValue.innerText = (+e.target.value / 100 * 255).toFixed(0)
    const value = e.target.value; // 0 ~ 100
    const gray = Math.round((value / 100) * 255);
    const color = `rgb(${gray}, ${gray}, ${gray})`;
    currentTextColor = color;
    updateColor();
    // Save slider value to localStorage
    localStorage.setItem('textColorPer', value);
    localStorage.setItem('textColor', color);
}

// Home blur
function updateBlurSwitch(e) {
    const value = e.target.checked;
    homeBlurSwitch = value
    updateColor()
    // Save state to localStorage
    localStorage.setItem('blurSwitch', value);
}

// Background overlay
function updateOverlaySwitch(e) {
    const value = e.target.checked;
    overlaySwitch = value
    updateColor()
    // Save state to localStorage
    localStorage.setItem('overlaySwitch', value);
}


// Update color + opacity
function updateColor() {
    const { r, g, b } = hsvToRgb(currentHue, currentSaturation, currentValue);
    const { h, s, l } = hsvToHsl(currentHue, currentSaturation, currentValue);

    // Base colors
    const lighterL = Math.min(l + 20, 100);
    const btnBaseOpacity = Math.min(currentOpacity * 1.2, 1);

    // Normal button
    const btnColor = `hsl(${Math.round(h)} ${s.toFixed(1)}% ${lighterL.toFixed(1)}% / ${(btnBaseOpacity * 100).toFixed(2)}%)`;

    // Active button (brighter, more saturated, more opaque)
    let activeS, activeL;

    if (lighterL > 80) {
        activeS = Math.min(s * 1.8, 100);
        activeL = Math.max(lighterL - 25, 20);
    } else {
        activeS = Math.min(s * 1.6, 100);
        activeL = Math.min(lighterL + 20, 60);
        activeL = Math.max(activeL, 20);
    }

    const btnActiveOpacity = Math.min(btnBaseOpacity + 0.2, 1);
    const btnActiveColor = `hsl(${Math.round(h)} ${activeS.toFixed(1)}% ${activeL.toFixed(1)}% / ${(btnActiveOpacity * 100).toFixed(2)}%)`;

    // Disabled button (desaturated, more transparent)
    const btnDisabledOpacity = Math.max(btnBaseOpacity - 0.2, 0.1);
    const btnDisabledColor = `hsl(${Math.round(h)} 0% ${lighterL.toFixed(1)}% / ${(btnDisabledOpacity * 100).toFixed(2)}%)`;

    const color = `rgba(${r}, ${g}, ${b}, ${currentOpacity})`;

    // Update CSS variables in :root
    document.documentElement.style.setProperty('--dark-bgi-color', color);
    document.documentElement.style.setProperty('--dark-tag-color', color);
    document.documentElement.style.setProperty('--dark-btn-color', btnColor);
    document.documentElement.style.setProperty('--dark-title-color', btnActiveColor);
    document.documentElement.style.setProperty('--dark-btn-color-active', btnActiveColor);
    document.documentElement.style.setProperty('--dark-btn-disabled-color', btnDisabledColor);
    document.documentElement.style.setProperty('--dark-text-color', currentTextColor);
    document.documentElement.style.setProperty('--blur-rate', homeBlurSwitch ? "4px" : "0");

    // Safari fix: -webkit-backdrop-filter does not support CSS variables
    document.querySelectorAll('.statusCard,thead,tbody,input')?.forEach(el => {
        homeBlurSwitch ? el.classList.add('blur-px') : el.classList.remove('blur-px')
    })
    const _style = document.createElement('style')
    _style.innerHTML = `.deviceList li {backdrop-filter: blur(${homeBlurSwitch ? "4px" : "0"}) !important;-webkit-backdrop-filter: blur(${homeBlurSwitch ? "4px" : "0"}) !important;}`
    document.querySelector('.status-container')?.insertAdjacentElement('beforebegin', _style)


    const el = document.querySelector('body');
    el.style.transform = 'translateZ(0)';  // Force a GPU layer


    document.querySelector('#BG_OVERLAY').style.backgroundColor = overlaySwitch ? `var(--dark-bgi-color)` : 'transparent';
    // Persist to localStorage
    localStorage.setItem('themeColor', currentHue);
}

// Load theme data
const initTheme = async (sync = false) => {
    const isCloudSync = document.querySelector("#isCloudSync")

    const isSync = localStorage.getItem("isCloudSync", isCloudSync.checked)
    if (isSync == true || isSync == "true" || sync) {
        // Fetch theme data from the cloud
        let result = null
        try {
            result = await (await fetchWithTimeout(KANO_baseURL + "/get_theme", {
                method: 'get'
            })).json()
        } catch (e) {
            result = null
            console.error('Failed to fetch theme data from cloud:', e)
        }

        if (result) {
            Object.keys(result).forEach((key) => {
                localStorage.setItem(key, result[key])
            })
        }
    }
    let color = localStorage.getItem('themeColor');
    let colorPer = localStorage.getItem('colorPer');
    let opacityPer = localStorage.getItem('opacityPer');
    let value = localStorage.getItem('brightPer');
    let saturation = localStorage.getItem('saturationPer');
    let textColor = localStorage.getItem('textColor');
    let textColorPer = localStorage.getItem('textColorPer');
    let blur = localStorage.getItem('blurSwitch');
    let overlay = localStorage.getItem('overlaySwitch');

    if (blur == null || blur == undefined) {
        blur = "true"
        localStorage.setItem('blurSwitch', blur);
    }

    if (overlay == null || overlay == undefined) {
        overlay = "true"
        localStorage.setItem('overlaySwitch', overlay);
    }

    if (color == null || color == undefined) {
        color = 201;
        localStorage.setItem('themeColor', color);
    }
    if (colorPer == null || colorPer == undefined) {
        colorPer = 67;
        localStorage.setItem('colorPer', colorPer);
    }
    if (opacityPer == null || opacityPer == undefined) {
        opacityPer = 21;
        localStorage.setItem('opacityPer', opacityPer);
    }
    if (value == null || value == undefined) {
        value = 21;
        localStorage.setItem('brightPer', value);
    }
    if (saturation == null || saturation == undefined) {
        saturation = 100;
        localStorage.setItem('saturationPer', saturation);
    }
    if (textColor == null || textColor == undefined) {
        textColor = 'rgba(255, 255, 255, 1)';
        localStorage.setItem('textColor', textColor);
    }
    if (textColorPer == null || textColorPer == undefined) {
        textColorPer = 100;
        localStorage.setItem('textColorPer', textColorPer);
    }

    homeBlurSwitch = blur == "true"
    overlaySwitch = overlay == "true"
    currentHue = color;
    currentOpacity = opacityPer / 100;
    currentValue = value / 100;
    currentSaturation = saturation / 100;
    currentTextColor = textColor;
    updateColor();
    try {
        document.querySelector("#colorEl").value = colorPer;
        document.querySelector("#opacityEl").value = opacityPer;
        document.querySelector("#brightEl").value = value;
        document.querySelector("#saturationEl").value = saturation;
        document.querySelector("#textColorEl").value = textColorPer;
        document.querySelector("#blurSwitch").checked = homeBlurSwitch;
        document.querySelector("#overlaySwitch").checked = overlaySwitch;
        document.querySelector("#fontColorValue").innerText = (+textColorPer / 100 * 255).toFixed(0)
        document.querySelector("#HValue").innerText = (+colorPer / 100 * 255).toFixed(0)
        document.querySelector("#SValue").innerText = (+saturation / 100 * 255).toFixed(0)
        document.querySelector("#LValue").innerText = (+value / 100 * 255).toFixed(0)
        document.querySelector("#opacityValue").innerText = (+opacityPer / 100 * 255).toFixed(0)
    } catch (e) {
        createToast(e.message, 'red')
    }
    isCloudSync.checked = isSync == "true"
}
initTheme();
