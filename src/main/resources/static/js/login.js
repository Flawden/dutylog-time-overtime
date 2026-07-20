"use strict";
const $ = id => document.getElementById(id);
const LANGUAGE_KEY = "dutylog.language.v1";
const L_EN = {
  "Вход":"Login", "Регистрация":"Registration", "Имя пользователя":"Username", "Пароль":"Password", "Войти":"Sign in", "Создать аккаунт":"Create account", "Имя пользователя (от 3 символов)":"Username (min. 3 characters)", "Пароль (от 8 символов)":"Password (min. 8 characters)", "Неверное имя или пароль":"Invalid username or password", "Регистрация закрыта администратором":"Registration is closed by administrator", "Публичная регистрация закрыта администратором. Войдите в существующий аккаунт.":"Public registration is closed by administrator. Sign in to an existing account.", "Создаю аккаунт…":"Creating account…", "Готово, вхожу…":"Done, signing in…", "Сеть недоступна":"Network unavailable", "Запомнить меня на этом устройстве":"Remember me on this device", "Вход сохранится на 30 дней. Кнопка «Выйти» завершит его сразу.":"Keep me signed in for 30 days. The Sign out button ends it immediately."
};
const L_RU = Object.fromEntries(Object.entries(L_EN).map(([ru,en]) => [en, ru]));
function loginLang(){ try { return (localStorage.getItem(LANGUAGE_KEY) || (navigator.language || "").slice(0,2)) === "en" ? "en" : "ru"; } catch (_) { return "ru"; } }
let currentLang = loginLang();
function lt(s){ return currentLang === "en" ? (L_EN[s] || s) : (L_RU[s] || s); }
function applyLoginLanguage(lang){
  currentLang = lang === "en" ? "en" : "ru";
  try { localStorage.setItem(LANGUAGE_KEY, currentLang); } catch (_) {}
  document.documentElement.lang = currentLang;
  document.querySelectorAll('[data-login-lang]').forEach(b => b.classList.toggle('on', b.dataset.loginLang === currentLang));
  if ($("title")) $("title").textContent = $("regForm")?.hidden === false ? lt("Регистрация") : lt("Вход");
  const pairs = [["tabLogin", "Вход"], ["tabReg", "Регистрация"]];
  for (const [id, ru] of pairs) if ($(id)) $(id).textContent = lt(ru);
  document.querySelector('label[for="lu"]').textContent = lt("Имя пользователя");
  document.querySelector('label[for="lp"]').textContent = lt("Пароль");
  document.querySelector('#loginForm .btn').textContent = lt("Войти");
  const rememberText = $("rememberText");
  if (rememberText) {
    rememberText.childNodes[0].nodeValue = lt("Запомнить меня на этом устройстве");
    $("rememberHint").textContent = lt("Вход сохранится на 30 дней. Кнопка «Выйти» завершит его сразу.");
  }
  document.querySelector('label[for="ru"]').innerHTML = currentLang === 'en' ? 'Username <span style="color:var(--dim)">(min. 3 characters)</span>' : 'Имя пользователя <span style="color:var(--dim)">(от 3 символов)</span>';
  document.querySelector('label[for="rp"]').innerHTML = currentLang === 'en' ? 'Password <span style="color:var(--dim)">(min. 8 characters)</span>' : 'Пароль <span style="color:var(--dim)">(от 8 символов)</span>';
  $("regBtn").textContent = lt("Создать аккаунт");
  if (typeof applyRegistrationStatus === "function") applyRegistrationStatus();
}
document.querySelectorAll('[data-login-lang]').forEach(b => b.addEventListener('click', () => applyLoginLanguage(b.dataset.loginLang)));

/* CSRF: Spring кладёт токен в cookie XSRF-TOKEN при загрузке страницы */
function csrfToken(){
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : "";
}
async function ensureCsrf(){
  if (!csrfToken()) {
    // cookie нет (например, страница пришла из кэша service worker) —
    // тихо дёргаем сервер напрямую, он поставит XSRF-TOKEN
    try { await fetch("/login.html", { cache: "no-store", credentials: "same-origin" }); } catch (e) {}
  }
  return csrfToken();
}
ensureCsrf().then(t => { $("csrfField").value = t || ""; });

let registrationEnabled = true;
async function loadRegistrationStatus(){
  try {
    const res = await fetch("/api/auth/registration-status", { cache:"no-store", credentials:"same-origin" });
    if (!res.ok) return;
    const data = await res.json();
    registrationEnabled = data.enabled !== false;
  } catch (e) {
    // Backend всё равно проверит POST. Если статус не загрузился, не ломаем вход.
    registrationEnabled = true;
  }
  applyRegistrationStatus();
}
function applyRegistrationStatus(){
  const closed = !registrationEnabled;
  $("tabReg").hidden = closed;
  $("regStatusHint").textContent = closed ? lt("Публичная регистрация закрыта администратором. Войдите в существующий аккаунт.") : "";
  if (closed) setTab(false);
}

$("loginForm").addEventListener("submit", (e) => {
  if (!csrfToken()) {
    // токена всё ещё нет — добываем и отправляем форму сами
    e.preventDefault();
    ensureCsrf().then(t => { $("csrfField").value = t || ""; $("loginForm").submit(); });
    return;
  }
  $("csrfField").value = csrfToken();
});

function setTab(reg) {
  if (reg && !registrationEnabled) {
    setMsg(lt("Регистрация закрыта администратором"), "err");
    return;
  }
  $("tabLogin").classList.toggle("on", !reg);
  $("tabReg").classList.toggle("on", reg);
  $("loginForm").hidden = reg;
  $("regForm").hidden = !reg;
  $("title").textContent = reg ? lt("Регистрация") : lt("Вход");
  setMsg("");
}
$("tabLogin").addEventListener("click", () => setTab(false));
$("tabReg").addEventListener("click", () => setTab(true));

function setMsg(text, cls) {
  const m = $("msg");
  m.textContent = text;
  m.className = "msg" + (cls ? " " + cls : "");
}

// ?error в URL — Spring Security вернул нас после неудачного входа
if (new URLSearchParams(location.search).has("error")) {
  setMsg(lt("Неверное имя или пароль"), "err");
}

$("regForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  if (!registrationEnabled) {
    setMsg(lt("Регистрация закрыта администратором"), "err");
    return;
  }
  const username = $("ru").value.trim();
  const password = $("rp").value;
  $("regBtn").disabled = true;
  setMsg(lt("Создаю аккаунт…"));
  try {
    await ensureCsrf();
    const res = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken() },
      body: JSON.stringify({ username, password, languagePreference: currentLang }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      setMsg(body.error || (currentLang === "en" ? `Error ${res.status}` : `Ошибка ${res.status}`), "err");
      $("regBtn").disabled = false;
      return;
    }
    // Аккаунт создан — входим теми же данными через обычную форму
    setMsg(lt("Готово, вхожу…"), "ok");
    $("lu").value = username;
    $("lp").value = password;
    $("csrfField").value = csrfToken();
    $("loginForm").submit();
  } catch (err) {
    setMsg(lt("Сеть недоступна"), "err");
    $("regBtn").disabled = false;
  }
});
applyLoginLanguage(currentLang);
loadRegistrationStatus();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("/service-worker.js", { updateViaCache: "none" }).catch(() => {}));
}
