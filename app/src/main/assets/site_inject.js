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

  function isSearchInput(el) {
    const type = (el.getAttribute("type") || "").toLowerCase();
    const name = (el.getAttribute("name") || "").toLowerCase();
    const role = (el.getAttribute("role") || "").toLowerCase();
    const aria = (el.getAttribute("aria-label") || "").toLowerCase();
    const ph = (el.getAttribute("placeholder") || "").toLowerCase();
    if (type === "search" || role === "searchbox" || role === "combobox") return true;
    if (name === "q" || name === "search" || name === "query") return true;
    if (aria.includes("search") || ph.includes("search") || ph.includes("google")) return true;
    return false;
  }

  function findButtonByText(keywords) {
    const btns = Array.from(document.querySelectorAll("button, [role='button'], a"));
    return btns.find(b => {
      if (!isVisibleEnabled(b)) return false;
      const t = (b.innerText || b.textContent || "").trim().toLowerCase();
      return keywords.some(k => t.includes(k));
    }) || null;
  }

  function findPromptInput(forExtend) {
    const areas = Array.from(document.querySelectorAll("textarea, [contenteditable='true']"));
    const visibleAreas = areas.filter(el => {
      if (!isVisibleEnabled(el) || el.readOnly || isSearchInput(el)) return false;
      const r = el.getBoundingClientRect();
      return r.width > 80 && r.height > 25;
    });

    if (forExtend) {
      const extBox = visibleAreas.find(el => {
        const ph = (el.getAttribute("placeholder") || "").toLowerCase();
        return ph.includes("continues") || ph.includes("what happens") || ph.includes("next");
      });
      if (extBox) return extBox;
    } else {
      const newBox = visibleAreas.find(el => {
        const ph = (el.getAttribute("placeholder") || "").toLowerCase();
        return ph.includes("enter your prompt") || ph.includes("prompt");
      });
      if (newBox) return newBox;
    }

    return visibleAreas[0] || null;
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

  function fillAndSubmit(forExtend) {
    const input = findPromptInput(forExtend);
    if (!input) {
      report("waiting", forExtend ? "Extend box nahi mila. Clip Done hone ka wait karo." : "Prompt box nahi mila.");
      return;
    }

    setValue(input, PROMPT);

    setTimeout(() => {
      const btn = forExtend
        ? findButtonByText(["generate next part", "extend", "generate"])
        : findButtonByText(["generate", "create", "submit"]);

      if (btn) {
        btn.click();
        report("started", (forExtend ? "Generate next part" : "Generate") + " [" + ORIENTATION + "] click ho gaya.");
      } else {
        report("filled", "Prompt bhar gaya hai, submit button manually dabao.");
      }
    }, 600);
  }

  try {
    const host = (location.hostname || "").toLowerCase();
    if (!host || host.includes("google.")) {
      report("waiting", "Google ya khali page khula hai. Apni target site open karo.");
      return;
    }

    const bodyText = (document.body && document.body.innerText || "").toLowerCase();
    if (bodyText.includes("captcha") || bodyText.includes("verify you are human")) {
      report("captcha", "Captcha ya verification dikh raha hai.");
      return;
    }

    if (IS_EXTEND) {
      const extBox = findPromptInput(true);
      if (extBox) {
        fillAndSubmit(true);
      } else {
        const extBtn = findButtonByText(["extend video", "extend"]);
        if (extBtn) {
          extBtn.click();
          setTimeout(() => fillAndSubmit(true), 700);
        } else {
          report("waiting", "'Extend video' button nahi mila. Clip complete hone ka wait karo.");
        }
      }
    } else {
      fillAndSubmit(false);
    }
  } catch (err) {
    report("error", err && err.message ? err.message : "JS error");
  }
})();
