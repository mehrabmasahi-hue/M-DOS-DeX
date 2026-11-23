/* M-DOS Connect — native desktop module, no external service or CDN required. */
(function () {
  'use strict';
  if (window.__MDOS_CONNECT_V1__) return;
  window.__MDOS_CONNECT_V1__ = true;

  const style = document.createElement('style');
  style.textContent = `
    #mdosConnectRoot{padding:4px 2px;color:var(--fg);font:inherit}
    #mdosConnectRoot .mc-card{background:var(--pn2);border:1px solid var(--bd);border-radius:12px;padding:12px;margin:8px 0}
    #mdosConnectRoot .mc-flex{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
    #mdosConnectRoot .mc-muted{color:var(--mu);font-size:.86em}
    #mdosConnectRoot .mc-code{direction:ltr;unicode-bidi:plaintext;word-break:break-all;text-align:left;
      padding:10px;background:var(--bg);border:1px dashed var(--acc);border-radius:8px;font:12px/1.6 monospace}
    #mdosConnectRoot button{font:inherit}
    #mdosConnectRoot .mc-warning{color:#a04c00;font-size:.84em}
    #mdosRemoteMaintenance{position:fixed;inset:0;z-index:200000;display:flex;align-items:center;justify-content:center;
      background:rgba(9,12,18,.94);color:#fff;text-align:center;padding:24px;font-family:inherit}
    #mdosRemoteMaintenance .mc-maint-card{max-width:520px;border:1px solid #ffc40088;border-radius:18px;
      background:#191919;padding:28px;box-shadow:0 20px 80px #0009}
    #mdos-remote-focus-style{display:none}
    body.mdos-remote-focus #ic,body.mdos-remote-focus #tb{display:none!important}
    @media(max-width:600px){#mdosConnectRoot .mc-code{font-size:10px}}
  `;
  document.head.appendChild(style);

  const commands = [
    'maintenance_on','maintenance_off','lock_desktop','refresh_shell','restart_shell',
    'hide_icons','show_icons','dark_mode','light_mode','focus_mode_on','focus_mode_off',
    'phonelink_disable','phonelink_enable','mstore_disable','mstore_enable',
    'backup_export','clear_temp','status','notify_user','wallpaper_reset'
  ];
  const labels = {
    maintenance_on:'ورود به حالت تعمیرات', maintenance_off:'خروج از حالت تعمیرات',
    lock_desktop:'قفل محیط M-DOS', refresh_shell:'بازآوری دسکتاپ',
    restart_shell:'راه‌اندازی مجدد پوسته', hide_icons:'مخفی‌کردن آیکون‌ها',
    show_icons:'نمایش آیکون‌ها', dark_mode:'تم تاریک', light_mode:'تم روشن',
    focus_mode_on:'فعال‌سازی حالت تمرکز', focus_mode_off:'خروج از حالت تمرکز',
    phonelink_disable:'محدودکردن Phone Link', phonelink_enable:'فعال‌کردن Phone Link',
    mstore_disable:'محدودکردن M Store', mstore_enable:'فعال‌کردن M Store',
    backup_export:'خروجی پشتیبان تنظیمات', clear_temp:'پاک‌سازی داده موقت',
    status:'گزارش وضعیت', notify_user:'نمایش پیام مدیریتی', wallpaper_reset:'بازنشانی پس‌زمینه'
  };

  function notify(title, message) {
    try {
      if (typeof window.toast === 'function') window.toast(title, message);
      else console.info('[M-DOS Global]', title, message);
    } catch (_) {}
  }

  function nativeInfo() {
    try {
      if (!window.MdosConnectNative) return {};
      return JSON.parse(window.MdosConnectNative.getInfoJson() || '{}');
    } catch (_) { return {}; }
  }

  function buildConnectApp(host) {
    host.innerHTML = '';
    const root = document.createElement('div');
    root.id = 'mdosConnectRoot';

    const head = document.createElement('div');
    head.className = 'mc-card';
    head.innerHTML = '<div style="font-size:20px;font-weight:900">🔗 M-DOS Connect</div><div class="mc-muted">مرکز اتصال و جفت‌سازی دستگاه M-DOS</div>';
    root.appendChild(head);

    const info = nativeInfo();
    const device = document.createElement('div');
    device.className = 'mc-card';
    device.innerHTML = '<b>مشخصات این دستگاه</b>';
    const details = document.createElement('div');
    details.className = 'mc-muted';
    details.textContent = 'شناسه: ' + (info.deviceId || 'در دسترس نیست') +
      ' · ' + (info.deviceName || 'M-DOS') + ' · شبکه محلی Wi-Fi';
    device.appendChild(details);
    root.appendChild(device);

    const keyCard = document.createElement('div');
    keyCard.className = 'mc-card';
    keyCard.innerHTML = '<b>کلید جفت‌سازی مدیر</b><p class="mc-muted">این کلید فقط برای مدیر مورد اعتماد است. آن را در اختیار افراد دیگر قرار ندهید.</p>';
    const key = document.createElement('div');
    key.className = 'mc-code';
    key.textContent = '••••••••••••••••••••••••••••••••';
    keyCard.appendChild(key);
    const row = document.createElement('div');
    row.className = 'mc-flex';
    const reveal = document.createElement('button'); reveal.className = 'btn s'; reveal.textContent = 'نمایش کلید';
    const copy = document.createElement('button'); copy.className = 'btn s'; copy.textContent = 'کپی کلید';
    const rotate = document.createElement('button'); rotate.className = 'btn s red'; rotate.textContent = 'تغییر کلید';
    let code = '';
    function loadKey() {
      try { code = window.MdosConnectNative.getPairingCode(); } catch (_) { code = ''; }
      key.textContent = code || 'کلید در دسترس نیست';
    }
    reveal.onclick = () => { if (!code) loadKey(); key.textContent = code || 'خطا'; };
    copy.onclick = async () => {
      if (!code) loadKey();
      try { await navigator.clipboard.writeText(code); notify('M-DOS Connect','کلید کپی شد.'); }
      catch (_) {
        const input = document.createElement('textarea');
        input.value = code; document.body.appendChild(input); input.select();
        try { document.execCommand('copy'); notify('M-DOS Connect','کلید کپی شد.'); }
        catch (_) { notify('کپی ناموفق','کلید را به‌صورت دستی انتخاب کنید.'); }
        input.remove();
      }
    };
    rotate.onclick = () => {
      if (!confirm('با تغییر کلید، دسترسی مدیرهای قبلی از بین می‌رود. ادامه می‌دهید؟')) return;
      try {
        code = window.MdosConnectNative.regeneratePairingCode();
        key.textContent = code;
        notify('کلید تغییر کرد','در برنامه Global Admin کلید جدید را ثبت کنید.');
      } catch (_) { notify('خطا','تغییر کلید انجام نشد.'); }
    };
    row.append(reveal, copy, rotate); keyCard.appendChild(row);
    const warning = document.createElement('p');
    warning.className = 'mc-warning';
    warning.textContent = 'این نسخه کنترل را فقط در شبکه محلی مشترک انجام می‌دهد. فرمان‌ها محیط M-DOS را تغییر می‌دهند، نه خاموشی یا تنظیمات سیستمی Android را.';
    keyCard.appendChild(warning);
    root.appendChild(keyCard);

    const modes = document.createElement('div');
    modes.className = 'mc-card';
    modes.innerHTML = '<b>۲۰ فرمان پشتیبانی‌شده</b><p class="mc-muted">اجرای فرمان‌ها از برنامه M-DOS Global Admin انجام می‌شود.</p>';
    const list = document.createElement('div');
    list.className = 'mc-flex';
    commands.forEach(command => {
      const chip = document.createElement('span');
      chip.className = 'perm-chip';
      chip.textContent = labels[command];
      list.appendChild(chip);
    });
    modes.appendChild(list); root.appendChild(modes);

    const note = document.createElement('div');
    note.className = 'mc-card mc-muted';
    note.textContent = 'برای اتصال: برنامه Global Admin را روی همان Wi-Fi باز کنید، دستگاه را جست‌وجو کنید و این کلید ۶۴ رقمی را در صفحه جفت‌سازی همان دستگاه وارد کنید.';
    root.appendChild(note);
    host.appendChild(root);
    loadKey();
  }

  function setRemoteFlag(name, value) {
    let flags = {};
    try { flags = JSON.parse(localStorage.getItem('mdosRemoteFlags') || '{}'); } catch (_) {}
    flags[name] = value;
    try { localStorage.setItem('mdosRemoteFlags', JSON.stringify(flags)); } catch (_) {}
    return flags;
  }

  function getRemoteFlags() {
    try { return JSON.parse(localStorage.getItem('mdosRemoteFlags') || '{}'); } catch (_) { return {}; }
  }

  function maintenance(on) {
    let old = document.getElementById('mdosRemoteMaintenance');
    if (!on) { if (old) old.remove(); return; }
    if (old) return;
    old = document.createElement('div');
    old.id = 'mdosRemoteMaintenance';
    old.innerHTML = '<div class="mc-maint-card"><div style="font-size:40px">🛠️</div><h2>حالت تعمیرات M-DOS</h2><p>محیط M-DOS موقتاً در حالت تعمیرات است.</p><p style="opacity:.7">برای بازگشت به حالت عادی، مدیر باید فرمان خروج از تعمیرات را ارسال کند.</p></div>';
    document.body.appendChild(old);
  }

  function exportSafeBackup() {
    const keys = ['mdosPrefs','mdosProfile','mdosEdition','mdosWeatherCity'];
    const data = { product:'M-DOS', type:'settings-only-backup', createdAt:new Date().toISOString(), data:{} };
    keys.forEach(k => { try { const v=localStorage.getItem(k); if(v!==null)data.data[k]=v; } catch (_) {} });
    const json = JSON.stringify(data,null,2);
    try {
      if (window.MdosConnectNative && typeof window.MdosConnectNative.saveBackupJson === 'function') {
        const result = window.MdosConnectNative.saveBackupJson(json);
        if (String(result).startsWith('ERROR:')) throw new Error(String(result));
        notify('پشتیبان تنظیمات','فایل در پوشه Downloads/M-DOS ذخیره شد.');
        return;
      }
    } catch (error) {
      notify('خطای پشتیبان‌گیری', String(error.message || error));
      return;
    }
    const blob = new Blob([json],{type:'application/json'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href=url; a.download='mdos-settings-backup.json';
    document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
    notify('پشتیبان تنظیمات','درخواست خروجی فایل ثبت شد.');
  }

  function handleRemoteCommand(command) {
    if (!commands.includes(command)) return false;
    try {
      switch (command) {
        case 'maintenance_on': maintenance(true); break;
        case 'maintenance_off': maintenance(false); break;
        case 'lock_desktop':
          if (typeof window.lock === 'function') window.lock();
          else notify('قفل M-DOS','قابلیت قفل در این نسخه دسکتاپ در دسترس نیست.');
          break;
        case 'refresh_shell':
          if (typeof window.apply === 'function') window.apply();
          if (typeof window.build === 'function') window.build();
          notify('M-DOS','دسکتاپ بازآوری شد.');
          break;
        case 'restart_shell': notify('M-DOS','پوسته در حال راه‌اندازی مجدد است.'); setTimeout(()=>location.reload(),700); break;
        case 'hide_icons': { const e=document.getElementById('ic'); if(e)e.style.display='none'; break; }
        case 'show_icons': { const e=document.getElementById('ic'); if(e)e.style.display='grid'; break; }
        case 'dark_mode': document.documentElement.dataset.t='d'; break;
        case 'light_mode': document.documentElement.removeAttribute('data-t'); break;
        case 'focus_mode_on':
          document.body.classList.add('mdos-remote-focus');
          break;
        case 'focus_mode_off':
          document.body.classList.remove('mdos-remote-focus');
          break;
        case 'phonelink_disable':
          setRemoteFlag('phonelinkDisabled',true);
          notify('Phone Link','محدودیت مدیریتی فعال شد.');
          break;
        case 'phonelink_enable':
          setRemoteFlag('phonelinkDisabled',false);
          notify('Phone Link','دسترسی دوباره فعال شد.');
          break;
        case 'mstore_disable':
          setRemoteFlag('mstoreDisabled',true);
          notify('M Store','محدودیت مدیریتی فعال شد.');
          break;
        case 'mstore_enable':
          setRemoteFlag('mstoreDisabled',false);
          notify('M Store','دسترسی دوباره فعال شد.');
          break;
        case 'backup_export': exportSafeBackup(); break;
        case 'clear_temp':
          ['mdosLastSearch','mdosConnectTemp','mdosRemoteNoticeTemp'].forEach(k=>localStorage.removeItem(k));
          notify('پاک‌سازی','داده‌های موقت انتخاب‌شده پاک شدند؛ فایل‌های کاربر دست‌نخورده ماندند.');
          break;
        case 'status':
          notify('وضعیت M-DOS','دستگاه آنلاین است و فرمان احراز هویت‌شده دریافت کرده است.');
          break;
        case 'notify_user':
          notify('پیام مدیر','یک پیام مدیریتی از M-DOS Global Admin دریافت شد.');
          break;
        case 'wallpaper_reset':
          document.body.style.backgroundImage='';
          const desk=document.getElementById('desk'); if(desk)desk.style.backgroundImage='';
          notify('پس‌زمینه','پس‌زمینه سفارشی موقت پاک شد.');
          break;
      }
      window.dispatchEvent(new CustomEvent('mdos-global-command',{detail:{command}}));
      return true;
    } catch (error) {
      console.error('M-DOS Global Control command failed',error);
      return false;
    }
  }

  document.addEventListener('click', function (event) {
    const flags = getRemoteFlags();
    if (!flags.phonelinkDisabled && !flags.mstoreDisabled) return;
    const target = event.target && event.target.closest ? event.target.closest('button,.ico,.li,.start-action,.fi') : null;
    if (!target) return;
    const text = (target.innerText || target.textContent || '').toLowerCase();
    const blockedPhone = flags.phonelinkDisabled && (text.includes('phone link') || text.includes('لینک تلفن'));
    const blockedStore = flags.mstoreDisabled && (text.includes('m store') || text.includes('فروشگاه'));
    if (blockedPhone || blockedStore) {
      event.preventDefault(); event.stopImmediatePropagation();
      notify('محدودیت مدیریتی','این بخش موقتاً توسط مدیر غیرفعال شده است.');
    }
  }, true);

  window.MDOSConnect = { open: buildConnectApp, handleRemoteCommand, commands: commands.slice() };

  try {
    APPS.mdosconnect = { n:'M-DOS Connect', i:'🔗', b:buildConnectApp };
    if (Array.isArray(PIN) && !PIN.includes('mdosconnect')) PIN.unshift('mdosconnect');
    if (Array.isArray(DESK) && !DESK.includes('mdosconnect')) DESK.unshift('mdosconnect');
  } catch (error) {
    console.warn('M-DOS Connect app registration skipped:', error);
  }
})();
