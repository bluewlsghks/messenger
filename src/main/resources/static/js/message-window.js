"use strict";
(() => {
  function windowRange(items, heights, scrollTop, viewport, overscan = 8) {
    const prefix = [0];
    for (const item of items) prefix.push(prefix.at(-1) + (heights.get(item.id) || 100));
    let low = 0, high = items.length;
    while (low < high) { const mid = (low + high) >>> 1; if (prefix[mid + 1] < scrollTop) low = mid + 1; else high = mid; }
    const start = Math.max(0, low - overscan);
    let end = low;
    while (end < items.length && prefix[end] < scrollTop + viewport) end++;
    end = Math.min(items.length, Math.max(start + 1, end + overscan));
    return {start, end, top:prefix[start], bottom:prefix.at(-1) - prefix[end], total:prefix.at(-1)};
  }
  if (typeof module !== 'undefined' && module.exports) module.exports = {windowRange};
  if (typeof window !== 'undefined') window.MessageWindow = {windowRange};
})();
