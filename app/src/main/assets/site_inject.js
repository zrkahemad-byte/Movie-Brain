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

  function isVisibleEnabled(el) {
    if (!el || el.disabled) return false;
    const style = window.getComputedStyle(el);
    if (style.display === "none" || style.visibility === "hidden" || parseFloat(style.opacity) === 0) {
      return false;
    }
    const r = el.getBoundingClientRect();
    return r.width > 10 && r.height > 10;
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
      el.textContent = "";
      el.dispatchEvent(new InputEvent("input", { bubbles: true, data: "" }));
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

  function tryClickExtend() {
    const els = Array.from(document.querySelectorAll("button, [role='button'], a, span, div"));
    const target = els.find(el => {
      if (!isVisibleEnabled(el)) return false;
      const t = (el.innerText || el.textContent || "").trim().toLowerCase();
      return t === "extend" || t.includes("extend clip") || t.includes("extend video") || t.includes("continue");
    });
    if (target) {
      target.click();
      return true;
    }
    return false;
  }

  function clickGenerate() {
    const btns = Array.from(document.querySelectorAll("button, [role='button']"));
    const target = btns.find(b => {
      if (!isVisibleEnabled(b)) return false;
      const t = (b.innerText || b.textContent || "").trim().toLowerCase();
      return t.includes("generate") || t.includes("extend") || t.includes("create") || t.includes("submit") || t.includes("send");
    });
    if (target) {
      target.click();
      return true;
    }
    return false;
  }

  function runFillAndSubmit(extendClicked) {
    const input = findPromptInput();
    if (!input) {
      report("waiting", "Page par prompt box nahi mila. Sahi page/tool kholo.");
      return;
    }

    setValue(input, PROMPT);

    setTimeout(() => {
      const clicked = clickGenerate();
      if (clicked) {
        const modeLabel = IS_EXTEND ? (extendClicked ? "Extend (button clicked)" : "Extend") : "New";
        report("started", modeLabel + " [" + ORIENTATION + "] trigger ho gaya.");
      } else {
        report("filled", "Prompt bhar diya gaya hai. Generate/Extend button manually dabao.");
      }
    }, 600);
  }

  try {
    const bodyText = (document.body && document.body.innerText || "").toLowerCase();
    if (bodyText.includes("captcha") || bodyText.includes("verify you are human")) {
      report("captcha", "Captcha ya verification dikh raha hai.");
      return;
    }

    if (IS_EXTEND) {
      const extClicked = tryClickExtend();
      setTimeout(() => runFillAndSubmit(extClicked), extClicked ? 800 : 150);
    } else {
      runFillAndSubmit(false);
    }
  } catch (err) {
    report("error", err && err.message ? err.message : "JS error");
  }
})();
