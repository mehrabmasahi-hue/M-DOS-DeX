#!/data/data/com.termux/files/usr/bin/bash
set -e

REPO="$HOME/M-DOS-DeX-repo"
cd "$REPO"

echo "=== M-DOS DeX Seasonal Update ==="

if [ -n "$(git status --porcelain)" ]; then
  echo "ERROR: ابتدا تغییرات قبلی را commit یا ذخیره کن."
  git status --short
  exit 1
fi

mkdir -p app/src/main/assets

cat > app/src/main/assets/mdos-seasonal.js <<'JS'
(() => {
  'use strict';

  const root = document.documentElement;
  const pad = n => String(n).padStart(2, '0');

  function getDeviceProfile() {
    const w = window.innerWidth;
    const h = window.innerHeight;
    const ua = navigator.userAgent || '';
    const tv = /Android TV|GoogleTV|SMART-TV|BRAVIA|AFT|TV/i.test(ua)
      || (w >= 1200 && h >= 650 && matchMedia('(hover: none)').matches);

    if (tv) return {
      type: 'tv', edition: 'full',
      maxWindows: 20, desktops: 4, effects: true
    };

    if (Math.min(w, h) < 600) return {
      type: 'phone', edition: 'lite',
      maxWindows: 3, desktops: 1, effects: false
    };

    return {
      type: 'tablet', edition: 'balanced',
      maxWindows: 8, desktops: 2, effects: true
    };
  }

  function getSeasonalTheme() {
    const d = new Date();
    const month = d.getMonth() + 1;
    const day = d.getDate();
    const weekday = d.getDay();

    if (month === 12 && day >= 20 && day <= 31) return 'christmas';
    if (month === 3 && day >= 20 && day <= 22) return 'nowruz';
    if (month === 2 && day === 11) return 'bahman22';
    if (month === 7 && day === 26) return 'mehrab-birthday';
    if (month === 11 && day === 22) return 'mdos-birthday';
    if (weekday === 4) return 'thursday';
    if (weekday === 5) return 'friday';
    return 'default';
  }

  function applyProfile() {
    const profile = getDeviceProfile();
    window.MDOSDeviceProfile = profile;
    root.dataset.mdosDevice = profile.type;
    root.dataset.mdosEdition = profile.edition;
    root.classList.toggle('mdos-lite-mode', profile.type === 'phone');
    root.classList.toggle('mdos-tv-mode', profile.type === 'tv');
  }

  function applyTheme() {
    const theme = getSeasonalTheme();
    root.dataset.mdosTheme = theme;
    root.classList.remove(
      'mdos-theme-christmas',
      'mdos-theme-nowruz',
      'mdos-theme-bahman22',
      'mdos-theme-mehrab-birthday',
      'mdos-theme-mdos-birthday',
      'mdos-theme-thursday',
      'mdos-theme-friday'
    );
    if (theme !== 'default') {
      root.classList.add('mdos-theme-' + theme);
    }
    window.MDOSSeasonalTheme = theme;
  }

  function installStyles() {
    if (document.getElementById('mdos-seasonal-styles')) return;
    const style = document.createElement('style');
    style.id = 'mdos-seasonal-styles';
    style.textContent = `
      :root[data-mdos-theme="christmas"] {
        --mdos-season-primary:#c62828;
        --mdos-season-accent:#16834a;
      }
      :root[data-mdos-theme="nowruz"] {
        --mdos-season-primary:#159b65;
        --mdos-season-accent:#e8c547;
      }
      :root[data-mdos-theme="bahman22"] {
        --mdos-season-primary:#d71920;
        --mdos-season-accent:#ffffff;
      }
      :root[data-mdos-theme="mehrab-birthday"] {
        --mdos-season-primary:#f2b705;
        --mdos-season-accent:#ff7b00;
      }
      :root[data-mdos-theme="mdos-birthday"] {
        --mdos-season-primary:#ffca28;
        --mdos-season-accent:#ff8f00;
      }
      :root[data-mdos-theme="thursday"] {
        --mdos-season-primary:#8065d8;
        --mdos-season-accent:#b39ddb;
      }
      :root[data-mdos-theme="friday"] {
        --mdos-season-primary:#168b78;
        --mdos-season-accent:#80cbc4;
      }
      :root.mdos-lite-mode *,
      :root.mdos-lite-mode *::before,
      :root.mdos-lite-mode *::after {
        animation-duration:.15s !important;
        transition-duration:.15s !important;
        backdrop-filter:none !important;
      }
    `;
    document.head.appendChild(style);
  }

  function enforceWindowLimit() {
    if (window.__MDOSWindowLimitInstalled) return;
    window.__MDOSWindowLimitInstalled = true;

    const originalLaunch = window.launch;
    if (typeof originalLaunch !== 'function') return;

    window.launch = function (...args) {
      const profile = window.MDOSDeviceProfile || getDeviceProfile();
      const windows = window.wins;
      if (windows && typeof windows === 'object') {
        const count = Object.keys(windows).filter(k => !!windows[k]).length;
        const key = args[0];
        if ((!windows[key] || !windows[key].isOpen) &&
            count >= profile.maxWindows) {
          alert('M-DOS: Maximum open windows: ' + profile.maxWindows);
          return;
        }
      }
      return originalLaunch.apply(this, args);
    };
  }

  function start() {
    applyProfile();
    applyTheme();
    installStyles();
    enforceWindowLimit();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start, { once: true });
  } else {
    start();
  }

  let resizeTimer;
  window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(applyProfile, 250);
  });

  window.addEventListener('focus', applyProfile);
  window.addEventListener('pageshow', start);

  window.MDOSRefreshTheme = applyTheme;
  window.MDOSRefreshDeviceProfile = applyProfile;
})();
JS

HTML="app/src/main/assets/index.html"

if [ ! -f "$HTML" ]; then
  echo "ERROR: index.html پیدا نشد."
  exit 1
fi

if ! grep -q 'mdos-seasonal.js' "$HTML"; then
  python - <<'PY'
from pathlib import Path
p = Path("app/src/main/assets/index.html")
s = p.read_text()
tag = '<script src="mdos-seasonal.js"></script>'
if "</body>" in s:
    s = s.replace("</body>", tag + "\n</body>", 1)
else:
    s += "\n" + tag + "\n"
p.write_text(s)
PY
fi

if command -v node >/dev/null 2>&1; then
  node --check app/src/main/assets/mdos-seasonal.js
fi

git diff --check
git add app/src/main/assets/mdos-seasonal.js "$HTML"
git commit -m "Add seasonal themes and adaptive device profiles"

git push origin HEAD

echo
echo "SUCCESS: Changes pushed to GitHub."
echo "Now check GitHub Actions for the APK build."
