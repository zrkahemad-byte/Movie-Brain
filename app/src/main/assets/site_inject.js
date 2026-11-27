(function () {
  const PROMPT = __P__;
  const ORIENTATION = __O__;
  const FRAME_B64 = __F__;
  const IS_EXTEND = __EXT__;

  function report(status, detail) {
    try {
      if (window.Bot && typeof window.Bot.report === "function") {
        window.Bot.report(String(status || ""), String(detail || ""));
      }
    } catch (e) {}
  }

  function findPromptInput() {
    const candidates = Array.from(document.querySelectorAll("textarea, input[type='text'], [contenteditable='true']"));
    return candidates.find(el => {
      const r = el.getBoundingClientRect();
      return r.width > 60 && r.height > 20 && !el.disabled && !el.readOnly;
    }) || null;
  }

  function setValue(el, val) {
    el.focus();
    if (el.isContentEditable) {
      el.textContent = val;
      el.dispatchEvent(new InputEvent("input", { bubbles: true, data: val }));
    } else {
      const proto = el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
      const desc = Object.getOwnPropertyDescriptor(proto, "value");
      if (desc && desc.set) desc.set.call(el, val);
      else el.value = val;
      el.dispatchEvent(new Event("input", { bubbles: true }));
      el.dispatchEvent(new Event("change", { bubbles: true }));
    }
  }

  function clickGenerate() {
    const btns = Array.from(document.querySelectorAll("button, [role='button']"));
    const target = btns.find(b => {
      const t = (b.innerText || b.textContent || "").trim().toLowerCase();
      return !b.disabled && (t.includes("generate") || t.includes("create") || t.includes("submit") || t.includes("send"));
    });
    if (target) {
      target.click();
      return true;
    }
    return false;
  }

  try {
    const bodyText = (document.body && document.body.innerText || "").toLowerCase();
    if (bodyText.includes("captcha") || bodyText.includes("verify you are human")) {
      report("captcha", "Captcha ya verification dikh raha hai.");
      return;
    }

    const input = findPromptInput();
    if (!input) {
      report("waiting", "Page par prompt box nahi mila. Sahi page/tool kholo.");
      return;
    }

    setValue(input, PROMPT);

    setTimeout(() => {
      const clicked = clickGenerate();
      if (clicked) {
        report("started", (IS_EXTEND ? "Extend" : "New") + " [" + ORIENTATION + "] trigger ho gaya.");
      } else {
        report("filled", "Prompt bhar diya gaya hai. Generate button manually ya selector se dabao.");
      }
    }, 500);
  } catch (err) {
    report("error", err && err.message ? err.message : "JS error");
  }
})();
